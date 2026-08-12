# Running the portal on your own machine, with the live data

Everything below runs on your laptop. The live server is only ever read from —
once, to copy a database dump down — and nothing here can change it.

## What you need installed

| Tool | Why | Check |
|---|---|---|
| Docker Desktop | runs MySQL | `docker --version` |
| Java 17 (JDK) | runs the backend | `java -version` |
| Maven | builds the backend | `mvn -version` |
| Node 18+ | runs the web app | `node --version` |

---

## 1. Start the database

```powershell
cd c:\Users\balas\Documents\GitHub\hr-port
docker compose up -d
```

That starts MySQL on port 3306 with an empty database called `hr`, and Redis
(which the app does not need but does not mind). The password is `root123` — the
same default the backend expects, so nothing has to be configured.

Check it came up:

```powershell
docker ps
docker logs hrportal-mysql-local --tail 20
```

---

## 2. Get a copy of the live database

The server writes a full dump **before every deployment**, so a recent one is
already sitting there. Nothing needs to be run on the live database to make one.

On the server:

```bash
ls -lh ~/backups/hr-predeploy-*.sql.gz | tail -5
```

Copy the newest down to your machine (run this **on your laptop**):

```powershell
scp -i C:\path\to\your-key.pem ubuntu@13.127.214.21:~/backups/hr-predeploy-20260803-041500.sql.gz C:\Users\balas\Downloads\
```

> If there is no recent backup, make one without disturbing anything — a dump
> only reads:
> ```bash
> docker exec hrportal-mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --quick --routines --events "$MYSQL_DATABASE"' | gzip > ~/backups/manual-$(date +%F).sql.gz
> ```
> `--single-transaction` is what keeps it from locking anything while it runs.

---

## 3. Load it in

```powershell
.\deploy\local-restore.ps1 -Dump C:\Users\balas\Downloads\hr-predeploy-20260803-041500.sql.gz
```

It drops the local `hr` database, recreates it, streams the dump in, and then
tells you how many tables and employees arrived. It never touches the server.

---

## 4. Start the backend

```powershell
cd backend
mvn spring-boot:run
```

It comes up on **http://localhost:7060**. On first start Flyway brings the
restored schema up to whatever the code now expects — the dump is from whenever
the backup was taken, so this step is normal and expected.

Watch for `Started HrPortalApplication` in the output.

---

## 5. Start the web app

In a second terminal:

```powershell
cd web
npm install     # first time only
npm run dev
```

Open **http://localhost:5174**. Requests to `/api` and `/ws` are proxied to the
backend on 7060, so nothing needs configuring.

Log in with the same username and password you use on the live portal — the
accounts came down with the dump.

---

## What does *not* come down with the dump

The dump is the database only. These live in a Docker volume on the server, so
they are missing locally until copied:

- profile photos
- chat attachments, voice notes and task files
- generated payslip PDFs and QR codes

The app handles their absence gracefully — a photo falls back to initials, a
missing file gives a broken link. To bring them too, on the server:

```bash
docker run --rm -v hr-portal_backend_storage:/data -v ~/:/out alpine tar czf /out/storage.tar.gz -C /data .
```

then copy `storage.tar.gz` down and unpack it into `backend/storage/`.

---

## Things worth knowing

**Local is genuinely separate.** After step 3 the two databases drift apart
immediately. Anything you do locally stays local; anything anybody does on the
live portal does not appear locally. Re-run step 3 whenever you want a fresh copy.

**HTTPS works locally for free.** `http://localhost` counts as a secure context
in every browser, so the camera, microphone and GPS all work on your machine —
video and audio calls, voice messages and GPS punch-in included. Those are the
features that cannot work on `http://13.127.214.21` and this is the easiest way
to test them.

**SMS is best left off.** Set `FAST2SMS_ENABLED=false` before starting the
backend unless you want local testing to spend real credits and text real people:

```powershell
$env:FAST2SMS_ENABLED = "false"
$env:TWILIO_ENABLED = "false"
cd backend; mvn spring-boot:run
```

**Do not point the local backend at the live database.** It would work, and the
first thing it would do is run Flyway migrations against production. Restore a
dump instead — that is what step 3 is for.

**Scheduled jobs run locally too.** Chat retention housekeeping, due-date
reminders and the daily work-report nudge all fire on a local run. They act on
the local copy, so this is harmless, but it does mean notifications appear.

---

## If something goes wrong

| What you see | What it means |
|---|---|
| `Cannot talk to Docker` | Docker Desktop is not running |
| `Communications link failure` on backend start | MySQL is not up yet — wait, then retry |
| `Table 'hr.users' doesn't exist` | The dump did not load; re-run step 3 and read its output |
| Flyway `checksum mismatch` | The dump is older than a migration that was edited. Ask before touching migration files — a mismatch on the server stops the app from starting |
| Login says wrong password | The dump predates the account. Use one that existed when the backup was taken |
| Port 3306 already in use | Another MySQL is installed locally. Stop it, or change the port in `docker-compose.yml` and set `DB_PORT` |
| Port 7060 or 5174 in use | Something else is on it — `netstat -ano | findstr 7060` |
