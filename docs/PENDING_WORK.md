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

## Still pending — tech-admin Users page (raised 13 Aug, not started)

The page at `/tech-admin/users` needs five separate things. Taking them in the
order they matter:

1. **Credentials must not be shown for most companies.** Today the table has a
   PASSWORD column for everyone. For every tenant except Pixous Technologies,
   list only that company's **company admin** — name, email, status, with edit
   and delete — and no login details for their HR, team leads or employees. A
   technical admin has no business reading another company's staff passwords,
   and the column makes it look like part of the job.
2. **Pixous Technologies keeps the full view** — the current list with
   credentials and every column.
3. **Role counts must be real.** HR Managers / Team Leads / Employees all read
   0 while the company has staff. They should count real rows, per company, and
   update when a company admin adds or removes someone — same treatment the
   dashboard tiles got.
4. **Adding a company admin must produce a working login immediately.** Today
   creating one does not reliably give a usable account.
5. **The page's own styling is wrong** — a background image and washed-out text
   that no other tech-admin page uses. It should match Dashboard and Companies.

### The one cause behind 3, 4 and 5

`GET /api/users` is what this page reads, and a technical admin gets nothing
back from it. They are allowed to call it — the `@PreAuthorize` includes
`hasRole('TECHNICAL_ADMIN')` — but the Hibernate tenant filter narrows the query
by `SecurityUtils.currentCompanyId()`, and a technical admin has no company by
design. Null tenant, no rows.

That is why the table is empty and all three role counts read 0, while the
dashboard beside it correctly reports 61 active employees: the dashboard uses a
different endpoint that does not go through that filter.

So the fix is one change, not three: let this endpoint take an explicit
`companyId` when the caller is a technical admin, and scope to that instead of
to their own (absent) tenant. Counts, the admin row, and provisioning all read
the same list, so all three follow from it.

**Treat this as high-risk work.** It touches tenant scoping, which is where the
V94 mistake in this project came from — a change that looked right and quietly
hid every holiday and leave type from all 62 users. Whatever is written here,
verify by calling the endpoint as a technical admin for two different companies
and confirming each sees only its own rows, before trusting it.

Do not widen the filter globally to "null tenant sees everything". That would
make every tenant-scoped query in the application leak across companies for any
caller without a tenant. Scope this endpoint explicitly.

## Still pending — chatbot ignores module settings (raised 13 Aug)

The HR assistant answers about modules the company has switched off. With
payroll off it still replies "View and download your monthly payslips under the
'Payslips' menu" — pointing at a menu that is no longer there.

It should answer only about enabled modules, and say plainly that a feature is
not available otherwise rather than giving directions to a page that does not
exist.

Found it: `ChatbotService.java` around lines 600-615 answers from a hardcoded
if-chain — `if (leave) return "..."; if (pay) return "...";` — with no reference
to module settings anywhere. The same chain is repeated per language (English,
Tamil, Hindi and others), so each one needs the same guard; fixing only the
English branch would leave the others wrong.

`CompanyModuleRepository.findByCompanyId(SecurityUtils.currentCompanyId())` is
the query to use — the same one behind `GET /api/my-modules`. Resolve the
enabled set once per request and skip the branches whose module is off, and give
the greeting the same treatment: it currently lists leave, attendance, payslips
and assets regardless.

When a feature is off, say it is not available rather than falling through to
the generic reply — otherwise someone asking about payroll gets a non-answer and
tries again.

## Still pending — HTTPS (raised 13 Aug)

Calls do not work because browsers only grant microphone and camera on a secure
origin, and the portal is served over plain HTTP at an IP address. Chat and
messages are unaffected.

`docker-compose.prod.yml` and `web/nginx.conf` already carry the HTTPS blocks,
commented out, for `employeeportal.pixoustech.app`. The remaining work is a DNS
A record, a certbot certificate, uncommenting those blocks, and pointing
`APP_CORS_ALLOWED_ORIGINS` at the https host.

Worth doing for its own sake, not only for calls: passwords, payslips and
identity documents currently cross the network unencrypted.

## Still pending — Audit Logs shows nothing (raised 13 Aug)

`/tech-admin/audit-logs` reads "Real-time monitoring of how employees, HRs and
Team Leads are utilising platform modules" and then shows an empty table. It is
not a bug in that page: **nothing records module usage**, so there is nothing
for it to read.

What is wanted there: who used which module, how many of each role used it, and
which modules are used most.

Confirmed by reading the code: `TechnicalAuditLog`, `TechnicalAuditLogRepository`
and `TechnicalAdminAuditController` all exist and the page reads them correctly —
but a search for anything constructing a `TechnicalAuditLog` finds nothing. The
table is empty because no code has ever written a row. (`AuditLog.java` beside it
is a deliberately empty file, replaced by this one.)

So the read side is already built. Only the write side is missing.

Two separate things are wanted, and they are not the same size:

1. **Technical-admin actions** — who toggled a module, edited a company, created
   or deleted a user. Small: those all pass through a handful of controllers in
   `modules/admin`, and `TechnicalAuditLog` already has the columns for it
   (adminId, action, entityType, oldValue, newValue, ipAddress). A day's work,
   and it makes the existing page real.

2. **Employee module usage** — which modules staff actually open, how many of
   each role, which are used most. Larger, and needs its own table; see below.

Do (1) first. It fills the page that exists, using a table that exists.

For (2), the recording step:

- a `module_usage` table — user, company, module code, action, timestamp; the
  session-duration column the page already renders needs a start/end or it
  should be dropped from the table rather than left blank
- something that writes a row. An interceptor over the module routes is less
  invasive than touching each controller, and cheaper to remove if it turns out
  to cost too much
- aggregation endpoints for the counts, grouped by role and by module

Two cautions. Writing a row per request against a database that allows twenty
connections in total needs batching or async writes, or the tracking becomes
the outage. And the table grows without limit — decide the retention window
when you create it, not after it is large.

Until any of that exists, the honest thing on that page is to say usage
tracking is not switched on, rather than show an empty table under a heading
that promises real-time monitoring.

## Still pending — Branding & Appearance (raised 13 Aug)

`/tech-admin/branding` should let a technical admin choose the look for a
tenant, and change it per module. Scope, as the user narrowed it: **about twenty
presets, not a hundred** — enough to pick from without becoming a design tool.

- roughly 20 ready-made looks (colour set, type, and a header image or pattern)
- editable text: product name, greeting, empty-state and login wording
- colours: accent, surface and text, with a live preview beside the choice
- per-module overrides, so one module can differ from the company default

Where it goes: the same `company_modules.featureFlags` JSON that custom modules
already use will hold a per-module override, and the company-wide default wants
a column or a settings row of its own. No new table is needed for the module
side.

Do the company-wide default first and get it rendering; per-module overrides on
top of it are a smaller step once the first one works, and shipping the default
alone is already useful.

## Still pending — raised 13 Aug, later batch

**Reload signs a technical admin out.** Pressing the browser reload button on
`/tech-admin/dashboard` returns a page that is still nominally signed in but has
Total Companies 0 and an empty Company Information panel — the company list
failed to load and the session did not survive. Start at
`TechAdminAuthContext`'s bootstrap: the sign-in stores an access token with `""`
for the refresh token, so anything that re-validates on mount has nothing to
refresh with. Highest priority of this batch: it makes the whole section feel
unreliable.

**Company admin created but counts do not move.** After creating a company admin
the role counts stay put, and the counts shown do not match the company selected
in the header. The directory now loads (the placeholder company name is fixed),
so this is the next layer: check that the create call refetches, and that the
counts follow `selectedCompanyFilter` rather than the logged-in tenant.

**Technical-admin login page.** Remove the control top-right, add a sound on/off
button, and put a video behind the form. The file the user supplied is at
`C:\Users\balas\Downloads\Use_the_uploaded_image_as_the (3).mp4` — it needs
copying into `web/public` first; a path outside the project will not survive a
build. Autoplay only works muted, so the sound toggle and the video have to be
wired together, and `prefers-reduced-motion` should fall back to a still.

**Branding & Appearance.** `/tech-admin/branding` currently renders the
technical-admin profile and MFA settings, not branding at all — and
`Branding.tsx` does not exist, so the sidebar entry lands on whatever the router
points at. What is wanted: about 20 ready-made looks, about 20 font pairings,
and per-module colour and font overrides that can be added and removed. Build
the company-wide default first; per-module overrides sit on top of it, in the
`company_modules.featureFlags` JSON that custom modules already use.

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
