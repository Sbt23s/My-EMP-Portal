package com.pixous.hrportal.modules.requestthread;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.common.StorageService;
import com.pixous.hrportal.modules.leave.LeaveRequest;
import com.pixous.hrportal.modules.leave.LeaveRequestRepository;
import com.pixous.hrportal.modules.leave.PermissionRequest;
import com.pixous.hrportal.modules.leave.PermissionRequestRepository;
import com.pixous.hrportal.modules.notification.NotificationService;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import com.pixous.hrportal.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

/**
 * Files and conversation on a leave or permission request.
 *
 * <h2>Who may look</h2>
 *
 * <p>Three people have business with a request: whoever raised it, whoever it
 * was addressed to, and whoever oversees the process. Everybody else has none,
 * and a leave request often says why somebody is unwell — so the check is made
 * here, once, and every entry point goes through it rather than each deciding
 * for itself.
 *
 * <p>Deliberately not "anyone with LEAVE_APPROVE". A Team Leader holds that
 * and may approve their own team; it does not follow that they may read the
 * medical certificate of somebody in another team.
 */
@Service
@RequiredArgsConstructor
public class RequestThreadService {

    /** A request may not become an unbounded file store. */
    private static final int MAX_FILES_PER_REQUEST = 10;
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    /**
     * What may be attached.
     *
     * <p>An allow-list rather than a block-list: the question is what a
     * medical certificate or a photograph of a document can be, and the answer
     * is short. Anything else — an archive, a script, an executable — has no
     * business on a leave request whatever it claims to be.
     */
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/heic", "image/heif",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final RequestAttachmentRepository attachments;
    private final RequestCommentRepository comments;
    private final LeaveRequestRepository leaveRepository;
    private final PermissionRequestRepository permissionRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final NotificationService notificationService;

    // ---------------------------------------------------------------- read --

    @Transactional(readOnly = true)
    public List<RequestThreadDtos.AttachmentView> listAttachments(String type, Long id) {
        String kind = normaliseType(type);
        requireAccess(kind, id);
        return attachments.findByRequestTypeAndRequestIdOrderByUploadedAtAsc(kind, id)
                .stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<RequestThreadDtos.CommentView> listComments(String type, Long id) {
        String kind = normaliseType(type);
        requireAccess(kind, id);
        return comments.findByRequestTypeAndRequestIdOrderByCreatedAtAsc(kind, id)
                .stream().map(this::toView).toList();
    }

    // --------------------------------------------------------------- write --

    @Transactional
    public RequestThreadDtos.AttachmentView attach(String type, Long id, MultipartFile file) {
        String kind = normaliseType(type);
        requireAccess(kind, id);

        if (file == null || file.isEmpty()) {
            throw ApiException.business("There is nothing to upload — the file came through empty.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw ApiException.business("That file is larger than 10 MB. "
                    + "A photograph taken on a phone can usually be sent at a smaller size.");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw ApiException.business(
                    "Only photographs, PDFs and Word documents can be attached.");
        }
        if (attachments.countByRequestTypeAndRequestId(kind, id) >= MAX_FILES_PER_REQUEST) {
            throw ApiException.business(
                    "A request can carry " + MAX_FILES_PER_REQUEST + " files at most.");
        }

        String stored = storageService.store(file, "request-attachments");

        RequestAttachment a = new RequestAttachment();
        a.setRequestType(kind);
        a.setRequestId(id);
        a.setFilePath(stored);
        // Their name, for the download only. Trimmed to the column and never
        // used to build a path -- the stored path is what StorageService gave.
        a.setFileName(safeName(file.getOriginalFilename()));
        a.setContentType(contentType);
        a.setFileSize(file.getSize());
        a.setUploadedBy(SecurityUtils.currentUserId());
        return toView(attachments.save(a));
    }

    @Transactional
    public void deleteAttachment(Long attachmentId) {
        RequestAttachment a = attachments.findById(attachmentId)
                .orElseThrow(() -> ApiException.notFound("Attachment"));
        Long me = SecurityUtils.currentUserId();
        /*
         * Only the person who uploaded it may remove it.
         *
         * An approver deleting the evidence they were sent is not a thing this
         * should allow, even by accident -- and an administrator who genuinely
         * needs a file gone can remove it at the storage layer, where the act
         * is deliberate rather than a button beside a thumbnail.
         */
        if (!a.getUploadedBy().equals(me)) {
            throw ApiException.business("You can only remove a file you uploaded.");
        }
        attachments.delete(a);
    }

    @Transactional
    public RequestThreadDtos.CommentView comment(String type, Long id,
                                                 RequestThreadDtos.CommentRequest req) {
        String kind = normaliseType(type);
        Owner owner = requireAccess(kind, id);

        RequestComment c = new RequestComment();
        c.setRequestType(kind);
        c.setRequestId(id);
        c.setAuthorId(SecurityUtils.currentUserId());
        c.setMessage(req.message().trim());
        c.setAttachmentPath(
                req.attachmentPath() == null || req.attachmentPath().isBlank()
                        ? null : req.attachmentPath().trim());
        RequestComment saved = comments.save(c);

        notifyOtherParty(kind, id, owner, saved);
        return toView(saved);
    }

    // ----------------------------------------------------------- internals --

    /** Who a request belongs to and who it was sent to. */
    private record Owner(Long raisedBy, Long requestedTo) {}

    private String normaliseType(String type) {
        String t = type == null ? "" : type.trim().toUpperCase();
        if (RequestAttachment.LEAVE.equals(t) || RequestAttachment.PERMISSION.equals(t)) {
            return t;
        }
        throw ApiException.business("Unknown request type: " + type);
    }

    /**
     * The access rule, in one place.
     *
     * @return who the request belongs to, since the caller usually needs it next
     */
    private Owner requireAccess(String kind, Long id) {
        Owner owner = ownerOf(kind, id);
        Long me = SecurityUtils.currentUserId();

        boolean mine = me != null && me.equals(owner.raisedBy());
        boolean addressedToMe = me != null && me.equals(owner.requestedTo());
        // Whoever oversees the whole process: HR and the administrators.
        boolean oversees = SecurityUtils.hasAuthority("USER_MANAGE");

        if (!mine && !addressedToMe && !oversees) {
            throw ApiException.business("This request is not yours to read.");
        }
        return owner;
    }

    private Owner ownerOf(String kind, Long id) {
        if (RequestAttachment.LEAVE.equals(kind)) {
            LeaveRequest r = leaveRepository.findById(id)
                    .orElseThrow(() -> ApiException.notFound("Leave request"));
            return new Owner(r.getUserId(), r.getRequestedTo());
        }
        PermissionRequest r = permissionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Permission request"));
        return new Owner(r.getUserId(), r.getRequestedTo());
    }

    /**
     * Tell the other side that something was said.
     *
     * <p>The applicant hears from the approver and the approver hears from the
     * applicant; nobody is told about their own message. A failure to notify
     * must not lose the comment, so it is not allowed to throw — the message is
     * already saved and is the thing that mattered.
     */
    private void notifyOtherParty(String kind, Long id, Owner owner, RequestComment c) {
        try {
            Long author = c.getAuthorId();
            Long other = author.equals(owner.raisedBy())
                    ? owner.requestedTo() : owner.raisedBy();
            if (other == null || other.equals(author)) return;

            String who = userRepository.findById(author).map(User::getName).orElse("Someone");
            String label = RequestAttachment.LEAVE.equals(kind) ? "leave" : "permission";
            String link = RequestAttachment.LEAVE.equals(kind)
                    ? "/leave/approvals" : "/leave/permissions";

            notificationService.createAndPush(other,
                    "New comment on a " + label + " request",
                    who + ": " + preview(c.getMessage()),
                    "LEAVE", link);
        } catch (Exception ignored) {
            // See the note above: the comment is saved either way.
        }
    }

    private static String preview(String message) {
        String one = message.replaceAll("\\s+", " ").trim();
        return one.length() <= 90 ? one : one.substring(0, 89) + "…";
    }

    private static String safeName(String original) {
        if (original == null || original.isBlank()) return "attachment";
        // The last segment only: a browser may send a path, and the name is
        // for display, so anything that looks like a directory is dropped.
        String base = original.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).trim();
        if (base.isEmpty()) return "attachment";
        return base.length() <= 255 ? base : base.substring(base.length() - 255);
    }

    private RequestThreadDtos.AttachmentView toView(RequestAttachment a) {
        return new RequestThreadDtos.AttachmentView(
                a.getId(),
                a.getFileName(),
                a.getContentType(),
                a.getFileSize(),
                a.isImage(),
                a.getFilePath(),
                userRepository.findById(a.getUploadedBy()).map(User::getName).orElse("User"),
                a.getUploadedAt());
    }

    private RequestThreadDtos.CommentView toView(RequestComment c) {
        User author = userRepository.findById(c.getAuthorId()).orElse(null);
        return new RequestThreadDtos.CommentView(
                c.getId(),
                c.getAuthorId(),
                author != null ? author.getName() : "User",
                author != null ? author.getEmployeeCode() : null,
                c.getMessage(),
                c.getAttachmentPath(),
                c.getCreatedAt());
    }
}
