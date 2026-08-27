package com.pixous.hrportal.modules.requestthread;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RequestCommentRepository extends JpaRepository<RequestComment, Long> {

    /** The thread, in the order it was written. */
    List<RequestComment> findByRequestTypeAndRequestIdOrderByCreatedAtAsc(
            String requestType, Long requestId);
}
