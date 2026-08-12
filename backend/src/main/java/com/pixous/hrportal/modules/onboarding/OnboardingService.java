package com.pixous.hrportal.modules.onboarding;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.modules.onboarding.dto.OnboardingChecklistResponse;
import com.pixous.hrportal.modules.onboarding.dto.OnboardingTaskResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OnboardingService {

    private final OnboardingChecklistRepository checklistRepository;
    private final OnboardingTaskRepository taskRepository;

    public OnboardingService(OnboardingChecklistRepository checklistRepository, OnboardingTaskRepository taskRepository) {
        this.checklistRepository = checklistRepository;
        this.taskRepository = taskRepository;
    }

    /** User IDs of employees currently in onboarding (active checklist). */
    @Transactional(readOnly = true)
    public List<Long> onboardingUserIds() {
        return checklistRepository.findByStatus("IN_PROGRESS").stream()
                .map(OnboardingChecklist::getUserId)
                .toList();
    }

    @Transactional
    public OnboardingChecklistResponse startOnboarding(Long userId) {
        if (checklistRepository.findByUserId(userId).isPresent()) {
            throw ApiException.business("Onboarding already started for this user");
        }
        
        OnboardingChecklist c = new OnboardingChecklist();
        c.setUserId(userId);
        c = checklistRepository.save(c);

        List<String> defaultTasks = List.of(
            "Document Collection",
            "Bank Details",
            "IT Asset Assignment",
            "Team Introduction",
            "Security Training"
        );

        for (String tName : defaultTasks) {
            OnboardingTask t = new OnboardingTask();
            t.setChecklistId(c.getId());
            t.setTaskName(tName);
            taskRepository.save(t);
        }

        return getOnboarding(userId);
    }

    @Transactional(readOnly = true)
    public OnboardingChecklistResponse getOnboarding(Long userId) {
        OnboardingChecklist c = checklistRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Onboarding checklist"));
        
        List<OnboardingTaskResponse> tasks = taskRepository.findByChecklistId(c.getId()).stream()
                .map(t -> new OnboardingTaskResponse(t.getId(), t.getTaskName(), t.getDescription(), t.isCompleted(), t.getCompletedAt()))
                .toList();

        return new OnboardingChecklistResponse(c.getId(), c.getUserId(), c.getStatus(), c.getStartedAt(), c.getCompletedAt(), tasks);
    }

    @Transactional
    public OnboardingChecklistResponse completeTask(Long userId, Long taskId) {
        OnboardingChecklist c = checklistRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Onboarding checklist"));
                
        OnboardingTask t = taskRepository.findById(taskId)
                .orElseThrow(() -> ApiException.notFound("Onboarding task"));
                
        if (!t.getChecklistId().equals(c.getId())) {
            throw ApiException.business("Task does not belong to user's checklist");
        }

        t.setCompleted(true);
        t.setCompletedAt(LocalDateTime.now());
        taskRepository.save(t);

        // Check if all done
        List<OnboardingTask> allTasks = taskRepository.findByChecklistId(c.getId());
        boolean allCompleted = allTasks.stream().allMatch(OnboardingTask::isCompleted);
        if (allCompleted) {
            c.setStatus("COMPLETED");
            c.setCompletedAt(LocalDateTime.now());
            checklistRepository.save(c);
        }

        return getOnboarding(userId);
    }
}
