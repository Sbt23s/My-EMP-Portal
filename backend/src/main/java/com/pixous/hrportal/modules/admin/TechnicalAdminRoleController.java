package com.pixous.hrportal.modules.admin;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.modules.user.Permission;
import com.pixous.hrportal.modules.user.Role;
import com.pixous.hrportal.modules.user.RoleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * The role catalogue, so the control centre's Roles &amp; Permissions page has
 * something to show.
 *
 * That page was empty because nothing exposed this: roles and permissions were
 * only ever read as part of a signed-in user's own token. Read-only, and the
 * whole catalogue rather than one company's — roles are global here, which is
 * why {@code Role} filters on "company_id OR IS NULL" rather than strict
 * equality.
 *
 * A flat record, not the entity: {@code Role.permissions} is a lazy collection
 * and returning the entity would meet the same uninitialised-proxy failure that
 * made the module list return 500.
 */
@RestController
@RequestMapping("/api/technical-admin/roles")
@PreAuthorize("hasRole('TECHNICAL_ADMIN')")
public class TechnicalAdminRoleController {

    private final RoleRepository roleRepository;

    public TechnicalAdminRoleController(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    /** One role, with the permissions it grants. */
    public record RoleView(
            Long id,
            String code,
            String name,
            String description,
            String industry,
            int permissionCount,
            List<PermissionView> permissions) {}

    public record PermissionView(Long id, String code, String name) {}

    @GetMapping
    public ResponseEntity<ApiResponse<?>> roles() {
        List<RoleView> rows = roleRepository.findAll().stream()
                .map(TechnicalAdminRoleController::toView)
                // Most capable first — the page reads as a hierarchy that way,
                // and the roles granting nothing stand out at the bottom, which
                // is exactly the thing worth noticing.
                .sorted(Comparator.comparingInt(RoleView::permissionCount).reversed()
                        .thenComparing(RoleView::code))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(rows));
    }

    private static RoleView toView(Role role) {
        List<PermissionView> perms = role.getPermissions() == null
                ? List.of()
                : role.getPermissions().stream()
                        .map(TechnicalAdminRoleController::toPermissionView)
                        .sorted(Comparator.comparing(PermissionView::code))
                        .toList();
        return new RoleView(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.getIndustry(),
                perms.size(),
                perms);
    }

    private static PermissionView toPermissionView(Permission p) {
        return new PermissionView(p.getId(), p.getCode(), p.getName());
    }
}
