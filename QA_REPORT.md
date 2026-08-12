# QA Test Report — Pixous HR Portal

**Date:** 2026-08-12
**Environment:** Local (throwaway DB in Docker, MySQL 8.4 on :3307, backend :7060, web :5174)
**Scope:** Backend API smoke + E2E, web UI walkthrough across 4 roles (arun / karthik / priya / admin), tech-admin (SaaS control center) API.
**Automated tests:** none exist in the repo (backend or frontend) — this was manual/scripted probing.

---

## Summary

| Area | Result |
|---|---|
| Backend boot | ✅ UP after two environment fixes (see E1, E2) |
| Web app | ✅ Runs; login + all role pages load |
| API smoke (real endpoints) | ✅ 45 passed / 10 flagged, of which 5 were wrong probe paths/params and 2 were legitimate permission denials |
| Confirmed bugs | **7** (see B1–B7) |
| Environment / setup issues | **2** (E1–E2) |
| Test coverage | ❌ Zero automated tests |

---

## Environment / setup issues (blocked the run)

### E1 — Local MySQL volume has the wrong database name
`docker-compose.yml` sets `MYSQL_DATABASE: nobile`, but `MYSQL_DATABASE` only applies on a **fresh** volume. This machine's `mysql_data` volume was already initialised with the older name `hr`, so the backend failed to boot with `Unknown database 'nobile'` (Flyway couldn't connect).

- **Fix (one-off):** `CREATE DATABASE nobile` in the container (we did this), or `docker compose down -v` to recreate the volume, or make `start-local.ps1`/compose consistent.
- Also inconsistent: `start-local.ps1` prints *"database 'hr', root/root123"* while setting `DB_NAME=nobile`.

### E2 — Generic `PORT` env var hijacks the backend port (config fragility)
`backend/src/main/resources/application.yml`:
```yaml
server:
  port: ${PORT:${SERVER_PORT:7060}}
```
`PORT` is a very generic name. In this shell `PORT=62949` is already set by the surrounding environment, so the backend tried to bind **62949** instead of 7060 → `Port 62949 was already in use` → boot failure.
- **Fix (recommended):** use a project-specific variable, e.g. `server.port: ${HR_PORTAL_PORT:${SERVER_PORT:7060}}`. Anyone whose shell exports `PORT` (very common on CI/containers/IDEs) hits this.

### E3 — README/docs drift
README says **Java 21**; `pom.xml` is `<java.version>17</java.version>`. Runs fine on 17, but docs are wrong.

---

## Confirmed bugs

### B1 — Unknown URL returns **500**, not 404
`GET /api/does-not-exist-xyz` → `500 {"message":"Something went wrong..." (ref …)}` with `NoResourceFoundException`.
Root cause: `GlobalExceptionHandler` has no handler for `NoResourceFoundException`, so it falls into the catch-all `Exception` handler → 500. Unknown paths should be 404. Repeated every time.

### B2 — Wrong HTTP method returns **500**, not 405
`GET /api/attendance/punch-in` (POST-only) → `500` with `HttpRequestMethodNotSupportedException` instead of `405 Method Not Allowed`. Same root cause as B1 — add an explicit handler.

### B3 — Leave approval dead-end: requests can never be approved (data + workflow)
- Seed data contains **no user with the `IT_TL` (Team Leader) role**, and employees have **NULL designation/team** (`designation_title`, `department_title` are NULL; the absent-today widget shows everyone as "No team").
- The approval rule for a plain employee's ≤3-day leave is: only an **IT_TL in the same team** can act (`LeaveService.canActOnLeave`: `leaveHasRole(approver,"IT_TL") && sameTeam(...)`).
- Result: arun applied for a 2-day leave (Sept 1–2) — API returns 200, request saved as `PENDING` with `requestedTo = null`, but **no one on earth can see or approve it**. `GET /api/leave/approvers?days=2` for arun returns **[]** (empty picker in the UI), karthik (IT_MGR) sees "Nothing to approve", and the request sits PENDING forever.
- Misleading UI: arun's dashboard shows *"1 leave request awaiting approval"*; karthik's shows *"LEAVE APPROVALS 0 — all caught up"*.
- **Fix direction:** seed an IT_TL (and assign teams), and/or make the apply endpoint refuse requests that have no routable approver (or fall back to HR/admin).

### B4 — Dashboard punch-in banner shows stale state on first load (intermittent)
Punched in at 13:30 via API; first dashboard load still said *"You haven't punched in yet today"*; after a re-render it correctly showed *"You punched in at 1:30 PM"*. Looks like a client cache/race between the punch-status query and the attendance query on mount. Needs a refetch on focus/mount.

### B5 — Onboarding page is unusable on a fresh install
The HR "Onboarding" page's employee picker is populated from `GET /api/onboarding/employees`, which only returns users who **already have an IN_PROGRESS checklist**. With a fresh seed nobody qualifies → *"No employees found"* → HR cannot start onboarding for anyone from the UI, even though the backend `POST /onboarding/{userId}/start` works.
- **Fix direction:** the picker should return employees without an onboarding checklist (e.g. `ACTIVE` profile status, no checklist row).

### B6 — Login page shows placeholder copy
`web/src/pages/Login.tsx:121` renders **"Welcome to Login Page"** — leftover template text on the public login screen.

### B7 — External third-party script loaded on every page
`web/index.html:15` loads `<script src="https://js.puter.com/v2/"></script>`. It logs a "Submit this app to the Puter App Store" ad to the console on every load and is a privacy/security exposure (unvetted external JS on the login + app pages). Remove unless actually used.

---

## Not bugs (by design / environment), for completeness

- `GET /api/assets`, `GET /api/complaints`, `GET /api/safety-incidents`, `GET /api/tasks/all` → **403** for `arun` (IT Employee). Employees use `/api/assets/my-assets` etc. — permission model is working as intended.
- `GET /api/payroll/salary-months` & `/api/payroll/salaries` → **403 for priya (IT HR)**. Only admin/payroll roles can see the org-wide payroll. Arguably HR *should* see payroll; flag for product decision, not a crash.
- `GET /api/onboarding/1` → 404 "Onboarding checklist not found" — correct (no checklist exists yet).
- **Analytics service not running** (heavy deps, dlib/PyTorch): attendance page honestly reports *"Face verification is unavailable — the analytics service is not reachable"* and punches still work. ✅ graceful degradation.
- **Mobile (Expo)** not tested — needs a simulator/device.

---

## What worked (spot-checked end-to-end)

- **Login/logout** across arun, karthik, priya, admin — ✅ (401 on wrong password; 401 without token).
- **Attendance:** punch-in → punch-out → `/attendance/today` record (PRESENT, late flag) → monthly summary table + Excel export button render.
- **Leave:** balances (CL/EL/SL…), apply returns 200, appears in *My Requests*, admin dashboard shows "1 pending" (only admin can see it — see B3).
- **Payroll:** salary-months/me, payslip list (empty state handled), download path intact.
- **Helpdesk:** create ticket (via API and visible in UI as `TKT-2026-00001`), lists, status tabs.
- **Dashboard:** employee (arun), manager (karthik: 10 emp / 1 present / 9 absent), HR/admin stats, holidays, leave analytics, absent-today table all render.
- **Manager views:** leave pending, requests-for-me, team attendance, absent-today, org insights, complaints, assets, tickets/all — all 200.
- **HR views:** employee directory with filters, org dropdowns, onboarding endpoints, import/export buttons present.
- **Admin:** executive dashboard, settings, payroll salaries — 200.
- **Tech-admin (SaaS control center):** login (admin/admin123), companies list (Sethu Technologies seeded), roles with permission counts, audit logs — all 200.
- **Web build:** `npx tsc -b` → clean, 0 TypeScript errors.
- **Backend stability:** after the env fixes, no unexpected exceptions during the whole UI walkthrough (log contains only the 500s from our deliberate probes).

---

## Recommended priorities

1. **B3 (leave approval dead-end)** — blocks a core HR workflow entirely in the seeded demo; fix seed data + routing fallback.
2. **B1 + B2 (404/405 as 500)** — one small `GlobalExceptionHandler` addition; also hides real bugs from monitoring.
3. **E2 (`PORT` var)** — one-line config change; will bite anyone with `PORT` exported.
4. **B5 (onboarding picker)** — small query change; feature is invisible otherwise.
5. **B7 (puter.js)** — remove external script.
6. Add at least smoke tests (backend) + a couple of Vitest/RTL tests (frontend) — currently zero coverage.
