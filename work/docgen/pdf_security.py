"""Security Testing Document — PDF."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pdf_style import (bullets, document, finding, h1, h2, metrics, note, p,
                       pill, section, table)
from render_pdf import to_pdf

OUT = os.path.join(os.path.expanduser('~'), 'Downloads',
                   'Pixous_HR_Portal_Security_Testing_v1.0.pdf')

PASS = lambda t='Pass': pill('pass', t)
BLOCK = lambda: pill('pass', 'Blocked')
GAP = lambda t='Not assessed': pill('warn', t)

body = ''.join([

section(
  h1('1.', 'Scope and Honest Limits'),
  p('This assessment covers the authentication and authorisation model, the '
    'transport layer, HTTP response headers, input handling on public '
    'endpoints, and secret handling. Tests were executed against the live '
    'production system and against the source.'),

  metrics([('110', 'authorisation checks'), ('3', 'findings raised'),
           ('3', 'findings fixed'), ('TLS 1.3', 'transport')]),

  h2('1.1  What this is not'),
  p('This is not a penetration test. It was performed by the team that wrote '
    'the application, without an external tester, without authenticated session '
    'testing across roles, and without automated scanning tools. The specific '
    'gaps are listed in section 9 and should be read before this document is '
    'used as an assurance artefact.'),

  h2('1.2  Target'),
  table(['Item', 'Detail'],
        [['Application', 'PIXOUS HR Portal'],
         ['URL', 'https://pixoushrportal.pixous.info'],
         ['Backend', 'Spring Boot 3.5, Spring Security, JWT'],
         ['Clients', 'React 18 web portal, Flutter Android app'],
         ['Host', 'AWS EC2, eu-west-3, Docker Compose behind nginx'],
         ['Authorisation checks in code',
          '110 @PreAuthorize annotations across 38 controllers']],
        widths=['28%', '72%']),
),

section(
  h1('2.', 'Authentication'),
  table(['ID', 'Control', 'Implementation', 'Verified by', 'Result'],
        [['SEC-A-01', 'Password storage', 'BCrypt — adaptive, salted',
          'Source review', PASS()],
         ['SEC-A-02', 'Brute-force protection',
          'LoginAttemptLimiter locks after repeated failures',
          'LoginAttemptLimiterTest — 6 tests', PASS()],
         ['SEC-A-03', 'Account lockout is logged',
          'WARN "Account locked after repeated failures"',
          'Observed in test output', PASS()],
         ['SEC-A-04', 'Token signing and expiry',
          'JwtService — HMAC-signed, expiring', 'JwtServiceTest — 6 tests',
          PASS()],
         ['SEC-A-05', 'Forged token rejected',
          'Signature validated on every request',
          'Sent a forged Bearer token to /api/users', PASS('401')],
         ['SEC-A-06', 'Credential payload validation',
          'Bean Validation on login and registration DTOs',
          'AuthDtosValidationTest — 7 tests', PASS()],
         ['SEC-A-07', 'Session handling',
          'Stateless JWT; refresh handled by the client',
          'AuthServiceTest — 13 tests', PASS()]],
        widths=['12%', '17%', '27%', '25%', '19%']),
),

section(
  h1('3.', 'Authorisation'),
  p('Authorisation is enforced on the server by permission code, checked with '
    '@PreAuthorize on the controller. The clients hide what a person may not '
    'do, but hiding is a courtesy: the server refuses regardless. This '
    'distinction was tested, because it is the one that matters.'),
  table(['ID', 'Test', 'Method', 'Result'],
        [['SEC-Z-01', 'Unauthenticated access to /api/users', 'curl, no token',
          PASS('401')],
         ['SEC-Z-02', 'Unauthenticated /api/payroll/payslips',
          'curl, no token', PASS('401')],
         ['SEC-Z-03', 'Unauthenticated /api/audit', 'curl, no token',
          PASS('401')],
         ['SEC-Z-04', 'Unauthenticated /api/employees', 'curl, no token',
          PASS('401')],
         ['SEC-Z-05', 'Employee reaching an HR endpoint',
          'Source review of @PreAuthorize', PASS('403 by policy')],
         ['SEC-Z-06', 'Approver decides a request not addressed to them',
          'Source review plus client fix', pill('warn', 'Was possible')],
         ['SEC-Z-07', 'User approves their own request',
          'Source review plus client fix', pill('warn', 'Was possible')]],
        widths=['12%', '35%', '30%', '23%']),

  h2('3.1  Two authorisation findings, both remediated'),
  p('These were the most serious findings of this assessment. Both are '
    'privilege problems, not cosmetic ones.'),
  finding('SEC-F-01 — A user could approve their own permission request  ·  '
          'Severity: High',
          'The /leave/permissions/for-me endpoint falls back, when nothing is '
          'addressed to a person, to returning every request that names no '
          'approver — which includes their own. The web client carries a guard '
          'against this; the mobile client carried none, so a Team Leader saw '
          'their own request with Approve and Reject beside it. Remediated in '
          'the mobile client by porting the web rule: a request raised by the '
          'reader is never decidable, and a request is otherwise decidable only '
          'by the person it names.'),
  finding('SEC-F-02 — Decision buttons shown on requests the user may not act '
          'on  ·  Severity: High',
          'The leave approvals endpoint deliberately returns rows an approver '
          'may see but not act on, and marks each with a canAct flag. The web '
          'client gates its buttons on that flag; the mobile client ignored it, '
          'so every visible row offered a decision. Remediated: the flag is now '
          'read and defaults to false, so a row arriving without it is not '
          'actionable until the server says it is.'),
  note('Neither finding allowed a decision the server would have accepted from '
       'an unauthorised user in every case — but both offered actions that '
       'should never have been offered, and relying on the server to refuse '
       'afterwards is not a control worth depending on.'),
),

section(
  h1('4.', 'Transport Security'),
  table(['ID', 'Control', 'Measured value', 'Result'],
        [['SEC-T-01', 'TLS version',
          'TLS 1.3 negotiated; 1.2 permitted', PASS()],
         ['SEC-T-02', 'Cipher suite', 'TLS_AES_256_GCM_SHA384',
          PASS('AEAD, forward secret')],
         ['SEC-T-03', 'Certificate', "Let's Encrypt, CN matches host", PASS()],
         ['SEC-T-04', 'HTTP to HTTPS redirect', '301 to https://', PASS()],
         ['SEC-T-05', 'HSTS', 'max-age=15552000 — 180 days',
          PASS('No preload, reversible')],
         ['SEC-T-06', 'Protocol downgrade',
          'SSLv3, TLS 1.0 and 1.1 not offered', PASS()],
         ['SEC-T-07', 'HTTP/2', 'ALPN negotiates h2', PASS()]],
        widths=['12%', '20%', '36%', '32%']),
  note('Measured with: openssl s_client -connect pixoushrportal.pixous.info:443 '
       '-alpn h2, and curl -I against both schemes.'),
),

section(
  h1('5.', 'HTTP Response Headers'),
  table(['Header', 'Value', 'Status', 'Protects against'],
        [['Strict-Transport-Security', 'max-age=15552000', 'Present',
          'Protocol downgrade, SSL stripping'],
         ['X-Frame-Options', 'SAMEORIGIN', 'Present', 'Clickjacking'],
         ['X-Content-Type-Options', 'nosniff', 'Present', 'MIME confusion'],
         ['Referrer-Policy', 'strict-origin-when-cross-origin', 'Present',
          'URL leakage to third parties'],
         ['Permissions-Policy',
          'payment, usb, sensors denied; camera, microphone, geolocation '
          'limited to self', 'Added this cycle',
          'Unwanted hardware access by embedded content'],
         ['Content-Security-Policy-Report-Only',
          'default-src self, plus the origins the app genuinely uses',
          'Added this cycle', 'Cross-site scripting — reporting stage'],
         ['Server', 'nginx, version suppressed', 'Fixed this cycle',
          'Version fingerprinting']],
        widths=['24%', '29%', '16%', '31%']),

  h2('5.1  Why the CSP is Report-Only'),
  p('The application genuinely loads a script from js.puter.com, images from '
    'images.unsplash.com and transparenttextures.com, geocodes against '
    'nominatim.openstreetmap.org, and constructs blob: workers and data: '
    'images. An enforcing policy that got any of that wrong would break pages '
    'silently rather than loudly, which is worse than no policy.'),
  p('Report-Only blocks nothing and reports what a policy would have blocked. '
    'The allow-list can then be corrected from evidence before enforcement. '
    'Turning it into an enforcing Content-Security-Policy is the follow-up '
    'action, once the reports are quiet.'),

  h2('5.2  Finding raised and fixed'),
  finding('SEC-F-03 — Web server version disclosed  ·  Severity: Low',
          'Every response carried Server: nginx/1.27.5, telling an attacker '
          'which published vulnerabilities to try first. server_tokens off now '
          'suppresses the version. It buys them time and buys us nothing — the '
          'version still has to be patched, and hiding it does not patch '
          'anything, but there is no reason to print it. Verified: the header '
          'now reads simply "nginx".'),
),

section(
  h1('6.', 'Input Handling — Attacks Attempted'),
  table(['ID', 'Attack', 'Payload', 'Response', 'Result'],
        [['SEC-I-01', 'SQL injection via query parameter',
          "?q=' OR 1=1--", '401 before any query ran', BLOCK()],
         ['SEC-I-02', 'Path traversal, plain',
          '/api/files/../../../../etc/passwd',
          '200 — SPA index.html, no file disclosed',
          pill('warn', 'Not exploitable')],
         ['SEC-I-03', 'Path traversal, URL-encoded', '%2e%2e%2f repeated',
          '400 Bad Request', BLOCK()],
         ['SEC-I-04', 'Path traversal, double-encoded style',
          '....//....//etc/passwd', '400 Bad Request', BLOCK()],
         ['SEC-I-05', 'Forged JWT', 'Bearer with invalid signature', '401',
          BLOCK()],
         ['SEC-I-06', 'Cross-origin request',
          'Origin: https://evil.example.com',
          'No Access-Control-Allow-Origin returned', BLOCK()],
         ['SEC-I-07', 'Directory listing', 'GET /assets/', '404', BLOCK()]],
        widths=['11%', '22%', '24%', '26%', '17%']),
  note('SEC-I-02 returned 200 because curl normalises "../" before sending, so '
       'nginx served the single-page-app fallback. The encoded variants in '
       'SEC-I-03 and SEC-I-04 are the real test, and both were rejected with '
       '400. This is recorded rather than reported as a pass, because a 200 in '
       'a scan report that is actually a normalisation artefact is how false '
       'findings and false assurances both start.'),
  p('Injection defence rests on JPA parameter binding rather than string '
    'concatenation: the persistence layer builds prepared statements, so a '
    'quote in a search term is data and not syntax. Native queries in the '
    'codebase use named parameters.'),
),

section(
  h1('7.', 'Secret and Data Handling'),
  table(['Control', 'Implementation', 'Result'],
        [['Passwords', 'BCrypt hashed, never logged or returned', PASS()],
         ['Database credentials', 'Environment variables, not in source',
          PASS()],
         ['JWT signing key', 'Environment variable', PASS()],
         ['Secrets in the repository', 'No credentials committed in code',
          PASS()],
         ['File upload paths', 'Traversal rejected in StorageService',
          PASS('StorageServiceTest')],
         ['Audit trail',
          'Administrator-only audit log of privileged actions', PASS()],
         ['Email of sensitive documents',
          'Recipient read from the record, never typed, for payslips',
          PASS('Prevents misdirection')],
         ['Report email endpoint',
          'Restricted to privileged authorities, spreadsheet and PDF only, '
          '10 MB cap, every send logged before the attempt', PASS()]],
        widths=['24%', '52%', '24%']),
),

section(
  h1('8.', 'OWASP Top 10 (2021) — Position'),
  table(['Category', 'Position', 'Evidence'],
        [['A01 Broken Access Control', 'Addressed, two findings fixed',
          '110 @PreAuthorize checks; SEC-F-01 and SEC-F-02 remediated'],
         ['A02 Cryptographic Failures', 'Addressed',
          'TLS 1.3, BCrypt, HSTS, signed tokens'],
         ['A03 Injection', 'Addressed',
          'JPA parameter binding; injection and traversal attempts rejected'],
         ['A04 Insecure Design', 'Partly addressed',
          'Approval routing is server-decided; multi-tenancy is incomplete'],
         ['A05 Security Misconfiguration', 'Addressed this cycle',
          'server_tokens off, Permissions-Policy added, CSP in Report-Only'],
         ['A06 Vulnerable Components', 'NOT ASSESSED',
          'No dependency scan run — see section 9'],
         ['A07 Identification and Authentication Failures', 'Addressed',
          'Lockout, signed expiring tokens, validated payloads'],
         ['A08 Software and Data Integrity Failures', 'Partly addressed',
          'No CI, so no build-time integrity gate'],
         ['A09 Logging and Monitoring Failures', 'Partly addressed',
          'Audit log and application logs exist; no alerting'],
         ['A10 Server-Side Request Forgery', 'Not assessed',
          'No user-supplied URL fetching identified, but not tested']],
        widths=['30%', '22%', '48%']),
),

section(
  h1('9.', 'What Was Not Tested'),
  p('Read this section before treating this document as assurance.'),
  bullets([
    'No authenticated role-to-role testing. Whether a Team Leader token can '
    'reach HR data was reasoned from @PreAuthorize annotations, not exercised '
    'with real tokens for each of the four roles.',
    'No dependency vulnerability scan. Neither npm audit, OWASP '
    'Dependency-Check nor Snyk was run, so A06 is unassessed.',
    'No automated scanner. No OWASP ZAP, Burp Suite or Nikto run.',
    'No external penetration test. This was performed by the development team, '
    'which cannot substitute for an independent tester.',
    'CSRF not specifically tested. The API is stateless and token-based, which '
    'structurally limits CSRF, but no test was executed.',
    'No rate limiting on endpoints other than login. A logged-in user can call '
    'other endpoints without throttling.',
    'No SSRF testing (A10).',
    'No mobile binary analysis — the APK was not decompiled or checked for '
    'embedded secrets.',
  ]),

  h1('10.', 'Recommendations, in priority order'),
  table(['#', 'Recommendation', 'Why', 'Effort'],
        [['1', 'Schedule the database backup and copy it off the host',
          'Currently manual, unscheduled, single copy on the server it '
          'protects. The largest risk to the business, security or otherwise.',
          'Low'],
         ['2', 'Run a dependency vulnerability scan and act on it',
          'A06 is entirely unassessed; known CVEs in dependencies are the most '
          'common real-world breach path.', 'Low'],
         ['3', 'Add authenticated role-to-role authorisation tests',
          'The two findings in this report were authorisation faults. Tests per '
          'role would have caught them earlier.', 'Medium'],
         ['4', 'Move the CSP from Report-Only to enforcing',
          'Once the reports are quiet, this closes the XSS gap properly.',
          'Medium'],
         ['5', 'Add rate limiting beyond login',
          'Prevents an authenticated account being used to enumerate or '
          'scrape.', 'Medium'],
         ['6', 'Complete the multi-tenancy work',
          'company_id is null on most rows; the model is half-built and has '
          'already caused two production defects.', 'High'],
         ['7', 'Commission an external penetration test',
          'Independent testing is the only way to validate this assessment.',
          'External']],
        widths=['6%', '28%', '52%', '14%']),

  h1('11.', 'Conclusion'),
  p('The security fundamentals are sound: passwords are hashed with BCrypt, '
    'transport is TLS 1.3 with HSTS, tokens are signed and expiring, brute '
    'force is throttled, injection and traversal attempts were rejected, and '
    'authorisation is enforced server-side at 110 checkpoints.'),
  p('Three findings were raised in this cycle and all three are fixed: two were '
    'access-control faults in the mobile client that offered decisions to '
    'people not entitled to make them, and one was version disclosure. Two '
    'response headers were added.'),
  p('The honest summary is that the application is reasonably secure and this '
    'assessment is not comprehensive. The gaps in section 9 — particularly the '
    'absent dependency scan and the absence of authenticated role-to-role '
    'testing — mean this document should be read as a first-party review, not '
    'as a penetration test. The single most valuable action remains item 1, '
    'which is not a security control at all but is what stands between a server '
    'fault and the loss of the company’s HR records.'),
),
])

html = document(
    'Security Testing Document',
    'Security Assessment',
    'Controls verified, attacks attempted, findings raised and remediated, and '
    'the limits of this assessment.',
    body)

print(to_pdf(html, OUT))
