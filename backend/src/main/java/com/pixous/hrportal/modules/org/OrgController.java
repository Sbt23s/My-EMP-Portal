package com.pixous.hrportal.modules.org;

import com.pixous.hrportal.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Organisation master data + dropdowns.
 * Backwards-compatible with the legacy PHP API:
 *   GET  /api/org/dropdown/{type}        (single type, path form)
 *   GET  /api/org/dropdown?type=...      (single type, query form)
 *   POST /api/org/dropdowns  ["a","b"]   (array form)
 */
@RestController
@RequestMapping("/api/org")
@Tag(name = "Organisation", description = "Departments, designations, locations, sites, shifts, holidays, dropdowns")
public class OrgController {

    private final OrgService orgService;

    public OrgController(OrgService orgService) {
        this.orgService = orgService;
    }

    @GetMapping("/dropdown/{type}")
    @Operation(summary = "Single dropdown by type (path form)")
    public ApiResponse<List<DropdownItem>> dropdownByPath(@PathVariable String type,
            @RequestParam(required = false) String industry) {
        return ApiResponse.ok(orgService.dropdown(type, industry));
    }

    @GetMapping("/dropdown")
    @Operation(summary = "Single dropdown by type (query form)")
    public ApiResponse<List<DropdownItem>> dropdownByQuery(@RequestParam String type,
            @RequestParam(required = false) String industry) {
        return ApiResponse.ok(orgService.dropdown(type, industry));
    }

    @PostMapping("/dropdowns")
    @Operation(summary = "Multiple dropdowns in one call (array form)")
    public ApiResponse<Map<String, List<DropdownItem>>> dropdowns(@RequestBody List<String> types,
            @RequestParam(required = false) String industry) {
        return ApiResponse.ok(orgService.dropdowns(types, industry));
    }

    @PostMapping("/designations")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyAuthority('USER_MANAGE','ORG_MANAGE','TEAM_MANAGE')")
    @Operation(summary = "Create a new designation (team)")
    public ApiResponse<DropdownItem> createDesignation(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(orgService.createDesignation(body.get("name"), body.get("industry")), "Team created");
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/designations")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyAuthority('USER_MANAGE','ORG_MANAGE','TEAM_MANAGE')")
    @Operation(summary = "Delete a designation (team) by name; detaches its members")
    public ApiResponse<Void> deleteDesignation(@RequestParam String name) {
        orgService.deleteTeamByName(name);
        return ApiResponse.message("Team deleted");
    }

    @GetMapping("/sites")
    @Operation(summary = "Active civil sites")
    public ApiResponse<List<Site>> sites() {
        return ApiResponse.ok(orgService.sites());
    }

    @GetMapping("/office-locations")
    @Operation(summary = "Active office locations")
    public ApiResponse<List<OfficeLocation>> officeLocations() {
        return ApiResponse.ok(orgService.officeLocations());
    }

    /**
     * Records an office at a set of coordinates, so a punch made there can be
     * named rather than shown as numbers.
     *
     * <p>This was the missing piece: attendance stored true coordinates and had
     * nothing to compare them against except two demo offices, so a punch from the
     * real office read as somewhere else. The intended use is to stand in the
     * office and save the position the browser reports — which is the only way to
     * get it right without looking coordinates up by hand.
     *
     * <p>Passing an existing id moves that office instead of adding another; the
     * usual mistake here is ending up with three copies of one building.
     */
    @PostMapping("/office-locations")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyAuthority('USER_MANAGE','ORG_MANAGE','EMPLOYEE_MANAGE')")
    @Operation(summary = "HR/Admin: add or move an office location")
    public ApiResponse<OfficeLocation> saveOfficeLocation(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(orgService.saveOfficeLocation(body), "Office location saved");
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/office-locations/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyAuthority('USER_MANAGE','ORG_MANAGE')")
    @Operation(summary = "Admin: remove an office location")
    public ApiResponse<Void> deleteOfficeLocation(@PathVariable Long id) {
        orgService.deleteOfficeLocation(id);
        return ApiResponse.message("Office location removed");
    }

    @GetMapping("/holidays")
    @Operation(summary = "Holiday calendar (optionally by year)")
    public ApiResponse<List<Holiday>> holidays(@RequestParam(required = false) Integer year) {
        return ApiResponse.ok(orgService.holidays(year));
    }

    @PostMapping("/holidays")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyAuthority('ORG_MANAGE','CALENDAR_MANAGE')")
    @Operation(summary = "Add a new holiday")
    public ApiResponse<Holiday> createHoliday(@jakarta.validation.Valid @RequestBody com.pixous.hrportal.modules.org.dto.HolidayRequest req) {
        return ApiResponse.ok(orgService.createHoliday(req), "Holiday created");
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/holidays/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyAuthority('ORG_MANAGE','CALENDAR_MANAGE')")
    @Operation(summary = "Remove a holiday")
    public ApiResponse<Void> deleteHoliday(@PathVariable Long id) {
        orgService.deleteHoliday(id);
        return ApiResponse.message("Holiday removed");
    }
}
