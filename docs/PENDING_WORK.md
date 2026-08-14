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

## Still pending — Branding, the half that is not built (14 Aug)

`/tech-admin/branding` now exists and works as a chooser: twenty colour themes,
twenty font pairings, a company default, and per-role and per-module overrides
that layer over it. It saves to `company_modules.featureFlags` under the
BRANDING code as `{ base, roles, modules }`.

**The portal does not read any of it yet.** That is the remaining work, and it
is the larger half:

1. **Resolve and apply.** At `AppLayout`, read the saved document, resolve
   module override → role override → company default for the current page and
   user, and write the result to CSS custom properties on the layout root.
   Components then follow by using those variables instead of fixed classes —
   which is the part that touches many files, and should be done a screen at a
   time rather than in one sweep.
2. **Preview the real screen.** The preview pane shows one generic dashboard
   card whatever module is selected. It should show that module's actual
   layout. Practical approach: render the real component inside a scaled,
   non-interactive container with the resolved variables applied, rather than
   maintaining a second set of mock screens that will drift.
3. **Company switcher on the page.** Scope currently follows the company chosen
   in the header. A list on the page itself was asked for; the header switcher
   already does this, so decide whether a second control earns its place before
   adding it.

Order matters: (1) first. Until the portal reads the settings, the preview is
the only place branding exists, and improving the preview would make it look
finished when nothing downstream has changed.

## Still pending — per-company roles (14 Aug)

Asked for: an "Add Role" button creating roles for one company.

Two things block it, and the second is the serious one:

- No create endpoint exists. `TechnicalAdminRoleController` is `@GetMapping`
  only, and nothing anywhere POSTs a Role.
- **`Role` has no `companyId`.** Roles are platform-wide, shared by every
  tenant. Per-company roles are not something the schema supports today.

So this needs a `company_id` column on `roles`, a migration, and every role
lookup made tenant-aware. That last part is tenant scoping — the same area V94
broke, hiding every holiday and leave type from all 62 users. Whoever picks
this up should verify by listing roles as two different companies and
confirming each sees only its own, before trusting any of it.

The roles page meanwhile shows the five roles a company staffs, with the
thirteen industry variants behind a "Show all" toggle and still reachable by
search.

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

## Done — Branding now reaches the portal (14 Aug)

All three items from the previous section are built.

**1. The portal reads the settings.** `GET /api/my-modules` returns the company's
branding document alongside its module list — same request, because both are
needed before the first screen can be drawn and a second request would show the
colour arriving late, as a flash. `AuthContext` parses it and exposes
`branding`; `useBranding`, mounted once in `AppLayout`, resolves
`module → role → company` and writes the result to the document.

Resolution is per property, not per object: an override that sets only a colour
keeps the company's font rather than falling back to the platform default for
everything it did not mention.

Two mechanisms, deliberately:

- **Colour** goes on the root element as an inline custom property, which
  outranks both `:root` and `.dark` without either having to know branding
  exists. Written as an HSL triple (`243 75% 59%`), not a hex — Tailwind
  composes these tokens with an alpha channel, and a hex in one of those
  variables produces no colour at all, silently.
- **Type** needs real rules, so a single `<style id="hrp-branding">` element is
  rewritten in place. No `!important`: it is appended after the compiled
  stylesheet, so at equal specificity it already wins.

**The page background is deliberately left alone.** Each person chooses light or
dark for themselves in the top bar, and a company theme overruling that would
take away a setting somebody made on purpose. The accent — the part that reads
as "our colour" — carries into both. The dark themes in the list (Midnight,
Carbon, Obsidian, Deep Ink) therefore contribute their accent, not their
surface.

**Timing.** Branding refreshes with the module list: on sign-in, and whenever the
tab is returned to. Someone with the portal already open sees the new colour when
they come back to it, not mid-keystroke.

**2. Per-module previews.** `BrandingPreview` renders the shape of the module in
view — table, request list, board, calendar, chat, dashboard. Not the real
component: those pages fetch as the signed-in employee, and a technical admin has
no company of their own, so every one of them would render as a spinner or an
error panel — a preview of the colour of nothing. They also expect a router and a
query client this area does not have. Shapes answer the actual question (does
this accent work on this kind of page) without a pixel copy that quietly stops
matching the day someone edits the real screen.

**3. Company list.** On the page, above the scope chips. Switching is blocked
while there are unsaved changes — a colour saved against the wrong tenant is not
something you find out about here, you find out when someone else's portal
changes.

### One trap worth remembering

Saving branding writes a `company_modules` row. `configured` in `/my-modules` had
to start excluding it, or choosing a theme for a company that had never opened
the module screen would answer "configured, nothing enabled" and empty that
company's portal for all of its people. Picking a colour must not be able to
switch off a company.

### Not carried over

- The **mobile app** does not read branding. It has its own theme; nothing there
  changed.
- The **sign-in page** is unbranded on purpose — nobody has said who they are
  yet, so there is no company whose colours apply.
- `THEMES` and `FONTS` now live in `web/src/lib/branding.ts` and are imported by
  both sides. Add a colour there, never in the page.

## Done — company admin = system admin, and usage tracking (14 Aug)

**The role bug that started it.** The tech-admin create form offered one option,
Company Admin, but the state it was bound to started at `"EMPLOYEE"`. A controlled
`<select>` whose value matches no option still displays the first one, so the form
read "Company Admin", nobody touched it, no change event fired, and the account
was created as an employee. Both catalogue and default now come from
`CREATABLE_ROLES`, so they cannot drift again.

Also fixed on that screen: the edit modal read `user.role` (the server sends
`roles`, a list) so it always held undefined; the Status column read `u.status`
which does not exist; the password column compared a hard-coded company name
against `roles[0]`; and the role badge printed "EMPLOYEE" for an account with no
role at all — a guess presented as a fact, and the reason a broken account looked
like ordinary staff.

**The deadlock.** Restricting the table to administrators meant an account created
with the wrong role could not be seen here, so the screen that made the mistake
was the only screen that could not fix it. There is now a count of what is being
left out, a "Show them" toggle, and a one-click **Make Company Admin** on
non-admin rows. Passwords stay hidden for non-administrators either way.

**COMPANY_ADMIN and SUPER_ADMIN are now one role.** They already held an identical
permission set (V96), but a dozen places asked `hasRole("SUPER_ADMIN")` by name and
against those the company's own administrator was silently an ordinary employee.
Aliased in one place per side rather than by editing each check:

- web: `AuthContext.hasRole` treats COMPANY_ADMIN as satisfying SUPER_ADMIN.
- backend: `LeaveService.leaveHasRole`, `PermissionService.hasRole`,
  `CommunityService.isAnnouncementRole`.

Consequences worth knowing: leave routing now escalates to the company admin as
"Admin" (before, a company whose admin was its only admin had leave nobody could
approve), and they can post company announcements.

**Deliberately NOT aliased:** `/admin/reset` (Fresh Start) stays SUPER_ADMIN only,
enforced by a new `hasRoleExact` used by `RoleGuard`'s `role` prop. Same access is
not a reason to hand a delete-everything button to more accounts.

**No cross-tenant risk from any of this.** `TenantFilterAspect` scopes every query
by the `company_id` on the signed-in principal, and `User` carries the filter, so
which company somebody can see is decided by their account and never by their
role. Two companies' administrators stay as separate as they were.

**Usage tracking — who used what, for how long.** `UsageTracker`, a
`HandlerInterceptor` (not a filter: interceptors run after the security chain, so
the signed-in person is already known), maps request path to module code and
records a row. Read at `GET /api/technical-admin/audit-logs/usage`, shown on the
Audit Logs page.

Three constraints, all about not becoming the problem:

- **No migration.** Rows reuse `technical_audit_logs`, which already has company,
  person, action and timestamp. The hosting account allows twenty connections
  total; a migration against a live database with sixty-two people in it is worth
  it only when there is no alternative.
- **Throttled to one row per person per module per ten minutes**, decided in
  memory before any database work. A single dashboard fires a dozen requests.
- **Never fails the request.** Recording that somebody opened the leave page must
  not stop them opening it.

Time is inferred, not measured: first touch to last touch per day, summed. The
field is `activeMinutes` and the UI says so in as many words — a duration shown
without that reads as a stopwatch, and somebody will make a decision on it.
Measuring properly needs a heartbeat from every open tab.

`POST /technical-admin/companies/{id}/admins` used to answer `200 "Company admin
created successfully"` while creating nothing — it never read the payload. It now
answers 410 naming the endpoint that works.

### Still open

- Usage rows only exist from deployment onwards. Earlier activity was never
  stored, and the empty state says so rather than implying nobody works there.
- Module mapping in `UsageTracker.moduleFor` is a prefix list. A new API path
  needs a line there or its module reports nothing — silently.
