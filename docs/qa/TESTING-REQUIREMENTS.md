# Testing Requirements — Pixous HR Portal

This document is the **master testing specification**: what must be tested, how, with what tools, and what "done" means for each level. It is the contract future QA work should follow.

---

## 1. Test Levels & Tooling

| Level | Tool | Where | Runs on | Gate |
|---|---|---|---|---|
| Unit (backend) | JUnit 5 + Mockito | `backend/src/test` | `mvn test` | ✅ present (40 tests) |
| Unit + Component (frontend) | Vitest + Testing Library + jsdom | `web/src/**/*.test.ts(x)` | `npm test` | ✅ present (22 tests) |
| Integration (backend) | Spring Boot Test + H2 | `backend/src/test` | `mvn test` | ⏳ planned |
| E2E (browser) | Playwright | `e2e/` | `npx playwright test` | ⏳ planned |
| API smoke (live) | curl script | `scripts/smoke.sh` | manual/CI on deploy | ⏳ planned |
| Load | k6 | `load/` | manual | ⏳ planned |
| Security | OWASP ZAP / manual probes | — | — | ⏳ periodic |
| Accessibility | axe-core + keyboard walkthrough | — | — | ⏳ planned |

**Coverage targets:** backend service layer ≥ 70% line, frontend ≥ 40% (critical paths 100%: auth, uploads, payroll export).

---

## 2. Unit Testing Requirements (backend)

### 2.1 Must-have suites (current state)
- **JWT service:** generate (claims, expiry, issuer), validate (signature, expiry, tamper), refresh rotation.
- **Login limiter:** threshold lockout, reset on success, per-username isolation, spoof-proof IP keying.
- **Storage service:** allowlist accept/reject, SVG/HTML → `.bin`, traversal rejection, size caps.
- **Auth service:** login success/failure, lockout integration, password-change rules (old ≠ new, length), signup DTO validation, refresh rotation.
- **DTO validation:** every `@NotBlank`/`@Email`/`@Size`/`@Pattern` rule with valid + invalid samples.

### 2.2 Required conventions
- One test class per service/controller; `given/when/then` style; Mockito for repos/external deps.
- Naming: `methodName_scenario_expectedResult` (e.g. `login_wrongPassword_throws401`).
- Assert on behavior (exceptions, returned DTOs), not implementation.

### 2.3 Expansion backlog (next 60 tests)
- `CompanyService` (tenant creation, code generation, uniqueness)
- `LeaveService` (overlap, balance, approval workflow, status transitions)
- `PayrollService` (run generation, payslip totals, rounding, duplicate-run guard)
- `AttendanceService` (clock-in/out, duplicate shift, grace rules)
- `FileController` (unauth access, upload validation, path traversal via URL)
- `UserService` (pagination, filters, role assignment, self-demotion guard)
- `NotificationService` (fan-out, unread counts, per-tenant isolation)
- `AuditService` (entry creation, query by actor/action/date range)

---

## 3. Component / Unit Testing Requirements (frontend)

### 3.1 Current coverage (22 tests)
- Pure utils: `cn`, `initials`, `monthName`, `formatMoney`, `minutesToHours`.
- Date helpers: `to12Hour`, `todayIso`, `thisMonthIso`.
- Login page: render, empty-submit errors, valid submit → login called + redirect, failed login → no redirect + retry enabled, password visibility toggle.

### 3.2 Expansion backlog (next ~30 tests)
- **Auth context/hook:** login/logout state, token expiry handling, protected-route redirect (with `MemoryRouter` + mocked API).
- **API client:** token attach, 401 → refresh retry, refresh failure → logout.
- **Form-heavy pages (each: validation, submit payload, error toast):** Employee form, Leave request, Claim entry, Payslip/Reports filter.
- **Data tables:** pagination controls, sorting, search debounce, empty state.
- **Chat/WS:** message send, reconnect indicator (mock STOMP client).

### 3.3 Rules
- Component tests must not hit the network: mock `api` module (`vi.mock("@/lib/api")`).
- Always test the negative path (error toast, disabled button, validation message).
- Keep `setup.ts` cleanup (already configured); never rely on RTL auto-cleanup with `globals: false`.

---

## 4. Integration Testing Requirements (backend)

- Boot a slice (`@SpringBootTest` + H2, `@ActiveProfiles("test")`) and test **real service ↔ repository** flows: user create → login → refresh → change password → lockout across calls.
- Migration integrity: Flyway runs clean against H2/MySQL 8 in CI.
- Multi-tenant isolation: company A cannot read company B rows (two tenants in one test).
- Soft-delete semantics: deleted entity disappears from lists, audit row written.

---

## 5. E2E Testing Requirements (Playwright — planned)

Top-10 user journeys, run against the deployed stack:

1. Login as admin → dashboard loads → logout.
2. Create employee → appears in list → edit → delete.
3. Submit leave request → admin approves → employee sees approved status.
4. Upload avatar/attachment → preview → open file (no 404).
5. Run payroll → generate payslips → export XLSX.
6. Role change (employee → manager) → new permissions take effect.
7. Search + filter employees by name/department (debounce, empty state).
8. Password change → old password stops working, new works.
9. Unauthorized route → redirected to login (deep link).
10. Mobile viewport: key pages render without horizontal overflow.

**A11y E2E:** run axe on journeys 1–3; fix all serious/critical violations.

---

## 6. API Smoke Script (planned — `scripts/smoke.sh`)

Runs after every deploy against the live URL:
- `GET /` → 200
- `GET /api/actuator/health` behind auth → 401 (or 200 with token)
- `POST /api/auth/login` (bad creds) → 401
- `POST /api/auth/login` (admin) → 200 + token
- `GET /api/users?size=1` with token → 200
- `GET /swagger-ui` → **must NOT be 200 in prod** (regression guard for QA-003)
- File upload (1 small PNG) → 200; upload `evil.html` → rejected/`.bin`

---

## 7. Security Testing Requirements

| Check | Method | Cadence |
|---|---|---|
| SQLi / XSS probes | manual + OWASP ZAP automated scan | every release |
| Auth bypass / privilege escalation | manual (login as each role, hit admin endpoints) | every release |
| Secrets scan | `gitleaks scan` in CI (block on findings) | every push |
| Dependency CVEs | `mvn dependency-check` / `npm audit` (fail on high) | weekly |
| Header check | securityheaders.com-style probe | monthly |
| Rate-limit bypass | spoofed `X-Forwarded-For` regression test (exists) | every release |
| Live DB safety | read-only user for smoke tests; never run seeds in prod | permanent |

---

## 8. Performance Requirements

| Metric | Target |
|---|---|
| Home page TTFP (public) | < 1.5 s (global) |
| Login round-trip | < 1 s |
| List APIs (p50/p95) | < 300 ms / < 800 ms at 100 concurrent users |
| Frontend initial JS | < 300 kB gzipped (after code-splitting) |
| Lighthouse performance | ≥ 85 mobile |
| Uptime (rolling 30 d) | ≥ 99.5% |

---

## 9. Definition of Done (per release)

- [ ] `mvn test` green (backend) + `npm test` green (frontend)
- [ ] `npm run build` + `mvn clean package` green
- [ ] Smoke script green against staging
- [ ] gitleaks scan clean
- [ ] No new high/critical findings from manual security pass
- [ ] All bug-tracker items for the release fixed or explicitly deferred

---

## 10. Reporting Format

Every testing cycle must produce:
1. **Result summary** (pass/fail counts per level)
2. **Bug table** (ID, severity, priority, module, repro, expected, actual, root cause, fix, status)
3. **Coverage delta** vs previous cycle
4. **Open risks** (with owner + due date)

This spec, the bug tracker in `01-full-qa-report.md`, and the roadmap in `08-production-readiness.md` together define the complete QA contract for Pixous HR Portal.
