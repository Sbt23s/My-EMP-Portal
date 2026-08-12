# Pixous HR Portal — Test Specification

| | |
|---|---|
| **Version** | 1.0 |
| **Date** | 12 August 2026 |
| **Audience** | QA team, engineering |
| **Companion** | `docs/REQUIREMENTS_SPECIFICATION.md` |

---

## Where this project stands on testing

```
backend/src/test  →  0 files
web/src/**.test.* →  0 files
```

**There is no automated test of any kind.** Every case in this document is currently a manual one. The suite described here does not exist yet; this is the specification for building it.

Why that matters more than usual on this codebase: during one day of work in August 2026, seven defects were found that a test would have caught in seconds — a public endpoint returning a password hash, every employee able to read every salary, a module list that returned 500 as soon as a module was saved, an id-based read of any other company's employee. Each was found by hand. Each will come back the first time someone refactors, and nothing will notice.

Three of those defects also share a lesson worth stating plainly: **a fix that has not been re-tested is not a fix.** Tenant isolation for holidays was "fixed" three times on 12 August. Each time the change was reasonable, compiled, and did nothing. Only the test showed it. Had there been no test, the report would have said "isolation complete" and been wrong.

---

## 1. What good looks like here

| Layer | Tool | Target | Priority |
|---|---|---|---|
| Unit — services | JUnit 5 + Mockito | 70% of service branches | HIGH |
| Slice — controllers | `@WebMvcTest` + MockMvc | Every endpoint's status codes and authorisation | **CRITICAL** |
| Integration — data | `@SpringBootTest` + Testcontainers MySQL | Migrations, tenant scoping, transactions | **CRITICAL** |
| Contract — API | REST Assured | Response envelope stability | MEDIUM |
| Frontend unit | Vitest + Testing Library | Auth context, hooks, guards | HIGH |
| End to end | Playwright | Six critical journeys | HIGH |

**Testcontainers, not H2.** H2 is on the classpath, and it will lie to you: this application depends on MySQL behaviour that H2 does not reproduce — `LONGBLOB`, `utf8mb4` for emoji in chat, MySQL-specific Flyway migrations, and the Hibernate tenant filter. A green H2 suite would be worse than no suite.

## 2. Naming and layout

```
backend/src/test/java/com/pixous/hrportal/
  ├── unit/          <service>Test.java          fast, mocked
  ├── web/           <controller>WebTest.java    MockMvc slice
  ├── integration/   <area>IT.java               Testcontainers
  └── security/      TenantIsolationIT.java      the ones that matter most
```

`methodUnderTest_condition_expectedResult`, e.g. `listSalaries_callerIsPlainEmployee_returnsOnlyTheirOwn`.

## 3. Fixtures

```java
// A person in a given company, with a given role.
User employee(Long companyId, String roleCode);

// Two tenants with a full set of data each. Every isolation test needs this.
record TwoTenants(Company a, Company b, User adminA, User adminB, User empA, User empB) {}
TwoTenants twoTenants();
```

Every isolation case needs **data on both sides**. A test where the second company is empty proves nothing: an empty result looks the same whether isolation works or the query simply found nothing.

---

# 4. Priority 1 — Regression tests for the defects already found

Write these first. Each one reproduces a defect that was real in this codebase in August 2026. Six are fixed and must not come back; two are open and these tests are their definition of done.

## 4.1 Payroll — salary must be private

Defect: `/payroll/salaries` was guarded by `PAYROLL_VIEW`, a permission every employee holds. A new employee account read another person's full salary. Fixed 12 Aug.

| ID | Case | Expected |
|---|---|---|
| UT-PAY-001 | `IT_EMP` calls `/payroll/salaries` | Own row only, never another person's |
| UT-PAY-002 | `IT_EMP` calls `/payroll/salary/{someoneElseId}` | 403 |
| UT-PAY-003 | `IT_EMP` calls `/payroll/salary/{ownId}` | 200 |
| UT-PAY-004 | `IT_HR` calls `/payroll/salaries` | All rows — HR was wrongly refused before the fix |
| UT-PAY-005 | `PAYROLL_RUN` holder calls it | All rows |
| UT-PAY-006 | No token | 401 |

```java
@Test
void listSalaries_callerIsPlainEmployee_returnsOnlyTheirOwn() throws Exception {
    var alice = employee(company.getId(), "IT_EMP");
    var bob   = employee(company.getId(), "IT_EMP");
    salaryFor(bob, 55_000);

    mockMvc.perform(get("/api/payroll/salaries").with(token(alice)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data[*].userId", not(hasItem(bob.getId().intValue()))));
}
```

Assert on **absence of the other person**, not on a count. A count passes for the wrong reason when the fixture is thin.

## 4.2 Tenant isolation

Defect A: `/users/{id}` was not scoped — Hibernate filters apply to queries but not to `findById`. A company-1 HR read, and could have edited or deleted, a company-4 account. Fixed 12 Aug.
Defect B: holidays and leave types still cross companies. **Open — GAP-17.**

| ID | Case | Expected | State |
|---|---|---|---|
| UT-TEN-001 | Tenant A lists employees | Only A's | passing |
| UT-TEN-002 | Tenant A reads B's employee by id | 404, not 403 | passing since 12 Aug |
| UT-TEN-003 | Tenant A edits B's employee | 404, and B's row unchanged | passing |
| UT-TEN-004 | Tenant A deletes B's employee | 404, and B's row still present | passing |
| UT-TEN-005 | Tenant A resets B's employee's password | 404, and B's old password still signs in | passing |
| UT-TEN-006 | Tenant A lists holidays | Only A's | **FAILING — GAP-17** |
| UT-TEN-007 | Tenant A lists leave types | Only A's | **FAILING — GAP-17** |
| UT-TEN-008 | Tenant A lists salaries | Only A's | passing |
| UT-TEN-009 | Tenant A lists communities and chat contacts | Only A's | passing |
| UT-TEN-010 | Tenant A reads settings | Only A's | untested |
| UT-TEN-011 | Tenant A lists departments and designations | Only A's | untested — the earlier "isolated" reading may be a fixture artefact |
| UT-TEN-012 | Technical admin | Sees both, by design | untested |

```java
@Test
void readingAnotherTenantsEmployee_isNotFound() throws Exception {
    var t = twoTenants();
    mockMvc.perform(get("/api/users/{id}", t.empB().getId()).with(token(t.adminA())))
           .andExpect(status().isNotFound());   // not 403 — 403 confirms the id exists
}

@Test
void deletingAnotherTenantsEmployee_leavesItAlone() throws Exception {
    var t = twoTenants();
    mockMvc.perform(delete("/api/users/{id}", t.empB().getId()).with(token(t.adminA())))
           .andExpect(status().isNotFound());
    assertThat(userRepository.findById(t.empB().getId())).isPresent();  // the point
}
```

UT-TEN-004 asserts the row **survives**, not just the status code. A 404 with the row gone would pass a status-only test.

> **UT-TEN-006 and UT-TEN-007 are the definition of done for GAP-17.** Write them now, watch them fail, and use them to check the fix. Do not mark the gap closed on a code change alone — that is exactly the mistake made three times on 12 August.

## 4.3 Authentication and session

| ID | Case | Expected |
|---|---|---|
| UT-AUTH-001 | Correct credentials | 200 with access and refresh tokens |
| UT-AUTH-002 | Wrong password | 401, message reveals nothing about which part was wrong |
| UT-AUTH-003 | Unknown username | 401, indistinguishable from UT-AUTH-002 |
| UT-AUTH-004 | Disabled account, right password | 403 "Account is disabled" |
| UT-AUTH-005 | Repeated failures | Locked; correct password then also refused |
| UT-AUTH-006 | Expired token | 401 |
| UT-AUTH-007 | Token signed with another key | 401 |
| UT-AUTH-008 | Token with the role claim edited | Original permissions, or 401 — never the claimed ones |
| UT-AUTH-009 | No token on a protected endpoint | 401 |
| UT-AUTH-010 | Refresh rotates the token | Old refresh token stops working |
| UT-AUTH-011 | Sign out | Refresh token no longer accepted |
| UT-AUTH-012 | Cached user with no token present | Treated as signed out — frontend, `AuthContext` |
| UT-AUTH-013 | `/auth/me` returns 401 | Session cleared |
| UT-AUTH-014 | `/auth/me` fails on a network error | Cached user kept, not signed out mid-task |

UT-AUTH-012 to 014 cover a real defect: the portal treated a leftover `localStorage` entry as a session, showing a signed-in shell with no token behind it — blank name, no permissions, every panel empty. Fixed 12 Aug.

## 4.4 Endpoints that must not exist, and files that must not execute

| ID | Case | Expected |
|---|---|---|
| UT-SEC-001 | `GET /api/public/debug/**` | 404 — a debug endpoint returned a password hash **and confirmed password guesses**, unauthenticated. Deleted 12 Aug |
| UT-SEC-002 | Any response body, anywhere | Never contains `password_hash` or a bcrypt string |
| UT-SEC-003 | Upload `evil.html` | Stored as `.bin`; served as octet-stream, not `text/html` |
| UT-SEC-004 | Upload `evil.svg` | Stored as `.bin` — SVG can carry script |
| UT-SEC-005 | Upload `photo.png` | Stays `.png`, served inline; the ordinary case must keep working |
| UT-SEC-006 | Fetch a non-image file | `Content-Disposition: attachment` and `X-Content-Type-Options: nosniff` |
| UT-SEC-007 | `GET /api/files/../../etc/passwd` | 400 |
| UT-SEC-008 | Same, URL-encoded | 400 |
| UT-SEC-009 | 500 in production profile | Body carries a reference, no class or package names |
| UT-SEC-010 | 500 in dev profile | Detail present — the convenience is deliberate |
| UT-SEC-011 | WebSocket from an unlisted origin | Refused |

```java
@Test
void uploadingHtml_isNotServedAsHtml() throws Exception {
    var file = new MockMultipartFile("file", "evil.html", "text/html",
                                     "<script>alert(1)</script>".getBytes());
    String path = storageService.store(file, "docs");
    assertThat(path).endsWith(".bin");

    mockMvc.perform(get("/api/files/" + path))
           .andExpect(header().string("Content-Type", not(containsString("text/html"))))
           .andExpect(header().string("X-Content-Type-Options", "nosniff"));
}
```

## 4.5 Module switching

Defect: reading a company's module list returned 500 as soon as one module was saved — a lazy `Company` reference serialised after the session closed. The save worked; only the read failed, so the setting was in the database and the screen could not show it. Fixed 12 Aug.

| ID | Case | Expected |
|---|---|---|
| UT-MOD-001 | Read modules for a company with none | 200, empty |
| UT-MOD-002 | Enable a module | 200, persisted |
| UT-MOD-003 | **Read the list again afterwards** | **200 — this was the 500** |
| UT-MOD-004 | Disable it, read again | 200, shows disabled |
| UT-MOD-005 | `simulate-access` | 200 — same defect was present here and unnoticed |
| UT-MOD-006 | Any module response | Contains no `company` object |
| UT-MOD-007 | Every module code offered | Has a route in `router.tsx` |

UT-MOD-007 is a guard, not a behaviour test: seven modules were once offered with no page behind them, so switching one on gave a company a menu entry leading to the not-found screen.

## 4.6 Parameters and errors

| ID | Case | Expected |
|---|---|---|
| UT-API-001 | `/attendance/me` with no `from`/`to` | **400** naming the parameter — was 500 |
| UT-API-002 | `/calendar/events` with no dates | 400 |
| UT-API-003 | `/reports/attendance` with no dates | 400 |
| UT-API-004 | `from` that is not a date | 400 |
| UT-API-005 | All three with valid dates | 200 |
| UT-API-006 | Malformed JSON body | 400 |
| UT-API-007 | Missing required multipart file | 400 |

---

# 5. Priority 2 — Business rules

## 5.1 Attendance

| ID | Case | Expected |
|---|---|---|
| UT-ATT-001 | Punch in | Recorded with the time |
| UT-ATT-002 | Punch in twice in a day | Rejected |
| UT-ATT-003 | Punch out without punching in | Rejected |
| UT-ATT-004 | Punch out | Hours computed |
| UT-ATT-005 | Punch in after the shift start | Late minutes recorded |
| UT-ATT-006 | Face punch, matching face | Accepted, verified |
| UT-ATT-007 | Face punch, different face | Rejected |
| UT-ATT-008 | Face punch with no enrolment | Clear message, not a 500 |
| UT-ATT-009 | Team view as `ATTENDANCE_TEAM` | Team only |
| UT-ATT-010 | Team view without it | 403 |
| UT-ATT-011 | Range crossing a month | Correct totals |
| UT-ATT-012 | Range where `from` is after `to` | 400 |

## 5.2 Leave

| ID | Case | Expected |
|---|---|---|
| UT-LV-001 | Apply within balance | Created, pending |
| UT-LV-002 | Apply beyond balance | Rejected or flagged as loss of pay |
| UT-LV-003 | Overlapping an existing request | Rejected |
| UT-LV-004 | Approve | Status approved, balance reduced |
| UT-LV-005 | Reject | Status rejected, balance untouched |
| UT-LV-006 | Approve without `LEAVE_APPROVE` | 403 |
| UT-LV-007 | Approve your own request | Rejected |
| UT-LV-008 | Cancel while pending | Allowed |
| UT-LV-009 | Cancel after approval | Rejected, or routed for approval |
| UT-LV-010 | Loss-of-pay preview | Matches what applying actually does |
| UT-LV-011 | Quarterly CL/SL accrual | Correct at a quarter boundary |
| UT-LV-012 | Half-day | Balance moves by 0.5 |

## 5.3 Payroll

| ID | Case | Expected |
|---|---|---|
| UT-PR-001 | Set a salary structure | Gross computed from its parts |
| UT-PR-002 | Run payroll for a month | A payslip per eligible employee |
| UT-PR-003 | Run the same month twice | Rejected, no duplicates |
| UT-PR-004 | Loss of pay | Deducted correctly |
| UT-PR-005 | PF at the configured percentage | Correct |
| UT-PR-006 | Payslip PDF | Generated, correct employee |
| UT-PR-007 | Payslip for someone else | 403 |
| UT-PR-008 | Run payroll without `PAYROLL_RUN` | 403 |

## 5.4 Assets, helpdesk, tasks

| ID | Case | Expected |
|---|---|---|
| UT-AST-001 | Allocate an asset | Status changes, history recorded |
| UT-AST-002 | Allocate one already allocated | Rejected |
| UT-AST-003 | Return | Back to stock |
| UT-AST-004 | Acknowledge someone else's allocation | 403 |
| UT-HD-001 | Raise a ticket | Created, reference issued |
| UT-HD-002 | See another person's ticket without agent rights | 403 |
| UT-HD-003 | Agent resolves | Status and timestamps recorded |
| UT-TSK-001 | Assign with `TASK_ASSIGN` | Created |
| UT-TSK-002 | Assign without it | 403 |
| UT-TSK-003 | Progress beyond 100 | Rejected |

## 5.5 Employee lifecycle

| ID | Case | Expected |
|---|---|---|
| UT-EMP-001 | Create with a duplicate username | 409 |
| UT-EMP-002 | Password under 8 characters | 400 |
| UT-EMP-003 | Username with invalid characters | 400 |
| UT-EMP-004 | Create, then sign in as them | Works — the account is real |
| UT-EMP-005 | Reset password, then sign in with the new one | Works |
| UT-EMP-006 | Reset password, then try the old one | Fails |
| UT-EMP-007 | Offboard | `OFFBOARDED`, disabled, records kept |
| UT-EMP-008 | Offboarded account signs in | Refused |
| UT-EMP-009 | Bulk import 50 rows | All created |
| UT-EMP-010 | Bulk import with one bad row | Reported per row, not one blanket failure |
| UT-EMP-011 | Undo an import | Exactly those accounts removed |

UT-EMP-004 and UT-EMP-005 matter more than they look. Before 12 August the technical-admin screens wrote accounts and password resets to browser storage: the interface reported success, and the person could not sign in. Assert the sign-in, not the 200.

---

# 6. Priority 3 — Frontend

| ID | Case | Expected |
|---|---|---|
| UT-FE-001 | `AuthContext` with no token | Signed out, cache cleared |
| UT-FE-002 | `/auth/me` returns 401 | Session cleared |
| UT-FE-003 | `/auth/me` fails on a network error | Cached user kept |
| UT-FE-004 | `RoleGuard` without the permission | Redirects |
| UT-FE-005 | Sidebar with a module off | Entry hidden |
| UT-FE-006 | Every sidebar link | Resolves to a real route |
| UT-FE-007 | Notifications request fails | "Couldn't load", not "You're all caught up" |
| UT-FE-008 | Dashboard request fails | Error card, no invented figures |
| UT-FE-009 | Employee list with 500 rows | Renders under 2s |
| UT-FE-010 | Token expires mid-session | Refresh, retry, or return to login |

UT-FE-006 catches the class of defect where the sidebar pointed at `/audit-log` while the route was `/audit`.

---

# 7. Priority 4 — End to end

| ID | Journey |
|---|---|
| E2E-001 | Employee: sign in → punch in → apply for leave → check payslip → sign out |
| E2E-002 | Manager: sign in → approve leave → view team attendance → assign a task |
| E2E-003 | HR: create an employee → set salary → run payroll → generate payslip |
| E2E-004 | HR: bulk import → verify → undo |
| E2E-005 | Two tenants: create data in both → confirm neither sees the other |
| E2E-006 | Employee: raise a ticket → agent resolves → employee rates it |

E2E-005 is the one that decides whether this can be sold to a second customer.

---

# 8. Non-functional

| ID | Case | Target |
|---|---|---|
| PERF-001 | Employee list, 500 employees | < 2s — was 40s before batch fetching |
| PERF-002 | Dashboard | < 1s |
| PERF-003 | 50 concurrent sign-ins | No connection-pool exhaustion |
| PERF-004 | Payroll for 500 | < 30s |
| PERF-005 | Main bundle | < 500 kB — currently 817 kB |
| A11Y-001 | Every page | axe-core, no critical violations |
| A11Y-002 | Keyboard only | Every action reachable |
| A11Y-003 | Contrast, both themes | WCAG AA |
| RESP-001 | 360 / 768 / 1920 | No horizontal scroll |

None of the accessibility or responsive cases has been run — there was no browser available during the August audit. They are unknown, not passing.

---

# 9. Coverage targets

| Area | Target | Rationale |
|---|---|---|
| Security and authorisation | **95%** | Where the real defects were |
| Tenant isolation | **100%** | One miss exposes a customer's data to another |
| Payroll | 90% | Money |
| Attendance, leave | 85% | Daily use |
| Everything else | 70% | |
| Frontend | 60% | |

## Definition of done, per change

- [ ] Unit tests for new branches
- [ ] Controller slice test if an endpoint changed
- [ ] **A tenant isolation test if any query changed** — see GAP-17 for why
- [ ] Regression test if a defect was fixed, written to fail first
- [ ] Full suite green

## Suggested order

**Week 1** — Testcontainers running; UT-TEN-001…012; UT-PAY-001…006; UT-SEC-001…011.
Those three groups cover every defect found in August that could expose data.

**Week 2** — UT-AUTH; UT-MOD; UT-API; UT-EMP.
**Week 3** — Business rules: attendance, leave, payroll.
**Week 4** — Frontend and E2E.
**Ongoing** — Every fix arrives with the test that would have caught it.

---

## One closing note for the QA team

Two of the cases in this document are written to fail: **UT-TEN-006** and **UT-TEN-007**. Please write them exactly as specified and confirm they fail before anyone attempts GAP-17 again. Three fixes have already been applied to that defect, all of them plausible, none of them effective. The next attempt should be judged by those two tests going green, and by nothing else.

---

*Prepared 12 August 2026. Every defect referenced was reproduced against a copy of production data, not inferred from reading the code.*
