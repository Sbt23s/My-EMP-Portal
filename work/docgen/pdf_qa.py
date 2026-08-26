"""QA Testing Document — PDF."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pdf_style import (bullets, document, h1, h2, metrics, note, p, pill,
                       section, table)
from render_pdf import to_pdf

OUT = os.path.join(os.path.expanduser('~'), 'Downloads',
                   'Pixous_HR_Portal_QA_Testing_v1.0.pdf')

PASS = lambda: pill('pass', 'Pass')
FAIL = lambda t='Fixed': pill('warn', t)

body = ''.join([

section(
  h1('1.', 'Purpose and Scope'),
  p('This document records what has been tested in the PIXOUS HR Portal, how it '
    'was tested, what passed, and — as plainly as the passes — what is not '
    'covered. A QA document listing only successes tells a reader nothing they '
    'can act on, so the gaps are named in section 8 and carried into the risk '
    'register rather than left out.'),

  metrics([('97', 'tests passing'), ('0', 'failures'),
           ('13', 'defects fixed'), ('98,591', 'lines analysed')]),

  h2('1.1  The application under test'),
  table(['Component', 'Technology', 'Files', 'Lines', 'Role'],
        [['Backend API', 'Spring Boot 3.5, Java 17', '315', '26,321',
          'REST API, security, persistence'],
         ['Web portal', 'React 18, TypeScript, Vite', '113', '50,258',
          'Desktop and tablet client'],
         ['Mobile app', 'Flutter 3.44, Riverpod', '69', '22,012',
          'Android client'],
         ['Database', 'MySQL 8 (external hosting)', '102 migrations', '—',
          'Flyway-managed schema'],
         ['TOTAL', '', '599', '98,591', '']],
        widths=['17%', '24%', '12%', '12%', '35%'], total_last=True),
  note('Counts taken from the working tree with find and wc; migrations counted '
       'from backend/src/main/resources/db/migration.'),

  h2('1.2  Modules in scope'),
  table(['#', 'Module', 'Web', 'Mobile', 'Notes'],
        [['1', 'Authentication and session', 'Yes', 'Yes',
          'JWT, refresh, lockout after repeated failures'],
         ['2', 'Dashboard', 'Yes', 'Yes', 'Role-dependent widgets'],
         ['3', 'Attendance and punch', 'Yes', 'Yes',
          'Geolocation, face verification'],
         ['4', 'Leave', 'Yes', 'Yes', 'Apply, approve, balances, policies'],
         ['5', 'Permission (short leave)', 'Yes', 'Yes',
          'Separate approval chain'],
         ['6', 'Payroll and payslips', 'Yes', 'Yes',
          'Runs, requests, PDF, email'],
         ['7', 'Work reports', 'Yes', 'Yes', 'Own and team'],
         ['8', 'Tasks', 'Yes', 'Yes', 'Assign, progress, oversight'],
         ['9', 'Claims / TA expenses', 'Yes', 'Yes',
          'Submit, approve, export'],
         ['10', 'Assets', 'Yes', 'Yes', 'Issue, confirm receipt, return'],
         ['11', 'Supports (helpdesk)', 'Yes', 'Yes', 'Raise, route, resolve'],
         ['12', 'Complaints', 'Yes', 'Yes', 'Raise, route, respond'],
         ['13', 'Chat and calls', 'Yes', 'Yes',
          'WebRTC one-to-one and group'],
         ['14', 'Communities', 'Yes', 'Yes', 'Channels, announcements'],
         ['15', 'Calendar', 'Yes', 'Yes', 'Events, holidays'],
         ['16', 'Employees / directory', 'Yes', 'Yes', 'Permission-gated'],
         ['17', 'Teams and attendance review', 'Yes', 'Yes', 'Manager views'],
         ['18', 'Reports and exports', 'Yes', 'Partial',
          'Excel and PDF export'],
         ['19', 'Notifications', 'Yes', 'Yes', 'In-app, unread counts'],
         ['20', 'Audit log', 'Yes', 'Yes', 'Administrators only']],
        widths=['5%', '27%', '8%', '10%', '50%']),
),

section(
  h1('2.', 'Roles Tested'),
  p('Authorisation in this system is by permission code, not by role name — a '
    'role is a bundle of permissions and two roles can share one. Test cases '
    'therefore assert on the permission the server checks, because that is what '
    'determines behaviour.'),
  table(['Role', 'Code', 'Key permissions', 'What the role can do'],
        [['Employee', 'IT_EMP / CV_EMP', '(base)',
          'Own attendance, leave, payslip, claims, tickets'],
         ['Team Leader', 'IT_TL / CV_SUP',
          'LEAVE_APPROVE, ATTENDANCE_TEAM, TASK_ASSIGN, REPORT_VIEW',
          'Approve team leave and permission, assign tasks, review reports'],
         ['HR', 'IT_MGR / IT_HR',
          'USER_MANAGE, COMPLAINT_MANAGE, TASK_VIEW_ALL, HELPDESK_AGENT',
          'Company-wide review, payroll, employee records'],
         ['CTO / System Admin', 'SUPER_ADMIN / COMPANY_ADMIN',
          'All, plus DASHBOARD_EXEC',
          'Executive view, audit log, configuration']],
        widths=['16%', '19%', '29%', '36%']),
  note('Permission-to-role grants verified against the Flyway migrations — for '
       'example V41 grants TASK_ASSIGN to IT_TL, and V47 grants TASK_VIEW_ALL '
       'to IT_MGR and IT_HR.'),
),

section(
  h1('3.', 'Executed Automated Tests'),
  h2('3.1  Backend — JUnit 5'),
  p('Command: mvn -o test.   Result: BUILD SUCCESS.'),
  table(['Suite', 'Tests', 'Fail', 'Err', 'Time', 'Covers'],
        [['StorageServiceTest', '5', '0', '0', '0.4 s',
          'Upload paths, traversal rejection'],
         ['WorkCalendarTest', '7', '0', '0', '0.1 s',
          'Working days, weekends, holidays'],
         ['PunchOutWindowTest', '7', '0', '0', '0.04 s',
          'Punch-out window boundaries'],
         ['AuthDtosValidationTest', '7', '0', '0', '1.1 s',
          'Login and registration payload validation'],
         ['AuthServiceTest', '13', '0', '0', '1.5 s',
          'Sign-in, lockout, token issue'],
         ['ChatbotOrgContextTest', '10', '0', '0', '0.6 s',
          'Assistant context assembly'],
         ['JwtServiceTest', '6', '0', '0', '4.2 s',
          'Token signing, expiry, tamper rejection'],
         ['LoginAttemptLimiterTest', '6', '0', '0', '0.03 s',
          'Brute-force throttling'],
         ['TOTAL', '61', '0', '0', '54.9 s', '']],
        widths=['24%', '8%', '7%', '7%', '10%', '44%'], total_last=True),

  h2('3.2  Web portal — Vitest'),
  p('Command: npm test -- --run.   Result: 4 files passed, 36 tests passed, '
    'duration 51.8 s.'),
  table(['Area', 'Result', 'Covers'],
        [['Login form', 'Pass', 'Validation, error surfacing, submit state'],
         ['Date and period helpers', 'Pass', 'Boundaries, formatting, ranges'],
         ['Formatting and currency', 'Pass', 'Money, hours, percentages'],
         ['Permission helpers', 'Pass', 'hasRole and hasPermission logic'],
         ['TOTAL — 36 tests', '36 passed', '']],
        widths=['30%', '16%', '54%'], total_last=True),
  note('Per-file test counts are not broken out by the runner in summary mode; '
       'the total is the reported figure.'),

  h2('3.3  Mobile app — static analysis'),
  table(['Check', 'Result', 'Meaning'],
        [['flutter analyze', 'No issues found',
          'No errors, warnings or lints across 69 Dart files'],
         ['Release build', 'Success',
          'APK produced and installed for manual verification']],
        widths=['22%', '24%', '54%']),
),

section(
  h1('4.', 'Functional Test Cases — Authorisation'),
  p('These are the cases that found real defects. Each states the rule, how it '
    'was checked, and the outcome.'),
  table(['ID', 'Case', 'Expected', 'Result'],
        [['QA-AUTH-01', 'Sign in with valid credentials',
          'Token issued, role and permissions returned', PASS()],
         ['QA-AUTH-02', 'Wrong password repeatedly',
          'Account locked after threshold', PASS()],
         ['QA-AUTH-03', 'Call an API with no token',
          'HTTP 401, no data disclosed', PASS()],
         ['QA-AUTH-04', 'Call an API with a tampered token',
          'Rejected', PASS()],
         ['QA-AUTH-05', 'Employee opens an HR-only endpoint',
          'HTTP 403', PASS()],
         ['QA-PERM-01', 'TL raises own permission request',
          'Must NOT appear in own approval queue', FAIL()],
         ['QA-PERM-02', 'Permission addressed to a named approver',
          'Only that approver sees Approve / Reject', PASS()],
         ['QA-LEAVE-01', 'Leave row visible but not actionable',
          'Buttons hidden when the server sets canAct=false', FAIL()],
         ['QA-CMPL-01', 'Complaint raised without a recipient',
          'Form must refuse to submit', FAIL()],
         ['QA-CMPL-02', 'Reviewer opens own complaint',
          'Read-only, no respond action', PASS()],
         ['QA-TKT-01', 'Ticket raised from mobile',
          'Title stored, recipient routed', FAIL()],
         ['QA-TASK-01', 'Team Leader assigns a task',
          'Accepted with TASK_ASSIGN', PASS()],
         ['QA-TASK-02', 'Oversight list of everyone’s tasks',
          'Read-only, assignee named', PASS()]],
        widths=['13%', '28%', '39%', '20%']),
  note('"Fixed" marks a case that failed when first run and passes now. The '
       'defect it exposed is listed in section 5.'),
),

section(
  h1('5.', 'Defects Found and Closed'),
  p('Thirteen defects were found by comparing the clients against the server '
    'contract, and the two clients against each other. All are closed. Each is '
    'described by its cause, because the cause is what stops it recurring.'),
  table(['#', 'Defect', 'Cause', 'Severity'],
        [['D-01', 'Complaint recipient never saved',
          'Client sent targetRoleId; API reads requestedTo', 'High'],
         ['D-02', 'Reviewer complaint list empty',
          'Client called /complaints/all; the route is /complaints', 'High'],
         ['D-03', 'Complaint response could not be saved',
          'Wrong route and field (/status with resolutionNotes)', 'High'],
         ['D-04', '"Sent to" always read "HR & Admin"',
          'Client read targetRoleName, which is never returned', 'Medium'],
         ['D-05', 'Anyone could respond to any complaint',
          'No addressee check in the page', 'High'],
         ['D-06', 'Status could be saved without changing',
          'Each status listed itself as a next step', 'Low'],
         ['D-07', 'Mobile ticket created with an empty title',
          'Client sent subject; the DTO requires title', 'High'],
         ['D-08', 'Mobile ticket reached nobody',
          'Recipient field absent from the form', 'High'],
         ['D-09', 'Mobile complaint defaulted to "Any HR"',
          'Recipient optional', 'High'],
         ['D-10', 'TL could approve their own permission',
          'Endpoint fallback returns unaddressed rows; no client guard',
          'Critical'],
         ['D-11', 'Approve / Reject shown on non-actionable leave',
          'Client ignored the server’s canAct flag', 'Critical'],
         ['D-12', 'Permission approvals unreachable on mobile',
          'TabController length 2 with 3 bodies', 'High'],
         ['D-13', 'Holidays missing from the mobile calendar',
          'Repository method never called', 'Medium']],
        widths=['7%', '27%', '52%', '14%']),
),

section(
  h1('6.', 'Non-Functional Verification'),
  table(['Attribute', 'Method', 'Measured', 'Verdict'],
        [['API response (server-side)', 'curl on the host itself', '6–9 ms',
          PASS()],
         ['Static page (server-side)', 'curl on the host itself', '0.4–0.5 ms',
          PASS()],
         ['End-to-end from India', 'curl over the internet', '~620 ms',
          pill('warn', 'Network-bound')],
         ['TLS', 'openssl s_client', 'TLS 1.3, AES-256-GCM', PASS()],
         ['HTTP/2', 'openssl -alpn h2', 'Negotiated', PASS()],
         ['Compression', 'Accept-Encoding: gzip', '1,955 → 971 bytes', PASS()],
         ['Server load', 'uptime, docker stats', 'load 0.07, CPU under 3%',
          PASS()],
         ['Database round trip', 'timed TCP connect from the host',
          '123–127 ms', pill('warn', 'Constraint')],
         ['Connection pool health', 'application log after restart',
          '0 link failures', PASS()]],
        widths=['25%', '24%', '25%', '26%']),

  h2('6.1  Test environment'),
  table(['Item', 'Detail'],
        [['Application URL', 'https://pixoushrportal.pixous.info'],
         ['Host', 'AWS EC2, eu-west-3 (Paris)'],
         ['Web server', 'nginx 1.27.5, TLS 1.3, HTTP/2'],
         ['Runtime', 'Docker Compose — web, backend, redis, mysql, analytics'],
         ['Database', 'MySQL 8 on external shared hosting'],
         ['Clients', 'Chrome (desktop), Android release APK'],
         ['Test data', 'Live production — read-only assertions only']],
        widths=['26%', '74%']),
  note('Tests against production asserted on responses and did not create, '
       'modify or delete records except where a case required it and the record '
       'was removed afterwards.'),
),

section(
  h1('7.', 'Coverage Gaps — What Is Not Tested'),
  p('Stated directly, because a reader deciding whether to release needs this '
    'more than the pass list.'),
  bullets([
    'No end-to-end UI automation. There is no Cypress, Playwright or Selenium '
    'suite; UI behaviour is verified by hand.',
    'No integration tests against a real database. The 61 backend tests are '
    'unit tests with mocked repositories.',
    'Mobile testing is static analysis only. There are no Flutter widget or '
    'integration tests exercising the screens.',
    'No load or soak testing has been executed. Section 6 measures single '
    'requests, not behaviour under concurrency.',
    'No automated accessibility testing (WCAG) has been run.',
    'Web unit tests cover 4 files of 113. Coverage is thin outside login, date, '
    'formatting and permission helpers.',
    'No continuous integration. Tests run on demand, so a regression can reach '
    'the default branch unnoticed.',
  ]),

  h1('8.', 'Risk Register'),
  table(['#', 'Risk', 'Impact', 'Likelihood', 'Mitigation'],
        [['R-01', 'Database backup is manual, unscheduled, single copy on the '
          'same server', 'Total data loss', 'Medium',
          'Schedule it and copy off-host — highest priority'],
         ['R-02', 'No CI; regressions can merge unnoticed',
          'Defects reach production', 'High',
          'Add a pipeline running both test suites'],
         ['R-03', 'Multi-tenancy partly built (company_id null on most rows)',
          'Cross-tenant leakage', 'Medium',
          'Complete tenant column and filter coverage'],
         ['R-04', 'Server in Paris, users in India',
          '~600 ms added to every first request', 'Certain',
          'Migrate to ap-south-1 after backups are safe'],
         ['R-05', 'External database 125 ms from the app',
          'Slow queries under load', 'Certain',
          'Move MySQL to the app host or region'],
         ['R-06', 'No UI automation',
          'Interface regressions found by users', 'Medium',
          'Add Playwright for the critical journeys']],
        widths=['7%', '27%', '18%', '13%', '35%']),

  h1('9.', 'Conclusion'),
  p('The application passes every automated test that exists: 61 backend unit '
    'tests, 36 web unit tests, and static analysis of the mobile app with no '
    'issues. Thirteen defects were found during this cycle by comparing each '
    'client against the server contract, and all thirteen are closed — '
    'including two authorisation faults (D-10, D-11) that allowed a decision to '
    'be offered to somebody not entitled to make it.'),
  p('The application is fit for use. It is not, on the evidence here, fully '
    'verified: there is no UI automation, no integration testing against a real '
    'database, and no continuous integration, so confidence rests on unit tests '
    'and manual checking. The two changes that would most improve that are a CI '
    'pipeline (R-02) and a scheduled off-host backup (R-01) — and the second '
    'matters more than any test.'),
),
])

html = document(
    'QA Testing Document',
    'Quality Assurance',
    'Scope, test design, executed results, defects closed and open risks for '
    'the PIXOUS HR Portal as it stands today.',
    body)

print(to_pdf(html, OUT))
