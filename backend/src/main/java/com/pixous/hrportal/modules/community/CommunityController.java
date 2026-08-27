package com.pixous.hrportal.modules.community;

import com.pixous.hrportal.common.StorageService;
import com.pixous.hrportal.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/communities")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;
    private final StorageService storageService;

    /**
     * Create a group. Open to the admin, to HR and to the company head — the
     * service decides, because the head is recognised by employee code rather
     * than by whichever roles his account happens to carry.
     */
    @PostMapping
    public ResponseEntity<CommunityDTOs.GroupResponse> createGroup(
            @RequestBody CommunityDTOs.CreateGroupRequest request) {
        Long adminId = SecurityUtils.currentUserId();
        return ResponseEntity.ok(communityService.createGroup(request, adminId));
    }

    /**
     * Every group, for the Communities management page. Restricted: a group is
     * only meant to be visible to the people in it, and this listing names them
     * all, so it is not something an ordinary employee should be able to read.
     * Chat asks {@code /me} instead, which is scoped to membership.
     */
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('USER_MANAGE') or hasAuthority('COMMUNITY_MANAGE')")
    public ResponseEntity<List<CommunityDTOs.GroupResponse>> getAllCommunities() {
        return ResponseEntity.ok(communityService.getAllCommunities(SecurityUtils.currentUserId()));
    }

    @GetMapping("/me")
    public ResponseEntity<List<CommunityDTOs.GroupResponse>> getMyCommunities() {
        return ResponseEntity.ok(communityService.getUserCommunities(SecurityUtils.currentUserId()));
    }

    /*
      Why a request came back the way it did.

      Community groups were reported missing from Chat and the code reads
      correctly at every step, so this says what the query actually sees for
      the caller: every non-direct group, whether they are a member, and
      whether it would be filtered out. Read-only and scoped to the caller --
      it discloses nothing they could not already see by other means.
    */
    @GetMapping("/diagnose")
    public ResponseEntity<java.util.Map<String, Object>> diagnose() {
        return ResponseEntity.ok(communityService.diagnose(SecurityUtils.currentUserId()));
    }

    /** Enabled users the signed-in user can start a private 1:1 chat with. */
    @GetMapping("/contacts")
    public ResponseEntity<List<com.pixous.hrportal.modules.user.dto.UserSummary>> getContacts() {
        return ResponseEntity.ok(communityService.getContacts(SecurityUtils.currentUserId()));
    }

    /** Find-or-create the private 1:1 conversation with the given user. */
    @PostMapping("/direct/{userId}")
    public ResponseEntity<CommunityDTOs.GroupResponse> openDirect(@PathVariable Long userId) {
        return ResponseEntity.ok(communityService.openDirect(SecurityUtils.currentUserId(), userId));
    }

    /** Find-or-create the caller's private team channel (used by the Teams page). */
    @PostMapping("/team")
    public ResponseEntity<CommunityDTOs.GroupResponse> openTeamRoom() {
        return ResponseEntity.ok(communityService.openTeamRoom(SecurityUtils.currentUserId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id) {
        communityService.deleteGroup(id, SecurityUtils.currentUserId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<com.pixous.hrportal.modules.user.dto.UserSummary>> getMembers(@PathVariable Long id) {
        return ResponseEntity.ok(communityService.getMembers(id, SecurityUtils.currentUserId()));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<Void> addMember(@PathVariable Long id, @RequestBody CommunityDTOs.AddMemberRequest request) {
        communityService.addMember(id, request.getUserId(), SecurityUtils.currentUserId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        communityService.removeMember(id, userId, SecurityUtils.currentUserId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<CommunityDTOs.ChatMessagePayload>> getMessages(@PathVariable Long id) {
        return ResponseEntity.ok(communityService.getMessages(id, SecurityUtils.currentUserId()));
    }

    /**
     * Post a message. A plain {@code {content}} body behaves as it always has;
     * a reply, a poll, an acknowledgement request or a scheduled time can be
     * added alongside it.
     */
    @PostMapping("/{id}/messages")
    public ResponseEntity<Void> sendMessage(
            @PathVariable Long id,
            @RequestBody CommunityDTOs.SendMessageRequest payload) {
        communityService.sendMessage(id, SecurityUtils.currentUserId(), payload);
        return ResponseEntity.ok().build();
    }

    // ---- search, pinning, reactions, receipts, polls ----

    /** Messages in this room whose text matches, newest first. */
    @GetMapping("/{id}/messages/search")
    public ResponseEntity<List<CommunityDTOs.ChatMessagePayload>> searchMessages(
            @PathVariable Long id, @RequestParam String q) {
        return ResponseEntity.ok(
                communityService.searchMessages(id, q, SecurityUtils.currentUserId()));
    }

    /** The pinned messages of this room. */
    @GetMapping("/{id}/messages/pinned")
    public ResponseEntity<List<CommunityDTOs.ChatMessagePayload>> pinnedMessages(@PathVariable Long id) {
        return ResponseEntity.ok(
                communityService.pinnedMessages(id, SecurityUtils.currentUserId()));
    }

    @PostMapping("/messages/{messageId}/pin")
    public ResponseEntity<Void> setPinned(@PathVariable Long messageId,
                                          @RequestBody Map<String, Object> body) {
        boolean pinned = !Boolean.FALSE.equals(body.get("pinned"));
        communityService.setPinned(messageId, pinned, SecurityUtils.currentUserId());
        return ResponseEntity.ok().build();
    }

    /** Adds the emoji, or takes it away when it is already there. */
    @PostMapping("/messages/{messageId}/reactions")
    public ResponseEntity<Void> react(@PathVariable Long messageId,
                                      @RequestBody Map<String, String> body) {
        communityService.toggleReaction(messageId, body.get("emoji"), SecurityUtils.currentUserId());
        return ResponseEntity.ok().build();
    }

    /** Records that the caller has seen this message. */
    @PostMapping("/messages/{messageId}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long messageId) {
        communityService.markRead(messageId, SecurityUtils.currentUserId());
        return ResponseEntity.ok().build();
    }

    /** Records that the caller confirms having read an announcement. */
    @PostMapping("/messages/{messageId}/acknowledge")
    public ResponseEntity<Void> acknowledge(@PathVariable Long messageId) {
        communityService.acknowledge(messageId, SecurityUtils.currentUserId());
        return ResponseEntity.ok().build();
    }

    /** Who has read and who has confirmed — sender, HR or admin only. */
    @GetMapping("/messages/{messageId}/receipts")
    public ResponseEntity<Map<String, Object>> receipts(@PathVariable Long messageId) {
        return ResponseEntity.ok(
                communityService.readReceipts(messageId, SecurityUtils.currentUserId()));
    }

    @PostMapping("/messages/{messageId}/vote")
    public ResponseEntity<Void> vote(@PathVariable Long messageId,
                                     @RequestBody Map<String, Integer> body) {
        Integer index = body.get("optionIndex");
        if (index == null) throw new IllegalArgumentException("Which choice?");
        communityService.votePoll(messageId, index, SecurityUtils.currentUserId());
        return ResponseEntity.ok().build();
    }

    // ---- how long chat history is kept ----

    @GetMapping("/retention")
    public ResponseEntity<Map<String, Object>> retention() {
        return ResponseEntity.ok(Map.of("days", communityService.retentionDays()));
    }

    @PutMapping("/retention")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('USER_MANAGE') or hasAuthority('COMMUNITY_MANAGE')")
    public ResponseEntity<Void> setRetention(@RequestBody Map<String, Integer> body) {
        Integer days = body.get("days");
        if (days == null) throw new IllegalArgumentException("How many days?");
        communityService.setRetentionDays(days, SecurityUtils.currentUserId());
        return ResponseEntity.ok().build();
    }

    /** Send a voice message — records are stored and served like other files. */
    @PostMapping("/{id}/voice")
    public ResponseEntity<Void> sendVoice(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        String path = storageService.store(file, "chat-voice");
        communityService.sendVoice(id, SecurityUtils.currentUserId(), path);
        return ResponseEntity.ok().build();
    }

    /**
     * Send one or more files to a chat — images, video, PDFs, documents — with an
     * optional caption. Stored and served exactly like voice notes.
     */
    @PostMapping("/{id}/attachments")
    public ResponseEntity<Void> sendAttachments(
            @PathVariable Long id,
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "caption", required = false) String caption) {
        List<String> paths = new java.util.ArrayList<>();
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                paths.add(storageService.store(file, "chat-files"));
            }
        }
        communityService.sendAttachments(id, SecurityUtils.currentUserId(), paths, caption);
        return ResponseEntity.ok().build();
    }

    /** Delete one of the caller's own messages. */
    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<Void> deleteMessage(@PathVariable Long messageId) {
        communityService.deleteMessage(messageId, SecurityUtils.currentUserId());
        return ResponseEntity.ok().build();
    }
}
