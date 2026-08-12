# Setup & Run Guide

This is a monorepo with three apps that talk to one MySQL database:

```
hr-portal/
├── backend/   Spring Boot 3.5 · Java 21 · REST API on :7060
├── web/       React 19 · Vite · TypeScript on :5174
├── mobile/    React Native · Expo (employee app)
└── docs/      requirements, architecture, API contract, this guide
```

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 21 | `java -version` should report 21 |
| Maven | 3.9+ | to build and run the backend |
| Node.js | 20 LTS+ | for web and mobile |
| MySQL | 8.4 | local install or the provided Docker container |
| Redis | 7 | optional in dev; used for token/session support |

The fastest way to get MySQL + Redis running is Docker:

```bash
docker compose up -d      # starts mysql:8.4 and redis:7
```

That creates a database `hr` reachable at `localhost:3306` (user `root`, password `root123`), matching the defaults in `backend/src/main/resources/application.yml`.

## 1. Backend (Spring Boot)

```bash
cd backend
mvn spring-boot:run
```

(No Maven wrapper is checked in. If you prefer `./mvnw`, generate one with `mvn -N wrapper:wrapper`.)

On first start, **Flyway** runs migrations `V1`–`V8` automatically: it creates every table and seeds roles, permissions, master data, and six demo users. No manual SQL needed.

- API base: `http://localhost:7060/api`
- Swagger UI: `http://localhost:7060/swagger-ui.html`
- Health: `http://localhost:7060/actuator/health`

To point at a different database, override in `application.yml` or via env vars:

```bash
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/hr \
SPRING_DATASOURCE_USERNAME=root \
SPRING_DATASOURCE_PASSWORD=root123 \
mvn spring-boot:run
```

Generated files (payslip PDFs, asset QR codes, uploads) are written under `backend/storage/` by default. Change with `app.storage.local-path`.

## 2. Web app (React)

```bash
cd web
npm install
npm run dev
```

Open `http://localhost:5174`. The Vite dev server proxies `/api` and `/ws` to `localhost:7060`, so no CORS setup is needed in development. For a production build:

```bash
npm run build && npm run preview
```

To call a remote backend instead of the proxy, set `VITE_API_URL` in `web/.env`.

## 3. Mobile app (Expo)

```bash
cd mobile
npm install
npx expo start
```

Scan the QR code with **Expo Go** (Android/iOS) or press `a`/`i` for an emulator.

Set the API base the device can reach in `mobile/.env` (a phone can't see `localhost` on your laptop — use your machine's LAN IP):

```
EXPO_PUBLIC_API_URL=http://192.168.1.5:7060
```

## Demo logins

All seeded users share the password **`Test1234@`**. Login is by **Aadhaar number**.

| Aadhaar | Name | Role | Sees |
|---------|------|------|------|
| `123456789022` | Arun Kumar | IT Employee | Self-service: attendance, leave, payslips, assets, helpdesk |
| `123456789033` | Divya | IT Employee | Same as above |
| `123456789044` | Karthik | IT Manager | + Leave approvals, team attendance, employee directory |
| `123456789055` | Priya | IT HR | + Employee management, payroll view |
| `123456789066` | Rajesh | Civil Supervisor | Field-oriented views |
| `999999999999` | Admin | Super Admin | Everything, including executive dashboard |

`EMP0001` and `EMP0002` report to `EMP0003`, so signing in as Karthik shows their leave requests in the approvals inbox.

## Troubleshooting

- **Flyway checksum error** — you changed a migration that already ran. In dev, drop the schema (`DROP DATABASE hr_portal; CREATE DATABASE hr;`) and restart.
- **`401` right after login on the web app** — the access token expired and the refresh call failed; sign in again. Tokens: access 15 min, refresh 7 days.
- **Geofence rejects an office punch** — the seeded office/site coordinates are samples. Update `office_locations`/`sites` lat-lng or widen `app.attendance.geofence-radius-metres`.
- **Mobile can't reach the API** — confirm `EXPO_PUBLIC_API_URL` uses your LAN IP and that the phone and laptop are on the same network.
