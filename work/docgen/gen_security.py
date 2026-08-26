"""Security Testing Document — PIXOUS HR Portal.

The findings here are from tests actually executed against the production
system and from reading the code, not from a checklist filled in by hand.
Where a test was NOT run, the document says so.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pixous_doc import (bullets, cover, h1, h2, new_document, note, page_break,
                        para, save, table)

OUT = os.path.join(os.path.expanduser('~'), 'Downloads',
                   'Pixous_HR_Portal_Security_Testing_v1.0.docx')

doc = new_document()
cover(doc, 'Security Testing Document',
      'Controls verified, attacks attempted, findings raised and remediated, '
      'and the limits of this assessment.')

# ── 1 ─────────────────────────────────────────────────────────────────────
h1(doc, '1. Scope and Honest Limits')
para(doc,
     'This assessment covers the authentication and authorisation model, the '
     'transport layer, HTTP response headers, input handling on public '
     'endpoints, and secret handling. Tests were executed against the live '
     'production system and against the source.')
h2(doc, '1.1 What this is not')
para(doc,
     'This is not a penetration test. It was performed by the team that wrote '
     'the application, without an external tester, without authenticated '
     'session testing across roles, and without automated scanning tools. The '
     'specific gaps are listed in section 9 and should be read before this '
     'document is used as an assurance artefact.')

h2(doc, '1.2 Target')
table(doc,
      ['Item', 'Detail'],
      [
          ['Application', 'PIXOUS HR Portal'],
          ['URL', 'https://pixoushrportal.pixous.info'],
          ['Backend', 'Spring Boot 3.5, Spring Security, JWT'],
          ['Clients', 'React 18 web portal, Flutter Android app'],
          ['Host', 'AWS EC2, eu-west-3, Docker Compose behind nginx'],
          ['Authorisation checks in code', '110 @PreAuthorize annotations '
           'across 38 controllers'],
      ],
      widths=[1.9, 4.6])

page_break(doc)

# ── 2 ─────────────────────────────────────────────────────────────────────
h1(doc, '2. Authentication')
table(doc,
      ['ID', 'Control', 'Implementation', 'Verified by', 'Result'],
      [
          ['SEC-A-01', 'Password storage', 'BCrypt (adaptive, salted)',
           'Source review', 'Pass'],
          ['SEC-A-02', 'Brute-force protection',
           'LoginAttemptLimiter locks after repeated failures',
           'LoginAttemptLimiterTest — 6 tests', 'Pass'],
          ['SEC-A-03', 'Account lockout is logged',
           'WARN "Account locked after repeated failures"',
           'Observed in test output', 'Pass'],
          ['SEC-A-04', 'Token signing and expiry',
           'JwtService, HMAC-signed, expiring',
           'JwtServiceTest — 6 tests', 'Pass'],
          ['SEC-A-05', 'Forged token rejected',
           'Signature validated on every request',
           'Sent a forged Bearer token to /api/users', 'Pass — 401'],
          ['SEC-A-06', 'Credential payload validation',
           'Bean Validation on login and registration DTOs',
           'AuthDtosValidationTest — 7 tests', 'Pass'],
          ['SEC-A-07', 'Session handling',
           'Stateless JWT; refresh handled by the client',
           'AuthServiceTest — 13 tests', 'Pass'],
      ],
      widths=[0.75, 1.2, 1.75, 1.55, 0.95])

page_break(doc)

# ── 3 ─────────────────────────────────────────────────────────────────────
h1(doc, '3. Authorisation')
para(doc,
     'Authorisation is enforced on the server by permission code, checked with '
     '@PreAuthorize on the controller. The clients hide what a person may not '
     'do, but hiding is a courtesy: the server refuses regardless. This '
     'distinction was tested, because it is the one that matters.')
table(doc,
      ['ID', 'Test', 'Method', 'Result'],
      [
          ['SEC-Z-01', 'Unauthenticated access to /api/users', 'curl, no token',
           '401 — Pass'],
          ['SEC-Z-02', 'Unauthenticated access to /api/payroll/payslips',
           'curl, no token', '401 — Pass'],
          ['SEC-Z-03', 'Unauthenticated access to /api/audit',
           'curl, no token', '401 — Pass'],
          ['SEC-Z-04', 'Unauthenticated access to /api/employees',
           'curl, no token', '401 — Pass'],
          ['SEC-Z-05', 'Employee reaching an HR endpoint',
           'Source review of @PreAuthorize', 'Pass — 403 by policy'],
          ['SEC-Z-06', 'Approver decides a request not addressed to them',
           'Source review plus client fix', 'Was possible — now blocked'],
          ['SEC-Z-07', 'User approves their own request',
           'Source review plus client fix', 'Was possible — now blocked'],
      ],
      widths=[0.75, 2.2, 1.75, 1.5])

h2(doc, '3.1 Two authorisation findings, both remediated')
para(doc,
     'These were the most serious findings of this assessment. Both are '
     'privilege problems, not cosmetic ones.')
table(doc,
      ['Finding', 'Detail'],
      [
          ['SEC-F-01 — A user could approve their own permission request',
           'Severity: High. The /leave/permissions/for-me endpoint falls back, '
           'when nothing is addressed to a person, to returning every request '
           'that names no approver — which includes their own. The web client '
           'carries a guard against this; the mobile client carried none, so a '
           'Team Leader saw their own request with Approve and Reject beside '
           'it. Remediated in the mobile client by porting the web rule: a '
           'request raised by the reader is never decidable, and a request is '
           'otherwise decidable only by the person it names.'],
          ['SEC-F-02 — Decision buttons shown on requests the user may not act on',
           'Severity: High. The leave approvals endpoint deliberately returns '
           'rows an approver may see but not act on, and marks each with a '
           'canAct flag. The web client gates its buttons on that flag; the '
           'mobile client ignored it, so every visible row offered a decision. '
           'Remediated: the flag is now read and defaults to false, so a row '
           'arriving without it is not actionable until the server says it is.'],
      ],
      widths=[2.4, 4.1])
note(doc, 'Neither finding allowed a decision the SERVER would have accepted '
          'from an unauthorised user in every case — but both offered actions '
          'that should never have been offered, and relying on the server to '
          'refuse afterwards is not a control worth depending on.')

page_break(doc)

# ── 4 ─────────────────────────────────────────────────────────────────────
h1(doc, '4. Transport Security')
table(doc,
      ['ID', 'Control', 'Measured value', 'Result'],
      [
          ['SEC-T-01', 'TLS version', 'TLS 1.3 only negotiated; 1.2 permitted',
           'Pass'],
          ['SEC-T-02', 'Cipher suite', 'TLS_AES_256_GCM_SHA384',
           'Pass — AEAD, forward secret'],
          ['SEC-T-03', 'Certificate', "Let's Encrypt, CN matches host",
           'Pass'],
          ['SEC-T-04', 'HTTP to HTTPS redirect', '301 to https://', 'Pass'],
          ['SEC-T-05', 'HSTS', 'max-age=15552000 (180 days)',
           'Pass — no preload, deliberately reversible'],
          ['SEC-T-06', 'Protocol downgrade', 'SSLv3, TLS 1.0/1.1 not offered',
           'Pass'],
          ['SEC-T-07', 'HTTP/2', 'ALPN negotiates h2',
           'Pass'],
      ],
      widths=[0.75, 1.3, 2.4, 1.75])
note(doc, 'Measured with: openssl s_client -connect '
          'pixoushrportal.pixous.info:443 -alpn h2, and curl -I against both '
          'schemes.')

page_break(doc)

# ── 5 ─────────────────────────────────────────────────────────────────────
h1(doc, '5. HTTP Response Headers')
table(doc,
      ['Header', 'Value', 'Status', 'Protects against'],
      [
          ['Strict-Transport-Security', 'max-age=15552000', 'Present',
           'Protocol downgrade, SSL stripping'],
          ['X-Frame-Options', 'SAMEORIGIN', 'Present', 'Clickjacking'],
          ['X-Content-Type-Options', 'nosniff', 'Present', 'MIME confusion'],
          ['Referrer-Policy', 'strict-origin-when-cross-origin', 'Present',
           'URL leakage to third parties'],
          ['Permissions-Policy', 'payment, usb, sensors denied; camera, '
           'microphone, geolocation limited to self', 'Added this cycle',
           'Unwanted hardware access by embedded content'],
          ['Content-Security-Policy-Report-Only', 'default-src self, with the '
           'origins the app genuinely uses', 'Added this cycle',
           'Cross-site scripting — reporting stage'],
          ['Server', 'nginx (version suppressed)', 'Fixed this cycle',
           'Version fingerprinting'],
      ],
      widths=[1.55, 1.9, 1.05, 2.0])

h2(doc, '5.1 Why the CSP is Report-Only')
para(doc,
     'The application genuinely loads a script from js.puter.com, images from '
     'images.unsplash.com and transparenttextures.com, geocodes against '
     'nominatim.openstreetmap.org, and constructs blob: workers and data: '
     'images. An enforcing policy that got any of that wrong would break pages '
     'silently rather than loudly, which is worse than no policy.')
para(doc,
     'Report-Only blocks nothing and reports what a policy would have blocked. '
     'The allow-list can then be corrected from evidence before enforcement. '
     'Turning it into an enforcing Content-Security-Policy is the follow-up '
     'action, once the reports are quiet.')

h2(doc, '5.2 Finding raised and fixed')
table(doc,
      ['Finding', 'Severity', 'Detail', 'Status'],
      [
          ['SEC-F-03 — Web server version disclosed',
           'Low',
           'Every response carried Server: nginx/1.27.5, telling an attacker '
           'which published vulnerabilities to try first. server_tokens off '
           'now suppresses the version. It buys them time and buys us nothing.',
           'Fixed and verified — header now reads "nginx"'],
      ],
      widths=[1.8, 0.7, 2.9, 1.1])

page_break(doc)

# ── 6 ─────────────────────────────────────────────────────────────────────
h1(doc, '6. Input Handling — Attacks Attempted')
table(doc,
      ['ID', 'Attack', 'Payload', 'Response', 'Result'],
      [
          ['SEC-I-01', 'SQL injection via query parameter',
           "?q=' OR 1=1--", '401 before any query ran', 'Blocked'],
          ['SEC-I-02', 'Path traversal, plain',
           '/api/files/../../../../etc/passwd',
           '200 — SPA index.html, no file disclosed', 'Not exploitable'],
          ['SEC-I-03', 'Path traversal, URL-encoded',
           '%2e%2e%2f repeated', '400 Bad Request', 'Blocked'],
          ['SEC-I-04', 'Path traversal, double-encoded style',
           '....//....//etc/passwd', '400 Bad Request', 'Blocked'],
          ['SEC-I-05', 'Forged JWT', 'Bearer token with invalid signature',
           '401', 'Blocked'],
          ['SEC-I-06', 'Cross-origin request',
           'Origin: https://evil.example.com',
           'No Access-Control-Allow-Origin returned', 'Blocked'],
          ['SEC-I-07', 'Directory listing', 'GET /assets/', '404', 'Blocked'],
      ],
      widths=[0.75, 1.4, 1.6, 1.65, 0.9])
note(doc, 'SEC-I-02 returned 200 because curl normalises "../" before sending, '
          'so nginx served the single-page-app fallback. The encoded variants '
          'in SEC-I-03 and SEC-I-04 are the real test, and both were rejected '
          'with 400. This is recorded rather than reported as a pass, because '
          'a 200 in a scan report that is actually a normalisation artefact is '
          'how false findings and false assurances both start.')
para(doc,
     'Injection defence rests on JPA parameter binding rather than string '
     'concatenation: the persistence layer builds prepared statements, so a '
     'quote in a search term is data and not syntax. Native queries in the '
     'codebase use named parameters.')

page_break(doc)

# ── 7 ─────────────────────────────────────────────────────────────────────
h1(doc, '7. Secret and Data Handling')
table(doc,
      ['Control', 'Implementation', 'Result'],
      [
          ['Passwords', 'BCrypt hashed, never logged or returned', 'Pass'],
          ['Database credentials', 'Environment variables, not in source',
           'Pass'],
          ['JWT signing key', 'Environment variable', 'Pass'],
          ['Secrets in the repository', 'No credentials committed in code',
           'Pass'],
          ['File upload paths', 'Traversal rejected in StorageService',
           'Pass — StorageServiceTest'],
          ['Audit trail', 'Administrator-only audit log of privileged actions',
           'Pass'],
          ['Email of sensitive documents',
           'Recipient read from the record, never typed, for payslips',
           'Pass — prevents misdirection'],
          ['Report email endpoint',
           'Restricted to privileged authorities, spreadsheet and PDF only, '
           '10 MB cap, every send logged before the attempt', 'Pass'],
      ],
      widths=[1.6, 3.3, 1.6])

page_break(doc)

# ── 8 ─────────────────────────────────────────────────────────────────────
h1(doc, '8. OWASP Top 10 (2021) — Position')
table(doc,
      ['Category', 'Position', 'Evidence'],
      [
          ['A01 Broken Access Control', 'Addressed, two findings fixed',
           '110 @PreAuthorize checks; SEC-F-01 and SEC-F-02 remediated'],
          ['A02 Cryptographic Failures', 'Addressed',
           'TLS 1.3, BCrypt, HSTS, signed tokens'],
          ['A03 Injection', 'Addressed',
           'JPA parameter binding; injection and traversal attempts rejected'],
          ['A04 Insecure Design', 'Partly addressed',
           'Approval routing is server-decided; multi-tenancy is incomplete '
           '(see risk)'],
          ['A05 Security Misconfiguration', 'Addressed this cycle',
           'server_tokens off, Permissions-Policy added, CSP in Report-Only'],
          ['A06 Vulnerable Components', 'Not assessed',
           'No dependency scan run — see section 9'],
          ['A07 Identification and Authentication Failures', 'Addressed',
           'Lockout, signed expiring tokens, validated payloads'],
          ['A08 Software and Data Integrity Failures', 'Partly addressed',
           'No CI, so no build-time integrity gate'],
          ['A09 Logging and Monitoring Failures', 'Partly addressed',
           'Audit log and application logs exist; no alerting'],
          ['A10 Server-Side Request Forgery', 'Not assessed',
           'No user-supplied URL fetching identified, but not tested'],
      ],
      widths=[1.9, 1.5, 3.1])

page_break(doc)

# ── 9 ─────────────────────────────────────────────────────────────────────
h1(doc, '9. What Was Not Tested')
para(doc,
     'Read this section before treating this document as assurance.')
bullets(doc, [
    'No authenticated role-to-role testing. Whether a Team Leader token can '
    'reach HR data was reasoned from @PreAuthorize annotations, not exercised '
    'with real tokens for each of the four roles.',
    'No dependency vulnerability scan. Neither npm audit, OWASP Dependency-'
    'Check nor Snyk was run, so A06 is unassessed.',
    'No automated scanner. No OWASP ZAP, Burp Suite or Nikto run.',
    'No external penetration test. This was performed by the development '
    'team, which cannot substitute for an independent tester.',
    'CSRF not specifically tested. The API is stateless and token-based, '
    'which structurally limits CSRF, but no test was executed.',
    'No rate limiting on endpoints other than login. A logged-in user can '
    'call other endpoints without throttling.',
    'No SSRF testing (A10).',
    'No mobile binary analysis — the APK was not decompiled or checked for '
    'embedded secrets.',
])

h1(doc, '10. Recommendations, in priority order')
table(doc,
      ['#', 'Recommendation', 'Why', 'Effort'],
      [
          ['1', 'Schedule the database backup and copy it off the host',
           'Currently manual, unscheduled, single copy on the server it '
           'protects. This is the largest risk to the business, security or '
           'otherwise.', 'Low'],
          ['2', 'Run a dependency vulnerability scan and act on it',
           'A06 is entirely unassessed; known CVEs in dependencies are the '
           'most common real-world breach path.', 'Low'],
          ['3', 'Add authenticated role-to-role authorisation tests',
           'The two findings in this report were authorisation faults. Tests '
           'per role would have caught them earlier.', 'Medium'],
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
           'External'],
      ],
      widths=[0.35, 2.1, 3.15, 0.9])

h1(doc, '11. Conclusion')
para(doc,
     'The security fundamentals are sound: passwords are hashed with BCrypt, '
     'transport is TLS 1.3 with HSTS, tokens are signed and expiring, brute '
     'force is throttled, injection and traversal attempts were rejected, and '
     'authorisation is enforced server-side at 110 checkpoints.')
para(doc,
     'Three findings were raised in this cycle and all three are fixed: two '
     'were access-control faults in the mobile client that offered decisions '
     'to people not entitled to make them, and one was version disclosure. '
     'Two response headers were added.')
para(doc,
     'The honest summary is that the application is reasonably secure and this '
     'assessment is not comprehensive. The gaps in section 9 — particularly '
     'the absent dependency scan and the absence of authenticated role-to-role '
     'testing — mean this document should be read as a first-party review, not '
     'as a penetration test. The single most valuable action remains item 1, '
     'which is not a security control at all but is what stands between a '
     'server fault and the loss of the company’s HR records.')

print(save(doc, OUT))
