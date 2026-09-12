package com.pixous.hrportal.modules.user.dto;

import java.time.LocalDate;
import java.util.List;

/** Compact row used in employee directory tables. */
public record UserSummary(
        Long id,
        String employeeCode,
        String name,
        /** Login name. The password is a one-way hash and is never exposed. */
        String username,
        String email,
        String phone,
        String industry,
        Long departmentId,
        String profileStatus,
        String photoPath,
        LocalDate dob,
        List<String> roles,
        Long designationId,
        String designationTitle,
        String techStack,
        String password,
        Long companyId,
        String companyName,

        /**
         * Whether this account is somebody who turns up for work.
         *
         * <p>False for the desk logins -- the HR inbox, the company-admin and
         * system-admin accounts -- which have nobody behind them to punch in.
         * They were sitting on the attendance roll at 0% with an absence
         * against every working day, which is not an attendance problem to
         * chase; it is a mailbox.
         *
         * <p>Computed from the record rather than configured, so a new desk
         * login needs no code change. Two signals together: an administrative
         * role and no team. Either alone is wrong -- real HR staff hold IT_HR
         * and punch in daily, and four ordinary employees simply have no
         * designation recorded yet.
         */
        boolean attends
) {}
