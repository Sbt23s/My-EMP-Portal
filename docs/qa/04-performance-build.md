# Performance & Build Audit — Pixous HR Portal

## 1. Build Audit

| Check | Backend | Frontend |
|---|---|---|
| Build command | `mvn clean package` | `tsc -b && vite build` |
| Result | ✅ pass (fat jar) | ✅ pass |
| Warnings | none | 1: main chunk > 500 kB |
| Docker build | `maven:3.9` → `eclipse-temurin:17-jre` (non-root user) | node:20 → nginx:1.27-alpine (multi-stage) |
| Dependency caching | `dependency:go-offline` first | `npm ci` with lockfile |
| CI compatibility | ✅ GitHub Actions SSH deploy | ✅ same |
| Unused packages | minor (e.g. `bootstrap` + `tailwind` both present) | noted in code review |

## 2. Runtime Performance (observed)

| Metric | Observed | Note |
|---|---|---|
| Home page (public) | HTTP 200 in ~0.8 s | via curl from external network |
| Login round-trip | ~200–400 ms | incl. JWT issue |
| Backend health | `{"status":"UP"}` | MySQL + Redis healthy |
| Static assets | served by Nginx (fast) | gzip not confirmed on nginx — add `gzip on` |
| Image handling | no CDN; served from Nginx | fine at team scale |

## 3. Bundle Analysis (finding QA-008)

- Vite reports a chunk > 500 kB (warning). Heavy deps: `xlsx` (export), `recharts` (charts), `framer-motion`, `react-qr-code`/`jsbarcode`, `sockjs-client`.
- **Fix:** lazy-load route-level chunks (`React.lazy`) for heavy pages (Reports, Dashboard charts) so the initial bundle shrinks; defer `xlsx` import to the export action.

## 4. Caching & Compression

- ✅ PWA service worker with precache (Workbox) — offline-first for the app shell; `navigateFallbackDenylist` correctly excludes `/api`, `/ws`, `/uploads`.
- ⚠️ Nginx: no explicit `gzip` config found in `nginx.conf` — enable `gzip on; gzip_types ...` for a free win.
- ⚠️ No `Cache-Control` for long-lived hashed assets (Vite emits hashed names; safe to cache `immutable`).

## 5. Scalability Observations

- Stateless backend (JWT) → horizontally scalable behind a load balancer.
- MySQL + Redis are single instances inside the VM — fine for team scale; move to managed RDS/ElastiCache if usage grows.
- Pagination (`size`/`page`) used on list APIs — good.
- Rate limiting is in-memory per instance — acceptable for one instance; use Redis-backed limiter if multi-instance.

## 6. Score: Performance 78/100

Strong foundation; the two cheap wins are nginx gzip + route-level code splitting. No load testing was performed — that is the main unknown (roadmap: k6/siege baseline).
