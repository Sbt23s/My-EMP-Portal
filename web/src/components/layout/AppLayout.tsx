import { useState, useEffect, useMemo } from "react";
import { NavLink, Outlet, useNavigate, useLocation } from "react-router-dom";
import { useTheme } from "next-themes";
import {
  LayoutDashboard, Clock, CalendarCheck, CheckSquare, Wallet, Users, Boxes,
  LifeBuoy, User, Bell, Menu, X, Moon, Sun, LogOut,
  FileBarChart, ClipboardList, Settings, Map, MessageSquareWarning, FileText,
  FolderOpen, ListTodo, FileArchive, CalendarDays, ChevronDown, Bot, Users2, Eraser, ScrollText,
  PartyPopper, MessageSquare, Building2, FolderGit2, History, ShieldAlert
} from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { useBranding } from "@/hooks/useBranding";
import { useNotifications } from "@/hooks/useNotifications";
import { Avatar } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { roleCodeLabel } from "@/lib/roles";
import dayjs from "dayjs";
import { useIsFetching } from "@tanstack/react-query";
import { CustomLoader } from "@/components/ui/custom-loader";
import { ChatBotWidget } from "@/components/ChatBotWidget";
import { CallProvider } from "@/hooks/useCalls";

interface NavItem {
  to: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  anyPermission?: string[];
  excludeRole?: string[];
  /** Shown only to these roles. A permission grant does not open it. */
  onlyRole?: string[];
  end?: boolean;
  moduleCode?: string;
}

interface NavGroup {
  type: "group";
  key: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  matchPath: string;
  children: NavItem[];
  moduleCode?: string;
}

type NavEntry = NavItem | NavGroup;

const NAV: NavEntry[] = [
  { to: "/", label: "Dashboard", icon: LayoutDashboard, end: true },
  { to: "/employees", label: "Employees", icon: Users, anyPermission: ["USER_MANAGE", "ATTENDANCE_TEAM", "REPORT_VIEW"], excludeRole: ["IT_TL"] },
  { to: "/attendance", label: "Attendance", icon: Clock, excludeRole: ["SUPER_ADMIN", "COMPANY_ADMIN"], moduleCode: "ATTENDANCE" },
  { to: "/team-attendance", label: "    Employee Attendance      ", icon: Users, anyPermission: ["ATTENDANCE_TEAM"], moduleCode: "ATTENDANCE" },
  // ─── Leave Management (collapsible group) ───────────────────────────────────
  {
    type: "group",
    key: "leave-management",
    label: "Leave Management",
    icon: CalendarCheck,
    matchPath: "/leave",
    moduleCode: "LEAVE",
    children: [
      { to: "/leave", label: "Leave", icon: CalendarCheck, end: true, excludeRole: ["SUPER_ADMIN", "COMPANY_ADMIN"] },
      { to: "/leave/permissions", label: "Permission", icon: Clock },
      { to: "/leave/approvals", label: "Approvals", icon: CheckSquare, anyPermission: ["LEAVE_APPROVE"] },
      { to: "/leave/policies", label: "Leave Policies", icon: Settings, anyPermission: ["ORG_MANAGE"] }
    ]
  },
  // ────────────────────────────────────────────────────────────────────────────
  // ─── Payroll (collapsible group) ────────────────────────────────────────────
  {
    type: "group",
    key: "payroll",
    label: "Payroll",
    icon: Wallet,
    matchPath: "/payroll",
    moduleCode: "PAYROLL",
    children: [
      { to: "/payroll/requests", label: "Payroll", icon: FileText, anyPermission: ["PAYROLL_RUN"] },
      { to: "/payslips", label: "Payslips", icon: Wallet, excludeRole: ["SUPER_ADMIN", "COMPANY_ADMIN", "IT_MGR", "IT_HR"] }
    ]
  },
  // ────────────────────────────────────────────────────────────────────────────
  { to: "/work-reports", label: "Work Reports", icon: ClipboardList, moduleCode: "REPORTS" },
  { to: "/tasks", label: "Tasks", icon: ListTodo, moduleCode: "TASKS" },
  { to: "/ta-expenses", label: "Claims", icon: Map, moduleCode: "EXPENSES" },
  { to: "/assets", label: "Assets", icon: Boxes, moduleCode: "ASSETS" },
  { to: "/helpdesk", label: "Supports", icon: LifeBuoy, moduleCode: "HELPDESK" },
  { to: "/complaints", label: "Complaints", icon: MessageSquareWarning, moduleCode: "HELPDESK" },
  { to: "/reports", label: "Reports", icon: FileBarChart, anyPermission: ["REPORT_VIEW"], excludeRole: ["SUPER_ADMIN", "COMPANY_ADMIN"], moduleCode: "REPORTS" },
  // HR runs their own groups here as well as the admin, so the section follows
  // COMMUNITY_MANAGE rather than the organisation-wide permission.
  { to: "/chat", label: "Chat", icon: MessageSquareWarning, moduleCode: "CHAT" },
  { to: "/calendar", label: "Calendar", icon: CalendarDays, moduleCode: "CALENDAR" },
  { to: "/teams", label: "Teams", icon: Users2, moduleCode: "TEAMS" },
  { to: "/documents", label: "Documents", icon: FolderOpen, moduleCode: "DOCUMENTS" },
  { to: "/projects", label: "Projects", icon: FolderGit2, moduleCode: "PROJECTS" },
  // Time Tracking, Learning, Surveys, Directory and OKR used to sit here. None of
  // them has a route, so switching the module on put a link in the sidebar that
  // led to the not-found page. Their entries in the tech-admin module list have
  // gone too; bring a link back at the same time as its page, not before.
  // Audit log is for Admins
  // "/audit", not "/audit-log" -- the route is registered as the former, so the
  // link went to the not-found page for everyone allowed to use it.
  { to: "/audit", label: "Audit Log", icon: History, moduleCode: "AUDIT_LOG", onlyRole: ["SUPER_ADMIN", "COMPANY_ADMIN"] },
  // Emptying the portal is not part of running it, so HR never sees this.
  // Fresh Start removed from the sidebar on request. The page and the API are
  // untouched — /admin/reset still exists, still requires SUPER_ADMIN and still
  // makes you type RESET — so putting it back is this one line, nothing more.
  // { to: "/admin/reset", label: "Fresh Start", icon: Eraser, onlyRole: ["SUPER_ADMIN"] }
];

/**
 * Which module a path belongs to, for the per-module branding.
 *
 * Read off NAV rather than kept as a second list beside it. A separate map would
 * be right the day it was written and wrong the first time a route moved, and
 * the symptom — one page quietly not taking its module's colour — is the kind
 * nobody reports.
 *
 * Longest match wins, so /leave/approvals is not answered by /leave.
 */
const MODULE_ROUTES: { path: string; moduleCode: string }[] = NAV.flatMap((entry) => {
  if ("type" in entry && entry.type === "group") {
    return entry.children
      .map((c) => ({ path: c.to, moduleCode: c.moduleCode ?? entry.moduleCode ?? "" }))
      .filter((r) => r.moduleCode);
  }
  const item = entry as NavItem;
  return item.moduleCode ? [{ path: item.to, moduleCode: item.moduleCode }] : [];
}).sort((a, b) => b.path.length - a.path.length);

function moduleForPath(pathname: string): string | null {
  const match = MODULE_ROUTES.find(
    (r) => pathname === r.path || pathname.startsWith(`${r.path}/`)
  );
  return match?.moduleCode ?? null;
}

function getRoleDisplayName(roles: string[] = []): string {
  if (roles.includes("BOARD_ADMIN")) return "Board Admin";
  if (roles.includes("SUPER_ADMIN")) return "System Admin";
  // The same job, so the same words. Reading "Company Admin" beside a portal
  // that behaves like the system administrator's invited the question of which
  // of the two this account was.
  if (roles.includes("COMPANY_ADMIN")) return "System Admin";
  if (roles.includes("IT_HR")) return "HR Head";
  if (roles.includes("IT_MGR")) return "HR";
  if (roles.includes("IT_TL")) return "Team Leader";
  if (roles.includes("IT_EMP")) return "Employee";
  if (roles.includes("CV_SUP")) return "Site Supervisor";
  if (roles.includes("CV_EMP")) return "Field Employee";
  if (roles.length > 0) return roles[0].replace(/_/g, " ");
  return "Employee";
}

// Per-type icon + colour so different kinds of alerts are recognisable at a
// glance in the notification bell (announcements, chat, leave, etc.).
function notificationStyle(type?: string) {
  switch (type) {
    case "LEAVE":
      return { icon: CalendarCheck, className: "bg-indigo-100 text-indigo-600 dark:bg-indigo-500/20 dark:text-indigo-400" };
    case "PERMISSION":
      return { icon: Clock, className: "bg-amber-100 text-amber-600 dark:bg-amber-500/20 dark:text-amber-400" };
    case "TASK":
      return { icon: CheckSquare, className: "bg-violet-100 text-violet-600 dark:bg-violet-500/20 dark:text-violet-400" };
    case "HELPDESK":
      return { icon: LifeBuoy, className: "bg-sky-100 text-sky-600 dark:bg-sky-500/20 dark:text-sky-400" };
    case "CHAT":
      return { icon: MessageSquare, className: "bg-emerald-100 text-emerald-600 dark:bg-emerald-500/20 dark:text-emerald-400" };
    case "CELEBRATION":
      return { icon: PartyPopper, className: "bg-pink-100 text-pink-600 dark:bg-pink-500/20 dark:text-pink-400" };
    case "ASSET":
      return { icon: Boxes, className: "bg-cyan-100 text-cyan-600 dark:bg-cyan-500/20 dark:text-cyan-400" };
    case "PAYSLIP":
      return { icon: Wallet, className: "bg-teal-100 text-teal-600 dark:bg-teal-500/20 dark:text-teal-400" };
    case "ANNOUNCEMENT":
      return { icon: MessageSquareWarning, className: "bg-rose-100 text-rose-600 dark:bg-rose-500/20 dark:text-rose-400" };
    default:
      return { icon: Bell, className: "bg-muted text-muted-foreground" };
  }
}

function AppShell() {
  const isFetching = useIsFetching();
  const { user, logout, hasPermission, hasRole, hasModule, hasDashboard } = useAuth();
  const { theme, setTheme } = useTheme();
  const navigate = useNavigate();
  const location = useLocation();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [bellOpen, setBellOpen] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const { notifications, unreadCount, markAllRead, markRead } = useNotifications(user?.id);

  /*
   * The company's look, resolved for this person on this page.
   *
   * Mounted here, at the one component every signed-in screen sits inside, so
   * moving between pages re-resolves rather than re-mounts — which is what lets
   * a module with its own colour take it on navigation, with no flash of the
   * company default in between.
   */
  const activeModule = useMemo(() => moduleForPath(location.pathname), [location.pathname]);
  const brand = useBranding(activeModule);

  const isSupAdmin = hasRole("SUPER_ADMIN") || hasRole("COMPANY_ADMIN");

  // Track open/closed state for each group by key
  const [groupOpen, setGroupOpen] = useState<Record<string, boolean>>(() => ({
    "leave-management": location.pathname.startsWith("/leave"),
    "payroll": location.pathname.startsWith("/payroll") || location.pathname.startsWith("/payslips")
  }));

  const toggleGroup = (key: string) =>
    setGroupOpen((prev) => ({ ...prev, [key]: !prev[key] }));

  // Build visible nav — handle group entries inline
  const visibleNav = NAV.filter((entry) => {
    // The dashboard asks hasDashboard rather than hasModule: a company that
    // has never configured its modules must keep it, so unknown means on.
    if (!("type" in entry) && entry.to === "/" && !hasDashboard()) return false;
    if (entry.moduleCode && !hasModule(entry.moduleCode)) return false;
    return true;
  }).map((entry) => {
    if ("type" in entry && entry.type === "group") {
      const visibleChildren = entry.children.filter(
        (c) =>
          !(c.excludeRole?.some((r) => hasRole(r))) &&
          (!c.anyPermission || hasPermission(...c.anyPermission) || isSupAdmin) &&
          (!c.moduleCode || hasModule(c.moduleCode))
      );
      if (visibleChildren.length === 0) return null;
      return { ...entry, children: visibleChildren };
    }
    const item = entry as NavItem;
    if (item.excludeRole?.some((r) => hasRole(r))) return null;
    // A role-only entry ignores the Super Admin shortcut below on purpose.
    if (item.onlyRole && !item.onlyRole.some((r) => hasRole(r))) return null;
    if (!item.anyPermission || hasPermission(...item.anyPermission) || isSupAdmin) {
      // Team Leaders (not HR/admins) see this as "Team Attendance".
      if (item.to === "/team-attendance") {
        const tl = hasRole("IT_TL") && !hasRole("IT_MGR") && !hasRole("SUPER_ADMIN");
        return { ...item, label: tl ? "Team Attendance" : "Employee Attendance" };
      }
      return item;
    }
    return null;
  }).filter(Boolean) as (NavItem | NavGroup)[];

  const isNavGroup = (grp: NavEntry): grp is NavGroup =>
    "type" in grp && grp.type === "group";

  const userName = (user as any)?.name || (user?.firstName ? `${user.firstName} ${user.lastName || ''}`.trim() : "User");
  const roleLabel = getRoleDisplayName(user?.roles);

  return (
    <div className="flex h-screen overflow-hidden bg-background">
      {/* Sidebar */}
      <aside
        className={cn(
          "fixed inset-y-0 left-0 z-40 w-64 transform bg-slate-900 text-slate-100 transition-transform duration-200 lg:static lg:translate-x-0 flex flex-col",
          sidebarOpen ? "translate-x-0" : "-translate-x-full"
        )}
      >
        {/* Logo Header */}
        <div className="flex h-16 items-center gap-3 border-b border-white/10 px-4 shrink-0">
          <img
            src="https://pixoustech.com/public/assets/images/common/pixous-logo1.png"
            alt={user?.companyName || "Company"}
            className="h-10 w-auto object-contain bg-white rounded p-1"
            onError={(e) => { (e.target as HTMLElement).style.display = 'none'; }}
          />
          <div className="flex-1 overflow-hidden">
            <div className="font-extrabold text-sm tracking-wide text-white truncate" title={user?.companyName}>
              {(() => {
                // The account carries its own company name; that is the answer.
                // A lookup through a localStorage copy of the tenant list used to
                // sit here as a second guess, and nothing writes that list any
                // more -- companies are read from the server now -- so it could
                // only ever have returned stale names or nothing at all.
                if (user?.companyName) return user.companyName.toUpperCase();
                return "PIXOUS TECHNOLOGIES";
              })()}
            </div>
            {/* The company's own wording where it has set some. Left empty in
                the branding screen, this stays as it has always read. */}
            <div className="text-[10px] text-white/50 uppercase tracking-widest font-semibold truncate">
              {brand?.productName || "EMPLOYEE MANAGEMENT SYSTEM"}
            </div>
          </div>
        </div>

        {/* Nav Links (scrollable) */}
        <nav className="flex-1 overflow-y-auto p-3 space-y-0.5">
          {visibleNav.map((entry) => {
            // ── Collapsible group ──
            if (isNavGroup(entry)) {
              const grp = entry as NavGroup;
              const isOnGroup = location.pathname.startsWith(grp.matchPath)
                || (grp.key === "payroll" && location.pathname.startsWith("/payslips"));
              const isOpen = !!groupOpen[grp.key];
              return (
                <div key={grp.key}>
                  <button
                    type="button"
                    onClick={() => toggleGroup(grp.key)}
                    className={cn(
                      "flex w-full items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                      isOnGroup
                        ? "bg-primary/20 text-white"
                        : "text-white/70 hover:bg-white/10 hover:text-white"
                    )}
                  >
                    <grp.icon className="h-[18px] w-[18px] shrink-0" />
                    <span className="flex-1 text-left">{grp.label}</span>
                    <ChevronDown
                      className={cn(
                        "h-4 w-4 shrink-0 text-white/50 transition-transform duration-200",
                        isOpen && "rotate-180"
                      )}
                    />
                  </button>

                  {isOpen && (
                    <div className="ml-4 mt-0.5 space-y-0.5 border-l border-white/10 pl-3">
                      {grp.children.map((child) => (
                        <NavLink
                          key={child.to}
                          to={child.to}
                          end={child.end ?? true}
                          onClick={() => setSidebarOpen(false)}
                          className={({ isActive }) =>
                            cn(
                              "flex items-center gap-2.5 rounded-md px-2 py-1.5 text-sm font-medium transition-colors",
                              isActive
                                ? "bg-primary text-primary-foreground"
                                : "text-white/60 hover:bg-white/10 hover:text-white"
                            )
                          }
                        >
                          <child.icon className="h-[15px] w-[15px] shrink-0" />
                          {child.label}
                        </NavLink>
                      ))}
                    </div>
                  )}
                </div>
              );
            }
            // ── Regular nav link ──
            const item = entry as NavItem;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                onClick={() => setSidebarOpen(false)}
                className={({ isActive }) =>
                  cn(
                    "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-semibold transition-colors",
                    isActive
                      ? "bg-primary text-primary-foreground font-bold shadow-sm"
                      : "text-slate-200 hover:bg-white/15 hover:text-white"
                  )
                }
              >
                <item.icon className="h-[18px] w-[18px] shrink-0" />
                {item.label}
              </NavLink>
            );
          })}
        </nav>

        {/* User Profile Footer */}
        <div className="shrink-0 border-t border-white/10 p-3">
          <button
            onClick={() => { setSidebarOpen(false); navigate("/profile"); }}
            className="flex w-full items-center gap-3 rounded-lg px-2 py-2.5 text-left transition-colors hover:bg-white/10 group"
          >
            <div className="relative shrink-0">
              <Avatar
                name={userName}
                src={user?.photoPath}
                className="h-10 w-10 ring-2 ring-white/20 shadow-md"
              />
              <span className="absolute -bottom-0.5 -right-0.5 h-3 w-3 rounded-full bg-emerald-400 ring-2 ring-secondary" />
            </div>
            <div className="flex-1 min-w-0">
              <div className="truncate text-sm font-bold text-white leading-tight">
                {userName}
              </div>
              <div className="truncate text-xs font-bold text-slate-300 leading-tight mt-0.5">
                {roleLabel}
              </div>
            </div>
          </button>
        </div>
      </aside>

      {sidebarOpen && (
        <div
          className="fixed inset-0 z-30 bg-black/40 lg:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      {/* Main column */}
      <div className="flex flex-1 flex-col overflow-hidden">
        {/* Topbar */}
        <header className="flex h-16 shrink-0 items-center gap-3 border-b bg-card px-4 lg:px-6">
          <button
            className="rounded-md p-2 hover:bg-muted lg:hidden"
            onClick={() => setSidebarOpen(true)}
            aria-label="Open menu"
          >
            <Menu className="h-5 w-5" />
          </button>


          <div className="ml-auto flex items-center gap-1">
            {/* Notification bell */}
            <div className="relative">
              <button
                className="relative rounded-md p-2 hover:bg-muted"
                onClick={() => setBellOpen((v) => !v)}
                aria-label="Notifications"
              >
                <Bell className="h-5 w-5" />
                {unreadCount > 0 && (
                  <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-destructive px-1 text-[10px] font-semibold text-destructive-foreground">
                    {unreadCount > 9 ? "9+" : unreadCount}
                  </span>
                )}
              </button>
              {bellOpen && (
                <>
                  <div className="fixed inset-0 z-10" onClick={() => setBellOpen(false)} />
                  <div className="absolute right-0 z-20 mt-2 w-80 rounded-lg border bg-popover shadow-lg animate-fade-in">
                    <div className="flex items-center justify-between border-b px-4 py-3">
                      <span className="font-display text-sm font-semibold">Notifications</span>
                      {unreadCount > 0 && (
                        <button
                          className="text-xs text-primary hover:underline"
                          onClick={() => markAllRead()}
                        >
                          Mark all read
                        </button>
                      )}
                    </div>
                    <div className="max-h-80 overflow-y-auto">
                      {notifications.length === 0 ? (
                        <p className="px-4 py-8 text-center text-sm text-muted-foreground">
                          You're all caught up.
                        </p>
                      ) : (
                        notifications.slice(0, 8).map((n) => {
                          const { icon: NIcon, className: nClassName } = notificationStyle(n.type);
                          return (
                            <button
                              key={n.id}
                              onClick={() => {
                                setBellOpen(false);
                                // Opening it is reading it — the blue dot stays
                                // only for notifications never looked at.
                                if (!n.read) markRead(n.id);
                                if (n.link) navigate(n.link);
                              }}
                              className={cn(
                                "flex w-full gap-3 border-b px-4 py-3 text-left transition-colors last:border-0 hover:bg-muted/60",
                                !n.read && "bg-primary/5"
                              )}
                            >
                              <span className={cn("mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg", nClassName)}>
                                <NIcon className="h-4 w-4" />
                              </span>
                              <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                                <div className="flex items-center gap-2">
                                  {!n.read && <span className="h-2 w-2 shrink-0 rounded-full bg-primary" />}
                                  <span className="truncate text-sm font-medium">{n.title}</span>
                                </div>
                                {n.body && (
                                  <span className="line-clamp-2 text-xs text-muted-foreground">{n.body}</span>
                                )}
                                <span className="text-[11px] text-muted-foreground">
                                  {dayjs(n.createdAt).format("DD MMM, h:mm A")}
                                </span>
                              </div>
                            </button>
                          );
                        })
                      )}
                    </div>
                    <button
                      className="block w-full border-t px-4 py-2.5 text-center text-xs font-medium text-primary hover:bg-muted/60"
                      onClick={() => {
                        setBellOpen(false);
                        navigate("/notifications");
                      }}
                    >
                      View all
                    </button>
                  </div>
                </>
              )}
            </div>

            {/* Real-time Loading Indicator */}
            {isFetching > 0 && (
              <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-primary/10 text-primary border border-primary/20 text-xs font-semibold shadow-xs" title="Loading data...">
                <CustomLoader className="h-4 w-4 text-primary" />
                <span className="hidden sm:inline text-[11px] font-mono tracking-tight">Syncing...</span>
              </div>
            )}

            {/* Theme toggle */}
            <button
              className="rounded-md p-2 hover:bg-muted"
              onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
              aria-label="Toggle theme"
            >
              <Sun className="h-5 w-5 dark:hidden" />
              <Moon className="hidden h-5 w-5 dark:block" />
            </button>

            {/* User menu */}
            <div className="relative ml-1">
              <button
                className="flex items-center gap-2 rounded-md p-1 pr-2 hover:bg-muted"
                onClick={() => setMenuOpen((v) => !v)}
              >
                <Avatar name={userName} src={user?.photoPath} />
                <div className="hidden text-left leading-tight sm:block">
                  <div className="text-sm font-medium">{userName}</div>
                  <div className="code-chip text-[11px] text-muted-foreground font-mono font-semibold">
                    {(() => {
                      if (isSupAdmin) return "ADMIN";
                      if ((user as any)?.employeeCode) return (user as any).employeeCode;
                      if (user?.employeeId) return user.employeeId;
                      if (user?.id && !String(user.id).includes("mock-admin") && !String(user.id).startsWith("b") && !String(user.id).startsWith("m")) {
                        return `EMP${String(user.id).substring(0, 4)}`;
                      }
                      return "EMP0001";
                    })()}
                  </div>
                </div>
              </button>
              {menuOpen && (
                <>
                  <div className="fixed inset-0 z-10" onClick={() => setMenuOpen(false)} />
                  <div className="absolute right-0 z-20 mt-2 w-56 rounded-lg border bg-popover p-1.5 shadow-lg animate-fade-in">
                    <div className="px-3 py-2">
                      <div className="text-sm font-medium">{userName}</div>
                      <div className="text-xs text-muted-foreground">{user?.email}</div>
                      <div className="mt-1.5 flex flex-wrap gap-1">
                        {user?.roles?.slice(0, 3).map((r) => (
                          <Badge key={r} variant="secondary" className="text-[10px]">
                            {roleCodeLabel(r)}
                          </Badge>
                        ))}
                      </div>
                    </div>
                    <div className="my-1 border-t" />
                    <button
                      className="flex w-full items-center gap-2 rounded-md px-3 py-2 text-sm hover:bg-muted"
                      onClick={() => {
                        setMenuOpen(false);
                        navigate("/profile");
                      }}
                    >
                      <User className="h-4 w-4" /> My profile
                    </button>
                    <button
                      className="flex w-full items-center gap-2 rounded-md px-3 py-2 text-sm text-destructive hover:bg-destructive/10"
                      onClick={() => {
                        logout();
                        navigate("/login");
                      }}
                    >
                      <LogOut className="h-4 w-4" /> Sign out
                    </button>
                  </div>
                </>
              )}
            </div>
          </div>
        </header>

        {/* Routed content */}
        <main className="flex-1 overflow-y-auto p-4 lg:p-6">
          <div className="mx-auto max-w-7xl">
            {visibleNav.length === 0 ? <NoModulesNotice /> : <Outlet />}
          </div>
        </main>
      </div>
      <ChatBotWidget />
    </div>
  );
}

/**
 * The shell, with calling wrapped around it. Mounting the call engine here
 * rather than on the chat page is what lets somebody's phone ring while they are
 * looking at their payslip.
 */
export function AppLayout() {
  return (
    <CallProvider>
      <AppShell />
    </CallProvider>
  );
}

/**
 * Shown in place of the routed page when this company has no module a person
 * in this role may open.
 *
 * Without it, someone in that position signed in to a portal with an empty
 * sidebar and a blank panel — indistinguishable from the application being
 * broken. It is not broken; nothing has been switched on for them yet, and
 * that is worth saying in those words.
 *
 * Deliberately not offering a "try again" or a link elsewhere. There is
 * nowhere to send them, and a button that cannot help is worse than none.
 */
function NoModulesNotice() {
  return (
    <div
      role="alert"
      className="mx-auto mt-10 max-w-lg rounded-xl border border-amber-300 bg-amber-50 p-6 text-center dark:border-amber-500/40 dark:bg-amber-950/20"
    >
      <span className="mx-auto grid h-12 w-12 place-items-center rounded-full bg-amber-100 text-amber-600 dark:bg-amber-500/20 dark:text-amber-400">
        <ShieldAlert className="h-6 w-6" />
      </span>
      <h2 className="mt-4 text-lg font-semibold text-amber-900 dark:text-amber-200">
        Nothing is switched on for you yet
      </h2>
      <p className="mt-2 text-sm text-amber-800/90 dark:text-amber-200/80">
        Your account is fine and you are signed in correctly. No modules have
        been enabled for your company yet, so there are no pages to show.
      </p>
      <p className="mt-3 text-sm text-amber-800/90 dark:text-amber-200/80">
        Ask your administrator to enable the modules your team needs.
      </p>
    </div>
  );
}
