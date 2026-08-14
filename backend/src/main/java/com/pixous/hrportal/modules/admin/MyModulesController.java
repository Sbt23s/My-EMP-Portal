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
     * @return {@code {"enabled": ["ATTENDANCE", ...], "configured": true,
     *         "branding": "{...}"}}
     *
     * <p>{@code configured} separates "this company has switched everything off"
     * from "nobody has ever set this up". The client must tell those apart: with
     * no rows at all, hiding every module would empty the portal for a company
     * that simply never visited the module screen. Absent means show everything;
     * present-and-off means hide.
     *
     * <p>{@code branding} is the company's appearance settings, verbatim as the
     * technical-admin screen wrote them. It rides along on this request rather
     * than having one of its own because it is needed at exactly the same moment
     * — the portal cannot draw its first screen without both — and a second
     * request would mean the colour arriving after the page, as a flash.
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> myModules() {
        Long companyId = SecurityUtils.currentCompanyId();
        if (companyId == null) {
            return ApiResponse.ok(Map.of("enabled", List.of(), "configured", false, "branding", ""));
        }

        List<CompanyModule> rows = companyModuleRepository.findByCompanyId(companyId);

        /*
         * Branding lives in a company_modules row of its own, kept switched off
         * — it is a place to store settings, not a feature anyone navigates to.
         * Which is why it is read here by code rather than falling out of the
         * enabled list below: that list only carries rows that are switched on.
         */
        String branding = rows.stream()
                .filter(r -> "BRANDING".equalsIgnoreCase(r.getModuleCode()))
                .map(CompanyModule::getFeatureFlags)
                .filter(f -> f != null && !f.isBlank())
                .findFirst()
                // Map.of rejects nulls, and an absent document is a normal state
                // — most companies have never opened the branding screen.
                .orElse("");

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

        /*
         * Counted without the branding row.
         *
         * Saving a colour writes a company_modules row, and if that row counted
         * as configuration then choosing a theme for a company that had never
         * opened the module screen would answer "configured, nothing enabled" —
         * and empty its portal for all of its people. Picking a colour must not
         * be able to switch off a company.
         */
        boolean configured = rows.stream()
                .anyMatch(r -> !"BRANDING".equalsIgnoreCase(r.getModuleCode()));

        return ApiResponse.ok(Map.of(
                "enabled", enabled,
                "configured", configured,
                "branding", branding));
    }
}
