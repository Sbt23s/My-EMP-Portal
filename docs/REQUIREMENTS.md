# Pixous HR Portal — Software Requirements Specification (SRS)

| Field | Value |
|---|---|
| Project | Pixous HR Portal (My-EMP-Portal) |
| Version | 1.0 |
| Date | 2026-08-14 |
| Status | Approved for development (QA-audited) |
| Live deployment | http://16.192.105.61 |
| Stack | Spring Boot 3 (Java 17) + React 19 + MySQL + Redis + Nginx |

---

## 1. Introduction

### 1.1 Purpose
This document defines the complete functional and non-functional requirements of the Pixous HR Portal — a multi-tenant HR management system for IT services and field operations. It is the single source of truth for what the product must do, who may do it, and how it must behave.

### 1.2 Scope
The system covers the full employee lifecycle: onboarding, attendance, leave, payroll, assets, claims, complaints, helpdesk, safety, chat, reports, and administration — under a **5-role access model**.

### 1.3 Definitions & Abbreviations
| Term | Meaning |
|---|---|
| Tenant / Company | An organisation isolated by `company_id` (e.g. "Pixous Technologies") |
| Role | A named set of authorities granted to a user |
| Authority | A granular permission (e.g. `USER_MANAGE`) checked by the backend |
| JWT | JSON Web Token used for stateless authentication |
| TL | Team Lead |
| HR | HR Manager |
| SA | System Admin (SUPER_ADMIN) |

---

## 2. Roles & Access Control (5-Role Model)

### 2.1 Role Hierarchy

```
SUPER_ADMIN  (System Admin — platform-wide control)
   └── COMPANY_ADMIN  (Company Admin — one tenant)
         ├── HR_MANAGER  (HR — people operations)
         │     └── TEAM_LEAD  (TL — team operations)
         │           └── EMPLOYEE  (self-service)
```

| # | Role code | Display name | Description |
|---|---|---|---|
| R1 | `SUPER_ADMIN` | System Admin | Platform owner: all tenants, all data, technical admin functions |
| R2 | `COMPANY_ADMIN` | Company Admin | Administrator of a single company/tenant |
| R3 | `HR_MANAGER` | HR | Employee lifecycle, leave & payroll operations |
| R4 | `TEAM_LEAD` | Team Lead (TL) | Manages a team: approvals, team visibility |
| R5 | `EMPLOYEE` | Employee | Self-service for own data and requests |

### 2.2 Permission Matrix (Roles × Capabilities)

| Capability | Authority | EMP | TL | HR | CA | SA |
|---|---|---|---|---|---|---|
| View own profile & update personal details | — | ✅ | ✅ | ✅ | ✅ | ✅ |
| Clock in / out (attendance) | — | ✅ | ✅ | ✅ | ✅ | ✅ |
| Apply for leave / view own balance | — | ✅ | ✅ | ✅ | ✅ | ✅ |
| View own payslips & download | `PAYROLL_VIEW` | ✅ | ✅ | ✅ | ✅ | ✅ |
| Submit claims & track status | — | ✅ | ✅ | ✅ | ✅ | ✅ |
| View & manage own assets | `ASSET_MANAGE` (self scope) | ✅ | ✅ | ✅ | ✅ | ✅ |
| Raise complaints / helpdesk tickets | — | ✅ | ✅ | ✅ | ✅ | ✅ |
| Chat & notifications | — | ✅ | ✅ | ✅ | ✅ | ✅ |
| Team dashboard, team attendance | `ATTENDANCE_TEAM`, `DASHBOARD_EXEC` | — | ✅ | ✅ | ✅ | ✅ |
| Approve team leave | `LEAVE_APPROVE` | — | ✅ | ✅ | ✅ | ✅ |
| Manage employees (CRUD) | `EMPLOYEE_MANAGE` | — | — | ✅ | ✅ | ✅ |
| Approve claims | `CLAIM_APPROVE` | — | — | ✅ | ✅ | ✅ |
| Run & approve payroll | `PAYROLL_RUN`, `PAYROLL_APPROVE` | — | — | ✅ | ✅ | ✅ |
| Manage leave policies | `LEAVE_APPROVE` (policy scope) | — | — | ✅ | ✅ | ✅ |
| View reports & export | `REPORT_VIEW` | — | — | ✅ | ✅ | ✅ |
| Manage users & roles | `USER_MANAGE`, `ORG_MANAGE` | — | — | — | ✅ | ✅ |
| Manage company config, branding, modules | `ORG_MANAGE` | — | — | — | ✅ | ✅ |
| Cross-tenant management, data reset | `SUPER_ADMIN`, `TECHNICAL_ADMIN` | — | — | — | — | ✅ |
| View audit logs (all) | `AUDIT_VIEW` | — | — | — | ✅ | ✅ |

**Rule:** authorities are enforced **server-side** on every endpoint via `@PreAuthorize`; the frontend only hides what the server rejects.

---

## 3. Functional Requirements

Each requirement has an ID, description, actor, and acceptance criteria (AC).

### FR-01 Authentication & Session Management
| ID | Requirement | Actor |
|---|---|---|
| FR-01.1 | User signs in with username and password; system validates against stored hash | All |
| FR-01.2 | On success, system issues an access JWT (short TTL) + refresh token (rotating) | All |
| FR-01.3 | On failure, system records the attempt; **8 consecutive failures lock the account** for that identity | All |
| FR-01.4 | System rejects disabled accounts even with correct credentials | All |
| FR-01.5 | User can change password; old password must be verified; new password ≥ 8 chars and different | All |
| FR-01.6 | Password change revokes all other active sessions | All |
| FR-01.7 | Access token expiry returns user to login; refresh flow restores session silently | All |
| FR-01.8 | Logout revokes the refresh token | All |

**AC-01:** lockout must be per-identity and IP-spoof-proof; after lockout the account stays locked until a successful login by an admin reset or a defined cooldown.

### FR-02 Dashboard
| ID | Requirement | Actor |
|---|---|---|
| FR-02.1 | Employee dashboard shows personal summary: leave balance, attendance, payslips, pending approvals | EMP, TL, HR |
| FR-02.2 | TL/HR dashboard shows team overview: headcount, attendance today, leave pending, birthdays/anniversaries | TL, HR, CA |
| FR-02.3 | **User count shown must be scoped to the active company**, not platform totals | TL, HR, CA, SA |
| FR-02.4 | Tech-admin dashboard shows platform totals and health | SA |

### FR-03 User & Employee Management
| ID | Requirement | Actor |
|---|---|---|
| FR-03.1 | Create employee: name, username, email, phone, department, designation, DOJ, DOB, reporting manager, role, employee code (auto), Aadhaar (validated) | HR, CA |
| FR-03.2 | Edit employee details; change role; activate/deactivate account | HR, CA |
| FR-03.3 | Search and filter employees (name, department, status) with pagination | HR, CA, TL (view) |
| FR-03.4 | Import/export employees (XLSX) with validation report | HR, CA |
| FR-03.5 | Prevent self-demotion and deletion of the last SUPER_ADMIN | SA |
| FR-03.6 | Bulk assign employees to a company tenant | SA |

### FR-04 Leave Management
| ID | Requirement | Actor |
|---|---|---|
| FR-04.1 | Employee applies for leave: type, from–to dates, reason; validates balance and overlaps | EMP |
| FR-04.2 | System computes leave balance per policy (e.g. annual, sick, casual) | All |
| FR-04.3 | TL approves/rejects leave for direct reports; HR/CA can override | TL, HR, CA |
| FR-04.4 | HR manages leave policies: types, accrual, max balance, restrictions | HR, CA |
| FR-04.5 | Approval/ rejection sends notification to the applicant | All |

### FR-05 Attendance
| ID | Requirement | Actor |
|---|---|---|
| FR-05.1 | Employee clocks in and out; system records time and prevents duplicates | EMP |
| FR-05.2 | Employee views own attendance calendar and monthly summary | EMP |
| FR-05.3 | TL/HR view team attendance; HR can correct entries | TL, HR, CA |
| FR-05.4 | System derives working hours from clock-in/out pairs | All |

### FR-06 Payroll & Payslips
| ID | Requirement | Actor |
|---|---|---|
| FR-06.1 | HR runs payroll for a month: selects employees, computes salary + deductions | HR, CA |
| FR-06.2 | Payroll run is guarded against duplicate runs for the same period | HR, CA |
| FR-06.3 | Generated payslips are visible to each employee (own only) and downloadable as PDF | EMP, HR, CA |
| FR-06.4 | Payroll requests / approvals workflow for non-standard runs | HR, CA |

### FR-07 Assets
| ID | Requirement | Actor |
|---|---|---|
| FR-07.1 | Admin maintains asset catalogue and assigns assets to employees | HR, CA |
| FR-07.2 | Employee views assets assigned to them and reports issues | EMP |
| FR-07.3 | Asset lifecycle: issue, return, transfer; audit trail kept | HR, CA |

### FR-08 Claims & Reimbursement
| ID | Requirement | Actor |
|---|---|---|
| FR-08.1 | Employee submits a claim: type, amount, date, receipts (file upload) | EMP |
| FR-08.2 | TL/HR approves or rejects; status notified | TL, HR |
| FR-08.3 | Claim must not be editable after approval | All |

### FR-09 Complaints & Grievances
| ID | Requirement | Actor |
|---|---|---|
| FR-09.1 | Employee raises a complaint (anonymously if chosen) with category | EMP |
| FR-09.2 | HR/CA tracks, responds, and closes complaints | HR, CA |

### FR-10 Helpdesk & Support
| ID | Requirement | Actor |
|---|---|---|
| FR-10.1 | Employee opens a helpdesk ticket with priority | EMP |
| FR-10.2 | Agent (HR/CA) claims, updates status, resolves with notes | HR, CA |
| FR-10.3 | Ticket status changes notify the requester | All |

### FR-11 Safety
| ID | Requirement | Actor |
|---|---|---|
| FR-11.1 | Employees report safety incidents (type, location, description, attachments) | EMP |
| FR-11.2 | HR/CA reviews and closes incidents; reports available | HR, CA |

### FR-12 Chat & Notifications
| ID | Requirement | Actor |
|---|---|---|
| FR-12.1 | Real-time chat (WebSocket/STOMP) between users; persisted history | All |
| FR-12.2 | Notifications for approvals, mentions, and system events with unread count | All |
| FR-12.3 | Notification preferences per module | All |

### FR-13 Calendar & Onboarding
| ID | Requirement | Actor |
|---|---|---|
| FR-13.1 | Shared calendar with events; employee sees own events | All |
| FR-13.2 | Onboarding checklist: HR assigns tasks, employee completes them | HR, EMP |

### FR-14 Reports & Exports
| ID | Requirement | Actor |
|---|---|---|
| FR-14.1 | HR/CA generate reports: attendance summary, leave, payroll, headcount | HR, CA |
| FR-14.2 | Reports exportable to XLSX (and payslips to PDF) | HR, CA |
| FR-14.3 | Report access restricted by `REPORT_VIEW` | HR, CA, SA |

### FR-15 Audit Log
| ID | Requirement | Actor |
|---|---|---|
| FR-15.1 | System records auditable actions: actor, action, entity, timestamp | All |
| FR-15.2 | CA/SA view and filter audit logs; logs are append-only | CA, SA |

### FR-16 Roles & Permissions
| ID | Requirement | Actor |
|---|---|---|
| FR-16.1 | CA/SA create roles, assign authorities, and map roles to users | CA, SA |
| FR-16.2 | Built-in roles (the 5 above) cannot be deleted | CA, SA |
| FR-16.3 | Changes take effect on the user's next request (JWT authority refresh) | All |

### FR-17 Settings & Branding
| ID | Requirement | Actor |
|---|---|---|
| FR-17.1 | Company settings: name, industry, theme/branding (logo, colors) | CA |
| FR-17.2 | Chatbot settings configurable | CA |
| FR-17.3 | Profile settings: photo, contact details, password | All |

### FR-18 Multi-Tenancy & Companies
| ID | Requirement | Actor |
|---|---|---|
| FR-18.1 | Each company is isolated by `company_id`; no cross-tenant data leakage | SA |
| FR-18.2 | SA creates companies, assigns users, controls module enablement per company | SA |
| FR-18.3 | New company bootstraps with the standard 5-role set | SA |

### FR-19 Data Reset (Technical Admin)
| ID | Requirement | Actor |
|---|---|---|
| FR-19.1 | SA can reset demo/tenant data with explicit confirmation | SA |
| FR-19.2 | Reset is guarded by `SUPER_ADMIN` role and must never run against a live production DB automatically (seeding is prod-disabled) | SA |

### FR-20 File Uploads & Storage
| ID | Requirement | Actor |
|---|---|---|
| FR-20.1 | System accepts uploads (images, PDF, XLSX) with size limits | All |
| FR-20.2 | Dangerous types (SVG, HTML, executables) are rejected or neutralised to `.bin` | All |
| FR-20.3 | Filenames are never used in filesystem paths (traversal-proof) | All |
| FR-20.4 | Downloads served with `nosniff`; no directory listing | All |

---

## 4. User Stories (by Role)

### 4.1 Employee (EMP)
- US-EMP-01: As an employee, I can sign in and see my dashboard with my leave balance, today's attendance, and latest payslip.
- US-EMP-02: As an employee, I can clock in/out and see my attendance history.
- US-EMP-03: As an employee, I can apply for leave and track approval status.
- US-EMP-04: As an employee, I can submit a claim with receipt and track it.
- US-EMP-05: As an employee, I can raise complaints and helpdesk tickets.
- US-EMP-06: As an employee, I can chat with colleagues and get notifications.
- US-EMP-07: As an employee, I can change my password and update my profile.

### 4.2 Team Lead (TL)
- US-TL-01: As a TL, I can see my team's attendance and dashboard.
- US-TL-02: As a TL, I can approve/reject leave and claims from my direct reports.
- US-TL-03: As a TL, I can see my team's assigned assets.
- US-TL-04: As a TL, I cannot access payroll run, user management, or cross-team data.

### 4.3 HR Manager (HR)
- US-HR-01: As HR, I can manage the full employee lifecycle (create, edit, deactivate).
- US-HR-02: As HR, I can run and approve payroll and generate payslips.
- US-HR-03: As HR, I can manage leave policies and correct attendance.
- US-HR-04: As HR, I can generate and export reports.
- US-HR-05: As HR, I can manage assets, complaints, and safety incidents.

### 4.4 Company Admin (CA)
- US-CA-01: As Company Admin, I can manage users, roles, and permissions within my company.
- US-CA-02: As Company Admin, I can configure company settings and branding.
- US-CA-03: As Company Admin, I can view audit logs and all reports.
- US-CA-04: As Company Admin, I cannot access another company's data.

### 4.5 System Admin (SA)
- US-SA-01: As System Admin, I can create and manage companies (tenants).
- US-SA-02: As System Admin, I can access all tenants' data, audit logs, and technical functions (data reset, module management).
- US-SA-03: As System Admin, I can assign any role and manage platform-wide settings.

---

## 5. Non-Functional Requirements

| NFR | Requirement | Target |
|---|---|---|
| NFR-01 Security | All endpoints enforce authn + authz server-side; JWT signed, short TTL, rotating refresh | No auth bypass; 401/403 correct |
| NFR-02 Security | Login rate limiting immune to header spoofing | Lockout at 8 failures |
| NFR-03 Security | Passwords hashed; no plaintext storage; no secrets in repo | — |
| NFR-04 Security | Upload allowlist + traversal protection | — |
| NFR-05 Performance | Page load (public) / login round-trip | < 1.5 s / < 1 s |
| NFR-06 Performance | List APIs paginated; p95 | < 800 ms at 100 concurrent |
| NFR-07 Availability | Rolling 30-day uptime | ≥ 99.5% |
| NFR-08 Usability | Responsive on desktop/tablet/mobile; dark + light mode | — |
| NFR-09 Maintainability | Layered architecture; automated tests gate releases | ≥ 70% backend coverage |
| NFR-10 Compliance | Audit log of privileged actions; GDPR-friendly data access | — |

---

## 6. Out of Scope (v1.0)

- Public-facing candidate portal and job applications
- Mobile native apps (PWA is provided)
- Multi-currency payroll and tax filing integrations
- BI/analytics beyond built-in reports
- SSO/SAML integration (JWT-based auth only)

---

## 7. Requirements Traceability

| Module | FR IDs | Primary roles |
|---|---|---|
| Auth | FR-01 | All |
| Dashboard | FR-02 | All |
| Users/Employees | FR-03 | HR, CA, SA |
| Leave | FR-04 | All |
| Attendance | FR-05 | All |
| Payroll | FR-06 | HR, CA |
| Assets | FR-07 | All |
| Claims | FR-08 | All |
| Complaints | FR-09 | EMP, HR |
| Helpdesk | FR-10 | All |
| Safety | FR-11 | All |
| Chat/Notifications | FR-12 | All |
| Calendar/Onboarding | FR-13 | All |
| Reports | FR-14 | HR, CA |
| Audit | FR-15 | CA, SA |
| Roles/Permissions | FR-16 | CA, SA |
| Settings/Branding | FR-17 | All |
| Tenancy | FR-18 | SA |
| Data Reset | FR-19 | SA |
| Files | FR-20 | All |

*This document is version-controlled in the repository (`docs/REQUIREMENTS.md`). Any functional change must update this document and the corresponding unit tests.*
