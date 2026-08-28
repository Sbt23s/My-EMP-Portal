package com.pixous.hrportal.modules.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TeamLeaderTeamRepository extends JpaRepository<TeamLeaderTeam, Long> {

    List<TeamLeaderTeam> findByUserId(Long userId);

    /**
     * The leaders assigned to a team, matched the way designations are matched
     * everywhere else: trimmed and case-insensitive, so "QA Testing" and
     * "qa testing" are one team rather than two.
     */
    @Query("""
           SELECT t.userId FROM TeamLeaderTeam t
           WHERE LOWER(TRIM(t.teamTitle)) = LOWER(TRIM(:team))
           """)
    List<Long> findLeaderIdsForTeam(@Param("team") String team);

    boolean existsByUserIdAndTeamTitleIgnoreCase(Long userId, String teamTitle);

    void deleteByUserIdAndTeamTitleIgnoreCase(Long userId, String teamTitle);
}
