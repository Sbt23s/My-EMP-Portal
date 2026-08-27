package com.pixous.hrportal.modules.requestthread;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RequestAttachmentRepository extends JpaRepository<RequestAttachment, Long> {

    /** Everything attached to one request, oldest first. */
    List<RequestAttachment> findByRequestTypeAndRequestIdOrderByUploadedAtAsc(
            String requestType, Long requestId);

    /** How many files a request already carries, for the per-request cap. */
    long countByRequestTypeAndRequestId(String requestType, Long requestId);
}
