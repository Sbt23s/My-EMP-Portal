package com.pixous.hrportal.modules.auth.dto;

import java.util.List;

/** Auth result: tokens plus a compact view of the signed-in user for the client. */
public record LoginResponse(
        TokenPair tokens,
        AuthUser user
) {
    public record AuthUser(
            Long id,
            String employeeCode,
            String username,
            String name,
            String aadhar,
            String email,
            String phone,
            String industry,
            String photoPath,
            String companyName,
            List<String> roles,
            List<String> permissions
    ) {}
}
