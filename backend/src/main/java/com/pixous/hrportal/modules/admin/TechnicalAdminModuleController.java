package com.pixous.hrportal.modules.admin;

import com.pixous.hrportal.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import com.pixous.hrportal.modules.org.Company;
import com.pixous.hrportal.modules.org.CompanyRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/technical-admin/companies/{companyId}/modules")
@PreAuthorize("hasRole('TECHNICAL_ADMIN')")
public class TechnicalAdminModuleController {

    private final CompanyModuleRepository moduleRepository;
    private final CompanyRepository companyRepository;

    public TechnicalAdminModuleController(CompanyModuleRepository moduleRepository,
                                          CompanyRepository companyRepository) {
        this.moduleRepository = moduleRepository;
        this.companyRepository = companyRepository;
    }

    /**
     * What the client is told about a module row.
     *
     * A flat record, deliberately, rather than the entity. CompanyModule holds a
     * LAZY reference to Company, the application runs with open-in-view disabled,
     * and returning the entity meant Jackson met an uninitialised proxy after the
     * session had closed:
     *
     *   HttpMessageNotWritableException: Could not initialize proxy
     *   [com.pixous.hrportal.modules.org.Company#1] - no session
     *
     * The effect was that saving a module worked, and reading the list back
     * afterwards returned 500 — so a company with even one saved module could no
     * longer show its module page at all. The company is already in the URL, so
     * nothing here needs to carry it.
     */
    public record ModuleView(Long id, String moduleCode, boolean enabled, String featureFlags) {
        static ModuleView of(CompanyModule m) {
            return new ModuleView(m.getId(), m.getModuleCode(), m.isEnabled(), m.getFeatureFlags());
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<?>> getCompanyModules(@PathVariable Long companyId) {
        List<ModuleView> rows = moduleRepository.findByCompanyId(companyId)
                .stream().map(ModuleView::of).toList();
        return ResponseEntity.ok(ApiResponse.ok(rows));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<?>> configureModule(@PathVariable Long companyId, @RequestBody CompanyModule payload) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));
                
        CompanyModule module = moduleRepository.findByCompanyIdAndModuleCode(companyId, payload.getModuleCode())
                .orElseGet(() -> {
                    CompanyModule newModule = new CompanyModule();
                    newModule.setCompany(company);
                    newModule.setModuleCode(payload.getModuleCode());
                    return newModule;
                });

        module.setEnabled(payload.isEnabled());
        module.setFeatureFlags(payload.getFeatureFlags());

        // Same flat shape as the GET. This one happened to serialise because the
        // company had just been loaded in this transaction, which made the
        // failure look intermittent rather than certain.
        return ResponseEntity.ok(ApiResponse.ok(ModuleView.of(moduleRepository.save(module))));
    }

    @PostMapping("/simulate-access")
    public ResponseEntity<ApiResponse<?>> simulateAccess(@PathVariable Long companyId, @RequestBody java.util.Map<String, Object> request) {
        // Here we simulate the entitlement evaluation.
        // We fetch the modules for the company and optionally intersect with a simulated user's role.
        List<CompanyModule> modules = moduleRepository.findByCompanyId(companyId);
        return ResponseEntity.ok(ApiResponse.ok(java.util.Map.of(
            "simulatedCompanyId", companyId,
            "simulatedRole", request.get("roleCode"),
            // Through ModuleView for the same reason as the two above -- this
            // would have hit the identical lazy-proxy failure.
            "entitledModules", modules.stream().filter(CompanyModule::isEnabled).map(ModuleView::of).toList(),
            "status", "SUCCESS",
            "message", "Simulated access successfully generated."
        )));
    }
}
