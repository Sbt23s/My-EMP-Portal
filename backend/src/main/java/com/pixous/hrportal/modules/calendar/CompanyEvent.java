package com.pixous.hrportal.modules.calendar;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * A celebration, a meeting, a training session or anything else the company puts
 * on its calendar.
 *
 * <p>Kept apart from {@code holidays} on purpose: a row there means a non-working
 * day, and payroll, loss-of-pay and the work-report reminder all read it that
 * way. Nothing here touches pay or attendance.
 */
@Getter
@Setter
@Entity
@Table(name = "company_events")
public class CompanyEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** CELEBRATION | MEETING | TRAINING | OTHER */
    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType = "OTHER";

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    /** Set only when it runs over more than one day, as training often does. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(length = 200)
    private String location;

    /** Null means the whole company; a team name limits it to that team. */
    @Column(name = "audience_team", length = 150)
    private String audienceTeam;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
