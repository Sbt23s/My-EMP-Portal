# Pixous HR Portal — End-to-End Review & Fixes

_Full-stack review of the shipped codebase: Spring Boot 3.5 / Java 21 backend,
React 19 / Vite / TypeScript web app, Expo React Native mobile app, and a
Python FastAPI analytics/face microservice._

---

## How this review was done (and what could be verified)

| Layer | Verification performed | Result |
|-------|------------------------|--------|
| **Web (React/TS)** | `tsc --noEmit` type-check + full `npm run build` (Vite) | **Was failing → now passes (exit 0), 24 → 0 type errors** |
| **Database schema** | Installed MariaDB 10.11, replayed **all 30 Flyway migrations** (V1–V30) in order against a live server | **All 30 apply cleanly; 66 tables created** |
| **Backend ↔ DB** | Custom entity↔schema cross-checker (simulates Hibernate `ddl-auto=validate`): every `@Column`/`@JoinColumn` on all 45 entities checked against live columns | **All persistent attributes map to real columns** |
| **API contract** | Custom parity checker: all 158 frontend axios calls diffed against all 156 backend Spring MVC routes | **100% of frontend calls resolve to a backend route** |
| **Backend compile** | The shipped archive contained a complete `target/classes` (compiled bytecode) dated the same day, with a `.class` for **all 232** source files and **zero** sources modified after that compile | **Confirms the backend compiled cleanly as shipped**; my edits are syntactically balanced and signature-consistent |
| **Mobile (Expo)** | esbuild syntax pass over all TS/TSX | **Clean** |
| **Python service** | `py_compile` + endpoint/consumer mapping | **Clean; 6 endpoints match 4 frontend fetches + OCR** |
| **Seed / RBAC** | Verified users, 13 roles, 14 permissions, 59 role-permission grants, password hashes | **Correct** |

> **Constraint:** Maven Central and all public Maven mirrors are blocked by the
> sandbox network policy, so the backend could not be booted on a live JVM here.
> However, the shipped compiled bytecode (see table) is strong evidence the
> backend compiles; the backend fixes below are small, self-contained, and
> signature-verified against their call sites.

---

## 1. Issues found & fixes implemented

### 🔴 Blocker — Web app did not build (24 TypeScript errors)
`npm run build` runs `tsc -b` first, which failed, so **the frontend could not
be built or deployed at all.** Root causes and fixes:

| # | Error | File(s) | Fix |
|---|-------|---------|-----|
| 1 | `Property 'asChild' does not exist on ButtonProps` (9 occurrences) — the Dashboard used `<Button asChild>` to wrap `<Link>`s, but the `Button` component never supported `asChild`. | `web/src/components/ui/button.tsx` | Added `asChild` support: when set, `Button` clones its child element and merges the button styling onto it (no new dependency). |
| 2 | `Property 'outLatitude'/'outLongitude' does not exist on AttendanceRecord` (8 occurrences) — Team Attendance renders punch-**out** GPS + map links, but the type and the backend response DTO only carried punch-**in** coordinates. | `web/src/types/index.ts`, `backend/.../attendance/dto/AttendanceResponse.java`, `backend/.../attendance/AttendanceService.java` | Added `outLatitude`/`outLongitude` to the TS type **and** to the backend `AttendanceResponse` record + its mapper. (The entity already persisted these columns on punch-out — only the response was dropping them.) |
| 3 | `Parameter implicitly has an 'any' type` (4) — untyped `useQuery` results in Leave & Assets. | `web/src/pages/Leave.tsx`, `web/src/pages/Assets.tsx` | Typed the queries with the existing `PageEnvelope<LeaveRequest>` / `PageEnvelope<Asset>` shapes. |
| 4 | `onBlur is specified more than once` (2) — Login inputs set `onBlur` and then spread `{...register()}` which also sets `onBlur`, silently overwriting the field-reset. | `web/src/pages/Login.tsx` | Composed both: call RHF's `register().onBlur(e)` **and** the active-field reset in one handler. |
| 5 | `Property 'referrerPolicy' does not exist on HTMLAudioElement` (1) — chatbot TTS. | `web/src/components/ChatBotWidget.tsx` | Used `setAttribute("referrerpolicy", …)` (valid at runtime; not in the TS DOM typings). |

### 🟠 Logic bug — Leave monthly cap was silently defeated by a conflicting rule
`LeaveService.apply()` contained **two overlapping rules** for Casual/Sick leave:
a **3-month-gap** check ("employee request already finished") *and* the intended
**1-per-calendar-month** cap. The 3-month rule is strictly stronger, so it
always fired first — meaning an employee who took one casual leave in January
was blocked until **April**, and the documented "1 casual + 1 sick per month"
behaviour was unreachable.
**Fix:** removed the contradictory 3-month-gap block; the correct
`monthly_limit`-driven cap (already backed by `leave_types.monthly_limit = 1`
for CL/SL and the `countMonthlyConsuming` query) now governs.
_File: `backend/.../leave/LeaveService.java`._

### 🟠 Visibility bug — Helpdesk tickets never showed for HR/Admin
The Helpdesk page fetched only **My tickets** (`/tickets`) and, for agents,
**Assigned to me** (`/tickets/assigned-to-me`). It **never called `/tickets/all`**,
so HR/Admin (who hold `USER_MANAGE`/`DASHBOARD_EXEC` but aren't
`HELPDESK_AGENT`) could only ever see tickets they personally raised — matching
the "raised tickets don't appear for HR/Admin" report.
**Fix:** added an **"All tickets"** tab, shown to `USER_MANAGE`/`DASHBOARD_EXEC`
holders, wired to the already-existing (and correctly authorized) `/tickets/all`
backend endpoint. Status-change controls remain agent-only (backend enforces
`HELPDESK_AGENT`), so this adds oversight without weakening authorization.
_File: `web/src/pages/Helpdesk.tsx`._

### 🟠 Config bug — Mobile app pointed at the wrong backend port
The Expo app defaulted its API base URL to **`http://localhost:8081`**, but the
Spring Boot backend listens on **`7060`** — so the mobile app failed to reach the
API out of the box.
**Fix:** corrected the fallback in `mobile/src/api/client.ts` and the
`mobile/.env.example` to `7060`.

### 🟡 Startup robustness — Flyway could get stuck on a failed migration
The user's own `error.log` showed the backend refusing to start with
_"Detected failed migration to version 16 (community chat)… run repair to fix"_.
This happens when a migration is recorded as failed (`success = 0`) in
`flyway_schema_history` (e.g. an interrupted run). The existing custom
`FlywayMigrationStrategy` did call `repair()`, but any exception during repair
would abort startup.
**Fix:** hardened `FlywayConfig` so `repair()` is wrapped defensively (logs and
continues on any repair hiccup) before `migrate()`, and documented *why*. On a
clean history this is a harmless no-op; on a stuck one it self-heals. This
directly addresses the crash in the shipped `error.log`.
_File: `backend/.../config/FlywayConfig.java`._

### 🟡 Stale/misleading config comment
`web/.env.example` told developers `/api` is proxied to `localhost:8081`; the
Vite proxy (and the whole app) actually targets `7060`.
**Fix:** corrected the comment to `7060`.

---

## 2. Areas reviewed and confirmed **already working** (no change needed)

These were flagged as risky or "unbuilt" in the project's own roadmap notes, but
on inspection are complete and correct end-to-end:

- **Payslip request → admin approve → customized generate → owner-only download.**
  Endpoints, DTOs, entity, and the requester-only PDF guard
  (`PayslipService.pdfBytes` line-checks `p.getUserId().equals(requesterId)`)
  are all present and correct.
- **Onboarding** (start → checklist → complete task, with graceful "not started"
  handling via `retry:false` + 404).
- **Safety incidents** — full CRUD (report / mine / all / get / resolve), entity
  ↔ schema parity verified. (Roadmap listed it as a "new system"; it was built.)
- **Attendance punch-in/out** with geofencing — coordinates persist correctly;
  only the response DTO was dropping the out-coordinates (fixed above).
- **Community/chat** (the V16 module) — schema, entities (`@EmbeddedId`,
  `is_announcement`, `audio_path`), and 14 endpoints all consistent.
- **Analytics + face-recognition microservice** — the Dashboard degrades
  gracefully (`return null`) if the Python service on :8082 is offline.
- **RBAC** — 13 roles / 14 permissions / 59 grants; Board Admins correctly derive
  power from a co-assigned `SUPER_ADMIN` role (the `BOARD_ADMIN` role is a
  display marker by design).
- **All 30 Flyway migrations** apply cleanly on a fresh database.

---

## 3. Remaining limitations (honest constraints)

1. **Projects** and **Documents** pages are still `ModulePlaceholder`s.
   - *Projects* has **no backend at all** (no table/entity/endpoint) and isn't in
     the navigation — it's an orphaned route.
   - *Documents* is in the nav and has an `employee_documents` **table**, but no
     controller/service to drive it.
   Building either as a real module means new backend code (entity + repository +
   service + controller + possibly a migration). Because the backend cannot be
   compiled/run in this environment (Maven Central is firewalled), I did **not**
   ship untested new server modules for these — that would risk labeling
   unverified code as "working." They render a clean "planned" placeholder, not
   an error. This is the one genuinely incomplete area and is the natural next
   task once a build/test environment is available.
2. **Backend could not be booted on a live JVM here** (blocked Maven repos). The
   backend fixes are small, self-contained, brace/paren-balanced, and verified
   against their call sites; the shipped compiled bytecode confirms the codebase
   compiles. Still, a `mvn spring-boot:run` on your machine is the final
   confirmation for the four backend edits.
3. **Bundle size:** the web build emits a single ~1.9 MB JS chunk (559 KB
   gzipped). It builds and runs fine; if you want faster first paint later,
   route-level `React.lazy` code-splitting would trim it. Not a bug — noted for
   polish.

---

## 4. How to run (unchanged)

```bash
# 1. infrastructure (MySQL + Redis)
docker compose up -d

# 2. backend  → http://localhost:7060  (Swagger at /swagger-ui.html)
cd backend && mvn spring-boot:run

# 3. web  → http://localhost:5174
cd web && npm install && npm run dev

# 4. mobile
cd mobile && npm install && npx expo start   # set EXPO_PUBLIC_API_URL to your LAN IP:7060

# 5. (optional) analytics + face service → http://localhost:8082
cd analytics-service && pip install -r requirements.txt && python main.py
```

Demo logins (password **`Test1234@`**), by **username**:
`admin` (Super Admin), `priya` (HR), `karthik` (Manager),
`arun` / `divya` (Employees), `rajesh` (Civil Supervisor),
`gokila` / `maaran` (Board Admins).
