# HR Portal — Requirements Analysis

> Source documents analysed:
> 1. `HR_Portal_User_Stories.docx` — 50+ user stories, IT + Civil industries, 11 roles.
> 2. `Employee_Management_postman_collection.json` — existing PHP API (`demo.pixoustech.com`), Aadhaar login + JWT bearer.

This document is the single source of truth that drives the data model, the API surface,
and the module breakdown. Every user story (US) in the Word doc is catalogued here and
mapped to a module. The story-by-story implementation status lives in
[`01-traceability-matrix.md`](./01-traceability-matrix.md).

---

## 1. What the existing API already tells us (the domain spine)

The Postman collection is small but it pins down the **identity model** and several
concrete contracts we must stay compatible with:

| Existing endpoint (PHP)                         | Method | Meaning                                              |
| ----------------------------------------------- | ------ | ---------------------------------------------------- |
| `/api/create_user.php`                          | POST   | Signup. Identity = **Aadhaar** + phone + password.   |
| `/api/auth/aadhar_login.php`                    | POST   | Login by **aadhar + password** → JWT bearer.         |
| `/api/phonenumbervalidate.php`                  | POST   | Check phone availability.                            |
| `/api/user/change_password.php`                 | POST   | current_password + new_password.                     |
| `/api/user/update_photo.php`                    | POST   | Base64 photo upload.                                 |
| `/api/user/update_profile.php`                  | POST   | Update demographic + address fields.                 |
| `/api/profile.php`                              | GET    | Current user profile.                                |
| `/api/bank/index.php`                           | POST   | Bank details, `action` = add/update/delete/view.     |
| `/api/payslip/index.php?action=generate`        | POST   | Generate payslip (user_id, month, year, components). |
| `/api/payslip/index.php?action=list`            | GET    | All payslips / per-user payslips.                    |
| `/api/dropdown.php`                             | POST   | Master data by `type` (single or array).             |

**Domain facts extracted:**

- **Person identity is Aadhaar-based** (India). A user has: `name, dob, gender, aadhar,
  phone, password`, address block (`careOf, house, street, locality, vtc, district, state,
  country, pincode, postOffice`), `photo`, `created_by`.
- **Bank details**: `bank_name, branch_name, account_number, ifsc_code, account_holder_name`.
- **Payslip components**: `basic_salary, hra, allowances, deductions` for a `pay_month/pay_year`.
- **Dropdown / master-data types**: `blood_group, office_location, department, designation,
  employment_status, position`. A single endpoint returns one type or several at once.
- **Auth is JWT bearer** on every protected call.

The Spring Boot backend **re-implements and extends** this contract (see
[`03-api-contract.md`](./03-api-contract.md)). We keep the Aadhaar login, the dropdown
`type` semantics, the bank `action` semantics, and the payslip component shape so existing
Postman flows have direct equivalents — then layer the much larger HR Portal on top.

---

## 2. Roles (actors) across both industries

The Word doc defines two industry "verticals" that share one platform. RBAC is built so a
single deployment serves both; an organisation/branch is tagged IT or Civil.

### Part A — IT Industry
| Role            | Code         | Responsibilities (summary)                                              |
| --------------- | ------------ | ----------------------------------------------------------------------- |
| Employee        | `IT_EMP`     | Dashboard, leave, attendance/WFH, payslip, IT declarations, helpdesk, training, appraisal. |
| HR / Payroll    | `IT_HR`      | Onboarding, leave policy, payroll run, bulk leave, compliance, offboarding. |
| Manager / Lead  | `IT_MGR`     | Approve team leave, appraisals, team attendance, hiring requests, team training. |
| Finance Officer | `IT_FIN`     | Approve payroll, expense reimbursements, HR budget, statutory tax reports. |
| CEO             | `IT_CEO`     | Executive dashboard, approve HR policies, talent/succession pipeline.   |
| Asset Manager   | `IT_AST`     | IT asset inventory, allocate/recover, software licences, maintenance.   |

### Part B — Civil Industry
| Role                    | Code        | Responsibilities (summary)                                       |
| ----------------------- | ----------- | ---------------------------------------------------------------- |
| Site Employee           | `CV_EMP`    | Site/biometric attendance, leave, wage payslip, safety incidents. |
| HR                      | `CV_HR`     | Contractual/daily-wage workforce, labour compliance, transfers, PPE & safety training. |
| Civil / Facilities Admin| `CV_ADM`    | Project sites, facility maintenance, infra asset registry, space/seat booking. |
| Civil Asset Manager     | `CV_AST`    | Heavy machinery register, preventive maintenance, materials inventory, PPE inventory. |
| Facility/Civil Supervisor | `CV_SUP`  | Approve site leave, daily site report, safety inspections/permits, worker skills, facility issues. |

A platform `SUPER_ADMIN` role sits above all of these for configuration and tenant setup.

---

## 3. Module breakdown (derived from the stories)

The 50+ stories collapse into 14 functional modules. Each maps to a backend package and a
web route group.

| #  | Module                  | Backend package                     | Key stories                                   |
| -- | ----------------------- | ----------------------------------- | --------------------------------------------- |
| 1  | Auth & Security / RBAC  | `modules.auth`, `security`          | Login, JWT, OTP, RBAC, audit (cross-cutting)  |
| 2  | Organisation / Master   | `modules.org`                       | IT-HR-02 (calendars), CV-ADM-01 (sites), dropdowns |
| 3  | Employee Management     | `modules.user`                      | IT-HR-01, CV-HR-01, profile/bank/docs         |
| 4  | Attendance              | `modules.attendance`                | IT-EMP-03, CV-EMP-01, IT-MGR-03               |
| 5  | Leave Management        | `modules.leave`                     | IT-EMP-02, IT-HR-02/04, IT-MGR-01, CV-EMP-02, CV-SUP-01 |
| 6  | Payroll                 | `modules.payroll`                   | IT-EMP-04, IT-HR-03, IT-FIN-01, CV-EMP-03     |
| 7  | Recruitment             | `modules.recruitment` *(scaffold)*  | IT-MGR-04                                     |
| 8  | Performance / Appraisal | `modules.performance` *(scaffold)*  | IT-EMP-08, IT-MGR-02, IT-CEO-03               |
| 9  | Training / LMS          | `modules.training` *(scaffold)*     | IT-EMP-07, IT-MGR-05, CV-HR-04                |
| 10 | Helpdesk                | `modules.helpdesk`                  | IT-EMP-06, CV-ADM-02, CV-SUP-05               |
| 11 | Asset Management        | `modules.asset`                     | IT-AST-01..04, CV-AST-01..04, CV-ADM-03       |
| 12 | Finance                 | `modules.finance` *(scaffold)*      | IT-FIN-02/03/04, IT-CEO-02                    |
| 13 | Reports & Analytics     | `modules.dashboard` + report svc    | IT-HR-05, IT-CEO-01, every "export" AC        |
| 14 | Notifications           | `modules.notification`              | Every "notify / real-time / email" AC         |
| —  | Civil safety/site ops   | `modules.site` *(scaffold)*         | CV-EMP-04, CV-SUP-02/03, CV-AST materials/PPE |

> *scaffold* = data model + clear extension point documented; not yet a full vertical slice.
> The eight modules without the *scaffold* tag are implemented end-to-end (entity → repo →
> service → controller → web page) in this deliverable.

---

## 4. Cross-cutting acceptance-criteria themes

Reading every AC, the same non-functional requirements recur. These are handled **once**, centrally:

- **Real-time / refresh-without-reload** (IT-EMP-01 AC7, IT-MGR-01 AC7, CV-SUP-02 AC5) →
  WebSocket (`/ws`) + TanStack Query invalidation. See `config.WebSocketConfig`.
- **Notifications: email + in-app bell + SMS** (everywhere) → `modules.notification`
  (in-app + WebSocket now; Spring Mail wired; SMS/FCM are documented integration points).
- **Document/photo upload with size + type limits** (IT-EMP-02 AC5, IT-EMP-05 AC3, safety photos) →
  `StorageService` abstraction (local in dev → S3/MinIO in prod), max-size guard.
- **Export to Excel & PDF** (IT-HR-05 AC3, IT-MGR-03 AC5, CV-AST PDF) → Apache POI (Excel)
  + JasperReports (PDF). `ReportService` abstraction.
- **QR codes for assets** (IT-AST-01 AC4) → ZXing in `asset.QrCodeService`.
- **GPS / geo-fence validation** (IT-EMP-03 AC2, CV-EMP-01 AC1–4, CV-ADM-01 AC1) →
  `attendance.GeofenceService` (haversine vs. configurable radius per site/office).
- **Configurable rules** (grace periods, SLAs, accrual, geo-fence radius) → `org` master
  data + per-entity config columns, never hard-coded.
- **Audit trail / who-did-what + timestamps** (IT-HR-03 AC8, IT-FIN-01 AC8, etc.) →
  `BaseEntity` (created/updated by + at) + a dedicated `audit_log` table + login history.
- **Responsive 320px → desktop + PWA** (IT-EMP-01 AC6, IT-CEO-01 AC1) → Tailwind responsive
  utilities + `vite-plugin-pwa`; mobile-critical flows also shipped as a React Native app.
- **Role/menu/API permissions** → `Role`/`Permission` model + method security + a web
  `RoleGuard` that filters the navigation.

---

## 5. High-priority "must-work" stories (chosen for the vertical slices)

To make the foundation genuinely usable on day one, these stories are implemented through
the full stack (DB → API → web, and mobile where flagged 📱):

1. **US-IT-EMP-01** View Personal Dashboard — `dashboard` 📱
2. **US-IT-EMP-02 / CV-EMP-02** Apply for Leave + history + cancel — `leave` 📱
3. **US-IT-EMP-03 / CV-EMP-01** Mark Attendance / WFH / GPS punch — `attendance` 📱
4. **US-IT-EMP-04 / CV-EMP-03** View & download payslip — `payroll` 📱
5. **US-IT-HR-01** Onboard employee (profile + bank + dropdowns) — `user` + `org`
6. **US-IT-MGR-01 / CV-SUP-01** Approve/reject team leave — `leave`
7. **US-IT-EMP-06** Raise helpdesk ticket — `helpdesk`
8. **US-IT-AST-01/02** Asset registry + allocation + QR — `asset`

Everything else is reachable in the UI and has its data model + extension point ready.
