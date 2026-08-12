# Architecture

## Monorepo layout

```
hr-portal/
├── backend/     Spring Boot 3.5 · Java 21 · MySQL 8.4 · Redis · Flyway   (REST API + WebSocket)
├── web/         React 19 · Vite · TypeScript · Tailwind · shadcn-style    (responsive PWA)
├── mobile/      React Native (Expo) · TypeScript                          (iOS + Android)
├── docs/        analysis · traceability · this file · API contract · setup
└── docker-compose.yml   MySQL + Redis for local dev
```

## Backend layering (per module)

```
modules/<name>/
  <Entity>.java            JPA entity (extends BaseEntity → id, audit columns)
  <Entity>Repository.java  Spring Data JPA repository
  <Name>Service.java       business logic, transactions, validation, mapping
  <Name>Controller.java    REST endpoints, returns ApiResponse<T>
  dto/ (inline records)    request/response DTOs (Java records + Bean Validation)
```

Cross-cutting code lives outside `modules`:

- `common/` — `BaseEntity`, `ApiResponse<T>`, `PageResponse<T>`, `ApiException`,
  `GlobalExceptionHandler`, error codes.
- `security/` — `JwtService`, `JwtAuthenticationFilter`, `UserPrincipal`,
  `CustomUserDetailsService`, `Role`/`Permission` model.
- `config/` — `SecurityConfig`, `OpenApiConfig`, `WebConfig` (CORS), `WebSocketConfig`,
  `AsyncConfig`, `RedisConfig`.

### Request flow

```
HTTP → CORS filter → JwtAuthenticationFilter (validates bearer, loads UserPrincipal)
     → @PreAuthorize role/permission check → Controller → Service (@Transactional)
     → Repository → MySQL.   Responses wrapped in ApiResponse<T>. Errors → GlobalExceptionHandler.
```

### Security

- **Login**: Aadhaar + password → `BCrypt` verify → access JWT (15 min) + refresh JWT (7 d).
- **Refresh**: `/api/auth/refresh` swaps a valid refresh token for a new access token.
- **RBAC**: each `User` has `Role`s; each `Role` has `Permission`s. Method security via
  `@PreAuthorize("hasRole('IT_HR')")` or `hasAuthority('LEAVE_APPROVE')`.
- **Audit**: `BaseEntity.createdBy/updatedBy/createdAt/updatedAt` (JPA auditing) + an
  `audit_log` table for sensitive actions, + `login_history`.
- **Account safety**: password policy, failed-attempt lock, session/refresh revocation.

### Real-time

`WebSocketConfig` exposes a STOMP endpoint at `/ws`. The server pushes to
`/topic/notifications/{userId}` (new leave request to manager, status change to employee,
new payslip, escalations). The web client subscribes and invalidates the relevant TanStack
Query keys, satisfying every "refresh in real-time without reload" acceptance criterion.

### Files, reports, QR

- `StorageService` — interface; `LocalStorageService` for dev, S3/MinIO impl is the
  documented prod swap. Stores documents, photos, payslip PDFs, safety photos.
- `ReportService` — Apache POI for `.xlsx`, JasperReports for `.pdf`.
- `QrCodeService` — ZXing; generates a PNG per asset for physical tagging (US-IT-AST-01 AC4).
- `GeofenceService` — haversine distance vs. a site/office's configured radius
  (US-CV-EMP-01, US-IT-EMP-03 AC2).

## Frontend (web)

- **Routing**: React Router v7. `ProtectedRoute` gates authenticated areas; `RoleGuard`
  hides routes/nav the user's role can't access.
- **Server state**: TanStack Query (caching, refetch, optimistic updates, WS-driven
  invalidation). **Client state**: React Context (auth, theme).
- **HTTP**: a single Axios instance with interceptors — attaches the access token, and on
  `401` transparently calls `/api/auth/refresh` then retries.
- **UI**: hand-written shadcn-style primitives (`Button`, `Input`, `Card`, `Badge`,
  `Dialog`, `Table`…) on Tailwind tokens. Recharts for dashboards, TanStack Table for grids.
- **PWA**: `vite-plugin-pwa` (installable, offline shell). Layout is responsive 320px → desktop.

### Adding a module (the repeatable recipe)

1. **Backend**: copy a ✅ module folder (e.g. `helpdesk`), rename the entity/service/controller,
   add a Flyway migration `V{n}__{module}.sql`, expose endpoints returning `ApiResponse<T>`.
2. **Web**: add a typed API hook in `src/hooks/`, a page in `src/pages/`, and a route +
   nav entry in `src/routes/`. Replace the `ModulePlaceholder` with the real page.
3. **Mobile** (if employee-facing): add a screen + an entry in `src/navigation/`.

Because every ✅ slice already demonstrates the pattern end-to-end, the 🟡/⬜ stories in the
traceability matrix are filled in by following this recipe — no architectural decisions left.

## Frontend (mobile)

Expo + React Native + TypeScript. Shares the API shape with the web client. Ships the
employee-critical, on-the-go flows: **login, dashboard, GPS attendance (with geo-fence
check via `expo-location`), apply leave, view payslips**. Single codebase → iOS + Android.
