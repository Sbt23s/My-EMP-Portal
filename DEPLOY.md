# Deploying the Pixous HR Portal to Render

This repo is ready to deploy on [Render](https://render.com) as two services:

| Service | What it is | Type on Render |
|---------|-----------|----------------|
| `pixous-ems-backend` | Spring Boot API (Java 17, Docker) | Web Service (Docker) |
| `pixous-ems-web` | React/Vite frontend | Static Site |

Everything is wired through the `render.yaml` Blueprint at the repo root, so most
of the setup is automatic. The only thing Render can't provide is a **MySQL
database** (Render only offers PostgreSQL), so you create one free elsewhere and
paste 3 values. Total time: ~15 minutes.

---

## Step 1 — Create a free MySQL database

Render has no managed MySQL, so use a free external provider. **Aiven** is
recommended (real MySQL 8, free plan, no card required):

1. Sign up at <https://aiven.io> → **Create service** → **MySQL** → **Free plan**.
2. Pick a region close to you and create it. Wait ~2 min until it's "Running".
3. Open the service → **Connection information**. Note these:
   - **Host** (e.g. `mysql-xxxx.aivencloud.com`)
   - **Port** (e.g. `12345`)
   - **User** (usually `avnadmin`)
   - **Password**
   - **Database name** (usually `defaultdb`)

> Any MySQL 8 host works (PlanetScale, Clever Cloud, Railway, your own server, …).
> Just make sure you can reach it from the public internet.

You'll turn these into one JDBC URL in Step 3:

```
jdbc:mysql://HOST:PORT/DBNAME?sslMode=REQUIRED&serverTimezone=Asia/Kolkata&allowPublicKeyRetrieval=true
```

Example:
```
jdbc:mysql://mysql-xxxx.aivencloud.com:12345/defaultdb?sslMode=REQUIRED&serverTimezone=Asia/Kolkata&allowPublicKeyRetrieval=true
```

> The app runs **Flyway** on first boot: it creates all 66 tables and seeds demo
> users automatically. You don't need to run any SQL yourself.

---

## Step 2 — Create the Render Blueprint

1. Push this repo to GitHub (already done if you're reading this there).
2. In Render, click **New → Blueprint**.
3. Connect your GitHub account and pick the **`pixous-ems`** repo.
4. Render detects `render.yaml` and lists **two services**. Click **Apply**.

Render will now ask you to fill in the env vars marked `sync: false`.

---

## Step 3 — Fill in the backend environment variables

For **pixous-ems-backend**, set:

| Variable | Value |
|----------|-------|
| `SPRING_DATASOURCE_URL` | the full JDBC URL from Step 1 |
| `SPRING_DATASOURCE_USERNAME` | your DB user (e.g. `avnadmin`) |
| `SPRING_DATASOURCE_PASSWORD` | your DB password |
| `APP_CORS_ALLOWED_ORIGINS` | leave blank for now — set in Step 5 |

`APP_JWT_SECRET` is generated automatically. `SPRING_PROFILES_ACTIVE=prod` and
`TWILIO_ENABLED=false` are already set.

Click **Apply / Create**. The backend builds from `backend/Dockerfile` (takes a
few minutes the first time) and starts. When healthy, note its URL, e.g.
`https://pixous-ems-backend.onrender.com`.

> Health check is `/actuator/health`. You can open it in a browser — it should
> return `{"status":"UP"}` once the DB connection is live.

---

## Step 4 — Point the frontend at the backend

For **pixous-ems-web**, set:

| Variable | Value |
|----------|-------|
| `VITE_API_URL` | your backend URL from Step 3, e.g. `https://pixous-ems-backend.onrender.com` |

Then **Manual Deploy → Deploy latest commit** for the web service, so the value
is baked into the build. Note the web URL, e.g.
`https://pixous-ems-web.onrender.com`.

> ⚠️ `VITE_API_URL` is read at **build time**, not runtime. If you change it, you
> must redeploy the web service.

---

## Step 5 — Allow the frontend origin (CORS)

Back on **pixous-ems-backend**, set:

| Variable | Value |
|----------|-------|
| `APP_CORS_ALLOWED_ORIGINS` | your web URL from Step 4, e.g. `https://pixous-ems-web.onrender.com` |

Save — the backend restarts automatically. (Multiple origins? Comma-separate
them, no spaces.)

---

## Step 6 — Log in

Open the web URL and log in. Login is by **username**; every seeded user's
password is **`Test1234@`**:

| Username | Role |
|----------|------|
| `admin` | Super Admin |
| `priya` | HR |
| `karthik` | Manager |
| `arun` | Employee |

---

## Notes & gotchas

- **Free tier cold starts.** Free web services sleep after ~15 min idle; the next
  request wakes them (~30–60s). The static frontend never sleeps, so you may see
  a short delay on the first login after a quiet period. This is normal.
- **File storage is ephemeral.** Generated payslip PDFs / QR images are written to
  the container's local disk and are lost on restart/redeploy. Fine for a demo;
  for permanent storage attach a Render Disk or switch `app.storage.type` to S3.
- **Redis, Kafka, mail are optional** and off by default — the app runs fine
  without them. Kafka auto-config is fully disabled; Redis/mail health checks are
  turned off so they never mark the service unhealthy.
- **The Python analytics service** (face recognition, `analytics-service/`) is
  optional. The dashboard degrades gracefully if it's absent, so it is not part of
  this Blueprint. To deploy it, add a third service (Docker/Python) later.
- **The mobile app** (`mobile/`) is an Expo app — it isn't a web service and is
  not deployed to Render. Run it locally or publish via Expo/EAS.

## Running locally

```bash
docker compose up -d                 # MySQL + Redis
cd backend && mvn spring-boot:run    # http://localhost:7060
cd web && npm install && npm run dev # http://localhost:5174
```
