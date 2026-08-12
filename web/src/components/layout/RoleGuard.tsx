import { ShieldAlert } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";

/**
 * Gates content by permission(s). `permission` accepts a comma-separated
 * list — the user needs ANY one of them.
 * SUPER_ADMIN always passes through regardless of permission.
 */
export function RoleGuard({
  permission,
  role,
  children
}: {
  permission?: string;
  /**
   * Require this role instead of a permission. Nothing bypasses it — a
   * permission grant does not open a door meant for one role only.
   */
  role?: string;
  children: React.ReactNode;
}) {
  const { hasPermission, hasRole } = useAuth();
  const perms = (permission ?? "").split(",").map((p) => p.trim()).filter(Boolean);

  // SUPER_ADMIN and COMPANY_ADMIN bypass all permission gates
  const allowed = role
    ? hasRole(role)
    : hasRole("SUPER_ADMIN") || hasRole("COMPANY_ADMIN") || hasPermission(...perms);

  if (!allowed) {
    return (
      <div className="mx-auto max-w-md py-20 text-center">
        <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-destructive/10">
          <ShieldAlert className="h-6 w-6 text-destructive" />
        </div>
        <h2 className="font-display text-lg font-semibold">Restricted</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          You don't have access to this area. If you think this is a mistake, contact your HR admin.
        </p>
      </div>
    );
  }

  return <>{children}</>;
}
