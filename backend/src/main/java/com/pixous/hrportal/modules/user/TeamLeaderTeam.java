package com.pixous.hrportal.modules.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * An extra team a Team Leader covers, beyond the one their own designation
 * already gives them.
 *
 * <p>A team here is a designation title, which is how the rest of the system
 * decides who somebody's Team Leader is. That gives one leader per team, so a
 * team with employees but no leader carrying that designation -- QA Testing --
 * has nobody to approve its leave, permission and work-from-home requests.
 *
 * <p>Rows are additive. A leader with none behaves exactly as they did before
 * this table existed.
 */
@Entity
@Table(name = "team_leader_team")
@Getter
@Setter
public class TeamLeaderTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The Team Leader. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * The designation title of the team they also lead.
     *
     * <p>Stored as written; compared trimmed and case-folded, as designation
     * matching is everywhere else in this system.
     */
    @Column(name = "team_title", nullable = false, length = 160)
    private String teamTitle;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Who made the assignment, for the audit trail. Null for rows seeded by hand. */
    @Column(name = "created_by")
    private Long createdBy;
}
