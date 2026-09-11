# Performance, Code Quality and Architecture Audit

**System:** Pixous HR Portal — `pixoushrportal.pixous.info`
**Date:** 11 September 2026
**Method:** Read-only inspection of the live system, the source, and measured production responses. No production data was modified.

---

## 1. Headline

The application is in better shape than this kind of audit usually finds. That is worth saying plainly, because the honest answer to "make it extremely fast" is that **most of it already is**, and the remaining work is specific rather than sweeping.

| Measurement | Value | Verdict |
|---|--:|---|
| API response, measured on the server | **21 ms** | Excellent |
| nginx → backend, in-cluster | **3.5 ms** | Excellent |
| Server load average | **0.00** | Idle |
| CPU, all containers | **< 1%** | Idle |
| Backend memory | 871 MB of 7.6 GB | Comfortable |
| Login page, first paint | **344 KB** | Good |
| Production errors, observation window | **0** | Clean |
| Dead files in 130-file frontend | **2** | Very clean |
| Business data in local storage | **none** | Correct |

**The single largest constraint on user-perceived speed is not the application.** The same URL answers in 21 ms measured from the server and 740 ms – 5.6 s measured from a laptop in India. That gap is network path and TLS handshake to an EC2 instance in Europe. No amount of code change closes it; a CDN or a region move does.

---

## 2. Findings

Each finding carries a severity, the evidence behind it, and an honest estimate of what fixing it buys.

### 2.1 N+1 queries — four confirmed

**Severity: Medium now, High at scale.**

A repository call inside a loop issues one query per item. At 65 employees this is invisible. At 10,000 it is the first thing that breaks.

| # | Location | Loop over | Queries today | At 10,000 users |
|---|---|---|--:|--:|
| 1 | `DashboardService.java:147` | Every leaver, looking up their offboarding record | ~1 | up to 10,000 |
| 2 | `ComplaintService.java:260` | Every complaint's recipient | 6 | proportional to complaints |
| 3 | `HelpdeskService.java:167` | Every ticket's recipient | 7 | proportional to tickets |
| 4 | `ApprovalRecipientService.java:281` | Every user id in a saved rule | small | proportional to rule size |

**Fix:** one `findAllById` before the loop, then a map lookup inside it. The pattern is already used correctly elsewhere in the codebase — `EmployeeHistoryService` does exactly this.

**Risk:** Low. Same data, same order, fewer round trips.
**Expected improvement:** At 10,000 employees, the dashboard leavers query goes from thousands of statements to two.

**Not a finding, examined and cleared:** `PayslipService.java:472` looks like an N+1 (`findById` inside a `.map`) but the map is over an `Optional` — it runs at most once.

### 2.2 EAGER `@ManyToMany` on `User.roles`

**Severity: Medium.**

`User.roles` is `FetchType.EAGER`. Every query that loads a user loads their roles, whether or not the caller needs them — and users are loaded on nearly every request, because authorities are resolved from the database on each one.

**Measured:** one `/users?size=300` call returning 61 users issues **15 SQL statements**. Without `default_batch_fetch_size=50` it would issue 62. The batch setting is already saving this from being far worse, and it was set deliberately.

**Fix:** change to `LAZY` and add an explicit `JOIN FETCH` where roles are genuinely needed.

**Risk: Medium — higher than it looks.** `UserPrincipal` builds its authority list from `roles`, and it is constructed on every authenticated request. A `LazyInitializationException` there fails every request rather than one screen. This needs a careful pass over each call site and a full regression run, not a one-line change.

**Recommendation: REQUIRES REVIEW.** The batch-fetch setting already mitigates most of the cost. Do this when there is time to test it properly, not as part of a cleanup.

### 2.3 Duplicate query keys on the dashboard

**Severity: Low.**

Four query keys appear twice in `Dashboard.tsx`: `["users","dash-list"]`, `["attendance","team","dash"]`, `["leave","pending","dashboard"]`, `["payslip-requests"]`. The second declaration of each carries a *different* `queryFn`.

**What actually happens:** TanStack Query deduplicates by key, so only the first-mounted `queryFn` runs. There is no duplicate network request — but there is a trap: whichever component mounts first silently decides which fetch logic wins. The two `["users","dash-list"]` functions differ in error handling.

**Fix:** give the two variants distinct keys, or consolidate on one fetcher.
**Risk:** Low. **Improvement:** correctness and predictability rather than speed.

### 2.4 Dead files

**Severity: Low.**

Two files in a 130-file frontend are imported by nothing:

| File | Assessment |
|---|---|
| `web/src/components/ui/custom-loader.tsx` | Superseded by `pixous-loader.tsx` on 11 Sep. **Safe to remove.** |
| `web/src/lib/credentialsSheet.ts` | Builds a printable employee **login-credentials sheet**. Nothing imports it. **Requires review** — this is either a removed feature or an unwired one, and it handles sensitive material. Not for a cleanup to decide. |

Also present: `web/public/video/bg.mp4`, **13 MB**, referenced nowhere, shipped in the Docker image and served from a server at 80% disk. Previously reported; still awaiting a decision.

### 2.5 Cache audit

**Severity: None — this is a clean result.**

| Cache | Contents | Classification |
|---|---|---|
| `localStorage` | 4 keys: `chunk_reload_attempted`, `just_logged_in`, `hrp.tech_admin_company_modules`, `hrp.tech_admin_current_company_id` | **Required** — all UI state |
| Redis | `OrgService`, `SettingsController` only | **Required** — reference data |
| Service worker precache | 2,263 KB, heavy chunks excluded | **Required** |
| HTTP cache | Assets `immutable`, `index.html` `no-cache` | **Correct** |
| TanStack Query | 30 s `staleTime`, background polling off | **Correct** |

**Your data-storage rule is already satisfied.** No employee, attendance, leave, payroll, claim, WFH, permission, work-report, task or approval record is written to client storage anywhere. The server is the source of truth throughout.

**No aggressive caching of HR data exists**, which matters: a 30-second `staleTime` on payroll or attendance is short enough that nobody reads a stale figure and makes a decision on it.

### 2.6 Database indexes

**Severity: None.**

Every hot table carries the indexes its query patterns need:

| Table | Indexes |
|---|---|
| `attendance` | `work_date`, `company_id`, `(work_date, face_verified)`, unique `(user_id, work_date)` |
| `leave_requests` | `user_id`, `status`, `company_id`, `leave_type_id` |
| `tickets` | `raised_by`, `status`, `company_id`, unique `ticket_code` |
| `complaints_needs` | `raised_by`, `requested_to`, `status`, `kind`, unique `reference_code` |
| `offboarding_records` | `user_id` |

**No index is recommended.** Adding one without a query that needs it costs write throughput and buys nothing.

### 2.7 Security and logging

**Severity: None.**

- No password, token, secret, Aadhaar, salary or payroll value is logged anywhere. The four log lines matching a naive search all report the *absence* of a secret or a token's expiry time — never a value.
- One `console.log` remains in the frontend.
- Headers verified live: HSTS, `X-Frame-Options`, `nosniff`, `Referrer-Policy`, `Permissions-Policy`.
- Tested and passing: 46 controller bases refuse anonymous access; 6 JWT forgery attempts including `alg=none` rejected; 6 path-traversal variants blocked; SQL injection and XSS payloads refused.

**One open item:** Content Security Policy is `Report-Only`. It reports violations and blocks nothing. Enforcing it is a small, real improvement that carries a small, real risk of breaking an embed — worth doing with a short observation period first.

### 2.8 Real-time

**Severity: None.**

STOMP over SockJS, one connection per session, nine destinations. Verified against the live system with the real client: handshake, CONNECT, subscribe, stable, and **no private-topic traffic leaked to an anonymous session**.

**Polling audit:** 13 `refetchInterval` declarations remain, 8 s to 60 s. All have `refetchIntervalInBackground: false`, so a tab nobody is looking at asks for nothing. That is the right arrangement and no change is recommended.

---

## 3. Classification

### SAFE TO REMOVE

| Item | Why |
|---|---|
| `web/src/components/ui/custom-loader.tsx` | Superseded, zero imports |
| The single `console.log` | Debug residue |

### SAFE TO OPTIMIZE

| Item | Fix |
|---|---|
| 4 N+1 loops (§2.1) | `findAllById` + map lookup |
| Duplicate dashboard query keys (§2.3) | Distinct keys or one fetcher |

### REQUIRES REVIEW — your decision

| Item | Why it is not a cleanup decision |
|---|---|
| `User.roles` EAGER → LAZY (§2.2) | Touches authentication on every request; needs a full regression pass |
| `credentialsSheet.ts` | Handles employee login credentials; removal is a product decision |
| `bg.mp4`, 13 MB | Irreversible; frees image and disk space |
| Enforce CSP (§2.7) | Small risk to an embed |

### DO NOT TOUCH

| Item | Why |
|---|---|
| Database indexes | Already correct for the query patterns |
| Redis caching | Correctly scoped to reference data |
| TanStack Query configuration | 30 s stale time and background-polling-off are deliberate and right |
| `default_batch_fetch_size=50` | This is what keeps the EAGER mapping affordable |
| Polling intervals | All already pause in a background tab |
| Skeleton loaders | A shape-accurate placeholder is a better signal than a spinner |
| Any production data | No destructive SQL is warranted by anything in this audit |

---

## 4. Scalability to 10,000 concurrent users

You asked for an assessment, not a claim. The honest one:

**What is verified today:** 65 users, 2 companies, 108 tables, 73 MB, 21 ms server-side responses, 0.00 load average. The system is nowhere near its limits.

**What is not verified:** anything above 65 users. No load test has been run. Nobody can responsibly say "10,000 users supported" from a 65-user deployment, and this document will not.

**What would break first, in order:**

| # | Bottleneck | Evidence | Fix |
|---|---|---|---|
| 1 | **The four N+1 loops** | §2.1 — query count grows with headcount | Batch the lookups |
| 2 | **Database connection pool** | Hikari max 10 by default | Raise and load-test; 10 connections will not serve 10,000 users |
| 3 | **EAGER roles on every authenticated request** | §2.2 — authorities resolve from the database per request | LAZY plus join fetch, or cache the authority list per session |
| 4 | **Single backend instance** | One container; no horizontal scaling | Multiple instances behind a load balancer — but STOMP sessions are in-memory, so this needs a shared broker first |
| 5 | **In-memory STOMP broker** | `SimpleBrokerMessageHandler` | A relay (RabbitMQ/ActiveMQ) before adding instances |
| 6 | **In-memory login rate limiter** | `ConcurrentHashMap` in `LoginAttemptLimiter` | Shared store, or it resets per instance |
| 7 | **Network path** | 21 ms server-side, 740 ms – 5.6 s from a browser | CDN or `ap-south-1` |

Items 4, 5 and 6 are the same problem in three places: **the application holds per-user state in memory**, which is fine for one instance and wrong for several. That is the real architectural work behind a 10,000-user target, and it is a project rather than a cleanup.

---

## 5. What this audit did not do

Stated so the report is not read as more than it is:

- **No load test was run.** Every scale statement above is reasoning from architecture, not measurement.
- **No production data was modified.** Read-only throughout.
- **No destructive SQL was run**, and none is recommended.
- **The 27 unused database roles were left alone** — retiring them is a product decision.
- **Frontend render profiling was not done.** React re-render counts would need the profiler against a real session; the static signals (177 `useQuery` calls, 30 s stale time, background polling off, 137 lazy chunks) are all healthy, but that is not the same as measuring.

---

## 6. Recommended order

1. **Fix the four N+1 loops** — small, safe, and the first thing to break at scale
2. **Remove the two dead items** in SAFE TO REMOVE
3. **Resolve the duplicate dashboard query keys** — correctness
4. **Decide the four REQUIRES REVIEW items**
5. **Raise the connection pool and run an actual load test** — everything above 65 users is presently unmeasured
6. **Then** consider the in-memory-state work in §4 items 4–6

Steps 1–3 are a single safe change. Step 5 is what turns the scalability section from reasoning into fact.

---

*Prepared by Sethubala, AI Engineer · Pixous Technologies · 11 September 2026*
