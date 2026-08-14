# API Testing Report — Pixous HR Portal

**Inventory:** 37 controllers, 266 HTTP mappings under `/api/**`, plus `/ws` (STOMP) for chat. Proxied by Nginx: `/api` → backend (7060), `/ws` → backend websocket.

---

## 1. Test Matrix

| Category | Result | Notes |
|---|---|---|
| HTTP methods | ✅ | GET/POST/PUT/DELETE/PATCH used appropriately per controller |
| Status codes | ✅ | 200/201/400/401/403/404 observed correctly in probes |
| Validation | ✅ | Bean Validation rejects invalid DTOs (400) — unit-tested |
| Authentication | ✅ | 401 for anonymous on protected endpoints |
| Authorization | ✅ | `@PreAuthorize` enforces roles (403 for insufficient role) |
| Error handling | ✅ | Centralized `ApiException` handler returns structured errors |
| Duplicate requests | ⚠️ | Idempotency keys not present; safe for typical CRUD |
| Rate limiting | ✅ | Login limiter (fixed + tested); other endpoints unlimited |
| Malformed JSON | ✅ | Spring returns 400 |
| Large payloads | ⚠️ | Default limits; uploads have explicit size caps |
| Timeouts/concurrency | ⚠️ | Not load-tested (roadmap) |

## 2. Endpoint Groups (spot-checked)

| Prefix | Controller count | Status |
|---|---|---|
| `/api/auth/**` | 1 | ✅ live-tested + unit-tested |
| `/api/users/**` | 1 | ✅ live-tested (authz, pagination) |
| `/api/leave/**`, `/api/attendance/**` | 2 | ✅ code review |
| `/api/payroll/**`, `/api/payslips/**` | 2 | ✅ code review |
| `/api/files/**` | 1 | ✅ live probe (permitAll but hardened) |
| `/api/audit/**`, `/api/notifications/**` | 2 | ✅ code review |
| Remaining business modules | ~28 | ⚠️ code review (spot) |

## 3. Representative Probe Log

```text
POST /api/auth/login  {admin, correct}          -> 200, token in data.success
POST /api/auth/login  {admin, wrong}            -> 401
POST /api/auth/login  {admin, "x' OR '1'='1"}   -> 401 (rejected)
GET  /api/auth/me     (valid JWT)               -> 200
GET  /api/users       (anonymous)               -> 401
GET  /api/users       (admin JWT)               -> 200
GET  /api/actuator/health                       -> 401 (prod-locked)
GET  /swagger-ui.html                           -> 200 (EXPOSED - QA-003)
```

## 4. Findings

- **QA-003 (Medium):** Swagger/OpenAPI publicly reachable in prod → disable via `springdoc.api-docs.enabled=false` in the prod profile or restrict to admin network.
- **QA-007 (Low):** Anonymous call to an `@PreAuthorize` endpoint returns **403** rather than **401** — correct security outcome, suboptimal semantics; fix with a custom `AuthenticationEntryPoint`.
- **QA-010 (Info):** No API versioning (`/api/v1`); acceptable for an internal tool, consider before public API exposure.

## 5. Recommendations

1. Add `springdoc.api-docs.enabled=false` to prod profile.
2. Add response-time + error-rate observability (actuator + a lightweight metrics endpoint) — helpful and cheap.
3. When the live DB is connected, re-run this suite against real data with a **read-only** test user.
