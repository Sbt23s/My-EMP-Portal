# Deploying the HR Portal on Windows hosting

## Read this part first

The portal is four pieces, and they do not all run in the same kind of place:

| Piece | What it is | Runs on Windows shared hosting? |
|---|---|---|
| Web app | React, built to plain HTML/JS/CSS | **Yes** — it is only static files |
| Database | MySQL | **Yes** — you already have one |
| Backend API | Spring Boot, **Java 17** | **No** — shared Windows plans have no JDK and will not keep a JVM running |
| Analytics | Python, optional | **No** — and the portal works without it |

So the question is only ever *where the Java backend runs*. Everything else goes
on your Windows hosting.

**Which do you have?** Log in to your hosting control panel and look:

- You see file manager, databases, email, SSL — and **no** "Connect by RDP" or
  "Reboot server" → **shared hosting** → follow **Plan A**.
- You can RDP into a Windows desktop and install software → **VPS or dedicated**
  → follow **Plan B**, and everything lives on that one machine.

---

# Plan A — web app on your hosting, backend on Render

The most common setup, and the least work. Your hosting serves the site and holds
the database; a free Render service runs the Java backend.

## A1. Allow the database to be reached from outside

Your MySQL currently only accepts connections from inside the hosting network.
Render is outside it.

1. Control panel → **MySQL / Databases**
2. Find **Remote Access**, **Remote MySQL**, or **Allow external connections**
3. Enable it. If it asks which addresses, and Render's are not knowable in
   advance, allow all (`%`) — the account is password-protected.
4. Write down, exactly:
   - host, e.g. `mysql1002.site4now.net`
   - port, usually `3306`
   - database name, e.g. `db_ab2fe4_ems`
   - username, e.g. `ab2fe4_ems`
   - password

> **Check the size limit on your plan.** Shared MySQL is often capped at a few
> hundred MB. The portal's chat carries attachments *paths*, not the files, so the
> database itself stays small — but watch it.

## A2. Load the schema and data

If this is a fresh database, the backend creates every table itself on first
start (Flyway). Nothing to do — skip to A3.

If you are moving from the existing server, restore a dump into it:

1. Get a dump — Actions → **Download a database dump** → run → download the
   artifact (see [LOCAL_SETUP.md](LOCAL_SETUP.md)).
2. Control panel → **phpMyAdmin** → your database → **Import** → choose the
   `.sql` file (unzip the `.gz` first).
3. If the file is too large for phpMyAdmin, restore it locally first and then use
   MySQL Workbench to push it up — or ask your host to import it for you.

## A3. Put the backend on Render

The repository already contains [render.yaml](../render.yaml), so Render sets
itself up.

1. Go to https://render.com and sign in **with GitHub**
2. **New → Blueprint**
3. Pick the `PixoustechIndia-org/HR-portal` repository → **Connect**
4. It reads `render.yaml` and asks for four values:

   | Field | What to enter |
   |---|---|
   | `SPRING_DATASOURCE_URL` | `jdbc:mysql://YOUR-HOST:3306/YOUR-DB?serverTimezone=Asia/Kolkata&useSSL=false&allowPublicKeyRetrieval=true` |
   | `SPRING_DATASOURCE_USERNAME` | your MySQL username |
   | `SPRING_DATASOURCE_PASSWORD` | your MySQL password |
   | `APP_CORS_ALLOWED_ORIGINS` | your site address, e.g. `https://portal.yourdomain.com` — put it in now if you know it, or come back at A6 |

5. **Apply**. The first build takes 5–15 minutes; it compiles the backend.
6. When it finishes, copy the URL it gives you, for example
   `https://pixous-ems-backend.onrender.com`

**Confirm it is alive** — open in a browser:

```
https://pixous-ems-backend.onrender.com/actuator/health
```

`{"status":"UP"}` means it started and reached your database. Anything else:
Render dashboard → the service → **Logs**. `Communications link failure` means
A1 is not done, or the credentials are wrong.

## A4. Build the web app

On your own machine. The backend address is **baked into the build**, so this
must be right — it cannot be corrected by editing files on the server.

```powershell
cd c:\Users\balas\Documents\GitHub\hr-port
.\build-for-hosting.ps1 -ApiUrl https://pixous-ems-backend.onrender.com
```

It checks the backend answers, builds, and confirms the address really is inside
the bundle. Output: `web\dist\`.

## A5. Upload

1. Control panel → **File Manager** (or FTP with FileZilla)
2. Go to `wwwroot\` — this is what visitors see
3. Delete whatever placeholder page is there
4. Upload **everything inside `web\dist\`** — not the `dist` folder itself, its
   contents. That means:
   - `index.html`
   - `web.config` ← **without this, every page refresh gives a 404**
   - the whole `assets\` folder
   - `sw.js`, `registerSW.js`, `manifest.webmanifest`, the icons, `bg.mp4`

   Uploading a zip and extracting it on the server is much faster than sending
   hundreds of files over FTP, if your file manager can do it.

## A6. Point the two at each other

The browser blocks a page on one address from calling an API on another unless
the API says it is allowed.

1. Render → your backend service → **Environment**
2. `APP_CORS_ALLOWED_ORIGINS` = your exact site address, with `https://`, no
   trailing slash — e.g. `https://portal.yourdomain.com`
3. **Save**, which restarts it

Several addresses are separated by commas and no spaces:
`https://portal.yourdomain.com,https://www.portal.yourdomain.com`

## A7. Turn on HTTPS

1. Control panel → **SSL/TLS** → **Let's Encrypt** (free) → issue for your domain
2. Turn on **Force HTTPS** so `http://` redirects

This is not optional cosmetics. Without HTTPS the browser refuses to give the
page a camera, a microphone or a location — so **video calls, audio calls, voice
messages and GPS punch-in do not work at all**. With it, they do.

## A8. Check it

Open your site and work through this list:

- [ ] the login page loads
- [ ] you can log in
- [ ] the dashboard shows real numbers
- [ ] go to any page, then press **F5** — it must reload that page, not 404
      (a 404 here means `web.config` is missing or URL Rewrite is not installed)
- [ ] open Chat — a message sent from another browser arrives without refreshing
      (that is the WebSocket working)
- [ ] upload a profile photo and see it come back
- [ ] the address bar shows a padlock

## Things to know about Plan A

**The free Render plan sleeps.** After 15 minutes with no requests the service
stops, and the next request waits ~30–50 seconds while it starts. Everything
works, it is just slow first thing in the morning. Their paid tier removes it.

**Uploaded files live on Render's disk and do not survive a redeploy** on the
free plan. Photos and chat attachments will disappear when the backend
redeploys. If that matters, use a paid plan with a persistent disk, or move
storage to S3.

**Scheduled jobs need the backend awake.** Due-date reminders, the daily
work-report nudge and chat retention run on a timer inside the backend. A sleeping
free service does not run them on time.

---

# Plan B — everything on one Windows VPS

For a VPS or dedicated Windows server you can RDP into. All four pieces run here.

## B1. Install what is needed

RDP in, then install:

1. **JDK 17** — https://adoptium.net → *Temurin 17 (LTS), Windows x64, .msi*.
   Tick **Set JAVA_HOME**. Confirm in a new PowerShell: `java -version`
2. **MySQL 8** — https://dev.mysql.com/downloads/installer → *Server only*.
   Set a root password and **write it down**.
3. **IIS with the right pieces** — Server Manager → Add Roles and Features →
   Web Server (IIS), and under it make sure these are ticked:
   - **WebSocket Protocol** ← chat and calls do not work without it
   - Static Content, Default Document, HTTP Compression
4. **URL Rewrite** — https://www.iis.net/downloads/microsoft/url-rewrite
5. **Application Request Routing (ARR)** —
   https://www.iis.net/downloads/microsoft/application-request-routing
6. **NSSM** — https://nssm.cc → unzip somewhere permanent, e.g. `C:\nssm\`
7. **Git** — https://git-scm.com (or copy the project across by hand)
8. **Node 18+** — https://nodejs.org, only if you want to build on the server

## B2. Create the database

```powershell
mysql -uroot -p
```
```sql
CREATE DATABASE hr CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'hruser'@'localhost' IDENTIFIED BY 'a-strong-password';
GRANT ALL PRIVILEGES ON hr.* TO 'hruser'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

Restoring an existing dump:

```powershell
# unzip the .gz first, then
Get-Content C:\dumps\hr-live.sql -Raw | mysql -uroot -p hr
```

## B3. Build the backend

```powershell
cd C:\hr-port\backend
mvn clean package -DskipTests
```

Produces `C:\hr-port\backend\target\hr-portal-*.jar`.

## B4. Run it as a Windows service

A service starts on boot and stays up without anybody logged in. Run this
elevated (**Run as administrator**):

```powershell
C:\nssm\nssm.exe install HrPortalBackend "C:\Program Files\Eclipse Adoptium\jdk-17\bin\java.exe"
```

The NSSM window opens. Fill in:

- **Application** tab
  - Path: the `java.exe` above
  - Startup directory: `C:\hr-port\backend`
  - Arguments: `-Xms256m -Xmx1024m -Duser.timezone=Asia/Kolkata -jar target\hr-portal-1.0.0.jar`
    *(use the real jar name from B3)*
- **Details** tab → Display name: `HR Portal Backend`
- **Environment** tab → one per line:
  ```
  SPRING_PROFILES_ACTIVE=prod
  DB_HOST=localhost
  DB_PORT=3306
  DB_NAME=hr
  DB_USER=hruser
  DB_PASSWORD=a-strong-password
  APP_JWT_SECRET=paste-40-plus-random-characters-here
  APP_CORS_ALLOWED_ORIGINS=https://portal.yourdomain.com
  STORAGE_PATH=C:\hr-port-storage
  FAST2SMS_ENABLED=false
  ```
- **I/O** tab → Output and Error: `C:\hr-port\logs\backend.log` (create the folder)

Then **Install service**, and:

```powershell
Start-Service HrPortalBackend
Start-Sleep 60
Invoke-WebRequest http://localhost:7060/actuator/health -UseBasicParsing
```

`{"status":"UP"}` and you are past the hard part. Otherwise read
`C:\hr-port\logs\backend.log`.

> `APP_JWT_SECRET` must be at least 32 characters or the backend refuses to
> start. Generate one:
> `[Convert]::ToBase64String((1..36 | ForEach-Object { Get-Random -Max 256 }))`

## B5. Build and place the web app

```powershell
cd C:\hr-port
.\build-for-hosting.ps1 -ApiUrl https://portal.yourdomain.com
```

Note the address: on this plan the site and the API share one hostname, because
IIS forwards `/api` to the backend in the next step.

Copy everything inside `C:\hr-port\web\dist\` into `C:\inetpub\wwwroot\`.

## B6. Make IIS forward the API

Two rules, above the SPA fallback that `web.config` already provides.

IIS Manager → your site → **URL Rewrite** → **Add Rule → Blank rule**:

| | Rule 1 | Rule 2 |
|---|---|---|
| Name | `API proxy` | `WebSocket proxy` |
| Pattern | `^api/(.*)` | `^ws/(.*)` |
| Action | Rewrite | Rewrite |
| URL | `http://localhost:7060/api/{R:1}` | `http://localhost:7060/ws/{R:1}` |
| Stop processing | ✔ | ✔ |

Both must be **above** "SPA fallback" in the list — the fallback matches
everything, so anything below it never runs.

Then enable the proxy: IIS Manager → click the **server** name (not the site) →
**Application Request Routing Cache** → *Server Proxy Settings* → tick **Enable
proxy** → Apply.

## B7. HTTPS

```powershell
# win-acme: https://www.win-acme.com — download, unzip, then
.\wacs.exe
```

Choose *N* (new certificate), pick your IIS site, follow the prompts. It installs
the certificate and renews it automatically.

Then in IIS: site → **SSL Settings** → **Require SSL**.

## B8. Check it

Same list as A8, plus:

- [ ] `Get-Service HrPortalBackend` says Running
- [ ] reboot the server and it comes back by itself
- [ ] Chat works — that proves WebSocket Protocol and the Rule 2 proxy

---

# When it does not work

| What you see | What it is |
|---|---|
| 404 on refreshing any page | `web.config` is missing from `wwwroot\`, or URL Rewrite is not installed |
| Login fails, console shows a CORS error | `APP_CORS_ALLOWED_ORIGINS` does not exactly match your site address — check `https` and no trailing slash |
| Site loads, every request fails | Wrong `-ApiUrl` at build time. Rebuild; editing files on the server cannot fix it |
| "Mixed content" blocked | The site is `https` and the backend is `http`. Both must be `https` |
| Chat does not update without refresh | WebSocket: on Plan A check Render's logs; on Plan B check the WebSocket Protocol feature and Rule 2 |
| `Communications link failure` in the backend log | It cannot reach MySQL — remote access (A1), credentials, or the host and port |
| Camera/microphone/GPS unavailable | No HTTPS yet |
| Backend will not start, mentions the JWT secret | `APP_JWT_SECRET` is missing or under 32 characters |
| Flyway "checksum mismatch" | The database was migrated by a different version of the code. Do not edit migration files to make it go away — ask first |

---

# One thing to do before any of this

Two real credentials are sitting in git history:

- [`backend/.env.example`](../backend/.env.example) — a MySQL password
- [`backend/src/main/resources/application-dev.yml`](../backend/src/main/resources/application-dev.yml) — a Twilio SID and auth token

Anybody who can read the repository can read both. Change them at the source —
the database password in your hosting panel, the Twilio token in the Twilio
console — and supply the new ones only as environment variables. Removing them
from the files now does not help: the old commits keep them.
