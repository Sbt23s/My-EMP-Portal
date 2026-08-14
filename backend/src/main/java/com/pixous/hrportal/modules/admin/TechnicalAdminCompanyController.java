package com.pixous.hrportal.modules.admin;

import com.pixous.hrportal.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import com.pixous.hrportal.modules.org.Company;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/technical-admin/companies")
@PreAuthorize("hasRole('TECHNICAL_ADMIN')")
public class TechnicalAdminCompanyController {

    private final CompanyService companyService;

    public TechnicalAdminCompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<?>> getAllCompanies() {
        return ResponseEntity.ok(ApiResponse.ok(companyService.getAllCompanies()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> getCompany(@PathVariable Long id) {
        Company company = companyService.getCompanyById(id).orElse(null);
        if (company == null) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Company not found", null));
        }
        return ResponseEntity.ok(ApiResponse.ok(company));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<?>> createCompany(@RequestBody Company company) {
        return ResponseEntity.ok(ApiResponse.ok(companyService.createCompany(company)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> updateCompany(@PathVariable Long id, @RequestBody Company company) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(companyService.updateCompany(id, company)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage(), null));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> deleteCompany(@PathVariable Long id) {
        companyService.deleteCompany(id);
        return ResponseEntity.ok(ApiResponse.ok("Deleted successfully"));
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<ApiResponse<?>> suspendCompany(@PathVariable Long id) {
        try {
            companyService.suspendCompany(id);
            return ResponseEntity.ok(ApiResponse.ok("Suspended successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage(), null));
        }
    }

    /**
     * Gone. Company administrators are created through {@code POST
     * /api/auth/employees} with {@code roleCode: "COMPANY_ADMIN"}.
     *
     * <p>This used to answer {@code 200 "Company admin created successfully"}
     * while creating nothing at all — it never even read the payload. A caller
     * could not tell it apart from a real creation, so an administrator that did
     * not exist was reported as made, and the failure only surfaced later as
     * somebody unable to sign in.
     *
     * <p>Answering plainly rather than being deleted: something may still be
     * calling it, and a 404 reads as a typo in the path. This says what happened
     * and where to go instead.
     */
    @PostMapping("/{companyId}/admins")
    public ResponseEntity<ApiResponse<?>> createCompanyAdmin(@PathVariable Long companyId,
                                                             @RequestBody java.util.Map<String, String> payload) {
        return ResponseEntity.status(410).body(ApiResponse.fail(
                "This endpoint never created anything. Use POST /api/auth/employees with "
                        + "roleCode COMPANY_ADMIN and companyId " + companyId + ".", null));
    }
}
