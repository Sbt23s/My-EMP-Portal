package com.pixous.hrportal.modules.leave;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PermissionRequestRepository extends JpaRepository<PermissionRequest, Long> {

    List<PermissionRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * One person's permissions for one day in one state.
     *
     * <p>Used by punch-out: an approved permission is what allows somebody to
     * leave before the office closes, so the rule needs to know whether one
     * exists for today and from what time it runs.
     */
    List<PermissionRequest> findByUserIdAndRequestDateAndStatus(
            Long userId, java.time.LocalDate requestDate, String status);

    @Query("SELECT p FROM PermissionRequest p WHERE p.status = 'PENDING' ORDER BY p.createdAt ASC")
    List<PermissionRequest> findAllPending();

    List<PermissionRequest> findByStatusAndRequestedToOrderByCreatedAtAsc(String status, Long requestedTo);

    List<PermissionRequest> findByRequestedToOrderByCreatedAtDesc(Long requestedTo);

    List<PermissionRequest> findAllByOrderByCreatedAtDesc();

    default List<PermissionRequest> findByStatusAndRequestedTo(String status, Long requestedTo) {
        return findByStatusAndRequestedToOrderByCreatedAtAsc(status, requestedTo);
    }

    @Query("SELECT p FROM PermissionRequest p WHERE p.status = 'PENDING' AND p.userId IN :userIds ORDER BY p.createdAt ASC")
    List<PermissionRequest> findPendingForUsers(@Param("userIds") List<Long> userIds);
}
