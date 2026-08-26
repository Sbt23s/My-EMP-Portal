"""QA Testing Document — PIXOUS HR Portal.

Every count in this document was taken from the repository and every test
result from a run recorded in the commentary, not estimated.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pixous_doc import (bullets, cover, h1, h2, new_document, note, page_break,
                        para, save, table)

OUT = os.path.join(os.path.expanduser('~'), 'Downloads',
                   'Pixous_HR_Portal_QA_Testing_v1.0.docx')

doc = new_document()
cover(doc, 'QA Testing Document',
      'Scope, test design, executed results and open risks for the '
      'HR Portal as it stands today.')

# ── 1 ─────────────────────────────────────────────────────────────────────
h1(doc, '1. Purpose and Scope')
para(doc,
     'This document records what has been tested in the PIXOUS HR Portal, how '
     'it was tested, what passed, and — as plainly as the passes — what is not '
     'covered. A QA document that lists only successes tells a reader nothing '
     'they can act on, so the gaps are named in section 8 and carried into the '
     'risk table rather than left out.')

h2(doc, '1.1 The application under test')
table(doc,
      ['Component', 'Technology', 'Files', 'Lines', 'Role'],
      [
          ['Backend API', 'Spring Boot 3.5, Java 17', '315', '26,321',
           'REST API, security, persistence'],
          ['Web portal', 'React 18, TypeScript, Vite', '113', '50,258',
           'Desktop and tablet client'],
          ['Mobile app', 'Flutter 3.44, Riverpod', '69', '22,012',
           'Android client'],
          ['Database', 'MySQL 8 (external hosting)', '102 migrations', '—',
           'Flyway-managed schema'],
      ],
      widths=[1.5, 1.9, 0.9, 0.8, 1.9])
note(doc, 'Counts taken from the working tree with find and wc; migrations '
          'counted from backend/src/main/resources/db/migration.')

h2(doc, '1.2 Modules in scope')
table(doc,
      ['#', 'Module', 'Web', 'Mobile', 'Notes'],
      [
          ['1', 'Authentication and session', 'Yes', 'Yes',
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
          ['9', 'Claims / TA expenses', 'Yes', 'Yes', 'Submit, approve, export'],
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
          ['20', 'Audit log', 'Yes', 'Yes', 'Administrators only'],
      ],
      widths=[0.35, 1.85, 0.55, 0.65, 3.1])

page_break(doc)

# ── 2 ─────────────────────────────────────────────────────────────────────
h1(doc, '2. Roles Tested')
para(doc,
     'Authorisation in this system is by permission code, not by role name — a '
     'role is a bundle of permissions and two roles can share one. Test cases '
     'therefore assert on the permission the server checks, which is what '
     'determines behaviour.')
table(doc,
      ['Role', 'Code', 'Key permissions', 'What the role can do'],
      [
          ['Employee', 'IT_EMP / CV_EMP', '(base)',
           'Own attendance, leave, payslip, claims, tickets'],
          ['Team Leader', 'IT_TL / CV_SUP',
           'LEAVE_APPROVE, ATTENDANCE_TEAM, TASK_ASSIGN, REPORT_VIEW',
           'Approve team leave and permission, assign tasks, review reports'],
          ['HR', 'IT_MGR / IT_HR',
           'USER_MANAGE, COMPLAINT_MANAGE, TASK_VIEW_ALL, HELPDESK_AGENT',
           'Company-wide review, payroll, employee records'],
          ['CTO / System Admin', 'SUPER_ADMIN / COMPANY_ADMIN',
           'All, plus DASHBOARD_EXEC',
           'Executive view, audit log, configuration'],
      ],
      widths=[1.0, 1.25, 1.9, 2.35])
note(doc, 'Permission-to-role grants verified against the Flyway migrations '
          '(for example V41 grants TASK_ASSIGN to IT_TL, V47 grants '
          'TASK_VIEW_ALL to IT_MGR and IT_HR).')

page_break(doc)

# ── 3 ─────────────────────────────────────────────────────────────────────
h1(doc, '3. Executed Automated Tests')
h2(doc, '3.1 Backend — JUnit 5')
para(doc, 'Command: mvn -o test. Result: BUILD SUCCESS.')
table(doc,
      ['Suite', 'Tests', 'Failures', 'Errors', 'Time', 'Covers'],
      [
          ['StorageServiceTest', '5', '0', '0', '0.4 s',
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
          ['TOTAL', '61', '0', '0', '54.9 s', ''],
      ],
      widths=[1.7, 0.5, 0.6, 0.5, 0.6, 2.6])

h2(doc, '3.2 Web portal — Vitest')
para(doc, 'Command: npm test -- --run. Result: 4 files passed, 36 tests passed.')
table(doc,
      ['Area', 'Tests', 'Result', 'Covers'],
      [
          ['Login form', '—', 'Pass',
           'Validation, error surfacing, submit state'],
          ['Date and period helpers', '—', 'Pass',
           'Boundaries, formatting, ranges'],
          ['Formatting and currency', '—', 'Pass',
           'Money, hours, percentages'],
          ['Permission helpers', '—', 'Pass',
           'hasRole and hasPermission logic'],
          ['TOTAL', '36', '36 passed', 'Duration 51.8 s'],
      ],
      widths=[1.85, 0.7, 1.0, 3.0])
note(doc, 'Per-file test counts are not broken out by the runner in summary '
          'mode; the total is the reported figure.')

h2(doc, '3.3 Mobile app — static analysis')
para(doc, 'Command: flutter analyze. Result: No issues found.')
table(doc,
      ['Check', 'Result', 'Meaning'],
      [
          ['flutter analyze', 'No issues found',
           'No errors, warnings or lints across 69 Dart files'],
          ['Release build', 'Success',
           'APK produced and installed for manual verification'],
      ],
      widths=[1.5, 1.6, 3.4])

page_break(doc)

# ── 4 ─────────────────────────────────────────────────────────────────────
h1(doc, '4. Functional Test Cases — Authorisation')
para(doc,
     'These are the cases that found real defects. Each one states the rule, '
     'how it was checked, and the outcome.')
table(doc,
      ['ID', 'Case', 'Expected', 'Result'],
      [
          ['QA-AUTH-01', 'Sign in with valid credentials',
           'Token issued, role and permissions returned', 'Pass'],
          ['QA-AUTH-02', 'Sign in with wrong password repeatedly',
           'Account locked after threshold', 'Pass (AuthServiceTest)'],
          ['QA-AUTH-03', 'Call an API with no token',
           'HTTP 401, no data disclosed', 'Pass (verified on production)'],
          ['QA-AUTH-04', 'Call an API with a tampered token',
           'Rejected', 'Pass (JwtServiceTest)'],
          ['QA-AUTH-05', 'Employee opens an HR-only endpoint',
           'HTTP 403', 'Pass (@PreAuthorize on controller)'],
          ['QA-PERM-01', 'Team Leader raises own permission request',
           'Must NOT appear in own approval queue',
           'Fixed — defect found and closed'],
          ['QA-PERM-02', 'Permission addressed to a named approver',
           'Only that approver sees Approve/Reject', 'Pass after fix'],
          ['QA-LEAVE-01', 'Leave row visible but not actionable',
           'Buttons hidden when server sets canAct=false',
           'Fixed — defect found and closed'],
          ['QA-CMPL-01', 'Complaint raised without a recipient',
           'Form must refuse to submit', 'Fixed — recipient now required'],
          ['QA-CMPL-02', 'Reviewer opens own complaint',
           'Read-only, no respond action', 'Pass after fix'],
          ['QA-TKT-01', 'Ticket raised from mobile',
           'Title stored, recipient routed',
           'Fixed — wrong field name corrected'],
          ['QA-TASK-01', 'Team Leader assigns a task',
           'Accepted with TASK_ASSIGN', 'Pass after fix'],
          ['QA-TASK-02', 'Oversight list of everyone’s tasks',
           'Read-only, assignee named', 'Pass after fix'],
      ],
      widths=[0.95, 2.05, 1.9, 1.6])

page_break(doc)

# ── 5 ─────────────────────────────────────────────────────────────────────
h1(doc, '5. Defects Found and Closed')
para(doc,
     'Thirteen defects were found by comparing the clients against the server '
     'contract and the two clients against each other. All are closed. Each is '
     'described by its cause, because the cause is what stops it recurring.')
table(doc,
      ['#', 'Defect', 'Cause', 'Severity', 'Status'],
      [
          ['D-01', 'Complaint recipient never saved',
           'Client sent targetRoleId; API reads requestedTo', 'High', 'Closed'],
          ['D-02', 'Reviewer complaint list empty',
           'Client called /complaints/all; route is /complaints', 'High',
           'Closed'],
          ['D-03', 'Complaint response could not be saved',
           'Wrong route and field (/status + resolutionNotes)', 'High',
           'Closed'],
          ['D-04', '"Sent to" always read "HR & Admin"',
           'Client read targetRoleName, never returned', 'Medium', 'Closed'],
          ['D-05', 'Anyone could respond to any complaint',
           'No addressee check in the page', 'High', 'Closed'],
          ['D-06', 'Status could be saved without changing',
           'Each status listed itself as a next step', 'Low', 'Closed'],
          ['D-07', 'Mobile ticket created with empty title',
           'Client sent subject; DTO requires title', 'High', 'Closed'],
          ['D-08', 'Mobile ticket reached nobody',
           'Recipient field absent from the form', 'High', 'Closed'],
          ['D-09', 'Mobile complaint defaulted to "Any HR"',
           'Recipient optional', 'High', 'Closed'],
          ['D-10', 'TL could approve own permission',
           'Endpoint fallback returns unaddressed rows; no client guard',
           'Critical', 'Closed'],
          ['D-11', 'Approve/Reject shown on non-actionable leave',
           'Client ignored the server’s canAct flag', 'Critical', 'Closed'],
          ['D-12', 'Permission approvals unreachable on mobile',
           'TabController length 2 with 3 bodies', 'High', 'Closed'],
          ['D-13', 'Holidays missing from mobile calendar',
           'Repository method never called', 'Medium', 'Closed'],
      ],
      widths=[0.5, 1.75, 2.25, 0.7, 0.6])

page_break(doc)

# ── 6 ─────────────────────────────────────────────────────────────────────
h1(doc, '6. Non-Functional Verification')
table(doc,
      ['Attribute', 'Method', 'Measured', 'Verdict'],
      [
          ['API response (server-side)', 'curl on the host itself',
           '6–9 ms', 'Pass'],
          ['Static page (server-side)', 'curl on the host itself',
           '0.4–0.5 ms', 'Pass'],
          ['End-to-end from India', 'curl over the internet',
           '~620 ms', 'Network-bound (section 7)'],
          ['TLS', 'openssl s_client', 'TLS 1.3, AES-256-GCM', 'Pass'],
          ['HTTP/2', 'openssl -alpn h2', 'Negotiated', 'Pass'],
          ['Compression', 'Accept-Encoding: gzip',
           '1,955 to 971 bytes', 'Pass'],
          ['Server load', 'uptime, docker stats',
           'load 0.07, CPU under 3%', 'Ample headroom'],
          ['Database round trip', 'timed TCP connect from the host',
           '123–127 ms', 'Known constraint'],
          ['Connection pool health', 'application log after restart',
           '0 link failures', 'Pass'],
      ],
      widths=[1.65, 1.6, 1.5, 1.75])

page_break(doc)

# ── 7 ─────────────────────────────────────────────────────────────────────
h1(doc, '7. Test Environment')
table(doc,
      ['Item', 'Detail'],
      [
          ['Application URL', 'https://pixoushrportal.pixous.info'],
          ['Host', 'AWS EC2, eu-west-3 (Paris)'],
          ['Web server', 'nginx 1.27.5, TLS 1.3, HTTP/2'],
          ['Runtime', 'Docker Compose — web, backend, redis, mysql, analytics'],
          ['Database', 'MySQL 8 on external shared hosting'],
          ['Client under test', 'Chrome (desktop), Android release APK'],
          ['Test data', 'Live production data — read-only assertions only'],
      ],
      widths=[1.6, 4.9])
note(doc, 'Tests against production asserted on responses and did not create, '
          'modify or delete records except where a case explicitly required it '
          'and the record was removed afterwards.')

# ── 8 ─────────────────────────────────────────────────────────────────────
h1(doc, '8. Coverage Gaps — What Is Not Tested')
para(doc,
     'Stated directly, because a reader deciding whether to release needs this '
     'more than the pass list.')
bullets(doc, [
    'No end-to-end UI automation. There is no Cypress, Playwright or Selenium '
    'suite; UI behaviour is verified by hand.',
    'No integration tests against a real database. The 61 backend tests are '
    'unit tests with mocked repositories.',
    'Mobile tests are static analysis only. There are no Flutter widget or '
    'integration tests in the app.',
    'No load or soak testing has been executed. Section 6 measures single '
    'requests, not behaviour under concurrency.',
    'No automated accessibility testing (WCAG) has been run.',
    'Web unit tests cover 4 files of 113. Coverage is thin outside login, '
    'date, formatting and permission helpers.',
    'No continuous integration. Tests are run on demand, so a regression can '
    'reach the default branch unnoticed.',
])

h1(doc, '9. Risk Register')
table(doc,
      ['#', 'Risk', 'Impact', 'Likelihood', 'Mitigation'],
      [
          ['R-01', 'Database backup is manual, unscheduled, single copy on '
           'the same server', 'Total data loss', 'Medium',
           'Schedule it and copy off-host — highest priority'],
          ['R-02', 'No CI; regressions can merge unnoticed', 'Defects reach '
           'production', 'High', 'Add a pipeline running both test suites'],
          ['R-03', 'Multi-tenancy is partly built (company_id null on most '
           'rows)', 'Cross-tenant leakage', 'Medium',
           'Complete tenant column and filter coverage'],
          ['R-04', 'Server in Paris; users in India', '~600 ms added to every '
           'first request', 'Certain',
           'Migrate to ap-south-1 (Mumbai) after backups are safe'],
          ['R-05', 'External database 125 ms from the app', 'Slow queries '
           'under load', 'Certain', 'Move MySQL onto the app host or same '
           'region'],
          ['R-06', 'No UI automation', 'Interface regressions found by users',
           'Medium', 'Add Playwright for the critical journeys'],
      ],
      widths=[0.45, 1.9, 1.2, 0.8, 2.15])

h1(doc, '10. Conclusion')
para(doc,
     'The application passes every automated test that exists: 61 backend unit '
     'tests, 36 web unit tests, and static analysis of the mobile app with no '
     'issues. Thirteen defects were found during this cycle by comparing each '
     'client against the server contract, and all thirteen are closed — '
     'including two authorisation faults (D-10, D-11) that allowed a decision '
     'to be offered to somebody not entitled to make it.')
para(doc,
     'The application is fit for use. It is not, on the evidence here, fully '
     'verified: there is no UI automation, no integration testing against a '
     'real database, and no continuous integration, so confidence rests on '
     'unit tests and manual checking. The two changes that would most improve '
     'that are a CI pipeline (R-02) and a scheduled off-host backup (R-01), '
     'and the second matters more than any test.')

print(save(doc, OUT))
