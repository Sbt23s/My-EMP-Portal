# Pixous HR Portal — QA, Security & Technical Audit

**Audit date:** 2026-08-14
**Scope:** Full-stack application — Spring Boot backend (37 controllers, 266 HTTP mappings, 304 Java files) + React 19 / Vite 6 frontend (37 pages) + MySQL + Redis + Nginx deployment on AWS EC2 (`eu-north-1`, live at `http://16.192.105.61`)
**Method:** Code review, static analysis, build verification, live API probing, automated unit/component tests, deployment pipeline review. No production data was modified.

---

## 1. Executive Summary

Pixous HR Portal is a **well-architected, production-credible multi-tenant HR application**. The codebase is unusually well-documented for its size, follows clear conventions (controller → service → repository layering, DTOs with Bean Validation, centralized `ApiException` handling), and ships with a sane security baseline: stateless JWT auth, login rate limiting, file-upload allowlists, and a hardened file-serving path.

**The most serious issue is not in the code — it is repository hygiene:** a filled-in `.env.backup` (DB password + JWT secret) sits in the **public** GitHub repo's history. It has been removed from the branch and gitignored, but history still contains it. This must be remediated (private repo + history purge + secret rotation).

Two functional gaps matter for production: the site is **HTTP-only** (no TLS) and the **live company DB (site4now) is not yet connected** — site4now blocks remote MySQL until the server's IP is whitelisted in their control panel (external dependency, instructions provided).

**The audit added a working test suite: 62 automated tests (40 backend + 22 frontend), all passing.** One real vulnerability was found and fixed during the audit: the login rate-limiter could be bypassed by spoofing `X-Forwarded-For` (fixed in `AuthController`).

**Verdict: deployable now for staging/team use; address the high-severity hygiene items before public production.**

---

## 2. Summary Dashboard

| Metric | Value |
|---|---|
| Backend Java files audited | 304 |
| Controllers / HTTP mappings | 37 / 266 |
| Frontend pages audited | 37 (+ nested routed pages) |
| Automated tests added (backend) | 40 — JWT, rate-limit, storage allowlist, auth service, DTO validation |
| Automated tests added (frontend) | 22 — utils, date helpers, login component |
| Test result | **62 / 62 passing** (0 failures) |
| Build (backend) | ✅ `mvn clean package` |
| Build (frontend) | ✅ `tsc -b && vite build` (1 chunk-size warning) |
| Live site probe | ✅ HTTP 200, backend health `UP` |

### Issue counts by severity

| Severity | Open | Fixed | Notes |
|---|---|---|---|
| Critical | 1 | 0 | `.env.backup` credentials in public repo history |
| High | 0 | 1 | `X-Forwarded-For` rate-limiter bypass (fixed) |
| Medium | 3 | 0 | Swagger exposure, missing security headers, HTTP-only |
| Low | 4 | 0 | Dev-profile JWT secret, 403-vs-401 nuance, bundle size, docs gaps |

### Scorecard

| Category | Score /100 |
|---|---|
| Security | 72 |
| Performance | 78 |
| Maintainability | 82 |
| Code quality | 85 |
| Accessibility | 60 |
| **Production readiness** | **75%** |
| **Overall grade** | **B+** |

---

## 3. What Was Verified (proof of work)

- **Authentication:** login (success + failure), JWT issue/validation/expiry, refresh rotation, lockout after repeated failures, protected-route denial (401), role checks (403), no user enumeration on error messages.
- **Security probes (live):** SQL injection payloads rejected, XSS payloads rejected, unauth access to protected APIs → 401/403, actuator health locked, upload traversal blocked, SVG/HTML uploads neutralized to `.bin`.
- **Data layer:** schema migrations V1–V96 apply cleanly, multi-tenant `company_id` isolation present, seeding correctly disabled under `prod` profile.
- **Deployment:** GitHub Actions → SSH → `git pull` → `docker compose up -d --build`; verified end-to-end against the live server; MySQL/Redis not exposed publicly (only Nginx:80).
- **Tests:** 62 automated tests written during this audit and passing (`mvn test`, `vitest run`).

---

## 4. Top Findings at a Glance

| # | Severity | Finding | Status |
|---|---|---|---|
| 1 | Critical | Credentials (DB password, JWT secret) in public repo history | Mitigated on branch; **purge history + rotate** |
| 2 | High | Login rate-limit bypass via spoofed `X-Forwarded-For` | **Fixed** |
| 3 | Medium | Swagger/OpenAPI publicly exposed | Open |
| 4 | Medium | Missing security headers (CSP/HSTS/X-Frame-Options) | Open |
| 5 | Medium | Site served over HTTP only, no TLS | Open |
| 6 | Low | Hardcoded JWT secret in `application-dev.yml` | Open (dev-only) |
| 7 | Low | Anonymous + `@PreAuthorize` → 403 instead of 401 | Open |
| 8 | Info | Frontend main chunk > 500 kB warning | Open |
| 9 | Info | Live DB (site4now) blocked by remote-IP whitelist | Blocked on vendor action |

*Full details, reproduction steps, and fixes: `01-full-qa-report.md` and `02-security-audit.md`.*
