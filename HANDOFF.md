# Handoff — Pixous HR Portal

Everything the next person (or agent) needs to pick this up without breaking it.
Read the whole file before running anything: two of the notes below are the
difference between a working portal and one that cannot reach its own database.

Repository: `https://github.com/PixoustechIndia-org/HR-portal.git`, branch `main`.

---

## 1. The one thing that is NOT in git

`backend/.env` holds the live database password and is git-ignored on purpose.
**Cloning the repository does not give you a working setup** — recreate it first:

```
backend/.env
------------
DB_HOST=mysql1002.site4now.net
DB_PORT=3306
DB_NAME=db_ab2fe4_ems
DB_USER=ab2fe4_ems
DB_PASSWORD=<ask the owner>
DB_PARAMS=useSSL=false&serverTimezone=Asia/Kolkata&allowPublicKeyRetrieval=true
DB_POOL_MAX=6
DB_POOL_MIN=1
REDIS_HOST=localhost
REDIS_PORT=6379
```

`DB_PARAMS` is spelled out because the default in `application.yml` includes
`createDatabaseIfNotExist=true`, which asks the server to `CREATE DATABASE` on
every connect. This hosting account has no such privilege, so **the application
refuses to start against a database that already exists and is perfectly healthy**,
and the error names the database, so it reads as though the database is missing.

---

## 2. Running it

```powershell
.\start-local.ps1 -LiveDb    # the real company database
.\start-local.ps1            # a throwaway local database instead
```

Starts, in order: Docker Desktop, MySQL + Redis + Kafka containers, the backend,
the Python analytics service, the web app. Both the backend and the analytics
service are handed the same database from one place — they used to be told
separately, which is how the dashboard ended up showing a different set of
employees from the rest of the page.

| Piece | Address |
|---|---|
| Portal | http://localhost:5174 |
| Backend | http://localhost:7060 |
| Analytics (optional) | http://localhost:8082 |
| Local MySQL | localhost:**3307** (not 3306 — another project owns that) |
| Redis | localhost:6379 |

Logins: `hr` / `Hr@123`, and `admin` (ADM0001) whose password the owner holds.

**Back up before anything destructive.** No undo exists:
```powershell
.\deploy\live-dump.ps1        # writes to %USERPROFILE%\hr-portal-backups
```

---

## 3. Things that will bite you

**The database allows 20 connections in total.** Across everything — this backend,
a second copy of it, a laptop running it, an open mysql client. `DB_POOL_MAX=6`
leaves room for a deploy that briefly runs two instances.

**Never run the backend against the live database with DevTools restart on.**
Every restart builds a new connection pool, and a pool whose context failed to
start is not always closed. The server's `interactive_timeout` is eight hours, so
nothing clears them: an afternoon of editing left the portal unable to connect at
all, and it took killing the sessions by hand to recover. `start-local.ps1`
sets `SPRING_DEVTOOLS_RESTART_ENABLED=false` for exactly this reason.

**`wait_timeout` on that server is 30 seconds.** Hikari's `max-lifetime` is set to
30000 and `idle-timeout` to 10000 to stay inside it. Raising them brings back
"Failed to validate connection" on every housekeeping pass.

**Never edit a migration that has already run.** Flyway compares checksums and the
application will not start. Corrections go in a new `V<n+1>__*.sql`. Current
schema: **v90**.

**Do not run `vite build` while `npm run dev` is running.** They share one esbuild
service and the build kills it, which looks like a mysterious dev-server failure.
Stop the dev server, build, start it again.

**Never run `docker system prune --volumes`.** It destroys the local MySQL volume.

**Docker Desktop on the owner's machine stops by itself, repeatedly.** When it
does, Redis disappears; the portal keeps working and caches in process memory
instead, and says so in the log. That is by design, not a fault to chase.

**PowerShell 5.1 quirks that have already cost time:** a native command's stderr
becomes a terminating error under `$ErrorActionPreference = "Stop"` (the scripts
use `Continue`); `Set-Content -Encoding utf8` writes a BOM, and `javac` rejects a
source file that starts with one; `@("x") | ConvertTo-Json` produces a bare string,
not a one-element array.

---

## 4. State of the data, as of this handoff

The live database has **6 user accounts**. The 58 employees from the original
Excel import were deliberately removed at the owner's request so a fresh sheet
could be imported; a backup was taken first and is in
`%USERPROFILE%\hr-portal-backups\db_ab2fe4_ems-before-import-wipe-*.sql`.

Everything else was left alone and verified afterwards: 13 teams, 6 departments,
2 offices, 14 roles, 22 permissions, 21 settings, 10 holidays, 7 leave types,
3 shifts. Attendance, payslips and leave are empty because none has happened yet.

Remaining accounts: `admin` (ADM0001, SUPER_ADMIN), `gokila` (BADM001,
SUPER_ADMIN), `hr` (HR0001), `priya` (EMP0004), `rajesh` (EMP0005), `arun`
(EMP0001, OFFBOARDED so it does not appear in the employee list).

---

## 5. Performance — what was measured and changed

Both causes were measured, not guessed. If either regresses, these are the numbers
to compare against.

**Database.** `User.roles` is EAGER and `Role.permissions` is EAGER, so loading any
list of people fetched each person's roles in its own query and each role's
permissions in another:

| Endpoint | Before | After |
|---|---|---|
| `GET /users?size=1000` | 112 selects, 40.2 s | 4 selects, 8.9 s |
| `GET /dashboard/org-insights` | 205 selects, 11.6 s | 6 selects, 5.6 s |
| `GET /attendance/today` | 754 selects, 7.6 s | 2 selects, 4.4 s |

Fixed with `hibernate.default_batch_fetch_size=50` in `application.yml` —
configuration only, no code path behaves differently. **Do not "improve" this by
marking those associations LAZY**: `open-in-view` is disabled, so every place that
touches roles outside its transaction would start throwing
`LazyInitializationException`, and those places are not obvious from the code.

**Bundle.** Was one 2,445 KB JavaScript file, so the login page downloaded the
payroll screens, the chat client, the spreadsheet writer and the charting library
before the form appeared. Routes are now `React.lazy` with a Suspense boundary
each (`web/src/routes/router.tsx`): 757 KB initial, 98 chunks, xlsx (419 KB) and
recharts (368 KB) only when a screen that uses them opens.

`ModulePlaceholder` is a **named** export and is mapped for `React.lazy`
accordingly. Getting that wrong fails only when the route is opened.

---

## 6. Known, not yet done

Ranked by how much they matter.

1. **HTTPS on the public site.** Browsers refuse camera, microphone and location
   over plain HTTP. A punch requires a face, so without HTTPS **nobody can punch
   in at all**. This is a hosting setting, not code.
2. **Where uploaded files live.** Selfies, registered faces and attachments are
   files on disk, not rows. On a free hosting plan they are erased on every
   redeploy while the database rows go on pointing at them. Needs a persistent
   disk or object storage.
3. **The dashboard fetches `/users` five times on one page**, under five different
   React Query keys (`dash-list`, `ticket-industry`, `task-assign-emps`,
   `dash-task-active-emps`, `recent-users`). They are not simply duplicates —
   each carries a different filter (`status=ACTIVE`, `industry=…`), so merging
   them naively would change what the page shows. Worth doing carefully.
4. Rotate the two credentials already in git history: the MySQL password that was
   in `backend/.env.example`, and the Twilio SID/token in
   `application-dev.yml`.
5. The larger attendance brief the owner asked for and has not received: team
   leader live dashboard, break in/out, attendance health score, correction
   request flow, KPI cards and heatmaps.
6. Payroll and onboarding bugs B1–B15 were catalogued and deliberately left alone
   — the owner asked for ideas at the time, not changes.

---

## 7. How the owner works

Requests arrive in Tanglish, quickly, often mid-task and with annotated
screenshots. The standing instructions, in their words: *"ethum damage/crash
pannama pannu"* — break nothing else; *"naa sonnatha mattum sei"* — only what was
asked; *"ethum delete pannama"* — ask before deleting. Explain in Tanglish, and
report bugs found along the way rather than quietly working around them.

Deployment target is Windows hosting for the web app plus Render for the Java
backend — shared Windows hosting cannot run a JVM. See `docs/WINDOWS_HOSTING.md`
and `render.yaml`, which carries the exact connection URL to paste and a warning
about the flag that stops it starting.
