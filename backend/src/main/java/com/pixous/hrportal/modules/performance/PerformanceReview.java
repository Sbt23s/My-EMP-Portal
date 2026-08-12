package com.pixous.hrportal.modules.performance;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "performance_reviews")
public class PerformanceReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "manager_id", nullable = false)
    private Long managerId;

    @Column(name = "review_period", nullable = false, length = 20)
    private String reviewPeriod;

    @Column(name = "self_rating")
    private Integer selfRating;

    @Column(name = "self_comment", columnDefinition = "TEXT")
    private String selfComment;

    @Column(name = "manager_rating")
    private Integer managerRating;

    @Column(name = "manager_comment", columnDefinition = "TEXT")
    private String managerComment;

    @Column(nullable = false, length = 20)
    private String status = "DRAFT"; // DRAFT|SUBMITTED|REVIEWED

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
