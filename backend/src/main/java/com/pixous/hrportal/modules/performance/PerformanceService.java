package com.pixous.hrportal.modules.performance;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.modules.performance.dto.PerformanceGoalResponse;
import com.pixous.hrportal.modules.performance.dto.PerformanceReviewResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PerformanceService {

    private final PerformanceGoalRepository goalRepository;
    private final PerformanceReviewRepository reviewRepository;

    public PerformanceService(PerformanceGoalRepository goalRepository, PerformanceReviewRepository reviewRepository) {
        this.goalRepository = goalRepository;
        this.reviewRepository = reviewRepository;
    }

    @Transactional
    public PerformanceGoalResponse createGoal(Long userId, String title, String description) {
        PerformanceGoal g = new PerformanceGoal();
        g.setUserId(userId);
        g.setTitle(title);
        g.setDescription(description);
        g = goalRepository.save(g);
        return new PerformanceGoalResponse(g.getId(), g.getUserId(), g.getTitle(), g.getDescription(), g.getProgress(), g.getStatus(), g.getCreatedAt(), g.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public List<PerformanceGoalResponse> getMyGoals(Long userId) {
        return goalRepository.findByUserId(userId).stream()
                .map(g -> new PerformanceGoalResponse(g.getId(), g.getUserId(), g.getTitle(), g.getDescription(), g.getProgress(), g.getStatus(), g.getCreatedAt(), g.getUpdatedAt()))
                .toList();
    }

    @Transactional
    public PerformanceReviewResponse createReview(Long userId, Long managerId, String period) {
        PerformanceReview r = new PerformanceReview();
        r.setUserId(userId);
        r.setManagerId(managerId);
        r.setReviewPeriod(period);
        r = reviewRepository.save(r);
        return mapReview(r);
    }

    @Transactional(readOnly = true)
    public List<PerformanceReviewResponse> getMyReviews(Long userId) {
        return reviewRepository.findByUserId(userId).stream()
                .map(this::mapReview)
                .toList();
    }
    
    @Transactional(readOnly = true)
    public List<PerformanceReviewResponse> getTeamReviews(Long managerId) {
        return reviewRepository.findByManagerId(managerId).stream()
                .map(this::mapReview)
                .toList();
    }

    private PerformanceReviewResponse mapReview(PerformanceReview r) {
        return new PerformanceReviewResponse(
                r.getId(), r.getUserId(), r.getManagerId(), r.getReviewPeriod(),
                r.getSelfRating(), r.getSelfComment(),
                r.getManagerRating(), r.getManagerComment(),
                r.getStatus(), r.getCreatedAt(), r.getUpdatedAt()
        );
    }
}
