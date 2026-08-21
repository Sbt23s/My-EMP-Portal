package com.pixous.hrportal.modules.user;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.common.PageResponse;
import com.pixous.hrportal.modules.user.dto.BankRequest;
import com.pixous.hrportal.modules.user.dto.BankResponse;
import com.pixous.hrportal.modules.user.dto.OffboardingRequest;
import com.pixous.hrportal.modules.user.dto.ProfileResponse;
import com.pixous.hrportal.modules.user.dto.UpdateProfileRequest;
import com.pixous.hrportal.modules.user.dto.UpdateEmployeeRequest;
import com.pixous.hrportal.modules.user.dto.UserSummary;
import com.pixous.hrportal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users & Profile", description = "Self-service profile, photo, employee directory, bank details")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the signed-in user's profile")
    public ApiResponse<ProfileResponse> me() {
        return ApiResponse.ok(userService.getProfile(SecurityUtils.currentUserId()));
    }

    @GetMapping("/my-team")
    @Operation(summary = "Get the signed-in employee's team (designation) and its members")
    public ApiResponse<com.pixous.hrportal.modules.user.dto.MyTeamResponse> myTeam() {
        return ApiResponse.ok(userService.getMyTeam(SecurityUtils.currentUserId()));
    }

    @PutMapping("/me")
    @Operation(summary = "Update the signed-in user's profile")
    public ApiResponse<ProfileResponse> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(userService.updateProfile(SecurityUtils.currentUserId(), request),
                "Profile updated");
    }

    @PostMapping("/me/photo")
    @Operation(summary = "Upload / replace profile photo")
    public ApiResponse<Map<String, String>> uploadPhoto(@RequestParam("file") MultipartFile file) {
        String path = userService.updatePhoto(SecurityUtils.currentUserId(), file);
        return ApiResponse.ok(Map.of("photoPath", path), "Photo updated");
    }

    @DeleteMapping("/me/photo")
    @Operation(summary = "Remove the signed-in user's profile photo")
    public ApiResponse<Void> removePhoto() {
        userService.removePhoto(SecurityUtils.currentUserId());
        return ApiResponse.message("Photo removed");
    }

    /**
     * The banner image on the signed-in user's own dashboard.
     *
     * Self-service only: the path carries no identifier, so one person cannot
     * set or clear another's banner.
     */
    @PostMapping("/me/cover")
    @Operation(summary = "Upload / replace the dashboard banner image")
    public ApiResponse<Map<String, String>> uploadCover(@RequestParam("file") MultipartFile file) {
        String path = userService.updateCoverPhoto(SecurityUtils.currentUserId(), file);
        return ApiResponse.ok(Map.of("coverPhotoPath", path), "Cover updated");
    }

    @DeleteMapping("/me/cover")
    @Operation(summary = "Remove the dashboard banner image")
    public ApiResponse<Void> removeCover() {
        userService.removeCoverPhoto(SecurityUtils.currentUserId());
        return ApiResponse.message("Cover removed");
    }

    /**
     * Store one file for an employee's paperwork and return its path. The paths
     * are collected by the caller and saved on the employee as a comma-separated
     * list, the same shape attachments take everywhere else.
     */
    @PostMapping("/documents")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','EMPLOYEE_MANAGE')")
    @Operation(summary = "HR/Admin: upload one employee document")
    public ApiResponse<Map<String, String>> uploadDocument(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(Map.of("path", userService.storeDocument(file)), "File uploaded");
    }

    /**
     * Employee directory. Beyond the search box it narrows by team, role,
     * department and a joining-date window — all optional, so a call that sends
     * none of them behaves exactly as it always did.
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','ATTENDANCE_TEAM','DASHBOARD_EXEC') or hasRole('TECHNICAL_ADMIN')")
    @Operation(summary = "Employee directory (paged, searchable, filterable)")
    public ApiResponse<PageResponse<UserSummary>> directory(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long designationId,
            @RequestParam(required = false) String designationTitle,
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) String joinedFrom,
            @RequestParam(required = false) String joinedTo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        boolean plain = designationId == null && (designationTitle == null || designationTitle.isBlank())
                && (roleCode == null || roleCode.isBlank())
                && (joinedFrom == null || joinedFrom.isBlank())
                && (joinedTo == null || joinedTo.isBlank());
        return ApiResponse.ok(plain
                ? userService.directory(q, industry, departmentId, status, page, size)
                : userService.directoryFiltered(q, industry, departmentId, designationId,
                        designationTitle, roleCode, joinedFrom, joinedTo, status, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','ATTENDANCE_TEAM','DASHBOARD_EXEC')")
    @Operation(summary = "Get a single employee profile by id")
    public ApiResponse<ProfileResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(userService.getById(id));
    }

    @PutMapping("/{id}")
    // TECHNICAL_ADMIN added alongside the existing authorities, not in place of
    // them. It can already list users and create them; not being able to edit one
    // is what pushed the control centre into keeping its own copy in the browser.
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','EMPLOYEE_MANAGE') or hasRole('TECHNICAL_ADMIN')")
    @Operation(summary = "Update an employee profile by id")
    public ApiResponse<ProfileResponse> updateById(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEmployeeRequest request) {
        return ApiResponse.ok(userService.updateEmployee(id, request), "Profile updated successfully");
    }

    @PostMapping("/{id}/credentials")
    // Same addition: resetting a password is the control centre's job too.
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','EMPLOYEE_MANAGE') or hasRole('TECHNICAL_ADMIN')")
    @Operation(summary = "Set an employee's login username and/or reset their password")
    public ApiResponse<Void> setCredentials(@PathVariable Long id,
                                            @RequestBody java.util.Map<String, String> body) {
        userService.setCredentials(id, body.get("username"), body.get("password"));
        return ApiResponse.message("Login updated");
    }

    @DeleteMapping("/{id}/designation")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Remove an employee from their team (clear designation)")
    public ApiResponse<Void> removeFromTeam(@PathVariable Long id) {
        userService.clearDesignation(id);
        return ApiResponse.message("Removed from team");
    }

    @PostMapping("/{id}/offboarding")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Offboard an employee")
    public ApiResponse<Void> offboardUser(@PathVariable Long id, @Valid @RequestBody OffboardingRequest request) {
        userService.offboardUser(id, request);
        return ApiResponse.message("Employee offboarded successfully");
    }

    // ---- bank details ----

    @GetMapping("/me/bank")
    @Operation(summary = "List the signed-in user's bank accounts")
    public ApiResponse<List<BankResponse>> listBanks() {
        return ApiResponse.ok(userService.listBanks(SecurityUtils.currentUserId()));
    }

    @GetMapping("/{id}/bank")
    @PreAuthorize("hasAnyAuthority('PAYROLL_RUN', 'PAYROLL_VIEW', 'USER_MANAGE', 'EMPLOYEE_MANAGE')")
    @Operation(summary = "List a specific employee's bank accounts")
    public ApiResponse<List<BankResponse>> listBanksForUser(@PathVariable Long id) {
        return ApiResponse.ok(userService.listBanks(id));
    }

    @PostMapping("/me/bank")
    @Operation(summary = "Add a bank account")
    public ApiResponse<BankResponse> addBank(@Valid @RequestBody BankRequest request) {
        return ApiResponse.ok(userService.addBank(SecurityUtils.currentUserId(), request),
                "Bank account added");
    }

    @PostMapping("/{id}/bank")
    // HR adds these on the joining form, and HR holds EMPLOYEE_MANAGE rather
    // than USER_MANAGE.
    @PreAuthorize("hasAnyAuthority('USER_MANAGE', 'EMPLOYEE_MANAGE')")
    @Operation(summary = "HR/Admin: add a bank account for a specific employee")
    public ApiResponse<BankResponse> addBankForUser(@PathVariable Long id,
                                                    @Valid @RequestBody BankRequest request) {
        return ApiResponse.ok(userService.addBank(id, request), "Bank account added");
    }

    @PutMapping("/{id}/bank/{bankId}")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE', 'EMPLOYEE_MANAGE')")
    @Operation(summary = "HR/Admin: update an employee's bank account")
    public ApiResponse<BankResponse> updateBankForUser(@PathVariable Long id,
                                                       @PathVariable Long bankId,
                                                       @Valid @RequestBody BankRequest request) {
        return ApiResponse.ok(userService.updateBank(id, bankId, request), "Bank account updated");
    }

    @PutMapping("/me/bank/{bankId}")
    @Operation(summary = "Update a bank account")
    public ApiResponse<BankResponse> updateBank(@PathVariable Long bankId,
                                                @Valid @RequestBody BankRequest request) {
        return ApiResponse.ok(userService.updateBank(SecurityUtils.currentUserId(), bankId, request),
                "Bank account updated");
    }

    @DeleteMapping("/me/bank/{bankId}")
    @Operation(summary = "Delete a bank account")
    public ApiResponse<Void> deleteBank(@PathVariable Long bankId) {
        userService.deleteBank(SecurityUtils.currentUserId(), bankId);
        return ApiResponse.message("Bank account deleted");
    }

    @DeleteMapping("/{id}")
    // HR runs the joining and leaving of staff and holds EMPLOYEE_MANAGE rather
    // than USER_MANAGE, so both may remove a record for good.
    @PreAuthorize("hasAnyAuthority('USER_MANAGE', 'EMPLOYEE_MANAGE') or hasRole('TECHNICAL_ADMIN')")
    @Operation(summary = "Delete an employee account entirely")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ApiResponse.message("Employee deleted successfully");
    }

    /**
     * Records that an employee's face has been registered, keeping one photo of
     * it and who registered it.
     *
     * <p>Registration is not something an employee does for themselves: somebody
     * has to be able to confirm it was the right face in front of the camera, and
     * a photo nobody ever looks at cannot be confirmed. HR, the admin and the
     * company head may do it — the same three who run the rest of an employee
     * record.
     *
     * <p>The photo is only ever looked at. The encodings a punch is matched
     * against live in the analytics service and are untouched by this.
     */
    @PostMapping(path = "/{id}/face-photo", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE', 'EMPLOYEE_MANAGE')")
    @Operation(summary = "HR/Admin: record an employee's face enrolment photo")
    public ApiResponse<Map<String, Object>> saveFacePhoto(
            @PathVariable Long id,
            @RequestParam("photo") org.springframework.web.multipart.MultipartFile photo) {
        return ApiResponse.ok(
                userService.saveFacePhoto(id, photo, SecurityUtils.currentUserId()),
                "Face registered");
    }

    @DeleteMapping("/{id}/face-photo")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE', 'EMPLOYEE_MANAGE')")
    @Operation(summary = "HR/Admin: forget an employee's face enrolment photo")
    public ApiResponse<Void> clearFacePhoto(@PathVariable Long id) {
        userService.clearFacePhoto(id);
        return ApiResponse.message("Face registration removed");
    }

    /**
     * The employee's current password, in the clear, for HR and the admin.
     * Deliberately its own call rather than a field on the profile: it is only
     * fetched when somebody presses Show, so it never travels with an ordinary
     * profile read, a directory listing or an export.
     */
    @GetMapping("/{id}/password")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE', 'EMPLOYEE_MANAGE')")
    @Operation(summary = "HR/Admin: read an employee's current password")
    public ApiResponse<Map<String, String>> currentPassword(@PathVariable Long id) {
        String password = userService.currentPassword(id);
        Map<String, String> body = new java.util.HashMap<>();
        body.put("password", password);
        return ApiResponse.ok(body);
    }
}
