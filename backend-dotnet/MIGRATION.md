# Backend migration: Spring Boot 3.5 → ASP.NET Core 10

The React frontend, the MySQL database and every API contract stay as they are.
The only intentional change is the implementation language and framework.

## Ground rules

1. **The database is live.** No schema is dropped, reset or recreated. EF Core
   is configured against the existing tables with `ddl-auto` equivalent to
   `Validate` — it maps to what Flyway has already built and never migrates.
   Flyway keeps owning the schema until every module is across, and the .NET
   side reads `flyway_schema_history` rather than writing its own.

2. **API contracts are frozen.** Same paths, same verbs, same JSON field names,
   same status codes, same error envelope. The frontend must not be able to
   tell. Anywhere the Java response has an oddity — a `data.personList` shape,
   a field emitted under two names — the C# reproduces the oddity.

3. **Business rules are read from the Java, not reasoned out afresh.** Every
   rule in this codebase that looks arbitrary is a bug somebody found in
   production: the punch-direction fallback, the lateness grace, the HR desk
   widening, the payroll per-day divisor. They are ported literally and their
   comments come with them.

4. **A module is not done until it is compared.** Same request against both
   backends, same response, before the row below is ticked.

## Status key

| Mark | Meaning |
|---|---|
| ☐ | not started |
| ◐ | implemented, not yet compared against Java |
| ☑ | implemented and compared: same request → same response |

---

## Phase 0 — foundation

Nothing else can be ported until these exist, because every module depends on
all of them.

| Item | Java | .NET | Status |
|---|---|---|---|
| Solution + project layout | — | `Pixous.HrPortal.*` | ☑ |
| EF Core context, MySQL provider (Pomelo) | `JpaConfig` | `HrPortalDbContext` | ◐ |
| Base entity / auditing | `BaseEntity` | `BaseEntity` | ◐ |
| Tenant filter | `TenantFilterAspect`, `TenantEntity` | EF global query filter | ◐ |
| Response envelope | `ApiResponse`, `PageResponse` | same JSON shape | ◐ |
| Error handling | `GlobalExceptionHandler`, `ApiException`, `ErrorCode` | exception middleware | ◐ |
| JWT issue + validate | `JwtService`, `JwtAuthenticationFilter` | JwtBearer | ☐ |
| Principal + claims | `UserPrincipal`, `TechnicalAdminPrincipal` | `ClaimsPrincipal` | ☐ |
| Authorisation | 145 × `@PreAuthorize` | policies + `[Authorize]` | ☐ |
| `SecurityUtils.currentUserId()` | static | `IHttpContextAccessor` accessor | ◐ |
| Login throttle | `LoginAttemptLimiter` | same, in-memory | ☐ |
| CORS | `WebConfig` | `AddCors` | ◐ |
| Config binding | `AppProperties` | `IOptions<AppSettings>` | ☐ |
| Async executor | `AsyncConfig` | `Task` / `IHostedService` | ☐ |
| Cache | `CacheConfig` (Redis, optional) | `IDistributedCache` | ☐ |
| Real time | `WebSocketConfig` STOMP/SockJS | SignalR — **see Conflicts** | ☐ |
| Scheduling | 7 × `@Scheduled` | `BackgroundService` | ☐ |
| Mail | `MailService` | `MailKit` | ☐ |
| SMS | `SmsService` (Twilio) | Twilio SDK | ☐ |
| File storage | `StorageService` | same disk layout | ☐ |
| Working calendar | `WorkCalendar` | port literally | ☐ |
| Platform accounts | `PlatformAccounts` | port literally | ☐ |

---

## Phase 1 — modules

Ordered by what depends on what: `user`, `org` and `auth` first because
everything reads them; the leaf modules last.

| # | Module | Ctrl | Endpoints | Entities | Java LOC | Status |
|---|---|---|---|---|---|---|
| 1 | user | 2 | 29 | 7 | 2,718 | ☐ |
| 2 | org | 3 | 13 | 11 | 1,279 | ☐ |
| 3 | auth | 1 | 15 | 2 | 1,208 | ☐ |
| 4 | notification | 1 | 5 | 1 | 347 | ☐ |
| 5 | attendance | 1 | 12 | 1 | 1,843 | ☐ |
| 6 | leave | 2 | 28 | 4 | 2,714 | ☐ |
| 7 | wfh | 1 | 9 | 1 | 1,067 | ☐ |
| 8 | payroll | 2 | 29 | 5 | 3,445 | ☐ |
| 9 | biometric | 2 | 11 | 2 | 4,097 | ☐ |
| 10 | community | 2 | 29 | 6 | 2,055 | ☐ |
| 11 | admin | 8 | 19 | 3 | 1,815 | ☐ |
| 12 | task | 1 | 16 | 2 | 1,330 | ☐ |
| 13 | chatbot | 1 | 10 | 1 | 1,554 | ☐ |
| 14 | helpdesk | 1 | 12 | 2 | 946 | ☐ |
| 15 | workreport | 1 | 13 | 1 | 770 | ☐ |
| 16 | discipline | 1 | 10 | 1 | 796 | ☐ |
| 17 | asset | 1 | 10 | 2 | 525 | ☐ |
| 18 | expense | 1 | 9 | 1 | 546 | ☐ |
| 19 | appreciation | 1 | 8 | 1 | 809 | ☐ |
| 20 | complaint | 1 | 7 | 1 | 800 | ☐ |
| 21 | approvalconfig | 1 | 6 | 2 | 840 | ☐ |
| 22 | audit | 1 | 4 | 1 | 753 | ☐ |
| 23 | requestthread | 1 | 5 | 2 | 532 | ☐ |
| 24 | safety | 1 | 5 | 1 | 408 | ☐ |
| 25 | announcement | 2 | 5 | 1 | 441 | ☐ |
| 26 | performance | 1 | 5 | 2 | 254 | ☐ |
| 27 | onboarding | 1 | 4 | 2 | 257 | ☐ |
| 28 | calendar | 1 | 4 | 1 | 398 | ☐ |
| 29 | presence | 1 | 1 | 0 | 176 | ☐ |
| 30 | file | 1 | 1 | 0 | 75 | ☐ |
| | dashboard, reports, misc | 2 | rest of 340 | — | — | ☐ |

---

## Rules that must survive, verbatim

These are the ones a rewrite loses. Each was a production bug; each has a test
or a comment in the Java saying why it is the way it is. Ported literally, with
the comment.

- **Punch direction** — earliest punch is the arrival, latest is the departure.
  Hikvision's `attendanceStatus` is deliberately ignored: it reported "off work"
  on people walking in and reversed sixteen days of September.
- **Lateness grace is zero** — 09:00 on time, 09:01 one minute late, and the
  value lives in `application.properties` because `.properties` beats `.yml`.
- **Payroll per-day divisor** — calendar days by default
  (`app.payroll.per-day-basis`), and weekends and holidays are never counted as
  absences regardless.
- **Empty attendance does not deduct a whole salary** — a month with no rows.
- **HR is a desk** — `IT_HR`, `CV_HR`, `IT_MGR` together, everywhere.
- **The desk reads the queue; the addressee answers it** — widened visibility,
  unwidened decisions.
- **The SUPER_ADMIN approval override stays removed** — an administrator sees
  everything and decides nothing.
- **Platform accounts are not staff** — excluded from the directory query
  itself, so paging stays correct.
- **Attendance is compulsory for the CTO** regardless of role visibility.
- **`personCode` cannot be edited** on Hikvision; mappings are manual and the
  hourly sync must not undo them.
- **Rate limit 4/sec** against Hikvision, one below the documented five.
- **Announcement receipts** use every active employee, not the member list.

---

## Conflicts to raise before changing behaviour

Recorded here as they are found. Nothing on this list is worked around
silently.

### 1. STOMP over SockJS → SignalR — **frontend change unavoidable**

The React client speaks STOMP over SockJS (`@stomp/stompjs`, `sockjs-client`)
against `/ws`, subscribing to `/topic/attendance` and
`/topic/notifications/{userId}`. SignalR is a different wire protocol; a STOMP
client cannot talk to a SignalR hub.

Three options, none of them invisible:

- **a.** Swap the client to `@microsoft/signalr`. Smallest change to the
  server, but it touches `useNotifications`, `useAttendanceLive`, `useCalls`
  and `usePresence` — and the brief says the frontend does not change.
- **b.** Implement a STOMP-over-WebSocket endpoint in ASP.NET so the existing
  client connects unchanged. No first-party support; a hand-written frame
  parser under the live notification path.
- **c.** Keep the Java service for real time only, alongside .NET. Contradicts
  "no half-Java backend".

**This needs your decision.** I would take (a) and treat the four hooks as part
of the migration — it is the only one of the three that is a normal amount of
work with a normal amount of risk.

### 2. Flyway → EF Core

Flyway stays. EF Core is configured read-only against the schema and never
migrates. 153 migrations are not re-expressed — they have already run, and
re-running them against live data is the one thing that could lose it. New
schema changes keep going through Flyway until the Java side is retired, then
move to a Flyway .NET runner or EF's SQL-script workflow.

No conflict, but recording the decision.

### 3. `@Async` + `REQUIRES_NEW` self-invocation

Several services depend on Spring's proxy semantics — `BiometricAttendanceProcessor`
and `PayslipService` inject a `@Lazy` self-reference so `REQUIRES_NEW` actually
starts a new transaction. .NET has no proxy: the equivalent is an explicit new
`DbContext` scope. Behaviour is preserved; the shape of the code is not.

---

## Progress log

Appended as modules land. Each entry says what was compared and how.

### Phase 0, part 1 — the layer everything sits on

Solution scaffolded: `Pixous.HrPortal.Api` (controllers, middleware, hosting),
`.Domain` (entities, the envelope, the error types), `.Infrastructure`
(EF Core, security context). .NET 10, builds clean.

Ported literally, checked against the Java source line by line:

- `ApiResponse` / `PageResponse` — same field names, same order, nulls omitted
  as Jackson's `NON_NULL` did, zero-based `page` as Spring Data emits.
- `ErrorCode` — all twelve, each mapped to the status the Java enum gave it,
  including the two that share 422.
- `ApiException` — the three factories, message text included, because
  `notFound("User")` produces a string a person reads on screen.
- `BaseEntity` / `TenantEntity` — column names pinned rather than left to a
  naming policy, since the tables already exist.
- Tenant filter — EF global query filter, keeping the deliberate hole the
  Hibernate filter had: a null `company_id` is visible to everybody, which is
  how holidays and seeded reference data are shared.
- Auditing — `SaveChanges` fills the same four columns, and `created_*` stay
  unwritable on update as `updatable = false` said.

Decisions worth recording:

- **EF never migrates.** No `Migrations` folder, no `Database.Migrate()`, no
  `EnsureCreated`. Flyway owns the schema. `ValidateSchemaAsync` restores the
  boot-time failure `ddl-auto=validate` gave us — that check caught a TINYINT
  mapped to an Integer and took the app down at startup rather than at the
  first query, which is the behaviour worth keeping.
- **Built-in OpenAPI removed.** `Microsoft.OpenApi` 2.x carries a high-severity
  advisory and 3.x breaks the `AspNetCore.OpenApi` 10.0.9 source generator.
  Swagger was a developer tool on the Java side, not part of the contract; it
  can come back through Swashbuckle once the versions settle.
- **No `SecurityUtils` static.** Java read a thread-local from anywhere; .NET
  has no equivalent that survives an `await` safely, so `ICurrentUser` is
  injected. Every `SecurityUtils.currentUserId()` call site takes it as a
  dependency.

IIS: `web.config` with in-process hosting, the 25 MB body limit matched to
`spring.servlet.multipart.max-request-size`, and **WebSockets enabled** — off by
default in IIS, and without it SignalR silently degrades to long polling, which
is the hardest kind of problem to notice.

Nothing is wired to the live database yet and no endpoint is served. The Java
backend is untouched and remains the only thing deployed.
