package com.pixous.hrportal.modules.requestthread;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RequestCommentRepository extends JpaRepository<RequestComment, Long> {

    /** The thread, in the order it was written. */
    List<RequestComment> findByRequestTypeAndRequestIdOrderByCreatedAtAsc(
            String requestType, Long requestId);

    /**
     * Every comment on a leave request this person is part of.
     *
     * <p>Part of means either side of it: they raised the request, or it was
     * sent to them for a decision. Their own comments are excluded -- a list
     * of things said to you should not be half your own voice.
     *
     * <p>Leave and permission are two tables with no common parent, so this is
     * two queries rather than one clever join. The alternative is a union
     * across unrelated entities, which JPA models badly and nobody reading it
     * later would thank us for.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT c FROM RequestComment c
            WHERE c.requestType = 'LEAVE'
              AND c.authorId <> :userId
              AND c.requestId IN (
                  SELECT r.id FROM LeaveRequest r
                  WHERE r.userId = :userId OR r.requestedTo = :userId)
            ORDER BY c.createdAt DESC
            """)
    List<RequestComment> findLeaveCommentsFor(
            @org.springframework.data.repository.query.Param("userId") Long userId);

    /** The same, for permission requests. */
    @org.springframework.data.jpa.repository.Query("""
            SELECT c FROM RequestComment c
            WHERE c.requestType = 'PERMISSION'
              AND c.authorId <> :userId
              AND c.requestId IN (
                  SELECT r.id FROM PermissionRequest r
                  WHERE r.userId = :userId OR r.requestedTo = :userId)
            ORDER BY c.createdAt DESC
            """)
    List<RequestComment> findPermissionCommentsFor(
            @org.springframework.data.repository.query.Param("userId") Long userId);
}
