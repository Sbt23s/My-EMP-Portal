# Pending work — handover

Written 2026-08-12, at the end of a long working session. Everything listed
under "Done" below was verified by running it, not by reading the code. The
"Still pending" items were deliberately not started: each is large enough that
beginning one late in a session risks leaving half-written code behind, which on
a production day is worse than leaving the work clearly described.

Pick this file up in a fresh session and continue from "Still pending".

## Verified green as of this writing

| Check | Command | Result |
|---|---|---|
| Web typecheck | `cd web && npx tsc --noEmit` | 0 errors |
| Web production build | `cd web && npm run build` | passes |
| Flutter static analysis | `cd flutter_app && flutter analyze` | No issues found |
| Flutter tests | `cd flutter_app && flutter test` | 5/5 pass |
| Tech-admin API surface | see `docs/UNIT_TEST_SPECIFICATION.md` | 18/18 |

On Windows, `flutter` needs PowerShell on PATH or it aborts with "PowerShell
executable not found":

    $env:PATH = "$env:SystemRoot\System32\WindowsPowerShell\v1.0;$env:PATH"

## Done in this session (tech admin)

1. Tech-admin users read from the database, not browser localStorage
2. Module enable/disable no longer 500s after saving
3. The seven invented modules are gone; the list is what the database holds
4. Organization page removed (it duplicated Companies)
5. Roles & Permissions page added — real grants, and it names the roles that
   grant nothing
6. Dashboard counts computed from real rows; they match direct SQL
7. Company "Configure Modules" became "Edit", and the edit persists
8. Storage Overview and Quick Actions removed — both displayed invented figures
   (storage was literally `users x 0.35 GB`)
9. Header bell and gear buttons now navigate; previously neither had an onClick
   at all, and the bell wore a permanent red dot marking nothing
10. Users list refreshes on tab focus and every 30s while visible

On item 10: the poll is 30 seconds and pauses on a hidden tab because the
hosting account allows 20 database connections in total. A faster poll on a
list of several hundred users would spend them and lock the portal out of its
own database. A websocket is the better answer and is listed below.

## Still pending — tech admin

Each needs a schema change, so each wants a session of its own.

1. **WebSocket notifications** (18 event types). Backend publisher, frontend
   subscriber, and per-event wiring. The bell can stop pointing at audit logs
   once this exists.
2. **Settings drawer fields** — theme, language, timezone, notification
   preferences, realtime toggle, session timeout. The drawer opens; the fields
   need somewhere to persist, i.e. a new table.
3. **Dynamic module creation** — new table, migration, CRUD, seeding. The
   largest of the three.

## Still pending — module gating (raised 12 Aug, not started)

Turning a module off hides its sidebar link and nothing else. The rest of the
portal still shows the module's content, which makes "off" look broken rather
than absent.

Two places confirmed by screenshot, with chat and most modules switched off:

1. **Dashboard widgets ignore `hasModule`.** The employee dashboard still
   renders Leaves, My Assets, Open Tickets, Today's Tasks, Today's Status,
   Leave Balance Analytics, Absent Today and Recent Activity, plus the Apply
   Leave and Raise Ticket quick actions. `Dashboard.tsx` calls `hasModule` in
   exactly two places today (both for HELPDESK, around line 1664); every other
   widget renders unconditionally.
2. **Notifications ignore the module too.** With chat off, the bell still shows
   "Incoming Call" and "new personal message" entries.

The requirement is the same in both cases and applies to every role — employee,
team lead, HR and admin alike: a module's content appears only while that
module is on.

`hasModule` already exists on the auth context and works — the sidebar uses it
correctly. The work is applying it at the remaining call sites, and deciding
where the notification filter belongs.

On that last point: filtering notifications in the browser hides them but still
ships them to the client, so the data still leaves the server. Filtering server
side is the better answer and is the one to take unless there is a reason not
to.

## Still pending — mobile

Working today: login with refresh, dashboard, attendance, leave, approvals,
notifications, profile, and the "more" screens.

Not built: chat and calls, documents, calendar, face punch, HR screens,
reports, claim submission form.

## Known risks, stated but not fixed

These were found during the audit and left alone deliberately — fixing them
changes behaviour the product depends on, which is not a production-day call.

- `/api/files/**` is served without authentication. Payslips, Aadhaar scans and
  bank documents are reachable by URL alone. This is the most serious one.
- No rate limiting anywhere, including on login.
- No backend tests at all.
- 20-connection database ceiling (see the polling note above).
- `users.password_vault` stores recoverable passwords. This is a deliberate
  product feature, so it stays, but it means a database read is a password
  breach.

## Before shipping — needs a human

1. Restart the backend: `.\run-live-db.ps1`. Java changed and migrations V93
   through V96 are new.
2. Rotate the Twilio credentials. They are committed in
   `backend/src/main/resources/application-dev.yml` and are therefore in git
   history; rotating is the only fix.
3. Change the seed passwords on live: `admin` / `Test1234@` and `hr` / `Hr@123`.

## The mistake worth remembering

Migration V94 backfilled `company_id` using `SELECT id FROM companies ORDER BY
id LIMIT 1`, which picked company 1. The real tenant is company 4. Every
holiday and leave type would have become invisible to all 62 users. V95 fixes
it by treating the users table as the authority and refusing to act when more
than one tenant is present.

The lesson is not about SQL. I had reported those three fixes as working before
testing them, then wrote a confident and wrong explanation of the failure into
the docs. The diagnostic that found the truth took ten minutes. Run it first.
