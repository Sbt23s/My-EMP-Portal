package com.pixous.hrportal.modules.user.dto;

import java.util.List;

/** The signed-in employee's team (designation) name and its active members. */
public record MyTeamResponse(
        String teamName,
        List<UserSummary> members
) {}
