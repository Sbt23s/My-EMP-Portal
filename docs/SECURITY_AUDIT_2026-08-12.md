# Pixous HR Portal — Security & Technical Audit

**Date:** 12 August 2026
**Scope:** backend (269 endpoints across 35 controllers), frontend (React/Vite), database (74 tables), build and configuration
**Method:** static analysis of the source, plus live request testing against a full copy of the production data (62 users, 4 companies) on an isolated backend. The hosted database and the running production backend were not touched.

**Nothing in this document has been changed.** Two earlier findings (C1, C2) were fixed and verified in a previous pass and are listed as closed.

---

## What could not be tested, and why

Being straight about the gaps rather than scoring them:

| Area | Why not | What it needs |
|---|---|---|
| Responsive layout, dark/light, alignment, animation | No browser | Playwright, or a person with the app open |
| Accessibility — contrast, focus order, screen reader, keyboard | No browser / axe | axe-core in CI, plus a manual keyboard pass |
| Real page-load and render timing | No browser | Lighthouse against a deployed build |
| Dependency CVEs (`npm audit`, OWASP check) | Shell tooling unavailable in this session | `npm audit`, `mvn dependency-check` |
| Concurrency and load behaviour | No load generator | k6 / JMeter against staging |
| Email, OTP, SMS delivery | Twilio disabled during testing, deliberately | A staging Twilio account |
| SEO / meta / Open Graph | This is a private portal behind a login | Not applicable |

Everything below was verified, not inferred. Each item says how.

---

## Summary

| | |
|---|---|
| Endpoints found | 269 |
| Endpoints carrying `@PreAuthorize` | 106 |
| Live requests made during testing | 60+ |
| **Critical** | **3 open**, 2 fixed |
| High | 6 |
| Medium | 8 |
| Low | 4 |

**Production readiness: not yet.** Three critical items are open, one of which (S1) exposes a password hash to anybody on the internet with no login at all.

---

# BUG TRACKER

| ID | Sev | Module | Finding | Evidence | Status |
|---|---|---|---|---|---|
| S1 | **CRITICAL** | Auth | Unauthenticated endpoint returns a user's password hash and answers "is the password X?" | `DebugController.java:20` · `/api/public/**` is `permitAll` | OPEN |
| C4 | **CRITICAL** | Multi-tenancy | No separation between companies — 0 of 59 entities are tenant-scoped | `company_id` absent from `attendance`, `payslips`, `tasks`, `tickets`, `assets`, … | OPEN |
| C3 | **CRITICAL** | Dashboard | Executive dashboard reports invented figures | `DashboardService.java:301` hard-codes departments, attendance %, payroll cost | OPEN |
| C1 | ~~CRITICAL~~ | Payroll | Any employee could read every salary | Re-tested 4/4 | **FIXED** |
| C2 | ~~CRITICAL~~ | Tech admin | Module list returned 500 once any module was saved | Re-tested 7/7 | **FIXED** |
| S2 | HIGH | Files | Every stored file is served without authentication | `SecurityConfig.java:72` `/api/files/**` `permitAll` | OPEN |
| S3 | HIGH | Uploads | Upload extension is unvalidated and the file is served back with a matching content type → stored XSS on the API origin | `StorageService.java:44` + `FileController.java:49` | OPEN |
| H1 | HIGH | Roles | `COMPANY_ADMIN`, `HR_MANAGER`, `TEAM_LEAD`, `EMPLOYEE`, `BOARD_ADMIN` hold zero permissions | SQL: 0 rows in `role_permissions` | OPEN |
| H3 | HIGH | Secrets | Live Twilio SID and auth token committed to git | `application-dev.yml:23-24` | OPEN |
| H4 | HIGH | Auth | Two live accounts still use the public seed password `Test1234@`, one is SUPER_ADMIN | Hash match in `users` | OPEN |
| H5 | HIGH | Quality | No tests of any kind | `backend/src/test` → 0 files | OPEN |
| S4 | MED | Errors | Internal exception chain is returned to the client on purpose | `GlobalExceptionHandler.java:66-78`; seen live | OPEN |
| S5 | MED | WebSocket | Any origin may open a socket | `WebSocketConfig.java:43` `setAllowedOriginPatterns("*")` | OPEN |
| S6 | MED | Auth | No rate limiting anywhere | No limiter dependency or filter in the codebase | OPEN |
| M1 | MED | API | A missing query parameter returns 500 instead of 400 | `/attendance/me` with no params | OPEN |
| M2 | MED | API | `UserSummary` carries a `password` field to the client | `UserSummary.java` | OPEN |
| M3 | MED | Admin | "Fresh Start" wipes the company from one click | `deploy/fresh-start.sql` | OPEN |
| M4 | MED | Build | V91–V93 migrations are untracked in git | `git status` | OPEN |
| M5 | MED | Infra | Hosted database allows 20 connections in total | Hit during this session with one tenant | OPEN |
| P1 | MED | Perf | Files are stored as LONGBLOB rows and served through the app | `V93__system_files_blob.sql`, `StorageService` | OPEN |
| L1 | LOW | Build | Main JS bundle 817 kB (257 kB gzipped), over Vite's warning threshold | `npm run build` | OPEN |
| L2 | LOW | Config | Dev JWT secret committed | `application-dev.yml` | OPEN |
| L3 | LOW | Code | 7 modules were offered in the UI with no page behind them | Fixed earlier this session | CLOSED |
| L4 | LOW | Code | Sidebar "Audit Log" pointed at `/audit-log`; the route is `/audit` | Fixed earlier this session | CLOSED |

---

# Critical findings in detail

## S1 — Anyone on the internet can read a password hash

`DebugController.java`

```java
@GetMapping("/api/public/debug/sethu_admin")
public Object getSethuAdmin() {
    User user = userRepository.findByUsername("sethu_admin").orElse(null);
    ...
    return Map.of(
        "passwordHash", user.getPasswordHash(),
        "matches_admin123", passwordEncoder.matches("admin123", user.getPasswordHash()),
        "matches_Test1234@", passwordEncoder.matches("Test1234@", user.getPasswordHash()),
        "companyId", ..., "enabled", ..., "profileStatus", ...);
}
```

`SecurityConfig.java:70` lists `/api/public/**` under `permitAll()`. **No token is required.**

Two things are wrong at once. The bcrypt hash is handed out, which can be attacked offline at leisure. And the endpoint is a password oracle — it will confirm whether a guess is correct, for free, with no lockout, because the lockout logic lives in the login path and this is not the login path.

**Fix:** delete the class. It is a debugging leftover, not a feature.

## C4 — No separation between companies

| Check | Result |
|---|---|
| Entities extending `TenantEntity` | **0** of 59 |
| `company_id` on `attendance`, `leave_requests`, `payslips`, `tasks`, `tickets`, `assets`, `communities`, `salary_structures`, `expense_claims`, `audit_log` | **absent on every one** |
| `users` | has it, and the filter works |

`TenantFilterAspect` enables a Hibernate filter before every controller and service call, so the mechanism is there — but only `User` declares the filter, and the other tables have no column for it to match on.

Nothing leaks today because there is exactly one real tenant. The second customer changes that immediately.

## C3 — Invented figures on the executive dashboard

`DashboardService.java:301`

```java
Map<String, Long> departmentBreakdown = Map.of("Engineering", 15L, "Sales", 8L, "HR", 4L);
List.of(Map.of("month","Jan","present",95,"absent",5), ...);
Map.of("month","Jan","cost",1500000);
```

Against the database: Engineering has 0 people, there is no Sales department at all, and `attendance` holds 0 rows. Headcount (32) is real; these three are not, and the screen does not distinguish them.

---

# High findings in detail

## S2 — Files are public

`SecurityConfig.java:72` puts `/api/files/**` under `permitAll()`. The code says why:

> *Public (permitAll) because image requests from the browser don't carry the JWT Authorization header; stored paths are UUID-based and unguessable.*

The reasoning holds for a profile photo. It does not hold for what else goes through the same route: payslips, Aadhaar and PAN documents, bank details, expense receipts, uploaded ID scans. A UUID in a URL is not an access control — URLs reach server logs, proxy caches (`cachePublic()` is set here), browser history, referrer headers and anyone the link is forwarded to. Once known, a URL works forever for anybody.

**Fix:** authenticate the route and check that the requester is entitled to that file; or issue short-lived signed URLs. Keep profile photos public if that is a deliberate choice, but not the document store.

## S3 — Upload → stored XSS

`StorageService.java:44`

```java
String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1) : "bin";
String relative = folder + "/" + yyyyMM + "/" + UUID.randomUUID() + "." + ext;
```

The extension is taken from the uploaded filename with no allowlist and no content inspection. `FileController.java:49` then serves the stored file with `MediaTypeFactory.getMediaType(relative)` — a content type derived from that same extension.

Chained together: upload `x.html` (or `x.svg`) containing a script → receive a `/api/files/...html` path → the route is public → opening the link executes the script **on the API's own origin**, from a URL that looks entirely legitimate.

There is also no size cap and no magic-byte check in this code path.

**Fix:** an extension and MIME allowlist per folder, sniff the actual bytes, force `Content-Disposition: attachment` plus `X-Content-Type-Options: nosniff` for anything that is not a known-safe image, and cap the size.

## H1 — Five roles grant nothing

```
COMPANY_ADMIN 0 · HR_MANAGER 0 · TEAM_LEAD 0 · EMPLOYEE 0 · BOARD_ADMIN 0
```

These are exactly the roles the technical-admin "create user" form offers. In the role matrix their columns are identical to a bare employee: dashboard, own profile, own leave — nothing else. An account created as `HR_MANAGER` has no HR access whatever.

The working roles (`IT_HR`, `IT_MGR`, `IT_EMP`, `SUPER_ADMIN`, …) come from the V8 seed. These five were created by the application on 11 Aug and never granted anything.

---

# Medium findings in detail

## S4 — Errors describe the internals

`GlobalExceptionHandler.java:66`

```java
StringBuilder detail = new StringBuilder();
for (Throwable t = ex; t != null && detail.length() < 1500; t = t.getCause()) {
    detail.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append(" || ");
}
return ... ApiResponse.fail("Something went wrong. Please try again later.", detail.toString());
```

The user-facing message is generic, but the `errors` field carries the exception chain. Observed live during this audit:

```
HttpMessageNotWritableException: Could not initialize proxy
[com.pixous.hrportal.modules.org.Company#1] - no session || JsonMappingException: ...
```

That hands out package names, entity names, ORM behaviour and primary keys. Useful in development, and a map of the system in production.

**Fix:** keep it behind the dev profile; log the detail server-side with a correlation id and return the id.

## S5 — WebSocket accepts every origin

`WebSocketConfig.java:43` — `setAllowedOriginPatterns("*")`, while HTTP CORS is properly restricted to configured origins. Any page on the internet can open a socket to the portal.

## S6 — No rate limiting

No limiter dependency, filter or annotation anywhere in the codebase. Login, password reset and the file routes are all unthrottled.

Partial mitigation: `AuthService` counts failed attempts and sets `lockedUntil`, so per-account guessing is slowed. It does nothing about username enumeration, distributed guessing, or simply flooding the service — which matters more than usual here, because the database allows 20 connections in total (M5).

## P1 — Files live in the database

V93 stores uploads as `LONGBLOB` rows and `StorageService` reads them through the app. Every payslip PDF and profile photo is a row in MySQL, fetched over a connection from a pool of six, on a hosting account limited to twenty connections in total. Backups grow with every upload; `mysqldump` already takes about five minutes.

---

# What passed

Verified, not assumed.

| Area | Result | How |
|---|---|---|
| SQL injection | **PASS** | No `nativeQuery` with concatenation anywhere; all access is JPA / Spring Data. String concatenations found build messages and filenames, not SQL |
| Path traversal on file serving | **PASS** | `FileController.java:44` decodes, then rejects `..` — in that order, which is correct |
| Password storage | **PASS** | BCrypt via `PasswordEncoder`; no plaintext in `users` |
| Unauthenticated access | **PASS** | `anyRequest().authenticated()`; missing token → 401, bad token → 401 |
| Privilege escalation via role | **PASS** | 9 roles probed across 24 endpoints; every refusal was correct after the C1 fix |
| Disabled accounts | **PASS** | `AuthService.java:128` refuses `enabled = 0` |
| Account lockout | **PASS** | Failed attempts counted, `lockedUntil` enforced |
| CORS (HTTP) | **PASS** | Origins come from configuration, not `*` |
| CSRF | **PASS (n/a)** | Disabled, which is right for a stateless JWT API with no cookie auth |
| SPA session handling | **PASS** | 401 → refresh → log out; fixed earlier this session |
| Frontend typecheck | **PASS** | 0 errors |
| Production build | **PASS** | Succeeds |
| Backend compile | **PASS** | Succeeds |
| CRUD round trips | **PASS** | create / edit / password reset / delete, each confirmed against the database |
| Password reset really works | **PASS** | The new password was used to sign in |

---

# Scores

Only for the areas actually measured. No score is given where nothing was tested.

| Area | Score | Basis |
|---|---|---|
| Security | **42 / 100** | One unauthenticated hash disclosure, public document store, stored-XSS path, no rate limiting. Injection and password storage are sound |
| Multi-tenancy | **10 / 100** | Mechanism present, applied to 1 of 59 entities |
| Data integrity | **65 / 100** | Schema and migrations are disciplined; dashboard figures are not |
| API design | **70 / 100** | Consistent envelope and status codes; 500 where 400 belongs; internals leak in errors |
| Code quality | **68 / 100** | Readable and well organised; large components, mock fallbacks now largely removed |
| Test coverage | **0 / 100** | No tests |
| Build & deploy | **60 / 100** | Builds cleanly; three migrations untracked; 817 kB bundle |
| Accessibility | not measured | Needs a browser |
| Performance | not measured | Needs a deployed build |

**Production readiness: 45%.** Fit for a single-tenant pilot with the criticals closed. Not fit to sell to a second customer until C4 is done.

---

# Fix roadmap

**Today — an hour, no risk**
1. **S1** delete `DebugController` — one file
2. **H3** rotate the Twilio credentials at the provider *(only you can do this)*
3. **H4** change the two seeded passwords on live
4. **M3** put a typed confirmation on Fresh Start

**This week**
5. **S2 + S3** authenticate `/api/files`, allowlist upload types, force download for non-images
6. **H1** grant the five empty roles their permissions, or stop offering them
7. **S4** exception detail behind the dev profile
8. **M1** map `MissingServletRequestParameterException` to 400
9. **M2** drop `password` from `UserSummary`
10. **M4** commit V91–V93

**Before selling**
11. **C3** compute the dashboard figures, or mark them as samples
12. **S5** restrict WebSocket origins · **S6** rate-limit login and uploads
13. **H5** tests — one that boots the app, one per critical path
14. **P1** move files out of MySQL to object storage

**Before a second customer**
15. **C4** tenant isolation, staged: add and backfill the columns → fill them on write → enable the filter one module at a time → prove it with two tenants
16. **M5** managed database

---

# Deployment recommendation

**Do not deploy for more than one customer.**

For the existing single tenant, deploying is reasonable **after items 1–4**. S1 alone is enough to hold a release: the endpoint hands a password hash to anyone who requests it, and confirms password guesses without limit.

C4 is the one that decides whether this is a product or an installation. Everything else on this list is a day or two of work; that one is a project, and it has to be finished before a second company's data goes anywhere near this system.

---

*Nothing here has been changed. Say which items to take and in what order.*
