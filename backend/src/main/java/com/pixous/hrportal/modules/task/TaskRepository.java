package com.pixous.hrportal.modules.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByAssignedToOrderByCreatedAtDesc(Long assignedTo);

    List<Task> findAllByOrderByCreatedAtDesc();

    List<Task> findByTeamBatchId(String teamBatchId);

    /** Everything still open — what the workload count and the reminders read. */
    List<Task> findByStatusNot(String status);
}
