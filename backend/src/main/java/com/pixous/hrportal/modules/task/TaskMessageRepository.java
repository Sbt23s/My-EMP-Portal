package com.pixous.hrportal.modules.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskMessageRepository extends JpaRepository<TaskMessage, Long> {

    List<TaskMessage> findByTaskIdOrderBySentAtAsc(Long taskId);

    /** Counted for the chat icon's badge, without loading a single message. */
    long countByTaskId(Long taskId);

    List<TaskMessage> findByTaskIdIn(List<Long> taskIds);
}
