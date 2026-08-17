import { lazy, Suspense, type ReactNode } from "react";
import { createBrowserRouter, Navigate } from "react-router-dom";
import { AppLayout } from "@/components/layout/AppLayout";
import { ProtectedRoute } from "@/components/layout/ProtectedRoute";
import { RoleGuard } from "@/components/layout/RoleGuard";
import { Skeleton } from "@/components/ui/skeleton";
import { CustomLoader } from "@/components/ui/custom-loader";
import { useAuth } from "@/hooks/useAuth";

// Login stays eagerly imported. It is the first thing an unauthenticated visitor
// sees, and splitting it would only add a network round trip before the form
// appears -- the opposite of what the splitting below is for.
import LoginPage from "@/pages/Login";
import NotFoundPage from "@/pages/NotFound";

/**
 * Every other page is fetched when it is first visited, not before.
 *
 * <p>The whole application used to be one JavaScript file of 2.4 MB: opening the
 * login page downloaded and parsed the payroll screens, the org chart, the chat
 * client, the spreadsheet writer and the charting library, none of which anybody
 * had asked for yet. On a phone on mobile data that is most of the wait before
 * anything appears at all.
 *
 * <p>Nothing about what these pages do changes. They render exactly as before,
 * the same routes guard them, and a page already visited is not fetched again.
 */
const DashboardPage = lazy(() => import("@/pages/Dashboard"));
const AttendancePage = lazy(() => import("@/pages/Attendance"));
const TeamAttendancePage = lazy(() => import("@/pages/TeamAttendance"));
const LeavePage = lazy(() => import("@/pages/Leave"));
const LeaveApprovalsPage = lazy(() => import("@/pages/LeaveApprovals"));
const LeavePoliciesPage = lazy(() => import("@/pages/LeavePolicies"));
const PermissionsPage = lazy(() => import("@/pages/Permissions"));
const PayslipsPage = lazy(() => import("@/pages/Payslips"));
const PayrollRunsPage = lazy(() => import("@/pages/PayrollRuns"));
const PayrollRequestsPage = lazy(() => import("@/pages/PayrollRequests"));
const EmployeesPage = lazy(() => import("@/pages/Employees"));
const AssetsPage = lazy(() => import("@/pages/Assets"));
const HelpdeskPage = lazy(() => import("@/pages/Helpdesk"));
const TicketEntryPage = lazy(() => import("@/pages/TicketEntry"));
const ComplaintsPage = lazy(() => import("@/pages/Complaints"));
const ProfilePage = lazy(() => import("@/pages/Profile"));
const NotificationsPage = lazy(() => import("@/pages/Notifications"));
const TaExpensesPage = lazy(() => import("@/pages/TaExpenses"));
const ClaimEntryPage = lazy(() => import("@/pages/ClaimEntry"));
const ReportsPage = lazy(() => import("@/pages/Reports"));
const OnboardingPage = lazy(() => import("@/pages/Onboarding"));
const WorkReportsPage = lazy(() => import("@/pages/WorkReports"));
const CommunitiesPage = lazy(() => import("@/pages/Communities"));
const ChatPage = lazy(() => import("@/pages/Chat"));
const AdminChatbotSettings = lazy(() => import("@/pages/AdminChatbotSettings"));
const DataResetPage = lazy(() => import("@/pages/DataReset"));
const AuditLogPage = lazy(() => import("@/pages/AuditLog"));
const CalendarPage = lazy(() => import("@/pages/Calendar"));
const TasksPage = lazy(() => import("@/pages/Tasks"));
const TeamsPage = lazy(() => import("@/pages/Teams"));
const MyTeamPage = lazy(() => import("@/pages/MyTeam"));

// Technical Admin Pages
import { TechAdminProvider } from "@/context/TechAdminAuthContext";
import { TechAdminLayout } from "@/pages/tech-admin/Layout";
import { TechAdminLogin } from "@/pages/tech-admin/Login";
const TechAdminDashboard = lazy(() => import("@/pages/tech-admin/Dashboard").then(m => ({ default: m.TechAdminDashboard })));
const TechAdminCompanies = lazy(() => import("@/pages/tech-admin/Companies").then(m => ({ default: m.TechAdminCompanies })));
const TechAdminCompanyConfig = lazy(() => import("@/pages/tech-admin/CompanyConfig").then(m => ({ default: m.TechAdminCompanyConfig })));
const TechAdminAuditLogs = lazy(() => import("@/pages/tech-admin/AuditLogs").then(m => ({ default: m.TechAdminAuditLogs })));
const TechAdminSettings = lazy(() => import("@/pages/tech-admin/Settings").then(m => ({ default: m.TechAdminSettings })));
const TechAdminBranding = lazy(() => import("@/pages/tech-admin/Branding").then(m => ({ default: m.TechAdminBranding })));
const TechAdminModuleManagement = lazy(() => import("@/pages/tech-admin/ModuleManagement").then(m => ({ default: m.TechAdminModuleManagement })));
const TechAdminUsers = lazy(() => import("@/pages/tech-admin/Users").then(m => ({ default: m.TechAdminUsers })));
const TechAdminRoles = lazy(() => import("@/pages/tech-admin/Roles").then(m => ({ default: m.TechAdminRoles })));

// ModulePlaceholder is a named export, so it needs mapping to the default shape
// React.lazy expects. Getting this wrong fails only when the route is opened,
// which is exactly the kind of thing that reaches production unnoticed.
const ModulePlaceholder = lazy(() =>
  import("@/pages/ModulePlaceholder").then((m) => ({ default: m.ModulePlaceholder }))
);

/**
 * What fills the page while its code is being fetched.
 *
 * <p>Shaped like the page that is coming -- a heading, then rows -- rather than a
 * spinner in the middle of an empty screen, so the layout does not jump when the
 * real content arrives. Only ever seen once per page per visit.
 */
function PageFallback() {
  return (
    <div className="flex h-full w-full items-center justify-center min-h-[50vh]" aria-busy="true" aria-label="Loading">
      <CustomLoader className="h-16 w-16" />
    </div>
  );
}

/**
 * Wraps a lazily-loaded page in its own Suspense boundary.
 *
 * <p>Per route rather than one boundary around the whole layout on purpose: a
 * shared boundary would blank the sidebar and header every time somebody moved
 * between pages. This way the frame stays put and only the page area waits.
 */
function page(node: ReactNode) {
  return <Suspense fallback={<PageFallback />}>{node}</Suspense>;
}

/** Admins & HR see all teams (HR read-only); others see only their own team. */
function TeamsRouteElement() {
  const { hasPermission, hasRole } = useAuth();
  const seeAll = hasPermission("USER_MANAGE") || hasRole("IT_MGR");
  return seeAll ? <TeamsPage /> : <MyTeamPage />;
}

export const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  {
    path: "/",
    element: (
      <ProtectedRoute>
        <AppLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: page(<DashboardPage />) },
      { path: "attendance", element: page(<AttendancePage />) },
      {
        path: "team-attendance",
        element: page(
          <RoleGuard permission="ATTENDANCE_TEAM">
            <TeamAttendancePage />
          </RoleGuard>
        )
      },
      { path: "leave", element: page(<LeavePage />) },
      { path: "leave/permissions", element: page(<PermissionsPage />) },
      {
        path: "leave/approvals",
        element: page(
          <RoleGuard permission="LEAVE_APPROVE">
            <LeaveApprovalsPage />
          </RoleGuard>
        )
      },
      {
        path: "leave/policies",
        element: page(
          <RoleGuard permission="ORG_MANAGE">
            <LeavePoliciesPage />
          </RoleGuard>
        )
      },
      { path: "payslips", element: page(<PayslipsPage />) },
      {
        path: "payroll/requests",
        element: page(
          <RoleGuard permission="PAYROLL_RUN">
            <PayrollRequestsPage />
          </RoleGuard>
        )
      },
      { path: "work-reports", element: page(<WorkReportsPage />) },
      {
        path: "employees",
        element: page(
          <RoleGuard permission="USER_MANAGE,ATTENDANCE_TEAM,REPORT_VIEW">
            <EmployeesPage />
          </RoleGuard>
        )
      },
      { path: "assets", element: page(<AssetsPage />) },
      { path: "helpdesk", element: page(<HelpdeskPage />) },
      { path: "helpdesk/new", element: page(<TicketEntryPage />) },
      { path: "complaints", element: page(<ComplaintsPage />) },
      { path: "notifications", element: page(<NotificationsPage />) },
      { path: "profile", element: page(<ProfilePage />) },
      { path: "ta-expenses", element: page(<TaExpensesPage />) },
      { path: "ta-expenses/new", element: page(<ClaimEntryPage />) },
      { path: "ta-expenses/:id/edit", element: page(<ClaimEntryPage />) },

      // Scaffolded modules — routed so navigation is complete end-to-end.
      {
        path: "payroll/run",
        element: page(
          <RoleGuard permission="PAYROLL_RUN,PAYROLL_APPROVE">
            <PayrollRunsPage />
          </RoleGuard>
        )
      },
      {
        path: "onboarding",
        element: page(
          <RoleGuard permission="USER_MANAGE">
            <OnboardingPage />
          </RoleGuard>
        )
      },
      {
        path: "reports",
        element: page(
          <RoleGuard permission="REPORT_VIEW">
            <ReportsPage />
          </RoleGuard>
        )
      },
      {
        path: "communities",
        element: page(
          <RoleGuard permission="ORG_MANAGE,COMMUNITY_MANAGE">
            <CommunitiesPage />
          </RoleGuard>
        )
      },
      {
        path: "projects",
        element: page(
          <RoleGuard permission="ORG_MANAGE">
            <ModulePlaceholder
              title="Projects"
              summary="Track and manage company projects, milestones and deliverables."
              endpoints={["org.projects"]}
            />
          </RoleGuard>
        )
      },
      {
        path: "tasks",
        element: page(<TasksPage />)
      },
      {
        path: "teams",
        element: page(<TeamsRouteElement />)
      },
      {
        path: "documents",
        element: page(
          <RoleGuard permission="ORG_MANAGE">
            <ModulePlaceholder
              title="Documents"
              summary="Manage company documents, policies and HR forms."
              endpoints={["org.documents"]}
            />
          </RoleGuard>
        )
      },
      {
        path: "calendar",
        element: page(<CalendarPage />)
      },
      {
        path: "chat",
        element: page(<ChatPage />)
      },
      {
        path: "admin/ai-assistant",
        element: page(
          <RoleGuard permission="USER_MANAGE">
            <AdminChatbotSettings />
          </RoleGuard>
        )
      },
      {
        // Reading the trail is itself privileged — it names who did what and from
        // which address — so HR and the admin, and nobody else.
        path: "audit",
        element: page(
          <RoleGuard permission="USER_MANAGE,EMPLOYEE_MANAGE">
            <AuditLogPage />
          </RoleGuard>
        )
      },
      {
        // Emptying the portal is not part of running it, so this is the one
        // place HR does not reach even holding every permission.
        path: "admin/reset",
        element: page(
          <RoleGuard role="SUPER_ADMIN">
            <DataResetPage />
          </RoleGuard>
        )
      }
    ]
  },
  {
    path: "/tech-admin",
    element: (
      <TechAdminProvider>
        <TechAdminLayout />
      </TechAdminProvider>
    ),
    children: [
      { index: true, element: <Navigate to="/tech-admin/dashboard" replace /> },
      { path: "dashboard", element: page(<TechAdminDashboard />) },
      { path: "companies", element: page(<TechAdminCompanies />) },
      { path: "companies/:id/config", element: page(<TechAdminCompanyConfig />) },
      { path: "module-management", element: page(<TechAdminModuleManagement />) },
      // Was rendering ModuleManagement, which is why Roles & Permissions looked
      // empty — it was showing a different screen.
      { path: "roles", element: page(<TechAdminRoles />) },
      { path: "users", element: page(<TechAdminUsers />) },
      { path: "employees", element: page(<TechAdminUsers />) },
      // "organization" removed with its sidebar entry — it rendered the
      // Companies page, which /tech-admin/companies already does.
      { path: "module/*", element: page(<TechAdminModuleManagement />) },
      { path: "audit-logs", element: page(<TechAdminAuditLogs />) },
      { path: "integrations", element: page(<TechAdminSettings />) },
      { path: "branding", element: page(<TechAdminBranding />) },
      { path: "security", element: page(<TechAdminSettings />) },
      { path: "settings", element: page(<TechAdminSettings />) }
    ]
  },
  {
    path: "/tech-admin/login",
    element: (
      <TechAdminProvider>
        <TechAdminLogin />
      </TechAdminProvider>
    )
  },
  { path: "*", element: <NotFoundPage /> }
]);
