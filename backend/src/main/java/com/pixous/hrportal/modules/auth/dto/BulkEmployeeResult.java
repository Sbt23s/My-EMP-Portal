package com.pixous.hrportal.modules.auth.dto;

/** Per-row outcome of a bulk employee import. */
public record BulkEmployeeResult(
        String username,
        String name,
        boolean created,
        String error
) {}
