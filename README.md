# Pixous HR Portal

A full-stack HR & Employee Management System built for a company that spans two
worlds: **IT** (desk-based staff) and **Civil / Facilities** (on-site field
workers). One platform handles attendance, leave, payroll, assets, and a
helpdesk across both — with role-based access for 11 roles and geofenced,
GPS-aware attendance for field teams.

```
hr-portal/
├── backend/    Spring Boot 3.5 · Java 21 · MySQL · JWT + RBAC · REST + WebSocket
├── web/        React 19 · Vite · TypeScript · Tailwind · TanStack Query/Table
├── mobile/     React Native · Expo (employee companion app)
└── docs/       requirements, traceability, architecture, API contract, setup
```

## What's inside

**Backend** — a cleanly layered Spring Boot API with:
- Username-based login, JWT access + refresh tokens with rotation, account lockout
- Role/permission RBAC (12 roles, 14 permissions) enforced per endpoint
- Ten working end-to-end modules: users, org master data, attendance (with
  geofencing), leave (with balances and approval workflow), payroll (with PDF
  payslips), assets (with QR tags), helpdesk (with SLA + ratings), notifications
  (live over WebSocket), and role-aware dashboards
- Flyway migrations that build the whole schema and seed demo data on first run

**Web** — a responsive, themeable (light/dark) portal covering dashboard,
attendance, leave + approvals, payslips, employee directory, assets, helpdesk,
notifications, and profile, with role-gated navigation.

**Mobile** — an Expo app for the four things employees do most on the go: see
their dashboard, punch attendance with GPS, check leave, and view payslips.

## Quick start

```bash
# 1. infrastructure
docker compose up -d

# 2. backend  (http://localhost:7060, Swagger at /swagger-ui.html)
cd backend && mvn spring-boot:run

# 3. web  (http://localhost:5174)
cd web && npm install && npm run dev

# 4. mobile
cd mobile && npm install && npx expo start
```

Full details, environment variables, and troubleshooting are in
[`docs/04-setup-guide.md`](docs/04-setup-guide.md).

## Demo logins

Login is by **username**; every seeded user's password is **`Test1234@`**.

| Username | Role | Highlights |
|----------|------|-----------|
| `arun`    | IT Employee | Self-service everything |
| `karthik` | IT Manager  | + leave approvals, team view, directory |
| `priya`   | IT HR       | + employee management, complaints/needs review |
| `admin`   | Super Admin | + executive dashboard |

> Aadhaar is now an **optional** profile field and is no longer used to sign in.
> HR and Admin can create new employees (username + password + details) from the
> **Employees** screen. Employees and managers can raise items in
> **Complaints / Needs**, which HR and Admin review and respond to.

## Documentation

- [`docs/00-requirements-analysis.md`](docs/00-requirements-analysis.md) — the source user stories, distilled
- [`docs/01-traceability-matrix.md`](docs/01-traceability-matrix.md) — story → implementation mapping
- [`docs/02-architecture.md`](docs/02-architecture.md) — system design and decisions
- [`docs/03-api-contract.md`](docs/03-api-contract.md) — endpoint reference
- [`docs/04-setup-guide.md`](docs/04-setup-guide.md) — how to run all three apps

## Scope note

This is a runnable, well-architected **foundation**, not a shrink-wrapped
product. The ten modules above work end-to-end across all three tiers; a few
areas (payroll batch runs, onboarding checklists, safety incidents, reporting
exports) are modelled and API-scaffolded on the backend with placeholder screens
on the web, ready to build out. See the traceability matrix for the exact status
of each story.

## Startup fix — schema validation (V14)

An earlier version failed to start with:

```
Schema-validation: missing column [status] in table [onboarding_checklists]
```

Cause: `onboarding_checklists` was first created in **V3** with an old design
(`item`, `completed`), and **V9** later re-declared it with the current design
(`status`, `started_at`) using `CREATE TABLE IF NOT EXISTS` — which was skipped
because the table already existed. Since `spring.jpa.hibernate.ddl-auto=validate`,
Hibernate rejected the mismatch at boot.

Migration **`V14__fix_onboarding_checklists.sql`** reconciles the live schema with
the JPA entities. It is idempotent and safe on both a fresh database and an existing
one (it checks `information_schema` before each change). It fixes three tables that
were out of sync with their entities:

- `onboarding_checklists` — adds `status`, `started_at`; migrates/drops the old
  `completed`/`item` columns.
- `ta_expenses` — adds `created_by` and `updated_by` (the audit columns inherited
  from `BaseEntity`). The `TaExpense` entity previously also declared its own
  `createdBy` as a `Long`, which clashed with `BaseEntity.getCreatedBy()` (a
  `String`) and broke compilation — that duplicate field was removed so the
  inherited audit column is used.
- `offboarding_records` — adds `created_by`/`updated_by` for the same
  `BaseEntity` audit reason.
- `positions` — adds the `code` column expected by the `Position` entity.

The `User` entity's `gender` field was also corrected to map to the existing
`gender` column (it previously pointed at a non-existent `gender_restriction`).

**To apply:** just start the backend again — Flyway runs `V14` automatically. No manual
SQL and no database reset is needed. Every entity now validates against the schema.
