"""SonarQube Code Quality Analysis — PDF."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pdf_style import (bullets, document, h1, h2, metrics, note, p, pill,
                       section, table)
from render_pdf import to_pdf

OUT = os.path.join(os.path.expanduser('~'), 'Downloads',
                   'Pixous_HR_Portal_SonarQube_Analysis_v1.0.pdf')

OK = lambda t='Clean': pill('pass', t)
GAP = lambda t='Not assessed': pill('warn', t)
BAD = lambda t='Fail': pill('fail', t)

body = ''.join([

section(
  h1('1.', 'Status of SonarQube in This Project'),
  p('SonarQube is not currently installed for this project. There is no '
    'sonar-project.properties file, no sonar-maven-plugin in the backend build, '
    'no sonarqube-scanner in the web build, and no scanner binary on the build '
    'machine — all four were checked.'),
  p('This document therefore does not present a SonarQube dashboard, because '
    'there is no scan to present and an invented one would be worse than none. '
    'What it does present is every measure SonarQube reports, obtained from '
    'tools that were actually run: the Java compiler and its test suite, the '
    'TypeScript compiler in strict mode, the Dart analyser, and direct '
    'measurement of the repository. Section 7 gives the steps to stand '
    'SonarQube up so the next version of this document is a native report.'),

  metrics([('0', 'compiler errors'), ('97/97', 'tests passing'),
           ('8', 'debt markers'), ('9.0%', 'comment density')]),

  h2('1.1  What was checked, and how'),
  table(['Check', 'Command', 'Outcome'],
        [['Sonar config file', 'ls sonar-project.properties', 'Not present'],
         ['Maven Sonar plugin', 'grep sonar backend/pom.xml', 'Not present'],
         ['npm Sonar scanner', 'grep sonar web/package.json', 'Not present'],
         ['Scanner binary', 'which sonar-scanner', 'Not installed']],
        widths=['26%', '38%', '36%']),
),

section(
  h1('2.', 'Codebase Under Analysis'),
  table(['Language', 'Files', 'Lines', 'Comment lines', 'Density'],
        [['Java (backend)', '315', '26,321', '3,454', '13.1%'],
         ['TypeScript / TSX (web)', '113', '50,258', '3,421', '6.8%'],
         ['Dart (mobile)', '69', '22,012', '1,951', '8.9%'],
         ['SQL (Flyway migrations)', '102', '—', '—', '—'],
         ['TOTAL', '599', '98,591', '8,826', '9.0%']],
        widths=['32%', '13%', '17%', '21%', '17%'], total_last=True),
  note('Counted with find, wc and grep over the working tree. Comment lines '
       'counted as lines whose first non-space characters are //, /*, * or #.'),
  p('A 9% comment density across nearly a hundred thousand lines is healthy. '
    'SonarQube treats very low density as a maintainability signal; this is '
    'comfortably above the threshold where that flag appears.'),
),

section(
  h1('3.', 'Reliability — Compiler and Analyser Findings'),
  p('These are the closest direct equivalent to Sonar’s Bugs measure: problems '
    'the language’s own toolchain can prove.'),
  table(['Component', 'Tool', 'Configuration', 'Errors', 'Warnings'],
        [['Backend', 'javac via Maven', 'Default', '0', '0'],
         ['Web portal', 'tsc --noEmit', 'strict: true', '0', '0'],
         ['Mobile app', 'flutter analyze', 'flutter_lints ruleset', '0', '0']],
        widths=['20%', '22%', '26%', '16%', '16%']),
  p('The TypeScript result is the most significant of the three: strict mode is '
    'enabled, which turns null and undefined handling, implicit any and unsafe '
    'narrowing into compile errors. Fifty thousand lines passing strict with no '
    'errors is a real reliability signal, not a formality.'),
  note('noUnusedLocals and noUnusedParameters are set to false. Enabling them '
       'would surface dead code that Sonar would otherwise report as a code '
       'smell — see section 5.'),

  h2('3.1  Test results'),
  table(['Suite', 'Framework', 'Tests', 'Passed', 'Failed', 'Duration'],
        [['Backend unit', 'JUnit 5', '61', '61', '0', '54.9 s'],
         ['Web unit', 'Vitest', '36', '36', '0', '51.8 s'],
         ['Mobile', 'flutter analyze only', '—', '—', '—', '—'],
         ['TOTAL', '', '97', '97', '0', '']],
        widths=['22%', '26%', '13%', '13%', '12%', '14%'], total_last=True),
),

section(
  h1('4.', 'Security — Sonar’s Vulnerability and Hotspot Categories'),
  table(['Sonar rule category', 'Position in this codebase', 'Evidence'],
        [['Hard-coded credentials', 'None found in application source',
          'Database, JWT and mail secrets read from environment variables'],
         ['SQL injection', 'Not present by construction',
          'JPA parameter binding; native queries use named parameters'],
         ['Weak hashing for passwords', 'Not present',
          'BCrypt — adaptive, salted'],
         ['Missing authorisation checks', 'Enforced at 110 checkpoints',
          '110 @PreAuthorize annotations across 38 controllers'],
         ['Insecure TLS configuration', 'Not present',
          'TLS 1.2 and 1.3 only; 1.3 negotiated with AES-256-GCM'],
         ['Permissive CORS', 'Not present',
          'A foreign Origin receives no Access-Control-Allow-Origin'],
         ['Path traversal in file handling', 'Guarded',
          'StorageService rejects traversal; covered by StorageServiceTest'],
         ['Security hotspot — CSP', 'Open, deliberately staged',
          'Content-Security-Policy is Report-Only while the allow-list is '
          'confirmed from reports'],
         ['Dependency vulnerabilities', 'NOT ASSESSED',
          'No npm audit, Dependency-Check or Snyk run — the notable gap']],
        widths=['28%', '27%', '45%']),
),

section(
  h1('5.', 'Maintainability — Measured Smells'),
  h2('5.1  File size'),
  p('SonarQube raises a smell on files past roughly 750 lines, on the reasoning '
    'that a file that large is usually doing several jobs. Seven files exceed '
    '1,400 lines.'),
  table(['File', 'Lines', 'Assessment'],
        [['web/src/pages/Employees.tsx', '3,604',
          'Directory, profile editing, onboarding and credentials in one page'],
         ['web/src/pages/Dashboard.tsx', '3,238',
          'Thirteen role-dependent widgets in one file'],
         ['web/src/pages/Tasks.tsx', '3,205',
          'Personal, team and company views combined'],
         ['web/src/pages/Chat.tsx', '2,317',
          'Messaging plus one-to-one and group calling'],
         ['web/src/pages/WorkReports.tsx', '2,158',
          'Own, team and company reporting'],
         ['mobile-app/lib/features/chat/chat_screen.dart', '2,047',
          'Channel list, room, attachments, calls'],
         ['mobile-app/lib/features/hr/hr_screens.dart', '1,439',
          'Several HR screens in one file']],
        widths=['44%', '12%', '44%']),
  p('This is the single largest quality debt in the codebase. It is real: these '
    'files are hard to review and hard to change safely. It is also not urgent '
    '— they work, they are covered by types, and splitting them is a refactor '
    'with regression risk that buys no user-visible benefit. The recommendation '
    'is to split a file when it is next changed substantially, not as a '
    'campaign.'),

  h2('5.2  Technical debt markers'),
  table(['Marker', 'Count', 'Assessment'],
        [['TODO / FIXME / HACK / XXX across all source', '8',
          'Very low for 98,591 lines. Sonar would report this as negligible '
          'self-declared debt.']],
        widths=['40%', '10%', '50%']),

  h2('5.3  Duplication'),
  p('Not measured. Detecting duplicated blocks properly needs a token-level '
    'comparison, which is exactly what the Sonar scanner does and what no tool '
    'available here does. This is stated as unmeasured rather than guessed at.'),
),

section(
  h1('6.', 'Coverage — The Honest Position'),
  table(['Component', 'Test files', 'Source files', 'Ratio', 'Assessment'],
        [['Backend', '8', '315', '2.5%',
          'Unit tests on security, auth, calendar and storage — the '
          'highest-risk areas — but no integration tests'],
         ['Web portal', '4', '113', '3.5%',
          'Login, dates, formatting and permissions only'],
         ['Mobile app', '14', '69', '20%',
          'Present, though they exercise the live production API rather than a '
          'fixture']],
        widths=['16%', '13%', '15%', '11%', '45%']),
  p('No line-coverage percentage is quoted because none was measured: JaCoCo is '
    'not configured for the backend and Vitest was not run with coverage '
    'enabled. A Sonar quality gate would fail this project on coverage, and '
    'that verdict would be correct.'),
  note('The mobile tests hitting the live production API is itself a finding: a '
       'test suite that depends on a remote server is not repeatable, and it '
       'can write to real data.'),
),

section(
  h1('7.', 'Standing SonarQube Up'),
  p('These are the concrete steps, so this document ends with something '
    'actionable rather than an observation.'),

  h2('7.1  Run the server'),
  p('docker run -d --name sonarqube -p 9000:9000 sonarqube:lts-community'),

  h2('7.2  Backend — add the plugin and coverage'),
  bullets([
    'Add jacoco-maven-plugin to backend/pom.xml with the prepare-agent and '
    'report goals bound to the test phase, so coverage data exists at all.',
    'Add sonar-maven-plugin.',
    'Run: mvn clean verify sonar:sonar -Dsonar.host.url=http://localhost:9000 '
    '-Dsonar.token=<token>',
  ]),

  h2('7.3  Web portal'),
  bullets([
    'npm install --save-dev sonarqube-scanner',
    'Enable coverage in Vitest: vitest run --coverage, with the lcov reporter.',
    'Add sonar-project.properties pointing sonar.javascript.lcov.reportPaths at '
    'coverage/lcov.info.',
  ]),

  h2('7.4  Mobile app'),
  bullets([
    'flutter test --coverage produces coverage/lcov.info.',
    'Point sonar.dart.lcov.reportPaths at it. Note the Dart plugin is '
    'community-maintained.',
  ]),

  h2('7.5  A quality gate worth having'),
  table(['Condition', 'Suggested threshold', 'Why'],
        [['Coverage on new code', 'at least 60%',
          'Applying it to new code only avoids blocking every change on the '
          'existing gap'],
         ['Duplicated lines on new code', 'under 3%', 'Sonar default'],
         ['Maintainability rating on new code', 'A', 'Sonar default'],
         ['Reliability rating on new code', 'A', 'Sonar default'],
         ['Security rating on new code', 'A', 'Sonar default'],
         ['Security hotspots reviewed', '100%',
          'Forces a decision on each, rather than silence']],
        widths=['34%', '22%', '44%']),
),

section(
  h1('8.', 'Projected Quality Gate Result'),
  p('Based on the measurements above, this is how a first Sonar run would most '
    'likely land. It is a projection from measured inputs, and labelled as one.'),
  table(['Sonar measure', 'Expected', 'Basis'],
        [['Bugs', 'Very low',
          'Zero compiler errors across three toolchains; TypeScript in strict '
          'mode'],
         ['Vulnerabilities', 'Low',
          'No hard-coded secrets, parameterised queries, BCrypt, TLS 1.3'],
         ['Security Hotspots', 'Some to review',
          'CSP staged as Report-Only; dependency scan absent'],
         ['Code Smells', 'Moderate',
          'Driven mainly by the seven oversized files'],
         ['Coverage', 'Below gate',
          'No coverage instrumentation configured; test-to-source ratio is low '
          'on backend and web'],
         ['Duplications', 'Unknown', 'Not measurable without the scanner'],
         ['Quality Gate', 'FAIL — on coverage',
          'Everything else would likely pass. Coverage is the one that fails, '
          'and it is a fair failure']],
        widths=['24%', '20%', '56%']),

  h1('9.', 'Conclusion'),
  bullets([
    'SonarQube is not installed. This document says so rather than presenting a '
    'dashboard that does not exist.',
    'The equivalent measures were taken from tools that were run: zero errors '
    'from javac, zero from tsc in strict mode, zero from flutter analyze, and '
    '97 of 97 tests passing.',
    'Security posture reads well against Sonar’s own rule categories, with one '
    'real gap: no dependency vulnerability scan has been run.',
    'The main maintainability debt is seven files over 1,400 lines. Real, not '
    'urgent, and best paid down when those files are next changed.',
    'Coverage is the one measure that would fail a quality gate, and that '
    'verdict would be correct: there is no coverage instrumentation at all.',
    'Section 7 gives the steps to make the next version of this document a '
    'genuine Sonar report rather than an equivalent.',
  ]),
),
])

html = document(
    'SonarQube Code Quality Analysis',
    'Static Analysis',
    'Static analysis of the codebase, the quality-gate position it implies, and '
    'how to put SonarQube itself in place.',
    body)

print(to_pdf(html, OUT))
