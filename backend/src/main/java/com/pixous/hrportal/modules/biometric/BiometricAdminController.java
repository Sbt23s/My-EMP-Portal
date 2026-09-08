package com.pixous.hrportal.modules.biometric;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.common.ErrorCode;
import com.pixous.hrportal.modules.biometric.hik.HikClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Administering the biometric link, for whoever set it up.
 *
 * <p>Deliberately small. There is no endpoint here that returns a punch or a
 * person's data — those belong to the attendance module, under its own
 * permissions, so that adding a device screen cannot become a second way to
 * read the workforce's movements.
 *
 * <p>Every method requires {@code USER_MANAGE}: this configures a company-wide
 * integration, which is the same authority as editing employee records and not
 * something an employee's own attendance permission should grant.
 */
@RestController
@RequestMapping("/api/biometric/admin")
@Tag(name = "Biometric administration",
        description = "The Hikvision link: connection state and the person mapping")
@RequiredArgsConstructor
public class BiometricAdminController {

    private final HikClient client;
    private final HikPersonSyncService syncService;
    private final HikPersonMapRepository mapRepository;

    /**
     * Whether the link is configured and working, without revealing how.
     *
     * <p>Reports that a key is present, never any part of its value. A
     * "configured" flag is what an administrator needs; the key itself on a
     * screen is the same exposure as committing it, and this endpoint is
     * reachable by anyone who can manage users.
     */
    @GetMapping("/status")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Is the Hikvision link configured, and how many people are mapped")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("configured", client.isEnabled());
        out.put("mappedPeople", mapRepository.count());
        return ApiResponse.ok(out);
    }

    /**
     * Rebuilds the mapping now.
     *
     * <p>The sync runs hourly on its own; this exists for the moment after
     * somebody adds an employee to the terminal and wants them able to punch
     * before the next hour. Synchronous, so the caller sees the count rather
     * than being told it started.
     */
    @PostMapping("/sync")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Match Hikvision people to employees by employee number")
    public ApiResponse<HikPersonSyncService.SyncResult> sync() {
        HikPersonSyncService.SyncResult result = syncService.sync();
        if (!result.ran()) {
            /*
             * An unconfigured link and an empty terminal produce identical
             * counts, so reporting this as an ordinary success would have an
             * administrator who has not yet supplied the credentials reading
             * "0 on the terminal" and going to look at the hardware.
             */
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Hikvision is not configured. Set HIKVISION_ENABLED and supply "
                            + "the app key and secret key on the server.");
        }
        return ApiResponse.ok(result, result.summary());
    }

    /**
     * Re-reads who has a face or a fingerprint enrolled.
     *
     * <p>One request per mapped person against a budget of five a second, so
     * this is slow by design and is not something to press repeatedly.
     */
    @PostMapping("/sync-enrolment")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Refresh the cached face and fingerprint enrolment flags")
    public ApiResponse<Integer> syncEnrolment() {
        if (!client.isEnabled()) {
            // Same reasoning as sync(): "refreshed 0 people" would read as a
            // workforce with no enrolments rather than as a missing key.
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Hikvision is not configured. Set HIKVISION_ENABLED and supply "
                            + "the app key and secret key on the server.");
        }
        int updated = syncService.refreshEnrolment();
        return ApiResponse.ok(updated, "Refreshed " + updated + " people");
    }
}
