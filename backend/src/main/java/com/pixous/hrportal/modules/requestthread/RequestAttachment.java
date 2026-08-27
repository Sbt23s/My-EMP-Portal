package com.pixous.hrportal.modules.requestthread;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A file attached to a leave or permission request.
 *
 * <p>Owned by a pair rather than a foreign key: {@code requestType} says which
 * kind of request and {@code requestId} which one. Two tables, one for leave
 * and one for permission, would have meant two repositories, two endpoints and
 * two of every future change, for records that differ only in what they hang
 * off.
 *
 * <p>The original filename is kept alongside the stored path so a download
 * arrives called what the person called it, rather than the opaque name the
 * storage layer generates. It is stored for display only and is never used to
 * build a path.
 */
@Getter
@Setter
@Entity
@Table(name = "request_attachments")
public class RequestAttachment {

    /** The kinds of request a file can hang off. */
    public static final String LEAVE = "LEAVE";
    public static final String PERMISSION = "PERMISSION";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_type", nullable = false, length = 20)
    private String requestType;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    /**
     * Whether this is something a browser can show inline.
     *
     * <p>The viewer renders an image and offers a link for everything else, so
     * a PDF is not squeezed into an img tag and a photograph is not hidden
     * behind a download.
     */
    public boolean isImage() {
        return contentType != null && contentType.toLowerCase().startsWith("image/");
    }
}
