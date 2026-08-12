# Pixous HR Portal — Software Requirements Specification

| | |
|---|---|
| **Product** | Pixous HR Portal — multi-tenant HR management system |
| **Version** | 1.0 |
| **Date** | 12 August 2026 |
| **Status** | Draft for review |
| **Audience** | Engineering, QA, Product, Implementation |

> **How this document was written.** Every requirement below was read out of the running system — the 269 API endpoints, the 74 database tables, the 92 applied migrations and the React routes — and where behaviour was uncertain it was confirmed by calling the API against a full copy of production data. Requirements marked **AS-BUILT** describe what the software does today. Requirements marked **GAP** describe something the product needs and does not yet have; each names the evidence. Nothing here is aspirational filler.

---

## Table of contents

1. [Purpose and scope](#1-purpose-and-scope)
2. [Definitions](#2-definitions)
3. [System overview](#3-system-overview)
4. [Actors and roles](#4-actors-and-roles)
5. [Role-wise functional requirements](#5-role-wise-functional-requirements)
   - 5.1 [Employee](#51-employee)
   - 5.2 [Team Leader](#52-team-leader)
   - 5.3 [Manager](#53-manager)
   - 5.4 [HR / Payroll Manager](#54-hr--payroll-manager)
   - 5.5 [Finance Officer](#55-finance-officer)
   - 5.6 [Asset Manager](#56-asset-manager)
   - 5.7 [CEO / Executive](#57-ceo--executive)
   - 5.8 [Super Admin](#58-super-admin)
6. [Module requirements](#6-module-requirements)
7. [Data requirements](#7-data-requirements)
8. [Security requirements](#8-security-requirements)
9. [Multi-tenancy requirements](#9-multi-tenancy-requirements)
10. [Integration requirements](#10-integration-requirements)
11. [Non-functional requirements](#11-non-functional-requirements)
12. [Constraints](#12-constraints)
13. [Open gaps register](#13-open-gaps-register)
14. [Acceptance criteria](#14-acceptance-criteria)

---

# 1. Purpose and scope

## 1.1 Purpose

The Pixous HR Portal manages the working life of an employee from the day they join to the day they leave: attendance, leave, payroll, assets, support, tasks, documents and internal communication. It is built to serve several client companies from one deployment, each seeing only its own people and records.

## 1.2 In scope

Employee self-service · attendance including face verification · leave and permission workflows · payroll and payslips · expense and travel claims · asset allocation · helpdesk and complaints · tasks and work reports · internal chat, calls and communities · document storage · a company calendar · reporting and audit · a technical control centre for onboarding client companies and switching modules on and off.

## 1.3 Out of scope

Recruitment and applicant tracking · learning management · performance appraisals as a working feature · public website and SEO surfaces · payment gateway processing · statutory e-filing.

> These appeared in an earlier module list with no implementation behind them and were removed from the interface on 12 Aug 2026 so that nothing offers a page that does not exist.

## 1.4 References

`docs/BUG_REPORT_2026-08-12.md` · `docs/SECURITY_AUDIT_2026-08-12.md` · `docs/UNIT_TEST_SPECIFICATION.md` · migrations `V1`–`V94`.

---

# 2. Definitions

| Term | Meaning |
|---|---|
| **Tenant / Company** | One client organisation. A row in `companies`, identified by a `company_id` such as `PIX-MASTER`. |
| **Module** | A switchable area of functionality (Attendance, Payroll…). Enabled per company. |
| **Permission** | A single capability such as `LEAVE_APPROVE`. Checked by the server. |
| **Role** | A named bundle of permissions such as `IT_HR`. A person may hold more than one. |
| **Technical Admin** | A platform operator. Separate account store (`technical_admins`), separate login, works across tenants. |
| **Punch** | One attendance event, in or out. |
| **Permission request** | A short paid absence within a working day. Distinct from leave. |
| **Offboarding** | Marking a leaver: `profile_status = OFFBOARDED`, account disabled, records retained. |

---

# 3. System overview

## 3.1 Shape of the system

```
Browser (React 18 + Vite, PWA)
      │  REST /api/**          WebSocket /ws (STOMP)
      ▼
Spring Boot 3.5 · Java 17
      ├── Security  JWT, role and permission checks, tenant filter
      ├── Modules   30 controllers, 269 endpoints
      └── Data      JPA / Hibernate, Flyway
      ▼
MySQL 8 · 74 tables
      + Redis (optional cache) · Kafka (present, unused) · Twilio (SMS)
```

## 3.2 Technology

| Layer | Choice | Note |
|---|---|---|
| Frontend | React 18, TypeScript, Vite, TailwindCSS, TanStack Query | PWA with a service worker |
| Backend | Spring Boot 3.5, Java 17 | 269 endpoints |
| Database | MySQL 8, Flyway | 94 migrations |
| Auth | JWT access + refresh, BCrypt | Stateless |
| Realtime | STOMP over WebSocket, SockJS | Notifications, chat, presence, calls |
| Cache | Redis | Optional; falls back to in-memory |
| SMS | Twilio | Off unless enabled |

## 3.3 Environments

| | Database | Notes |
|---|---|---|
| Local | MySQL in Docker, port 3307 | `start-local.ps1` |
| Live | Hosted MySQL | `run-live-db.ps1`; **20 connections in total** — see [12.1](#121-database-connection-ceiling) |

---

# 4. Actors and roles

## 4.1 Roles as configured

Read from `roles` joined to `role_permissions`.

| Code | Name | Permissions | State |
|---|---|---|---|
| `IT_MGR` | Manager / Team Lead | 18 | Working |
| `IT_HR` | HR / Payroll Manager | 16 | Working |
| `SUPER_ADMIN` | Platform Super Admin | 15 | Working |
| `CV_HR` | Civil HR Manager | 12 | Working |
| `CV_SUP` | Civil Supervisor | 7 | Working |
| `IT_TL` | Team Leader | 7 | Working |
| `IT_EMP` | Employee | 4 | Working |
| `CV_EMP` | Civil Site Employee | 4 | Working |
| `CV_ADM` | Facilities Admin | 3 | Working |
| `IT_FIN` | Finance Officer | 2 | Working |
| `IT_CEO` | CEO | 2 | Working |
| `IT_AST` / `CV_AST` | Asset Manager | 2 | Working |
| `COMPANY_ADMIN` | Company Admin | **0** | **GAP-01** |
| `HR_MANAGER` | HR Manager | **0** | **GAP-01** |
| `TEAM_LEAD` | Team Lead | **0** | **GAP-01** |
| `EMPLOYEE` | Employee | **0** | **GAP-01** |
| `BOARD_ADMIN` | Board Admin | **0** | **GAP-01** |

### GAP-01 — Five roles grant nothing

**Evidence.** `SELECT COUNT(*) FROM role_permissions` returns 0 for each of the five. In a role-by-role API sweep their access was identical to a bare employee.

**Why it matters.** These five are exactly the options the technical-admin "create user" form offers. An account created as `HR_MANAGER` receives no HR access at all.

**Requirement.** Each must be granted a permission set, or removed from the form. Proposed mapping, for approval:

| Empty role | Mirror | 
|---|---|
| `COMPANY_ADMIN` | `SUPER_ADMIN` minus platform settings |
| `HR_MANAGER` | `IT_HR` |
| `TEAM_LEAD` | `IT_TL` |
| `EMPLOYEE` | `IT_EMP` |
| `BOARD_ADMIN` | read-only: `REPORT_VIEW`, `DASHBOARD_EXEC` |

## 4.2 Permissions

`USER_MANAGE` · `EMPLOYEE_MANAGE` · `LEAVE_APPLY` · `LEAVE_APPROVE` · `ATTENDANCE_SELF` · `ATTENDANCE_TEAM` · `PAYROLL_VIEW` · `PAYROLL_RUN` · `PAYROLL_APPROVE` · `ASSET_MANAGE` · `HELPDESK_RAISE` · `HELPDESK_AGENT` · `REPORT_VIEW` · `DASHBOARD_EXEC` · `ORG_MANAGE` · `TASK_ASSIGN` · `TASK_VIEW_ALL` · `COMPLAINT_MANAGE` · `CALENDAR_MANAGE` · `CLAIM_APPROVE` · `COMMUNITY_MANAGE` · `TEAM_MANAGE`

> **Naming note.** `PAYROLL_VIEW` is defined as *"View own payslips"* and held by every employee. It once guarded the company-wide salary list; that was corrected on 12 Aug 2026 so the endpoint scopes to the caller unless they also hold `PAYROLL_RUN`, `PAYROLL_APPROVE`, `USER_MANAGE` or `EMPLOYEE_MANAGE`. The name still reads more broadly than it means and should be reconsidered.

---

# 5. Role-wise functional requirements

Each requirement carries an ID, the endpoint or screen behind it, and a state.
**AS-BUILT** — verified working. **GAP** — required, missing or wrong.

---

## 5.1 Employee

*Roles: `IT_EMP`, `CV_EMP`, and the empty `EMPLOYEE` (GAP-01)*

The baseline. Everything an employee may do concerns their own record.

### 5.1.1 Sign in and session

| ID | Requirement | Endpoint | State |
|---|---|---|---|
| EMP-001 | Sign in with username and password | `POST /auth/login` | AS-BUILT |
| EMP-002 | A disabled account is refused with "Account is disabled. Contact HR." | `AuthService:128` | AS-BUILT |
| EMP-003 | Repeated failures lock the account for a period | `failed_login_count`, `locked_until` | AS-BUILT |
| EMP-004 | The session renews without re-entering the password | `POST /auth/refresh` | AS-BUILT |
| EMP-005 | Signing out invalidates the refresh token | `POST /auth/logout` | AS-BUILT |
| EMP-006 | Without a valid token the portal returns to the login page and shows nothing else | `AuthContext` | AS-BUILT (fixed 12 Aug) |
| EMP-007 | Every failed sign-in is recorded with time, IP and user agent | `login_history` | AS-BUILT |
| EMP-008 | Forgot password / self-service reset | — | **GAP-02** |

**GAP-02.** No password reset exists for the employee. A forgotten password requires HR or an admin. For a product sold to other companies this is a day-one support burden. Requires an email or SMS token flow and a reset endpoint.

### 5.1.2 Attendance

| ID | Requirement | Endpoint | State |
|---|---|---|---|
| EMP-010 | Punch in, once per day | `POST /attendance/punch-in` | AS-BUILT |
| EMP-011 | Punch out; hours are computed | `POST /attendance/punch-out` | AS-BUILT |
| EMP-012 | Punch with face verification where the company requires it | `POST /attendance/face-punch` | AS-BUILT |
| EMP-013 | See today's own record | `GET /attendance/today` | AS-BUILT |
| EMP-014 | See own attendance between two dates | `GET /attendance/me?from&to` | AS-BUILT |
| EMP-015 | See own monthly summary with late minutes | `GET /attendance/me/summary` | AS-BUILT |
| EMP-016 | Enrol a face photo for verification | `POST /attendance/face/enrol` | AS-BUILT |
| EMP-017 | A missing date parameter returns 400 with which parameter is missing | — | AS-BUILT (fixed 12 Aug; was 500) |

### 5.1.3 Leave and permissions

| ID | Requirement | Endpoint | State |
|---|---|---|---|
| EMP-020 | See own balance per leave type | `GET /leave/balances` | AS-BUILT |
| EMP-021 | Apply for leave, choosing the approver | `POST /leave/apply` | AS-BUILT |
| EMP-022 | See own history and status | `GET /leave/me` | AS-BUILT |
| EMP-023 | Cancel a request that is still pending | `POST /leave/{id}/cancel` | AS-BUILT |
| EMP-024 | Preview loss of pay before applying | `GET /leave/lop-preview` | AS-BUILT |
| EMP-025 | Request a short permission within a day | `POST /leave/permissions` | AS-BUILT |
| EMP-026 | CL and SL accrue quarterly | migration `V42` | AS-BUILT |

### 5.1.4 Pay

| ID | Requirement | Endpoint | State |
|---|---|---|---|
| EMP-030 | See own payslips | `GET /payroll/payslip/list` | AS-BUILT |
| EMP-031 | Download a payslip as PDF | `GET /payroll/payslip/{id}` | AS-BUILT |
| EMP-032 | Request a payslip that has not been issued | `POST /payroll/payslip-requests` | AS-BUILT |
| EMP-033 | See own salary structure **and no one else's** | `GET /payroll/salary/{id}` | AS-BUILT (fixed 12 Aug) |
| EMP-034 | An employee must not be able to read another person's salary | — | AS-BUILT (fixed 12 Aug; **was a live leak**) |

### 5.1.5 Claims, assets, support

| ID | Requirement | Endpoint | State |
|---|---|---|---|
| EMP-040 | Submit a travel or expense claim with receipts | `POST /ta-expenses` | AS-BUILT |
| EMP-041 | Track own claims | `GET /ta-expenses/me` | AS-BUILT |
| EMP-042 | See assets allocated to them | `GET /assets/my-assets` | AS-BUILT |
| EMP-043 | Acknowledge receipt of an asset | `POST /assets/{id}/acknowledge` | AS-BUILT |
| EMP-044 | Raise a support ticket with attachments | `POST /tickets` | AS-BUILT |
| EMP-045 | Raise a confidential complaint to a named person | `POST /complaints` | AS-BUILT |
| EMP-046 | Report a safety incident | `POST /safety-incidents` | AS-BUILT |

### 5.1.6 Work, communication, profile

| ID | Requirement | Endpoint | State |
|---|---|---|---|
| EMP-050 | See tasks assigned to them and report progress | `GET /tasks/me`, `POST /tasks/{id}/progress` | AS-BUILT |
| EMP-051 | Submit a daily work report with attachments | `POST /work-reports` | AS-BUILT |
| EMP-052 | Chat one to one and in groups | `/communities/**` | AS-BUILT |
| EMP-053 | Voice and video calls | `/calls/signal` | AS-BUILT |
| EMP-054 | React to and acknowledge announcements | `/communities/messages/**` | AS-BUILT |
| EMP-055 | See who is online | `GET /presence` | AS-BUILT |
| EMP-056 | Receive live notifications | WebSocket `/topic/notifications/{id}` | AS-BUILT |
| EMP-057 | View and edit own profile | `GET`, `PUT /users/me` | AS-BUILT |
| EMP-058 | Upload a profile photo and documents | `POST /users/me/photo`, `/users/documents` | AS-BUILT |
| EMP-059 | Add own bank details | `POST /users/me/bank` | AS-BUILT |
| EMP-060 | See the company calendar and holidays | `GET /calendar/events` | AS-BUILT |
| EMP-061 | Ask the built-in assistant | `POST /chatbot/chat` | AS-BUILT |

---

## 5.2 Team Leader

*Role: `IT_TL` — 7 permissions. Everything in [5.1](#51-employee), plus:*

| ID | Requirement | Endpoint | State |
|---|---|---|---|
| TL-001 | See the team's attendance for today | `GET /attendance/my-team-today` | AS-BUILT |
| TL-002 | See team members | `GET /users/my-team` | AS-BUILT |
| TL-003 | Assign tasks within the team | `POST /tasks` with `TASK_ASSIGN` | AS-BUILT |
| TL-004 | See the team's work reports | `GET /work-reports/team` | AS-BUILT |
| TL-005 | Chase a missing work report | `POST /work-reports/reminder/send` | AS-BUILT |
| TL-006 | A TL must **not** reach the full employee directory | verified 403 | AS-BUILT |
| TL-007 | A TL must **not** reach the company asset register or all tickets | verified 403 | AS-BUILT |

> A TL is deliberately narrower than a Manager: team visibility, no approvals. Confirmed by sweep.

---

## 5.3 Manager

*Role: `IT_MGR` — 18 permissions, the widest operational role.*

| ID | Requirement | Endpoint | State |
|---|---|---|---|
| MGR-001 | Approve or reject leave for their people | `POST /leave/{id}/decision` | AS-BUILT |
| MGR-002 | Approve permission requests | `POST /leave/permissions/{id}/decision` | AS-BUILT |
| MGR-003 | See team attendance over a range | `GET /attendance/team` | AS-BUILT |
| MGR-004 | See who is absent today | `GET /attendance/absent-today` | AS-BUILT |
| MGR-005 | First-stage approval of expense claims | `POST /finance/expenses/{id}/manager-action` | AS-BUILT |
| MGR-006 | Assign and reassign tasks across teams | `TASK_VIEW_ALL` | AS-BUILT |
| MGR-007 | See the employee directory | `GET /users` | AS-BUILT |
| MGR-008 | Run attendance and leave reports | `GET /reports/**` | AS-BUILT |
| MGR-009 | Respond to complaints addressed to them | `POST /complaints/{id}/respond` | AS-BUILT |
| MGR-010 | Approve payslip requests | `PAYROLL_RUN` | AS-BUILT |

---

## 5.4 HR / Payroll Manager

*Roles: `IT_HR`, `CV_HR`, and the empty `HR_MANAGER` (GAP-01)*

| ID | Requirement | Endpoint | State |
|---|---|---|---|
| HR-001 | Create an employee account with a username and password | `POST /auth/employees` | AS-BUILT |
| HR-002 | Bulk-create from a spreadsheet | `POST /auth/employees/bulk` | AS-BUILT |
| HR-003 | Preview an import before committing | `GET /auth/employees/imports/{id}/preview` | AS-BUILT |
| HR-004 | Undo an import in one action | `DELETE /auth/employees/imports/{id}` | AS-BUILT |
| HR-005 | Edit any employee profile | `PUT /users/{id}` | AS-BUILT |
| HR-006 | Reset an employee's password | `POST /users/{id}/credentials` | AS-BUILT |
| HR-007 | Read an employee's current password | `GET /users/{id}/password` | AS-BUILT — see note |
| HR-008 | Offboard a leaver, keeping their records | `POST /users/{id}/offboarding` | AS-BUILT |
| HR-009 | Delete an account outright | `DELETE /users/{id}` | AS-BUILT |
| HR-010 | Maintain departments, designations, sites, shifts | `/org/**` | AS-BUILT |
| HR-011 | Maintain leave types and entitlements | `/leave/types` | AS-BUILT |
| HR-012 | Maintain the holiday calendar | `POST /org/holidays` | AS-BUILT |
| HR-013 | Allocate leave balances in bulk | `POST /leave/allocations/apply-defaults` | AS-BUILT |
| HR-014 | Set salary structures | `POST /payroll/salary` | AS-BUILT |
| HR-015 | Run payroll for a month | `POST /payroll/run` | AS-BUILT |
| HR-016 | Generate payslips | `POST /payroll/payslip/generate` | AS-BUILT |
| HR-017 | See all salary structures | `GET /payroll/salaries` | AS-BUILT (fixed 12 Aug — HR was refused) |
| HR-018 | Start and track onboarding checklists | `/onboarding/**` | AS-BUILT |
| HR-019 | See the audit trail | `GET /audit` | AS-BUILT |
| HR-020 | Manage all complaints | `COMPLAINT_MANAGE` | AS-BUILT |
| HR-021 | Enrol another person's face for attendance, attributed to HR | `V88` | AS-BUILT |

> **HR-007 note.** `users.password_vault` stores the password reversibly so HR can read it back. This is a deliberate product decision, guarded by permission. It is called out in the security audit because a reversible password is a different risk profile from a hash; it is recorded here as intended behaviour, not a defect.

---

## 5.5 Finance Officer

*Role: `IT_FIN` — 2 permissions*

| ID | Requirement | Endpoint | State |
|---|---|---|---|
| FIN-001 | Final approval of expense claims | `POST /finance/expenses/{id}/finance-action` | AS-BUILT |
| FIN-002 | See claims awaiting finance | `GET /finance/expenses/pending` | AS-BUILT |
| FIN-003 | Payroll cost reports | `GET /reports/payroll` | AS-BUILT |
| FIN-004 | Approve a payroll run before release | `PAYROLL_APPROVE` | **GAP-03** |

**GAP-03.** `PAYROLL_APPROVE` exists as a permission and is granted to `IT_FIN`, but no endpoint requires it and payroll has no approval step — a run is generated and immediately final. Either add the step or drop the permission.

---

## 5.6 Asset Manager

*Roles: `IT_AST`, `CV_AST`, `CV_ADM`*

| ID | Requirement | Endpoint | State |
|---|---|---|---|
| AST-001 | Maintain the asset register | `POST`, `GET /assets` | AS-BUILT |
| AST-002 | Allocate an asset to a person | `POST /assets/{id}/allocate` | AS-BUILT |
| AST-003 | Take an asset back | `POST /assets/{id}/return` | AS-BUILT |
| AST-004 | Record maintenance | `asset_maintenance` | AS-BUILT |
| AST-005 | Print a QR label | `GET /assets/{id}/qr` | AS-BUILT |
| AST-006 | Look up an asset by scanning | `GET /assets/lookup` | AS-BUILT |
| AST-007 | Track quantity for consumables | `V32` | AS-BUILT |
| AST-008 | Act as a helpdesk agent | `HELPDESK_AGENT` | AS-BUILT |

---

## 5.7 CEO / Executive

*Role: `IT_CEO` — `DASHBOARD_EXEC`, `REPORT_VIEW`*

| ID | Requirement | Endpoint | State |
|---|---|---|---|
| CEO-001 | Headcount, present today, attendance rate | `GET /dashboard/executive` | AS-BUILT — headcount verified against the database |
| CEO-002 | Breakdown by department | same | **GAP-04** |
| CEO-003 | Monthly attendance trend | same | **GAP-04** |
| CEO-004 | Payroll cost trend | same | **GAP-04** |
| CEO-005 | Filter by industry | `?industry=` | AS-BUILT |
| CEO-006 | Organisation insights | `GET /dashboard/org-insights` | AS-BUILT |
| CEO-007 | Birthdays and anniversaries | `GET /dashboard/celebrations` | AS-BUILT |

### GAP-04 — Three executive figures are invented

**Evidence.** `DashboardService.java:301`:

```java
Map<String, Long> departmentBreakdown = Map.of("Engineering", 15L, "Sales", 8L, "HR", 4L);
List.of(Map.of("month","Jan","present",95,"absent",5), ...);
Map.of("month","Jan","cost",1500000);
```

Against the database: Engineering has 0 people, **there is no Sales department**, and `attendance` holds 0 rows.

**Why it matters.** A director reads these as fact, and the screen does not distinguish them from the real figures beside them.

**Requirement.** Compute all three from data, or label them as sample data until they are computed.

---

## 5.8 Super Admin

*Role: `SUPER_ADMIN` — 15 permissions*

| ID | Requirement | Endpoint | State |
|---|---|---|---|
| SA-001 | Everything HR and Manager can do | — | AS-BUILT |
| SA-002 | Reach the executive dashboard | `DASHBOARD_EXEC` | AS-BUILT |
| SA-003 | Full audit log with filters | `GET /audit` | AS-BUILT |
| SA-004 | Sign-in history | `GET /audit/logins` | AS-BUILT |
| SA-005 | Configure the assistant and its knowledge | `/chatbot/admin/**` | AS-BUILT |
| SA-006 | Clear the cache | `DELETE /api/cache` | AS-BUILT |
| SA-007 | System settings | `/settings` | AS-BUILT |
| SA-008 | Reset company data — "Fresh Start" | `POST /admin/reset` | AS-BUILT — **GAP-05** |

**GAP-05.** Fresh Start empties the tenant from a single click, with no typed confirmation, no backup and no audit entry. For a product installed at a client site this needs a typed confirmation naming the company, an automatic export first, and an audit record of who did it.

---

> **Technical Admin is covered separately.** The platform-operator role — creating client companies, switching modules on and off, cross-tenant administration — is deliberately left out of this document and will be issued as its own specification on request.

---

# 6. Module requirements

The 16 modules a company can be given. Each maps to a real page.

| # | Module | Code | Pages | Depends on |
|---|---|---|---|---|
| 1 | Attendance | `ATTENDANCE` | `/attendance`, `/team-attendance` | Employees |
| 2 | Chat | `CHAT` | `/chat` | Users |
| 3 | Payroll | `PAYROLL` | `/payslips`, `/payroll/*` | Employees |
| 4 | Leave Management | `LEAVE` | `/leave/*` | Employees |
| 5 | Assets | `ASSETS` | `/assets` | Employees |
| 6 | Helpdesk | `HELPDESK` | `/helpdesk`, `/complaints` | Users |
| 7 | Reports | `REPORTS` | `/reports`, `/work-reports` | — |
| 8 | Tasks | `TASKS` | `/tasks` | Users |
| 9 | Employee Onboarding | `ONBOARDING` | `/onboarding` | Employees |
| 10 | Expense Claims | `EXPENSES` | `/ta-expenses` | Employees |
| 11 | Calendar | `CALENDAR` | `/calendar` | — |
| 12 | Teams | `TEAMS` | `/teams` | Employees |
| 13 | Audit Log | `AUDIT_LOG` | `/audit` | — |
| 14 | Document Management | `DOCUMENTS` | `/documents` | — |
| 15 | Project Management | `PROJECTS` | `/projects` | Teams |
| 16 | Communities | `COMMUNITIES` | `/communities` | Users |

**MOD-001** Turning a module off must hide its menu entry and refuse its endpoints. Menu hiding is AS-BUILT; **server-side refusal is GAP-07** — the checks are in the client only, so a module that is off can still be reached by calling the API directly.

**MOD-002** A module must never be offered without a page behind it. AS-BUILT since 12 Aug.

---

# 7. Data requirements

## 7.1 Core entities

| Entity | Table | Holds |
|---|---|---|
| User | `users` | Person, login, employment, `company_id` |
| Company | `companies` | Tenant |
| Role / Permission | `roles`, `permissions`, `role_permissions` | Access model |
| Attendance | `attendance` | Punches, hours, late minutes, face result |
| Leave | `leave_requests`, `leave_types`, `leave_balances` | Absence |
| Payroll | `salary_structures`, `payroll_runs`, `payslips` | Pay |
| Assets | `assets`, `asset_allocations`, `asset_maintenance` | Equipment |
| Helpdesk | `tickets`, `ticket_comments`, `complaints_needs` | Support |
| Tasks | `tasks`, work reports | Work |
| Chat | `communities`, `community_messages`, members, reads | Communication |
| Audit | `audit_log`, `login_history` | Trail |
| Files | `system_files` | Uploads as BLOB — see [GAP-08](#gap-08--files-are-stored-in-the-database) |

## 7.2 Retention

**DAT-001** Offboarding must retain records; only `profile_status` and `enabled` change. AS-BUILT.
**DAT-002** Deleting an account removes it outright — for mistaken entries, not leavers. AS-BUILT.
**DAT-003** Audit entries must not be editable or deletable through the API. AS-BUILT.
**DAT-004** An import must be reversible as one action. AS-BUILT (`V84`).

### GAP-08 — Files are stored in the database

`V93` stores uploads as `LONGBLOB`. Every payslip and photo is a MySQL row, served through the application, over a pool of six connections on an account limited to twenty. Backups grow with every upload; a dump already takes about five minutes. Object storage is required before volume grows.

---

# 8. Security requirements

## 8.1 Authentication

**SEC-001** Passwords stored with BCrypt. AS-BUILT.
**SEC-002** JWT signed HMAC-SHA; the production secret must come from the environment. AS-BUILT.
**SEC-003** Every endpoint except the documented public list requires a token. AS-BUILT — no token 401, invalid token 401, verified.
**SEC-004** No endpoint may disclose a password hash. AS-BUILT since 12 Aug — a public debug endpoint returned one and also confirmed password guesses; the class was deleted.
**SEC-005** Lock an account after repeated failures. AS-BUILT.
**SEC-006** Rate-limit sign-in, reset and upload. **GAP-09** — no limiter anywhere.

## 8.2 Authorisation

**SEC-010** Enforced on the server, never only in the client. AS-BUILT for 106 annotated endpoints; **GAP-07** for module gating.
**SEC-011** No horizontal escalation — one employee must not reach another's record. AS-BUILT since 12 Aug (salary leak, and an id-based read of any user).
**SEC-012** Vertical escalation blocked. AS-BUILT — 9 roles × 24 endpoints swept.

## 8.3 Data protection

**SEC-020** No SQL injection. AS-BUILT — all access is JPA; no native concatenated queries.
**SEC-021** No path traversal in file serving. AS-BUILT — decode then reject `..`.
**SEC-022** Uploads must not be executable in a browser. AS-BUILT since 12 Aug — extension allowlist, `nosniff`, non-images sent as attachments.
**SEC-023** Errors must not describe internals. AS-BUILT since 12 Aug — detail is dev-only; production returns a reference.
**SEC-024** Documents must only be readable by those entitled. **GAP-10** — `/api/files/**` is public; a URL is the only protection.
**SEC-025** No secret in version control. **GAP-11** — live Twilio SID and token are committed.

## 8.4 Transport and origin

**SEC-030** CORS restricted to configured origins. AS-BUILT.
**SEC-031** WebSocket restricted to the same origins. AS-BUILT since 12 Aug — was `"*"`.
**SEC-032** HSTS, CSP, X-Frame-Options. **GAP-12** — no header configuration.

---

# 9. Multi-tenancy requirements

The requirement that decides whether this is a product or one installation.

**TEN-001** A company must see only its own data, enforced on the server.
**TEN-002** The technical admin may cross tenants; nobody else may.
**TEN-003** Every tenant-owned row must carry `company_id`, set on insert.
**TEN-004** A query without a tenant scope must not reach tenant data.

## 9.1 Where this stands

Measured by moving an HR account into company 1 and asking for company 4's records.

| Area | Result |
|---|---|
| Employee list | isolated |
| Employee by id | isolated — **fixed 12 Aug**, previously readable *and editable* |
| Salaries | isolated |
| Communities, chat contacts | isolated |
| Assets | isolated |
| Departments | isolated |
| Holidays | **LEAKING — GAP-17** |
| Leave types | **LEAKING — GAP-17** |

**Score: 7 of 9 checked areas isolated.**

### GAP-17 — Holidays and leave types cross companies

A company-1 account is shown company-4's ten holidays and seven leave types. Reproducible; see `UT-TEN-006` and `UT-TEN-007`.

Three fixes were attempted on 12 Aug and **none worked**:

1. `@Filter` on the `Holiday` and `LeaveType` entities — still leaked
2. Company added to the `@Cacheable` keys — still leaked
3. An explicit `companyId` comparison in `OrgService.holidays()` and `LeaveService.types()` — still leaked

All three failing the same way points at one shared cause rather than three separate ones: the caller's company is not reaching these code paths — most likely `SecurityUtils.currentCompanyId()` returning null there, which would make the aspect skip the filter, collapse both cache keys to the same string, and turn the explicit comparison into a no-op. That is a hypothesis, not a finding; it has not been confirmed.

**Next step for whoever picks this up:** log `SecurityUtils.currentCompanyId()` at the top of `OrgService.holidays()` and call the endpoint as a user in a non-default company. That single line settles it. If it is null, the fix belongs in how the principal is built, not in these three places — and the same root cause probably affects other paths that look isolated only because of how they happen to be queried.

The code from attempt 3 has been left in place: it is correct, readable and harmless, and it will start working the moment the company reaches it.

## 9.2 How it works, and what remains

`User` carries `company_id` and a Hibernate filter; `TenantFilterAspect` switches the filter on before every controller and service call. Anything reached through a user inherits the scope — which is why most areas were already isolated.

Two gaps were found and closed:
- Hibernate filters apply to queries, **not to `findById`**. `UserService.findUser()` now verifies the company, which closes read, edit, delete and password reset in one place.
- Master data had no `company_id`. `V94` added it to 38 tables and stamped every existing row; `Holiday` and `LeaveType` now filter.

**GAP-13.** The remaining tables have the column and are stamped, but their entities do not yet filter. They are not currently leaking — the user relationship scopes them — but that is a property of how they happen to be queried, not a guarantee. Each should be switched on and re-tested, module by module, in this order: attendance → leave → payroll → assets → helpdesk → tasks → communities → documents.

---

# 10. Integration requirements

| ID | Integration | State |
|---|---|---|
| INT-001 | Twilio SMS, off by default | AS-BUILT — **GAP-11** on credentials |
| INT-002 | Redis cache, optional, falls back to memory | AS-BUILT |
| INT-003 | Kafka | Present on the classpath, nothing publishes |
| INT-004 | Face recognition service | AS-BUILT (`V85`–`V88`) |
| INT-005 | Assistant with speech in and out | AS-BUILT |
| INT-006 | Email | **GAP-14** — mail properties exist, nothing sends. Blocks GAP-02 |

---

# 11. Non-functional requirements

| ID | Requirement | Target | State |
|---|---|---|---|
| NFR-001 | Employee list response | < 2s for 500 | Was 40s; batch fetching added, now acceptable |
| NFR-002 | Dashboard response | < 1s | Not measured |
| NFR-003 | Concurrent users | 100 per tenant | **At risk** — 20 database connections in total |
| NFR-004 | Main bundle | < 500 kB | **817 kB** — GAP-15 |
| NFR-005 | Works offline for read | Partial | Service worker present |
| NFR-006 | Mobile, tablet, desktop | All | Not verified — no browser in this audit |
| NFR-007 | WCAG 2.1 AA | Required for enterprise sale | Not verified |
| NFR-008 | Uptime | 99.5% | No monitoring |
| NFR-009 | Backups | Daily, restore tested | Manual only |
| NFR-010 | Automated tests | Meaningful coverage | **Zero tests exist** — GAP-16 |

---

# 12. Constraints

## 12.1 Database connection ceiling

The hosted account allows **20 connections in total**, across every process. The pool is capped at six for this reason. It was exhausted during a single day of development with one tenant. This ceiling makes NFR-003 unreachable and must be lifted — managed MySQL — before a second customer.

## 12.2 Java 17

Pattern matching in `switch` and other newer syntax are unavailable.

## 12.3 Schema owned by Flyway

`ddl-auto: validate`. Every schema change is a migration. An entity field without its column stops the application from starting — which is why tenant columns were added by migration first and filters switched on afterwards.

---

# 13. Open gaps register

| ID | Gap | Severity | Blocks |
|---|---|---|---|
| GAP-01 | Five roles hold no permissions | HIGH | Creating usable accounts from tech admin |
| GAP-02 | No self-service password reset | HIGH | Selling to a company without a support desk |
| GAP-03 | `PAYROLL_APPROVE` has no step | MEDIUM | Finance sign-off |
| GAP-04 | Executive dashboard figures invented | **CRITICAL** | Any executive use |
| GAP-05 | Fresh Start too easy to trigger | HIGH | Client installation |
| GAP-06 | New company starts with no master data | HIGH | Onboarding a customer |
| GAP-07 | Module gating is client-side only | HIGH | Selling modules separately |
| GAP-08 | Files stored in MySQL | MEDIUM | Scale |
| GAP-09 | No rate limiting | MEDIUM | Public exposure |
| GAP-10 | Document store is public | HIGH | Holding ID and bank documents |
| GAP-11 | Twilio credentials in git | HIGH | Immediate — rotate |
| GAP-12 | No security headers | MEDIUM | Enterprise review |
| GAP-13 | Remaining entities not yet filtered | HIGH | Second customer |
| GAP-14 | No email | MEDIUM | Blocks GAP-02 |
| GAP-15 | 817 kB bundle | LOW | Mobile |
| GAP-16 | No automated tests | HIGH | Every future change |
| GAP-17 | Holidays and leave types cross companies; three fixes failed | **CRITICAL** | Second customer |

---

# 14. Acceptance criteria

## 14.1 Single tenant, pilot

- [ ] GAP-11 credentials rotated
- [ ] GAP-04 dashboard figures real or labelled
- [ ] GAP-05 Fresh Start protected
- [ ] GAP-01 roles granted or removed
- [x] Salary visible only to the employee and to HR/payroll
- [x] Module on/off works end to end
- [x] No unauthenticated endpoint discloses credentials
- [x] Uploads cannot execute in a browser

## 14.2 Second customer

- [ ] GAP-13 all entities filtered, each re-tested
- [ ] GAP-06 new company seeded with master data
- [ ] GAP-07 module gating enforced server-side
- [ ] GAP-10 document access controlled
- [ ] Two-tenant isolation test passes with data on both sides
- [ ] Database moved off the 20-connection account

## 14.3 General availability

- [ ] GAP-16 tests, per `docs/UNIT_TEST_SPECIFICATION.md`
- [ ] GAP-02 and GAP-14 password reset and email
- [ ] GAP-09 rate limiting · GAP-12 headers · GAP-08 object storage
- [ ] Accessibility and responsive testing signed off
- [ ] Load tested at target concurrency

---

*Prepared 12 August 2026 from the state of the code and a live copy of production data. Items marked AS-BUILT were verified by calling the system, not by reading the source alone.*
