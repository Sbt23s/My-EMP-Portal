package com.pixous.hrportal.modules.admin;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.security.SecurityUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Which modules are switched on for the signed-in person's company.
 *
 * <p>The portal used to answer this from the browser's own localStorage, written
 * by the technical-admin screens. That only ever worked in the one browser where
 * somebody had opened those screens: an ordinary employee never does, so their
 * copy was always absent and every module read as enabled. Switching a module
 * off changed nothing for the people it was switched off for.
 *
 * <p>Read-only, and scoped to the caller's own company — it reports what someone
 * is already entitled to see rather than granting anything. A technical admin has
 * no company of their own, so they get an empty list; their own screens read the
 * per-company endpoint instead.
 */
@RestController
@RequestMapping("/api/my-modules")
public class MyModulesController {

    private final CompanyModuleRepository companyModuleRepository;

    public MyModulesController(CompanyModuleRepository companyModuleRepository) {
        this.companyModuleRepository = companyModuleRepository;
    }

    /**
     * @return {@code {"enabled": ["ATTENDANCE", ...], "configured": true}}
     *
     * <p>{@code configured} separates "this company has switched everything off"
     * from "nobody has ever set this up". The client must tell those apart: with
     * no rows at all, hiding every module would empty the portal for a company
     * that simply never visited the module screen. Absent means show everything;
     * present-and-off means hide.
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> myModules() {
        Long companyId = SecurityUtils.currentCompanyId();
        if (companyId == null) {
            return ApiResponse.ok(Map.of("enabled", List.of(), "configured", false));
        }

        List<CompanyModule> rows = companyModuleRepository.findByCompanyId(companyId);
        List<String> enabled = rows.stream()
                .filter(CompanyModule::isEnabled)
                .map(CompanyModule::getModuleCode)
                .filter(code -> code != null && !code.isBlank())
                // Stored lower-case in places and upper-case in others; the
                // client compares against upper-case codes.
                .map(code -> code.trim().toUpperCase())
                .distinct()
                .sorted()
                .toList();

        return ApiResponse.ok(Map.of(
                "enabled", enabled,
                "configured", !rows.isEmpty()));
    }
}
