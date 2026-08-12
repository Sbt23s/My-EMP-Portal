# HR Portal — Test Report & Bug List

**Date:** 12 August 2026
**Tested against:** local `hr` database (a full copy of the live data — 62 users, 4 companies, Flyway 92/92), backend on port 7061
**Live database and the running live backend were not touched.** Every account created for testing was deleted afterwards; the one setting changed on the local copy (a technical-admin password, to make sign-in possible) was put back to its original value and verified.

**C1 and C2 have since been fixed and re-tested — 11/11 (see "Fixes applied" at the end).** Everything else in this report stands as found.

---

## Summary

| | Count |
|---|---|
| Checks run | 50+ |
| Passed | 35 |
| **Critical** | **4** — 2 fixed, 2 open |
| High | 5 — 1 fixed by the same change |
| Medium | 5 |

Two of the four criticals are closed. The two that remain — C3 (invented dashboard figures) and C4 (no separation between companies) — still stop this being sellable as it stands.

---

# CRITICAL

## C1 — Any employee can read every salary in the company — **FIXED**

**Reproduced.** A brand-new account with the plain `IT_EMP` role, created seconds earlier, asked for `/api/payroll/salaries` and was given another person's full pay breakdown:

```json
{"userId":1,"employeeName":"Arun Kumar","employeeCode":"EMP0001",
 "basicSalary":35000.00,"hra":14000.00,"allowances":6000.00,
 "ptAmount":200.00,"grossSalary":55000.00}
```

The test copy holds one salary record, so one came back. Live has salary structures for the workforce, and the endpoint returns **all of them** — there is no filter by user.

**Cause** — `PayrollController.java:49`

```java
@GetMapping("/salaries")
@PreAuthorize("hasAuthority('PAYROLL_VIEW')")
public ApiResponse<List<SalaryStructureResponse>> listSalaries()
```

`PAYROLL_VIEW` is seeded as *"View own payslips"* and granted to every employee role. It is being used here to guard *everyone's* salaries.

**Also wrong in the other direction:** `IT_HR` — the HR / payroll manager role — is **not** granted `PAYROLL_VIEW`, so it gets 403 on the same call. The people who should see salaries cannot; the people who should not, can.

**Impact:** every employee can read every colleague's pay. This is the single most serious finding.

---

## C2 — Module on/off breaks the module list — **FIXED**

**Reproduced, in order:**

| Step | Result |
|---|---|
| `GET /api/technical-admin/companies/1/modules` | `200` — empty list |
| `POST` same path, `{"moduleCode":"ATTENDANCE","enabled":true}` | `200` — saved |
| `GET` the list again | **`500`** |

```
HttpMessageNotWritableException:
Could not initialize proxy [com.pixous.hrportal.modules.org.Company#1] - no session
```

Once **any** module row exists for a company, that company's module list can no longer be read. Toggling off then fails too (`500`).

**Cause** — three things meeting:

- `CompanyModule.company` is `@ManyToOne(fetch = FetchType.LAZY)` (`CompanyModule.java:15`)
- `spring.jpa.open-in-view: false` (`application.yml:40`)
- `TechnicalAdminModuleController.getCompanyModules` returns the entity list straight to Jackson

Jackson then tries to serialise the lazy `Company` proxy after the Hibernate session has closed.

The save worked; only reading back fails. So the setting is in the database and the screen cannot show it.

**Impact:** Module Management cannot be used. This became reachable now that the page reads the server instead of browser storage — before this change the failure was hidden, because the page was reading its own local copy.

---

## C3 — The executive dashboard reports invented figures

`GET /api/dashboard/executive` returns:

```json
"departmentBreakdown": {"Engineering": 15, "Sales": 8, "HR": 4},
"monthlyAttendanceTrend": [{"month":"Jan","present":95,"absent":5}, ...],
"payrollCosts": [{"month":"Jan","cost":1500000}, ...]
```

What the database actually holds:

| Figure shown | Reality |
|---|---|
| Engineering: 15 people | Engineering has **0** |
| Sales: 8 people | **There is no Sales department** |
| Attendance 95% / 92% / 97% | `attendance` table has **0 rows** |
| Payroll cost ₹15,00,000 | Not derived from anything |
| Headcount 32 | ✅ correct — matches 32 enabled users |

**Cause** — `DashboardService.java:301`

```java
Map<String, Long> departmentBreakdown = Map.of("Engineering", 15L, "Sales", 8L, "HR", 4L);
List.of(Map.of("month","Jan","present",95,"absent",5), ...);
Map.of("month","Jan","cost",1500000);
```

Hard-coded in the service. Headcount and a few other tiles are real; these three are not.

**Impact:** the screen a director looks at is partly fabricated, and there is nothing on it saying which parts.

---

## C4 — No separation between companies

| Check | Result |
|---|---|
| Entities extending `TenantEntity` | **0** (of 59) |
| `company_id` column on `attendance`, `leave_requests`, `payslips`, `tasks`, `tickets`, `assets`, `communities`, `salary_structures`, `expense_claims`, `audit_log` | **absent on all** |
| `users` | ✅ has `company_id`, and the filter works |

The machinery exists — `TenantFilterAspect` enables a Hibernate filter before every controller and service call — but only `User` declares that filter, and the other tables have no column to filter on.

Nothing is leaking **today**, for one reason only: there is a single real tenant (all 62 users belong to company 4). **The day a second customer is added, that customer sees the first customer's attendance, payroll, tasks, tickets and chat.**

This is a schema change (a `company_id` column on ~50 tables, backfilled, plus every write path setting it) and cannot be done as a small patch.

---

# HIGH

## H1 — Five roles carry no permissions at all

```
COMPANY_ADMIN   0 permissions
HR_MANAGER      0
TEAM_LEAD       0
EMPLOYEE        0
BOARD_ADMIN     0
```

These are precisely the roles the technical-admin "create user" form offers. An account created as `HR_MANAGER` gets no HR access whatever; it behaves exactly like a bare employee — verified in the matrix below, where those four columns are identical to a self-service user.

The roles that do work (`IT_HR`, `IT_MGR`, `IT_EMP`, `SUPER_ADMIN`…) come from the V8 seed. The five empty ones were created by the application on 11 Aug and never given grants.

**This is the likely explanation for the "logged in but only Dashboard visible" screen.**

## H2 — HR cannot see salaries

`IT_HR` → `403` on `/payroll/salaries`. See C1; the same misassigned permission causes both halves.

## H3 — Live Twilio credentials are committed to git

```
<!-- backend/src/main/resources/application-dev.yml:23  account-sid: 
backend/src/main/resources/application-dev.yml:24  auth-token:  c5b4fb8e4f2ff7db7a7dd50aeecadea3
```

Anyone with the repository can send SMS on that account. An older live database password is in the history as well. Both need rotating at the provider — changing the file is not enough once it has been committed.

## H4 — Two live accounts still use the seeded password

`Test1234@` appears in the seed migration, in the repository, and in this report. Two accounts on live still have that hash, one of them `admin` (SUPER_ADMIN). `hr` / `Hr@123` likewise.

## H5 — No tests

```
backend/src/test → 0 files
```

There is nothing to catch a regression. The Flyway incident earlier in this session would have been caught in seconds by one startup test.

---

# MEDIUM

## M1 — A missing query parameter returns 500

`GET /api/attendance/me` without `from`/`to` → **500** and *"Unhandled exception"* in the log. It should be `400`.

`GlobalExceptionHandler` maps `ApiException`, `MethodArgumentNotValidException`, `BadCredentialsException`, `AccessDeniedException`, `SecurityException`, `IllegalArgumentException`, then falls through to `Exception` → 500. `MissingServletRequestParameterException` and `MethodArgumentTypeMismatchException` are not handled.

Affects `/attendance/me`, `/calendar/events`, `/reports/attendance` and anything else with a required parameter. **All three work correctly once the parameters are supplied** (verified 200).

## M2 — `UserSummary` carries a `password` field

The user list DTO returns a `password`, and the technical-admin table shows it behind an eye toggle. Passwords should not leave the server in any form.

## M3 — "Fresh Start" is one click from wiping the company

A `SUPER_ADMIN` sees the button in the sidebar; `deploy/fresh-start.sql` empties the tenant. No typed confirmation, no backup, no audit entry.

## M4 — V91, V92, V93 are untracked in git

```
?? V91__saas_control_center.sql
?? V92__fix_saas_column_types.sql
?? V93__system_files_blob.sql
```

They exist only on this machine. A clone would build a different schema. **V93 creates `system_files` and has not yet run on live — it will apply automatically the next time the live backend restarts.** (It is additive — `CREATE TABLE` only — so it is safe; you should just know it is coming.)

## M5 — The hosted database allows 20 connections in total

Reached during this session with a single tenant. It cannot support several customers.

---

# Role access matrix

One throwaway account per role, signed in, every endpoint probed, accounts deleted afterwards.
`200` = allowed · `403` = refused · `500` = server error · `422` = bad request from the test itself

| Endpoint | SUPER_ADMIN | IT_HR | IT_MGR | IT_TL | IT_EMP | COMPANY_ADMIN | HR_MANAGER | TEAM_LEAD | EMPLOYEE |
|---|---|---|---|---|---|---|---|---|---|
| Dashboard (self) | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 |
| Dashboard (executive) | 200 | 403 | 403 | 403 | 403 | 403 | 403 | 403 | 403 |
| Employees list | 200 | 200 | 200 | 200 | 403 | 403 | 403 | 403 | 403 |
| My profile | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 |
| My team | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 |
| Attendance (team) | 200 | 200 | 200 | 200 | 403 | 403 | 403 | 403 | 403 |
| Leave balances | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 |
| Leave (mine) | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 |
| Leave types | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 |
| Payslips | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 |
| **Salaries** | 200 | **403** | 200 | 403 | **200** | 403 | 403 | 403 | 403 |
| Assets (mine) | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 |
| Assets (all) | 200 | 200 | 200 | 403 | 403 | 403 | 403 | 403 | 403 |
| Helpdesk (mine) | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 |
| Helpdesk (all) | 200 | 200 | 200 | 403 | 403 | 403 | 403 | 403 | 403 |
| Tasks | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 |
| Work reports | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 |
| Claims | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 |
| Complaints | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 |
| Communities | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 |
| Notifications | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 |
| Onboarding | 200 | 200 | 200 | 403 | 403 | 403 | 403 | 403 | 403 |
| Audit log | 200 | 200 | 200 | 403 | 403 | 403 | 403 | 403 | 403 |
| Presence | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 |

Two things to read off it:

- **The Salaries row** — an employee is allowed, HR is refused. C1 / H2.
- **The last four columns are identical to a bare employee.** `COMPANY_ADMIN`, `HR_MANAGER`, `TEAM_LEAD` and `EMPLOYEE` grant nothing. H1.

---

# What passed

| Area | Result |
|---|---|
| Employee sign-in (`admin`) | ✅ |
| Technical admin sign-in | ✅ |
| Create employee → sign in with it | ✅ |
| Edit employee (`PUT /users/{id}`) | ✅ |
| Password reset — **and the new password really signs in** | ✅ |
| Delete employee | ✅ |
| Same operations as `TECHNICAL_ADMIN` | ✅ (the authority added this session works) |
| Companies read from the database | ✅ 4 returned |
| Module save (`POST`) | ✅ persists — only the read-back fails, C2 |
| No token → 401 | ✅ |
| Invalid token → 401 | ✅ |
| Dashboard headcount vs database | ✅ 32 = 32 |
| `/attendance/me`, `/calendar/events`, `/reports/attendance` with parameters | ✅ 200 |
| Backend compile | ✅ |
| Frontend typecheck | ✅ 0 errors |
| Frontend production build | ✅ |

---

# Suggested order

**Before anyone else uses it**
1. C1 — salary leak. A one-line authority change plus deciding what HR should hold.
2. H3 — rotate the Twilio credentials at the provider.
3. H4 — change the two seeded passwords on live.

**Before selling**
4. C2 — module on/off. Return a small DTO instead of the entity; contained fix.
5. H1 — grant the five empty roles their permissions, or stop offering them.
6. C3 — either compute the dashboard figures or label them as samples.
7. M3 — put a real confirmation on Fresh Start.

**Before a second customer**
8. C4 — tenant isolation. Staged: add the columns and backfill, fill them on write, then enable the filter one module at a time, then prove it with two tenants.
9. H5 — tests, starting with one that boots the app and one per critical path above.

---

---

# Fixes applied

Two changes, both backend, both re-tested on the local copy. **11 of 11 checks passed.** No URL, request shape or response shape changed, so nothing on the client needed touching.

## C1 — salary is private again

`PayrollController.java`. `PAYROLL_VIEW` still guards the endpoints; what changed is what you are handed once you are through it.

```java
private boolean maySeeEveryonesPay() {
    return SecurityUtils.hasAuthority("PAYROLL_RUN")
        || SecurityUtils.hasAuthority("PAYROLL_APPROVE")
        || SecurityUtils.hasAuthority("USER_MANAGE")
        || SecurityUtils.hasAuthority("EMPLOYEE_MANAGE");
}
```

- `GET /payroll/salaries` — an employee now receives a list holding their own row and nothing else. Same endpoint, same shape.
- `GET /payroll/salary/{userId}` — the same hole existed here and was arguably worse, because it was targeted: any employee could name any id. Refused now unless it is your own id or you are one of the four above.
- Admin, HR and payroll keep the full list. **This closes H2 as well** — `IT_HR` holds `USER_MANAGE`, so HR can finally see salaries.

| Check | Result |
|---|---|
| Employee sees no one else's salary (was: Arun Kumar, 55000) | PASS |
| Employee refused another person's salary by id | PASS — 403 |
| Employee can still read their own | PASS — 200 |
| Admin still sees everything | PASS |

## C2 — module on/off works end to end

`TechnicalAdminModuleController.java`. The endpoints return a flat record instead of the entity:

```java
public record ModuleView(Long id, String moduleCode, boolean enabled, String featureFlags)
```

The lazy `Company` reference never reaches Jackson, so there is no proxy left to initialise after the session closes. The company is in the URL already and was never needed in the body.

Applied in three places: `GET` (the one that returned 500), `POST`, and `simulate-access` — which had the identical fault and had not been noticed.

| Check | Result |
|---|---|
| Read modules, empty | PASS — 200 |
| Toggle ON saved | PASS — 200 |
| **Read back after saving** | **PASS — 200 (was 500)** |
| Saved value really is ON | PASS |
| Toggle OFF | PASS — 200 |
| OFF persisted and reads back | PASS |
| simulate-access | PASS — 200 |

## Still open

C3 (invented dashboard figures), C4 (no separation between companies), H1 (five roles with no permissions), H3–H5, M1–M5.

---

*Say which to take next.*
