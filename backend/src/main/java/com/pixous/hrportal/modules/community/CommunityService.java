package com.pixous.hrportal.modules.community;

import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import com.pixous.hrportal.modules.user.dto.UserSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommunityService {

    /** Prefix used to name the hidden 2-member rooms that back private 1:1 chats. */
    private static final String DM_PREFIX = "__dm__";
    /** Prefix for the private per-team rooms shown on the Teams page. */
    private static final String TEAM_PREFIX = "__team__";

    private final CommunityGroupRepository groupRepository;
    private final CommunityMemberRepository memberRepository;
    private final CommunityMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final com.pixous.hrportal.modules.notification.NotificationService notificationService;
    private final com.pixous.hrportal.common.SmsService smsService;
    private final MessageReactionRepository reactionRepository;
    private final MessageReadRepository readRepository;
    private final PollVoteRepository voteRepository;
    private final com.pixous.hrportal.modules.org.SystemSettingRepository settingRepository;

    @Transactional
    public CommunityDTOs.GroupResponse createGroup(CommunityDTOs.CreateGroupRequest request, Long adminId) {
        assertCanManage(adminId);
        if (request.getName() != null && request.getName().startsWith(DM_PREFIX)) {
            throw new IllegalArgumentException("Invalid community name.");
        }
        if (groupRepository.existsByNameIgnoreCase(request.getName())) {
            throw new IllegalArgumentException("A community group with this name already exists.");
        }

        User admin = userRepository.findById(adminId).orElseThrow();

        CommunityGroup group = new CommunityGroup();
        group.setName(request.getName());
        group.setDescription(request.getDescription());
        group.setCreatedBy(admin);
        group.setAnnouncement(request.isAnnouncement());

        CommunityGroup saved = groupRepository.save(group);

        // Auto-add creator as member
        CommunityMember member = new CommunityMember();
        member.setCommunity(saved);
        member.setUser(admin);
        memberRepository.save(member);

        return new CommunityDTOs.GroupResponse(
                saved.getId(), saved.getName(), saved.getDescription(), adminId, LocalDateTime.now(), saved.isAnnouncement()
        );
    }

    /**
     * Add somebody to a group. Anyone on the staff may be added — an employee, a
     * Team Leader, HR, the admin, the company head — so a group can be whoever it
     * needs to be. Adding the same person twice is a no-op rather than an error.
     */
    @Transactional
    public void addMember(Long communityId, Long userId, Long actorId) {
        assertCanManage(actorId);
        CommunityGroup group = groupRepository.findById(communityId).orElseThrow();
        User user = userRepository.findById(userId).orElseThrow();

        // STRICT COMPANY ISOLATION
        if (group.getCreatedBy().getCompanyId() != null && user.getCompanyId() != null &&
            !group.getCreatedBy().getCompanyId().equals(user.getCompanyId())) {
            throw new IllegalArgumentException("Cannot add an employee from a different company to this group.");
        }

        if (memberRepository.isMember(communityId, userId)) return;

        CommunityMember member = new CommunityMember();
        member.setCommunity(group);
        member.setUser(user);

        memberRepository.save(member);
    }

    @Transactional
    public void removeMember(Long communityId, Long userId, Long actorId) {
        assertCanManage(actorId);
        memberRepository.deleteByCommunity_IdAndUser_Id(communityId, userId);
    }

    /**
     * Find-or-create a private 1:1 conversation between the current user and another user.
     * These rooms are hidden from the admin community listing and only ever have two members.
     */
    @Transactional
    public CommunityDTOs.GroupResponse openDirect(Long currentUserId, Long otherUserId) {
        if (otherUserId == null || otherUserId.equals(currentUserId)) {
            throw new IllegalArgumentException("Cannot start a chat with yourself.");
        }
        User me = userRepository.findById(currentUserId).orElseThrow();
        User other = userRepository.findById(otherUserId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found."));

        // STRICT COMPANY ISOLATION
        if (me.getCompanyId() != null && other.getCompanyId() != null &&
            !me.getCompanyId().equals(other.getCompanyId())) {
            throw new IllegalArgumentException("Cannot chat with an employee from a different company.");
        }

        String name = directName(currentUserId, otherUserId);
        CommunityGroup group = groupRepository.findByName(name).orElseGet(() -> {
            CommunityGroup g = new CommunityGroup();
            g.setName(name);
            g.setDescription("");
            g.setCreatedBy(me);
            g.setAnnouncement(false);
            return groupRepository.save(g);
        });

        // Idempotently ensure exactly the two participants are members.
        ensureMember(group, me);
        ensureMember(group, other);

        return toDirectResponse(group, other);
    }

    /**
     * Find-or-create the private channel for the caller's team, and make sure
     * every teammate is a member. These rooms live only on the Teams page —
     * they are kept out of the Chat listing so that page stays as it is.
     */
    @Transactional
    public CommunityDTOs.GroupResponse openTeamRoom(Long userId) {
        User me = userRepository.findById(userId).orElseThrow();
        String title = me.getDesignationTitle() == null ? "" : me.getDesignationTitle().trim();
        if (title.isEmpty()) {
            throw new IllegalArgumentException("You are not assigned to a team yet.");
        }

        String roomName = TEAM_PREFIX + title.toLowerCase();
        CommunityGroup group = groupRepository.findByName(roomName).orElseGet(() -> {
            CommunityGroup g = new CommunityGroup();
            g.setName(roomName);
            g.setDescription(title + " team channel");
            g.setCreatedBy(me);
            g.setAnnouncement(false);
            return groupRepository.save(g);
        });

        List<User> teammates = userRepository.findTeammatesByTitleOrDesignation(title, me.getDesignationId());
        if (teammates.isEmpty()) {
            ensureMember(group, me);
        } else {
            teammates.forEach(u -> ensureMember(group, u));
        }

        return new CommunityDTOs.GroupResponse(
                group.getId(), title, group.getDescription(),
                group.getCreatedBy().getId(), group.getCreatedAt(), false);
    }

    @Transactional(readOnly = true)
    public List<CommunityDTOs.GroupResponse> getUserCommunities(Long userId) {
        // A group belongs to the people who were added to it, so only those
        // people see it here. The company announcement channel is the exception:
        // it is meant for all staff, and only admin and HR can post to it.
        // Team rooms are excluded — they are reached from the Teams page instead.
        return groupRepository.findAll().stream()
                .filter(g -> !isTeamRoom(g))
                .filter(g -> isDirect(g) || g.isAnnouncement()
                        || memberRepository.isMember(g.getId(), userId))
                .map(g -> {
                    if (isDirect(g)) {
                        if (!memberRepository.isMember(g.getId(), userId)) return null; // not my DM
                        User partner = memberRepository.findByCommunity_Id(g.getId()).stream()
                                .map(CommunityMember::getUser)
                                .filter(u -> !u.getId().equals(userId))
                                .findFirst()
                                .orElse(null);
                        if (partner == null) return null; // orphaned DM room — hide it
                        
                        // STRICT COMPANY ISOLATION
                        User me = userRepository.findById(userId).orElseThrow();
                        if (me.getCompanyId() != null && partner.getCompanyId() != null &&
                            !me.getCompanyId().equals(partner.getCompanyId())) {
                            return null;
                        }
                        
                        return toDirectResponse(g, partner);
                    }
                    return new CommunityDTOs.GroupResponse(
                            g.getId(), g.getName(), g.getDescription(), g.getCreatedBy().getId(), g.getCreatedAt(), g.isAnnouncement()
                    );
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Who may run Communities: the admin, HR, and the company head by employee
     * code. The same three who may post an announcement, which is the same idea —
     * they speak for the company rather than for a team.
     */
    private void assertCanManage(Long userId) {
        if (!isPrivileged(userId)) {
            throw new SecurityException("Only an admin or HR can manage community groups.");
        }
    }

    // Method replaced below

    @Transactional(readOnly = true)
    public List<CommunityDTOs.GroupResponse> getAllCommunities(Long requesterId) {
        assertCanManage(requesterId);
        
        User me = userRepository.findById(requesterId).orElseThrow();
        Long myCompanyId = me.getCompanyId();
        
        // Never expose the private 1:1 rooms in the admin community listing.
        // STRICT COMPANY ISOLATION: Admin only sees groups created by users in their company.
        return groupRepository.findAll().stream()
                .filter(g -> !isDirect(g))
                .filter(g -> {
                    if (myCompanyId == null) return g.getCreatedBy().getCompanyId() == null;
                    return myCompanyId.equals(g.getCreatedBy().getCompanyId());
                })
                .map(g -> new CommunityDTOs.GroupResponse(
                        g.getId(), g.getName(), g.getDescription(), g.getCreatedBy().getId(), g.getCreatedAt(), g.isAnnouncement()
                )).collect(Collectors.toList());
    }

    /** Enabled users (excluding the requester) that can be reached for a private chat. */
    @Transactional(readOnly = true)
    public List<UserSummary> getContacts(Long currentUserId) {
        User me = userRepository.findById(currentUserId).orElseThrow();
        Long myCompanyId = me.getCompanyId();
        
        return userRepository.findByEnabledTrue().stream()
                .filter(u -> !u.getId().equals(currentUserId))
                // STRICT COMPANY ISOLATION
                .filter(u -> {
                    if (myCompanyId == null) return u.getCompanyId() == null;
                    return myCompanyId.equals(u.getCompanyId());
                })
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteGroup(Long communityId, Long actorId) {
        assertCanManage(actorId);
        deleteGroup(communityId);
    }

    @Transactional
    public void deleteGroup(Long communityId) {
        // Manually delete children first to avoid foreign key constraint issues if DB cascade is missing
        messageRepository.deleteAll(messageRepository.findByCommunityIdOrderBySentAtAsc(communityId));
        memberRepository.findByCommunity_Id(communityId).forEach(memberRepository::delete);
        groupRepository.deleteById(communityId);
    }

    @Transactional(readOnly = true)
    public List<UserSummary> getMembers(Long communityId, Long requesterId) {
        // Admins/HR manage groups they aren't members of, so let them view the
        // roster; everyone else must be a member of the group.
        if (!isPrivileged(requesterId)) {
            assertMember(communityId, requesterId);
        }
        return memberRepository.findByCommunity_Id(communityId).stream()
                .map(cm -> toSummary(cm.getUser()))
                .toList();
    }

    /** True for SUPER_ADMIN / IT_HR / IT_MGR ("HR") — they can manage any community. */
    private boolean isPrivileged(Long userId) {
        if (userId == null) return false;
        return userRepository.findById(userId)
                .map(CommunityService::canAnnounce)
                .orElse(false);
    }

    private static boolean isAnnouncementRole(String code) {
        // COMPANY_ADMIN is the same job as SUPER_ADMIN under the name a tenant
        // company's own administrator carries. Left out, the one person meant to
        // speak for the company could not post an announcement to it.
        return "SUPER_ADMIN".equals(code) || "COMPANY_ADMIN".equals(code)
                || "IT_HR".equals(code) || "IT_MGR".equals(code);
    }

    /** Employee code of the one person who speaks for the company by name. */
    private static final String COMPANY_HEAD_CODE = "PIX-E100";

    /**
     * Who may post to an announcement channel: an admin, HR, and the company
     * head by employee code. The code is checked as well as the roles so this
     * holds whatever his roles happen to be.
     */
    private static boolean canAnnounce(User u) {
        if (u == null) return false;
        if (COMPANY_HEAD_CODE.equalsIgnoreCase(u.getEmployeeCode())) return true;
        return u.getRoles().stream().anyMatch(r -> isAnnouncementRole(r.getCode()));
    }

    @Transactional
    public void sendMessage(Long communityId, Long senderId, String content) {
        CommunityDTOs.SendMessageRequest req = new CommunityDTOs.SendMessageRequest();
        req.setContent(content);
        sendMessage(communityId, senderId, req);
    }

    /**
     * Post a message, which may be a reply, a poll, an announcement that asks to
     * be confirmed, or one held back until a chosen time.
     *
     * <p>A scheduled message is stored immediately but neither broadcast nor
     * notified until its time comes — the hourly job does that — so nothing is
     * lost if the server restarts in between.
     */
    @Transactional
    public void sendMessage(Long communityId, Long senderId, CommunityDTOs.SendMessageRequest req) {
        CommunityGroup group = groupRepository.findById(communityId).orElseThrow();
        User sender = userRepository.findById(senderId).orElseThrow();
        assertCanParticipate(group, sender);

        // Check announcement channel permissions
        if (group.isAnnouncement()) {
            boolean isAdminOrHr = canAnnounce(sender);
            if (!isAdminOrHr) {
                throw new SecurityException("Only administrators can post announcements to this channel.");
            }
        }

        String content = req.getContent();
        java.util.List<String> options = req.getPollOptions() == null ? null
                : req.getPollOptions().stream()
                        .map(o -> o == null ? "" : o.trim())
                        .filter(o -> !o.isEmpty()).toList();
        boolean isPoll = options != null && options.size() >= 2;
        if (options != null && !options.isEmpty() && !isPoll) {
            throw new IllegalArgumentException("A poll needs at least two choices.");
        }
        if ((content == null || content.isBlank()) && !isPoll) {
            throw new IllegalArgumentException("Nothing to send.");
        }

        // Only an announcement channel can ask to be confirmed, and only the
        // people who may post there can ask it.
        boolean requiresAck = req.isRequiresAck() && group.isAnnouncement();

        LocalDateTime scheduled = null;
        if (req.getScheduledAt() != null && !req.getScheduledAt().isBlank()) {
            try {
                scheduled = LocalDateTime.parse(req.getScheduledAt().trim());
            } catch (Exception e) {
                throw new IllegalArgumentException("Could not read the scheduled time.");
            }
            // A time already past is simply now.
            if (!scheduled.isAfter(LocalDateTime.now())) scheduled = null;
        }

        // A reply must belong to the same room as the message it answers.
        if (req.getParentId() != null) {
            CommunityMessage parent = messageRepository.findById(req.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("The message being replied to is gone."));
            if (!parent.getCommunity().getId().equals(communityId)) {
                throw new IllegalArgumentException("That message is in another conversation.");
            }
        }

        CommunityMessage msg = new CommunityMessage();
        msg.setCommunity(group);
        msg.setSender(sender);
        msg.setContent(content == null ? "" : content);
        msg.setParentId(req.getParentId());
        msg.setRequiresAck(requiresAck);
        msg.setScheduledAt(scheduled);
        if (isPoll) {
            try {
                msg.setPollOptions(new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(options));
            } catch (Exception e) {
                throw new IllegalArgumentException("Could not store the poll choices.");
            }
        }

        CommunityMessage saved = messageRepository.save(msg);

        // Held back until its time: no broadcast, no notification yet.
        if (scheduled != null) return;

        messagingTemplate.convertAndSend("/topic/community/" + communityId, toPayload(saved));

        String preview = content == null || content.isBlank()
                ? (isPoll ? "New poll" : "New message")
                : (content.length() > 60 ? content.substring(0, 60) + "…" : content);
        notifyMembers(group, sender, chatTitle(group, sender), preview);
    }

    /**
     * Notification headline that says where a chat message came from: the
     * sender's name for a 1:1, otherwise the channel it was posted in.
     */
    private String chatTitle(CommunityGroup group, User sender) {
        String name = sender.getName();
        if ("CEO".equalsIgnoreCase(name) || "PIX-E100".equalsIgnoreCase(sender.getEmployeeCode())) {
            name = "CTO";
        }
        if (isDirectRoom(group)) {
            return name + " · new personal message";
        }
        if (isTeamRoom(group)) {
            return name + " · new message in your team chat";
        }
        if (group.isAnnouncement()) {
            return name + " · new announcement in " + group.getName();
        }
        return name + " · new message in " + group.getName();
    }

    /** True for the hidden 2-member rooms that back private 1:1 chats. */
    private static boolean isDirectRoom(CommunityGroup group) {
        return group.getName() != null && group.getName().startsWith(DM_PREFIX);
    }

    /** True for the private per-team rooms surfaced on the Teams page. */
    private static boolean isTeamRoom(CommunityGroup group) {
        return group.getName() != null && group.getName().startsWith(TEAM_PREFIX);
    }

    /** Push a chat notification to every member except the sender, deep-linked to the room. */
    private void notifyMembers(CommunityGroup group, User sender, String title, String body) {
        // Team rooms live on the Teams page; everything else opens in Chat.
        String link = isTeamRoom(group) ? "/teams" : "/chat?c=" + group.getId();
        List<User> recipients = memberRepository.findByCommunity_Id(group.getId()).stream()
                .map(CommunityMember::getUser).filter(Objects::nonNull)
                .filter(u -> !u.getId().equals(sender.getId()))
                .toList();

        recipients.forEach(u -> notificationService.createAndPush(
                u.getId(), title, body, "CHAT", link));

        // Announcements also go out as SMS so nobody misses them.
        if (group.isAnnouncement()) {
            smsService.sendBulk(
                    recipients.stream()
                            .map(User::getPhone)
                            .filter(p -> p != null && !p.isBlank())
                            .toList(),
                    "Pixous HR announcement from " + sender.getName() + ": " + body);
        }
    }

    /** Send a voice message (audio already stored; {@code audioPath} is its served path). */
    @Transactional
    public void sendVoice(Long communityId, Long senderId, String audioPath) {
        CommunityGroup group = groupRepository.findById(communityId).orElseThrow();
        User sender = userRepository.findById(senderId).orElseThrow();
        assertCanParticipate(group, sender);

        if (group.isAnnouncement()) {
            boolean isAdminOrHr = canAnnounce(sender);
            if (!isAdminOrHr) {
                throw new SecurityException("Only administrators can post announcements to this channel.");
            }
        }

        CommunityMessage msg = new CommunityMessage();
        msg.setCommunity(group);
        msg.setSender(sender);
        msg.setContent("🎤 Voice message");
        msg.setAudioPath(audioPath);
        CommunityMessage saved = messageRepository.save(msg);

        messagingTemplate.convertAndSend("/topic/community/" + communityId, toPayload(saved));

        notifyMembers(group, sender, chatTitle(group, sender), "🎤 Voice message");
    }

    /**
     * Send files — images, video, PDFs or documents — with an optional caption.
     * Announcement channels stay restricted to HR and admins for posting; every
     * member can still open and download whatever was posted there.
     */
    @Transactional
    public void sendAttachments(Long communityId, Long senderId,
                                java.util.List<String> paths, String caption) {
        CommunityGroup group = groupRepository.findById(communityId).orElseThrow();
        User sender = userRepository.findById(senderId).orElseThrow();
        assertCanParticipate(group, sender);

        if (group.isAnnouncement()) {
            boolean isAdminOrHr = canAnnounce(sender);
            if (!isAdminOrHr) {
                throw new SecurityException("Only administrators can post announcements to this channel.");
            }
        }
        if (paths == null || paths.isEmpty()) {
            throw new IllegalArgumentException("No files were uploaded.");
        }

        String count = paths.size() == 1 ? "an attachment" : paths.size() + " attachments";
        boolean hasCaption = caption != null && !caption.isBlank();
        String text = hasCaption ? caption.trim() : "Sent " + count;

        CommunityMessage msg = new CommunityMessage();
        msg.setCommunity(group);
        msg.setSender(sender);
        msg.setContent(text);
        msg.setAttachments(String.join(",", paths));
        CommunityMessage saved = messageRepository.save(msg);

        messagingTemplate.convertAndSend("/topic/community/" + communityId, toPayload(saved));

        // The notification must say a file arrived, not only repeat the caption.
        notifyMembers(group, sender, chatTitle(group, sender),
                hasCaption ? caption.trim() + " (" + count + ")" : text);
    }

    /** Delete a message. Only its own sender may delete it. Broadcasts a removal signal. */
    @Transactional
    public void deleteMessage(Long messageId, Long requesterId) {
        CommunityMessage msg = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found."));
        if (msg.getSender() == null || !msg.getSender().getId().equals(requesterId)) {
            throw new SecurityException("You can only delete your own messages.");
        }
        Long communityId = msg.getCommunity().getId();
        messageRepository.delete(msg);

        CommunityDTOs.ChatMessagePayload signal = new CommunityDTOs.ChatMessagePayload();
        signal.setMessageId(messageId);
        signal.setCommunityId(communityId);
        signal.setDeleted(true);
        messagingTemplate.convertAndSend("/topic/community/" + communityId, signal);
    }

    @Transactional(readOnly = true)
    public List<CommunityDTOs.ChatMessagePayload> getMessages(Long communityId, Long requesterId) {
        CommunityGroup group = groupRepository.findById(communityId).orElseThrow();
        // Reading a group needs membership, the same as posting to it — a room
        // that is hidden from the list should not be readable by its id either.
        // The announcement channel and team rooms stay open, as above.
        if (!group.isAnnouncement() && !isTeamRoom(group)) {
            assertMember(communityId, requesterId);
        }
        // A message waiting for its time is not shown, except to whoever wrote it
        // -- they should be able to see what they have queued.
        List<CommunityMessage> all = messageRepository.findByCommunityIdOrderBySentAtAsc(communityId)
                .stream()
                .filter(m -> m.getScheduledAt() == null
                        || !m.getScheduledAt().isAfter(LocalDateTime.now())
                        || (m.getSender() != null && m.getSender().getId().equals(requesterId)))
                .toList();
        List<CommunityDTOs.ChatMessagePayload> out =
                all.stream().map(this::toPayload).collect(Collectors.toList());
        decorate(out, all, requesterId);
        return out;
    }

    private CommunityDTOs.ChatMessagePayload toPayload(CommunityMessage msg) {
        CommunityDTOs.ChatMessagePayload p = new CommunityDTOs.ChatMessagePayload();
        p.setMessageId(msg.getId());
        p.setCommunityId(msg.getCommunity().getId());
        p.setSenderId(msg.getSender().getId());
        p.setSenderName(msg.getSender().getName());
        p.setContent(msg.getContent());
        p.setAudioPath(msg.getAudioPath());
        p.setAttachments(msg.getAttachments());
        p.setSentAt(msg.getSentAt() != null ? msg.getSentAt() : LocalDateTime.now());
        p.setDeleted(false);
        p.setParentId(msg.getParentId());
        p.setPinned(msg.getPinnedAt() != null);
        p.setPinnedAt(msg.getPinnedAt());
        p.setRequiresAck(msg.isRequiresAck());
        p.setScheduledAt(msg.getScheduledAt());
        p.setPollOptions(parsePollOptions(msg.getPollOptions()));
        return p;
    }

    // ================= the newer chat features =================

    /**
     * Fills in the parts of a payload that need other tables — reactions, read
     * receipts, replies and poll votes. Done for a whole list at once rather than
     * per message, so opening a room is four queries and not four hundred.
     */
    private void decorate(List<CommunityDTOs.ChatMessagePayload> payloads,
                          List<CommunityMessage> all, Long readerId) {
        if (payloads.isEmpty()) return;
        List<Long> ids = payloads.stream().map(CommunityDTOs.ChatMessagePayload::getMessageId).toList();

        java.util.Map<Long, java.util.Map<String, Integer>> reactions = new java.util.HashMap<>();
        java.util.Map<Long, java.util.List<String>> mine = new java.util.HashMap<>();
        reactionRepository.findByMessageIdIn(ids).forEach(r -> {
            reactions.computeIfAbsent(r.getMessageId(), k -> new java.util.LinkedHashMap<>())
                    .merge(r.getEmoji(), 1, Integer::sum);
            if (r.getUserId().equals(readerId)) {
                mine.computeIfAbsent(r.getMessageId(), k -> new java.util.ArrayList<>()).add(r.getEmoji());
            }
        });

        java.util.Map<Long, Integer> reads = new java.util.HashMap<>();
        java.util.Map<Long, Integer> acks = new java.util.HashMap<>();
        java.util.Set<Long> ackedByMe = new java.util.HashSet<>();
        readRepository.findByMessageIdIn(ids).forEach(r -> {
            reads.merge(r.getMessageId(), 1, Integer::sum);
            if (r.getAcknowledgedAt() != null) {
                acks.merge(r.getMessageId(), 1, Integer::sum);
                if (r.getUserId().equals(readerId)) ackedByMe.add(r.getMessageId());
            }
        });

        java.util.Map<Long, java.util.Map<Integer, Integer>> votes = new java.util.HashMap<>();
        java.util.Map<Long, Integer> myVote = new java.util.HashMap<>();
        voteRepository.findByMessageIdIn(ids).forEach(v -> {
            votes.computeIfAbsent(v.getMessageId(), k -> new java.util.HashMap<>())
                    .merge(v.getOptionIndex(), 1, Integer::sum);
            if (v.getUserId().equals(readerId)) myVote.put(v.getMessageId(), v.getOptionIndex());
        });

        // Replies are counted from the same list, so no extra query is needed.
        java.util.Map<Long, Integer> replies = new java.util.HashMap<>();
        all.forEach(m -> {
            if (m.getParentId() != null) replies.merge(m.getParentId(), 1, Integer::sum);
        });

        payloads.forEach(p -> {
            Long id = p.getMessageId();
            p.setReactions(reactions.getOrDefault(id, java.util.Map.of()));
            p.setMyReactions(mine.getOrDefault(id, java.util.List.of()));
            p.setReadCount(reads.getOrDefault(id, 0));
            p.setAckCount(acks.getOrDefault(id, 0));
            p.setAcknowledgedByMe(ackedByMe.contains(id));
            p.setReplyCount(replies.getOrDefault(id, 0));
            if (p.getPollOptions() != null && !p.getPollOptions().isEmpty()) {
                java.util.Map<Integer, Integer> counts = votes.getOrDefault(id, java.util.Map.of());
                java.util.List<Integer> tally = new java.util.ArrayList<>();
                for (int i = 0; i < p.getPollOptions().size(); i++) {
                    tally.add(counts.getOrDefault(i, 0));
                }
                p.setPollVotes(tally);
                p.setMyVote(myVote.get(id));
            }
        });
    }

    /** A poll's labels, or null when the message is not a poll. */
    private static java.util.List<String> parsePollOptions(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(raw, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    /** Messages in a room whose text matches, newest first. */
    @Transactional(readOnly = true)
    public List<CommunityDTOs.ChatMessagePayload> searchMessages(Long communityId, String q, Long requesterId) {
        CommunityGroup group = groupRepository.findById(communityId).orElseThrow();
        if (!group.isAnnouncement() && !isTeamRoom(group)) assertMember(communityId, requesterId);
        if (q == null || q.trim().length() < 2) return List.of();
        List<CommunityMessage> found = messageRepository.search(communityId, q.trim()).stream()
                .filter(m -> m.getScheduledAt() == null || !m.getScheduledAt().isAfter(LocalDateTime.now()))
                .limit(100).toList();
        List<CommunityDTOs.ChatMessagePayload> out =
                found.stream().map(this::toPayload).collect(Collectors.toList());
        decorate(out, found, requesterId);
        return out;
    }

    /** The pinned messages of a room, newest pin first. */
    @Transactional(readOnly = true)
    public List<CommunityDTOs.ChatMessagePayload> pinnedMessages(Long communityId, Long requesterId) {
        CommunityGroup group = groupRepository.findById(communityId).orElseThrow();
        if (!group.isAnnouncement() && !isTeamRoom(group)) assertMember(communityId, requesterId);
        List<CommunityMessage> found =
                messageRepository.findByCommunityIdAndPinnedAtIsNotNullOrderByPinnedAtDesc(communityId);
        List<CommunityDTOs.ChatMessagePayload> out =
                found.stream().map(this::toPayload).collect(Collectors.toList());
        decorate(out, found, requesterId);
        return out;
    }

    /**
     * Pins or unpins a message. The sender may pin their own; anyone who runs the
     * portal may pin anything, since a pin is a room-wide act.
     */
    @Transactional
    public void setPinned(Long messageId, boolean pinned, Long actorId) {
        CommunityMessage msg = messageRepository.findById(messageId).orElseThrow();
        boolean own = msg.getSender() != null && msg.getSender().getId().equals(actorId);
        if (!own && !isPrivileged(actorId)) {
            throw new SecurityException("Only the sender, HR or an admin can pin a message.");
        }
        msg.setPinnedAt(pinned ? LocalDateTime.now() : null);
        msg.setPinnedBy(pinned ? actorId : null);
        messageRepository.save(msg);
        messagingTemplate.convertAndSend("/topic/community/" + msg.getCommunity().getId(), toPayload(msg));
    }

    /** Adds a reaction, or takes it away when it is already there. */
    @Transactional
    public void toggleReaction(Long messageId, String emoji, Long userId) {
        CommunityMessage msg = messageRepository.findById(messageId).orElseThrow();
        User user = userRepository.findById(userId).orElseThrow();
        assertCanParticipate(msg.getCommunity(), user);
        String e = emoji == null ? "" : emoji.trim();
        if (e.isEmpty() || e.length() > 16) throw new IllegalArgumentException("Invalid reaction.");

        reactionRepository.findByMessageIdAndUserIdAndEmoji(messageId, userId, e)
                .ifPresentOrElse(reactionRepository::delete, () -> {
                    MessageReaction r = new MessageReaction();
                    r.setMessageId(messageId);
                    r.setUserId(userId);
                    r.setEmoji(e);
                    reactionRepository.save(r);
                });
    }

    /**
     * Records that somebody has seen a message, and tells the room so the
     * sender's single tick becomes a double one without waiting for anything.
     *
     * <p>Broadcast only on the first read of a message by a person — the method
     * returns early otherwise — so this is one message per reader, not one per
     * time they scroll past it.
     */
    @Transactional
    public void markRead(Long messageId, Long userId) {
        if (readRepository.findByMessageIdAndUserId(messageId, userId).isPresent()) return;
        CommunityMessage msg = messageRepository.findById(messageId).orElse(null);
        if (msg == null) return;
        MessageRead r = new MessageRead();
        r.setMessageId(messageId);
        r.setUserId(userId);
        readRepository.save(r);

        // The sender is the one who cares. Never let a failed broadcast undo the
        // read itself — being seen matters more than the tick arriving instantly.
        try {
            if (msg.getCommunity() != null) {
                messagingTemplate.convertAndSend(
                        "/topic/community/" + msg.getCommunity().getId(), toPayload(msg));
            }
        } catch (Exception ignored) {
            // the next refresh will carry it
        }
    }

    /** Records that somebody has confirmed reading an announcement. */
    @Transactional
    public void acknowledge(Long messageId, Long userId) {
        CommunityMessage msg = messageRepository.findById(messageId).orElseThrow();
        if (!msg.isRequiresAck()) {
            throw new IllegalArgumentException("This message does not ask to be acknowledged.");
        }
        MessageRead r = readRepository.findByMessageIdAndUserId(messageId, userId)
                .orElseGet(() -> {
                    MessageRead fresh = new MessageRead();
                    fresh.setMessageId(messageId);
                    fresh.setUserId(userId);
                    return fresh;
                });
        if (r.getAcknowledgedAt() == null) r.setAcknowledgedAt(LocalDateTime.now());
        readRepository.save(r);
    }

    /**
     * Who has read a message and who has confirmed it, against everybody the
     * message went to — so "12 of 32 have not confirmed" can be answered.
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> readReceipts(Long messageId, Long requesterId) {
        CommunityMessage msg = messageRepository.findById(messageId).orElseThrow();
        if (!isPrivileged(requesterId)
                && !(msg.getSender() != null && msg.getSender().getId().equals(requesterId))) {
            throw new SecurityException("Only the sender, HR or an admin can see who has read a message.");
        }
        java.util.Map<Long, MessageRead> byUser = readRepository.findByMessageId(messageId).stream()
                .collect(Collectors.toMap(MessageRead::getUserId, r -> r, (a, b) -> a));

        // An announcement channel reaches every member; so does any other room.
        List<User> audience = memberRepository.findByCommunity_Id(msg.getCommunity().getId()).stream()
                .map(CommunityMember::getUser).filter(java.util.Objects::nonNull).toList();

        List<java.util.Map<String, Object>> people = audience.stream().map(u -> {
            MessageRead r = byUser.get(u.getId());
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("userId", u.getId());
            m.put("name", u.getName());
            m.put("employeeCode", u.getEmployeeCode());
            m.put("enabled", u.isEnabled());
            m.put("profileStatus", u.getProfileStatus());
            m.put("readAt", r == null ? null : r.getReadAt());
            m.put("acknowledgedAt", r == null ? null : r.getAcknowledgedAt());
            return m;
        }).toList();

        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("total", audience.size());
        out.put("readCount", people.stream().filter(m -> m.get("readAt") != null).count());
        out.put("ackCount", people.stream().filter(m -> m.get("acknowledgedAt") != null).count());
        out.put("requiresAck", msg.isRequiresAck());
        out.put("people", people);
        return out;
    }

    /** Casts or moves a vote on a poll. */
    @Transactional
    public void votePoll(Long messageId, int optionIndex, Long userId) {
        CommunityMessage msg = messageRepository.findById(messageId).orElseThrow();
        User user = userRepository.findById(userId).orElseThrow();
        assertCanParticipate(msg.getCommunity(), user);
        java.util.List<String> options = parsePollOptions(msg.getPollOptions());
        if (options == null || optionIndex < 0 || optionIndex >= options.size()) {
            throw new IllegalArgumentException("That is not one of the choices.");
        }
        PollVote v = voteRepository.findByMessageIdAndUserId(messageId, userId)
                .orElseGet(() -> {
                    PollVote fresh = new PollVote();
                    fresh.setMessageId(messageId);
                    fresh.setUserId(userId);
                    return fresh;
                });
        v.setOptionIndex(optionIndex);
        voteRepository.save(v);
    }

    /**
     * How many days of chat history to keep. Zero — the value it starts at —
     * means keep everything, so nothing is ever deleted by surprise.
     */
    @Transactional(readOnly = true)
    public int retentionDays() {
        return settingRepository.findById("chat.retention_days")
                .map(s -> {
                    try { return Integer.parseInt(s.getValue().trim()); }
                    catch (Exception e) { return 0; }
                })
                .orElse(0);
    }

    /** Sets the retention period. Admin only, checked by the caller. */
    @Transactional
    public void setRetentionDays(int days, Long actorId) {
        assertCanManage(actorId);
        if (days < 0 || days > 3650) throw new IllegalArgumentException("Between 0 and 3650 days.");
        com.pixous.hrportal.modules.org.SystemSetting s = settingRepository
                .findById("chat.retention_days")
                .orElseGet(() -> {
                    com.pixous.hrportal.modules.org.SystemSetting fresh =
                            new com.pixous.hrportal.modules.org.SystemSetting();
                    fresh.setKey("chat.retention_days");
                    return fresh;
                });
        s.setValue(String.valueOf(days));
        settingRepository.save(s);
    }

    /**
     * Deletes chat older than the retention period, and posts any scheduled
     * message whose time has come. Runs hourly.
     *
     * <p>Pinned messages are kept whatever the period: somebody pinned them on
     * purpose. Retention of zero deletes nothing at all.
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 5 * * * *")
    @Transactional
    public void chatHousekeeping() {
        LocalDateTime now = LocalDateTime.now();

        // Scheduled posts whose time has come: notify, then stop treating them
        // as scheduled so this does not fire twice.
        messageRepository.findByScheduledAtIsNotNullAndScheduledAtLessThanEqual(now).forEach(m -> {
            m.setScheduledAt(null);
            messageRepository.save(m);
            String preview = m.getContent() == null ? ""
                    : (m.getContent().length() > 60 ? m.getContent().substring(0, 60) + "…" : m.getContent());
            notifyMembers(m.getCommunity(), m.getSender(),
                    chatTitle(m.getCommunity(), m.getSender()), preview);
            messagingTemplate.convertAndSend("/topic/community/" + m.getCommunity().getId(), toPayload(m));
        });

        int days = retentionDays();
        if (days <= 0) return;
        LocalDateTime cutoff = now.minusDays(days);
        List<CommunityMessage> old = messageRepository.findBySentAtBefore(cutoff).stream()
                .filter(m -> m.getPinnedAt() == null)
                .toList();
        if (!old.isEmpty()) messageRepository.deleteAll(old);
    }

    // ---- helpers ----

    /** Guard: the requester must belong to the conversation. Keeps 1:1 chats private, even from admins. */
    private void assertMember(Long communityId, Long requesterId) {
        if (requesterId == null || !memberRepository.isMember(communityId, requesterId)) {
            throw new SecurityException("You are not a member of this conversation.");
        }
    }

    /**
     * Who may take part in a conversation.
     *
     * <p>A group a person was added to is theirs; one they were not added to is
     * not, so nothing is auto-joined any more — being able to reach a room used
     * to be enough to become a member of it, which made every group everybody's.
     *
     * <p>Two exceptions. The company announcement channel is read by all staff by
     * design, and posting to it is separately restricted to admin and HR. Team
     * rooms follow the team rather than an invitation, so a team member who has no
     * membership row yet is added on first use.
     */
    private void assertCanParticipate(CommunityGroup group, User user) {
        if (user == null) throw new SecurityException("Not authenticated.");
        if (group.isAnnouncement()) return;          // everyone reads; posting is checked separately
        if (isTeamRoom(group)) { ensureMember(group, user); return; }
        assertMember(group.getId(), user.getId());
    }

    private void ensureMember(CommunityGroup group, User user) {
        if (!memberRepository.isMember(group.getId(), user.getId())) {
            CommunityMember m = new CommunityMember();
            m.setCommunity(group);
            m.setUser(user);
            memberRepository.save(m);
        }
    }

    private static String directName(Long a, Long b) {
        long min = Math.min(a, b);
        long max = Math.max(a, b);
        return DM_PREFIX + min + "_" + max;
    }

    private static boolean isDirect(CommunityGroup g) {
        return g.getName() != null && g.getName().startsWith(DM_PREFIX);
    }

    private CommunityDTOs.GroupResponse toDirectResponse(CommunityGroup group, User partner) {
        return new CommunityDTOs.GroupResponse(
                group.getId(),
                partner.getName(),
                partner.getEmployeeCode(),
                group.getCreatedBy().getId(),
                group.getCreatedAt() != null ? group.getCreatedAt() : LocalDateTime.now(),
                false,               // isAnnouncement
                true,                // direct
                partner.getId(),
                partner.getPhotoPath()
        );
    }

    private UserSummary toSummary(User u) {
        return new UserSummary(
                u.getId(),
                u.getEmployeeCode(),
                u.getName(),
                u.getUsername(),
                u.getEmail(),
                u.getPhone(),
                u.getIndustry(),
                u.getDepartmentId(),
                u.getProfileStatus(),
                u.getPhotoPath(),
                u.getDob(),
                u.getRoles().stream().map(com.pixous.hrportal.modules.user.Role::getCode).toList(),
                u.getDesignationId(),
                u.getDesignationTitle(),
                u.getTechStack(),
                null,
                u.getCompanyId(),
                null
        );
    }
}
