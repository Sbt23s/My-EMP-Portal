package com.pixous.hrportal.modules.auth;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.modules.auth.dto.ChangePasswordRequest;
import com.pixous.hrportal.modules.auth.dto.CreateEmployeeRequest;
import com.pixous.hrportal.modules.auth.dto.LoginRequest;
import com.pixous.hrportal.modules.auth.dto.LoginResponse;
import com.pixous.hrportal.modules.auth.dto.PhoneValidateRequest;
import com.pixous.hrportal.modules.auth.dto.RefreshRequest;
import com.pixous.hrportal.modules.auth.dto.SignupRequest;
import com.pixous.hrportal.modules.auth.dto.TokenPair;
import com.pixous.hrportal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Login, signup, token refresh, password management")
public class AuthController {

    private final AuthService authService;
    private final com.pixous.hrportal.modules.user.EmployeeImportService employeeImportService;
    private final com.pixous.hrportal.security.LoginAttemptLimiter loginAttemptLimiter;

    public AuthController(AuthService authService,
                          com.pixous.hrportal.modules.user.EmployeeImportService employeeImportService,
                          com.pixous.hrportal.security.LoginAttemptLimiter loginAttemptLimiter) {
        this.authService = authService;
        this.employeeImportService = employeeImportService;
        this.loginAttemptLimiter = loginAttemptLimiter;
    }

    @PostMapping("/login")
    @Operation(summary = "Login with username and password")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                             HttpServletRequest http) {
        String ip = clientIp(http);
        String username = request.username();

        // Refuse before checking the password, so a blocked caller learns
        // nothing about whether the guess was right.
        loginAttemptLimiter.checkAllowed(ip, username);

        LoginResponse response;
        try {
            response = authService.login(request, ip, http.getHeader("User-Agent"));
        } catch (RuntimeException failed) {
            // Anything that stops a sign-in counts, not only a wrong password:
            // a locked or disabled account being hammered is the same signal.
            loginAttemptLimiter.recordFailure(ip, username);
            throw failed;
        }

        loginAttemptLimiter.recordSuccess(ip, username);
        return ApiResponse.ok(response, "Login successful");
    }

    /**
     * Register an account.
     *
     * <p>This was reachable without signing in — {@code /api/auth/**} is public so
     * that login and token refresh work — which meant anyone who could reach the
     * site could create a login, and accounts with no name, no email and no role
     * turned up in the directory. Nobody joins this company by filling in a form,
     * so it now requires the same authority as adding an employee.
     */
    @PostMapping("/signup")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','EMPLOYEE_MANAGE')")
    @Operation(summary = "Register a new employee account (admin/HR only)")
    public ResponseEntity<ApiResponse<LoginResponse>> signup(@Valid @RequestBody SignupRequest request) {
        LoginResponse response = authService.signup(request);
        return ResponseEntity.status(201).body(ApiResponse.ok(response, "Account created"));
    }

    @PostMapping("/employees")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','EMPLOYEE_MANAGE') or hasRole('TECHNICAL_ADMIN')")
    @Operation(summary = "HR/Admin: create an employee with a username + password")
    public ResponseEntity<ApiResponse<LoginResponse.AuthUser>> createEmployee(
            @Valid @RequestBody CreateEmployeeRequest request) {
        LoginResponse.AuthUser created = authService.createEmployee(request);
        return ResponseEntity.status(201)
                .body(ApiResponse.ok(created, "Employee account created"));
    }

    @PostMapping("/employees/bulk")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','EMPLOYEE_MANAGE')")
    @Operation(summary = "HR/Admin: bulk-create employees (e.g. from an Excel import)")
    public ApiResponse<java.util.List<com.pixous.hrportal.modules.auth.dto.BulkEmployeeResult>> createEmployeesBulk(
            @RequestBody java.util.List<CreateEmployeeRequest> requests,
            @RequestParam(value = "fileName", required = false) String fileName) {
        java.util.List<com.pixous.hrportal.modules.auth.dto.BulkEmployeeResult> results =
                new java.util.ArrayList<>();

        // The import is recorded before anything is created, so the accounts it
        // makes can point back at it — which is what makes undoing it possible.
        // A query parameter rather than a wrapper object, so a caller that only
        // sends the list of rows still works exactly as it did.
        var batch = employeeImportService.begin(
                fileName, requests.size(), com.pixous.hrportal.security.SecurityUtils.currentUserId());

        for (CreateEmployeeRequest req : requests) {
            try {
                // A list body is not validated element by element the way a single
                // one is, so these three are checked here. Without it a row with no
                // name became an account with no name, which is how nameless logins
                // reached the directory. Checked rather than rejecting the batch, so
                // one bad row is reported and the rest still land.
                if (req.name() == null || req.name().isBlank()) {
                    throw new IllegalArgumentException("Name is required");
                }
                if (req.username() == null || req.username().trim().length() < 3) {
                    throw new IllegalArgumentException("Username must be at least 3 characters");
                }
                if (req.password() == null || req.password().length() < 8) {
                    throw new IllegalArgumentException("Password must be at least 8 characters");
                }
                // Proxied call — each create runs in its own transaction, so one
                // bad row doesn't roll back the whole batch.
                var created = authService.createEmployee(req);
                employeeImportService.stamp(created.id(), batch.getId());
                results.add(new com.pixous.hrportal.modules.auth.dto.BulkEmployeeResult(
                        req.username(), req.name(), true, null));
            } catch (Exception e) {
                results.add(new com.pixous.hrportal.modules.auth.dto.BulkEmployeeResult(
                        req.username(), req.name(), false, e.getMessage()));
            }
        }
        long ok = results.stream().filter(com.pixous.hrportal.modules.auth.dto.BulkEmployeeResult::created).count();
        employeeImportService.finish(batch.getId(), (int) ok, results.size() - (int) ok);
        return ApiResponse.ok(results, ok + " of " + results.size() + " employees created");
    }

    // ---- past imports, and undoing one ----

    /** Every Excel import so far, newest first, with how many accounts remain. */
    @GetMapping("/employees/imports")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','EMPLOYEE_MANAGE')")
    @Operation(summary = "HR/Admin: list past employee imports")
    public ApiResponse<java.util.List<Map<String, Object>>> listImports() {
        return ApiResponse.ok(employeeImportService.list());
    }

    /**
     * Matches a sheet against employees already in the directory, so that sheet can
     * then be removed like any other import.
     *
     * <p>For a directory that was filled before imports were recorded. Writes a
     * batch id and nothing else — the removal itself is still the ordinary endpoint
     * below, with its preview and all its guards.
     */
    @PostMapping("/employees/imports/adopt")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','EMPLOYEE_MANAGE')")
    @Operation(summary = "HR/Admin: match a sheet to employees already in the directory")
    public ApiResponse<Map<String, Object>> adoptImport(
            @RequestParam(required = false) String fileName,
            // Optional so a sheet that yielded no rows is answered with the service's
            // own explanation. Required, Spring rejects the missing body before the
            // method runs and the caller gets "something went wrong" instead.
            @RequestBody(required = false) java.util.List<String> identifiers) {
        Map<String, Object> result = employeeImportService.adopt(
                fileName, identifiers, com.pixous.hrportal.security.SecurityUtils.currentUserId());
        return ApiResponse.ok(result,
                result.get("linkedCount") + " employee(s) matched to this sheet");
    }

    /**
     * Who an undo would remove and who it would keep back. Asked first, so the
     * decision is made against names rather than a count.
     */
    @GetMapping("/employees/imports/{id}/preview")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','EMPLOYEE_MANAGE')")
    @Operation(summary = "HR/Admin: what undoing an import would remove")
    public ApiResponse<Map<String, Object>> previewImport(@PathVariable Long id) {
        return ApiResponse.ok(employeeImportService.preview(id));
    }

    /**
     * Removes the accounts an import created. Anybody who has started being
     * used — a punch, a leave request, a payslip — is kept and reported.
     */
    @DeleteMapping("/employees/imports/{id}")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','EMPLOYEE_MANAGE')")
    @Operation(summary = "HR/Admin: undo an employee import")
    public ApiResponse<Map<String, Object>> revertImport(@PathVariable Long id) {
        Map<String, Object> result = employeeImportService.revert(
                id, com.pixous.hrportal.security.SecurityUtils.currentUserId());
        return ApiResponse.ok(result, result.get("removedCount") + " account(s) removed");
    }

    /** Drops the record of an import that has no accounts left. */
    @DeleteMapping("/employees/imports/{id}/record")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','EMPLOYEE_MANAGE')")
    @Operation(summary = "HR/Admin: forget an empty import record")
    public ApiResponse<Void> forgetImport(@PathVariable Long id) {
        employeeImportService.forget(id);
        return ApiResponse.message("Import record removed");
    }

    @GetMapping("/check-username")
    @Operation(summary = "Check whether a username is already taken")
    public ApiResponse<Map<String, Boolean>> checkUsername(@RequestParam String username) {
        return ApiResponse.ok(Map.of("available", !authService.usernameExists(username)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new access token")
    public ApiResponse<TokenPair> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request));
    }

    @GetMapping("/me")
    @Operation(summary = "Current signed-in user with roles and permissions")
    public ApiResponse<LoginResponse.AuthUser> me() {
        return ApiResponse.ok(authService.currentUser(SecurityUtils.currentUserId()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke all refresh tokens for the current user")
    public ApiResponse<Void> logout() {
        authService.logout(SecurityUtils.currentUserId());
        return ApiResponse.message("Logged out");
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change the current user's password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(SecurityUtils.currentUserId(), request);
        return ApiResponse.message("Password updated");
    }

    @PostMapping("/validate-phone")
    @Operation(summary = "Check whether a phone number is already registered")
    public ApiResponse<Map<String, Boolean>> validatePhone(@Valid @RequestBody PhoneValidateRequest request) {
        boolean exists = authService.phoneExists(request.phone());
        return ApiResponse.ok(Map.of("exists", exists));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
