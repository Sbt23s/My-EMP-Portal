# Traceability Matrix — every user story → module, endpoint, status

Legend for **Status**:
- ✅ **Implemented** — full vertical slice (DB + API + web page, mobile where noted).
- 🟡 **Data + API** — entity/schema + REST endpoint exist; web is a placeholder page.
- ⬜ **Scaffolded** — data model + documented extension point; no endpoint yet.

Every story below has a home. The eight ✅ slices are the working core; 🟡/⬜ items follow
the identical pattern, so adding them is mechanical (see `02-architecture.md` → "Adding a module").

## Part A — IT Industry

### Employee (`IT_EMP`)
| Story | Title | Module | Primary endpoint(s) | Status |
| --- | --- | --- | --- | --- |
| US-IT-EMP-01 | View Personal Dashboard | dashboard | `GET /api/dashboard/me` | ✅ 📱 |
| US-IT-EMP-02 | Apply for Leave | leave | `POST /api/leave/requests`, `GET /api/leave/requests/me`, `POST /api/leave/requests/{id}/cancel` | ✅ 📱 |
| US-IT-EMP-03 | Mark Attendance / WFH | attendance | `POST /api/attendance/punch-in`, `/punch-out`, `GET /api/attendance/me/calendar` | ✅ 📱 |
| US-IT-EMP-04 | View & Download Payslip | payroll | `GET /api/payroll/payslips?user_id=`, `GET /api/payroll/payslips/{id}/pdf` | ✅ 📱 |
| US-IT-EMP-05 | Investment Declaration | finance | `POST /api/finance/declarations` | ⬜ |
| US-IT-EMP-06 | Raise Helpdesk Ticket | helpdesk | `POST /api/helpdesk/tickets`, `GET /api/helpdesk/tickets/me` | ✅ |
| US-IT-EMP-07 | Training & Learning | training | `GET /api/training/my-courses` | ⬜ |
| US-IT-EMP-08 | Acknowledge Appraisal | performance | `GET /api/performance/my-appraisal` | ⬜ |

### HR / Admin / Payroll (`IT_HR`)
| Story | Title | Module | Primary endpoint(s) | Status |
| --- | --- | --- | --- | --- |
| US-IT-HR-01 | Onboard New Employee | user + org | `POST /api/users`, `POST /api/users/{id}/bank`, `POST /api/dropdown` | ✅ |
| US-IT-HR-02 | Manage Leave Policies | leave + org | `POST /api/leave/types`, `POST /api/org/holidays` | 🟡 |
| US-IT-HR-03 | Process Monthly Payroll | payroll | `POST /api/payroll/runs`, `POST /api/payroll/runs/{id}/confirm` | 🟡 |
| US-IT-HR-04 | Bulk Approve/Reject Leave | leave | `POST /api/leave/requests/bulk-action` | 🟡 |
| US-IT-HR-05 | Compliance Reports | dashboard/report | `GET /api/reports/{type}?format=xlsx|pdf` | 🟡 |
| US-IT-HR-06 | Offboarding / F&F | user | `POST /api/users/{id}/offboarding` | ⬜ |

### Manager / Lead (`IT_MGR`)
| Story | Title | Module | Primary endpoint(s) | Status |
| --- | --- | --- | --- | --- |
| US-IT-MGR-01 | Approve Team Leave | leave | `GET /api/leave/requests/pending`, `POST /api/leave/requests/{id}/action` | ✅ |
| US-IT-MGR-02 | Conduct Appraisal | performance | `POST /api/performance/appraisals` | ⬜ |
| US-IT-MGR-03 | Track Team Attendance | attendance | `GET /api/attendance/team/today` | 🟡 |
| US-IT-MGR-04 | Raise Hiring Request | recruitment | `POST /api/recruitment/requisitions` | ⬜ |
| US-IT-MGR-05 | Assign Team Training | training | `POST /api/training/assignments` | ⬜ |

### Finance (`IT_FIN`)
| Story | Title | Module | Primary endpoint(s) | Status |
| --- | --- | --- | --- | --- |
| US-IT-FIN-01 | Approve Payroll | payroll/finance | `POST /api/payroll/runs/{id}/finance-approve` | 🟡 |
| US-IT-FIN-02 | Expense Reimbursements | finance | `GET/POST /api/finance/expenses` | ⬜ |
| US-IT-FIN-03 | HR Budget | finance | `GET/POST /api/finance/budgets` | ⬜ |
| US-IT-FIN-04 | Tax / Statutory Reports | finance/report | `GET /api/reports/statutory/{type}` | ⬜ |

### CEO (`IT_CEO`)
| Story | Title | Module | Primary endpoint(s) | Status |
| --- | --- | --- | --- | --- |
| US-IT-CEO-01 | Executive HR Dashboard | dashboard | `GET /api/dashboard/executive` | 🟡 |
| US-IT-CEO-02 | Approve HR Policies | finance/policy | `POST /api/policies/{id}/approve` | ⬜ |
| US-IT-CEO-03 | Talent / Succession | performance | `GET /api/performance/succession` | ⬜ |

### Asset Manager (`IT_AST`)
| Story | Title | Module | Primary endpoint(s) | Status |
| --- | --- | --- | --- | --- |
| US-IT-AST-01 | IT Asset Inventory | asset | `GET/POST /api/assets`, `GET /api/assets/{id}/qr` | ✅ |
| US-IT-AST-02 | Allocate / Recover Assets | asset | `POST /api/assets/{id}/allocate`, `/return` | ✅ |
| US-IT-AST-03 | Software Licences | asset | `GET/POST /api/assets/licenses` | 🟡 |
| US-IT-AST-04 | Asset Maintenance | asset | `POST /api/assets/{id}/maintenance` | 🟡 |

## Part B — Civil Industry

### Site Employee (`CV_EMP`)
| Story | Title | Module | Primary endpoint(s) | Status |
| --- | --- | --- | --- | --- |
| US-CV-EMP-01 | Site / Biometric Attendance | attendance | `POST /api/attendance/punch-in` (geo-fenced) | ✅ 📱 |
| US-CV-EMP-02 | Apply for Leave (Civil) | leave | same as IT-EMP-02 + notice-period rule | ✅ 📱 |
| US-CV-EMP-03 | Wage Payslip & Register | payroll | `GET /api/payroll/payslips`, wage register view | ✅ 📱 |
| US-CV-EMP-04 | Safety Incident Reporting | site | `POST /api/site/incidents` | ⬜ |

### HR (`CV_HR`)
| Story | Title | Module | Primary endpoint(s) | Status |
| --- | --- | --- | --- | --- |
| US-CV-HR-01 | Contractual / Daily-Wage Workers | user | `POST /api/users` (employmentType) | 🟡 |
| US-CV-HR-02 | Labour Compliance | finance/report | `GET /api/reports/labour/{type}` | ⬜ |
| US-CV-HR-03 | Multi-Site Transfers | user/org | `POST /api/users/{id}/transfers` | ⬜ |
| US-CV-HR-04 | PPE & Safety Training | training/asset | `POST /api/training/certifications`, PPE issue | ⬜ |

### Civil / Facilities Admin (`CV_ADM`)
| Story | Title | Module | Primary endpoint(s) | Status |
| --- | --- | --- | --- | --- |
| US-CV-ADM-01 | Project Sites & Locations | org | `GET/POST /api/org/sites` | 🟡 |
| US-CV-ADM-02 | Facility Maintenance | helpdesk | `POST /api/helpdesk/tickets` (type=FACILITY) | ✅ |
| US-CV-ADM-03 | Infra Asset Registry | asset | `GET/POST /api/assets` (category=INFRA) | ✅ |
| US-CV-ADM-04 | Space / Seat Allocation | site | `GET/POST /api/site/spaces`, `/bookings` | ⬜ |

### Civil Asset Manager (`CV_AST`)
| Story | Title | Module | Primary endpoint(s) | Status |
| --- | --- | --- | --- | --- |
| US-CV-AST-01 | Heavy Machinery Register | asset | `GET/POST /api/assets` (category=MACHINERY) | ✅ |
| US-CV-AST-02 | Preventive Maintenance | asset | `POST /api/assets/{id}/maintenance/schedule` | 🟡 |
| US-CV-AST-03 | Material / Consumable Inventory | site | `GET/POST /api/site/materials` | ⬜ |
| US-CV-AST-04 | PPE Inventory & Distribution | site/asset | `GET/POST /api/site/ppe` | ⬜ |

### Facility / Civil Supervisor (`CV_SUP`)
| Story | Title | Module | Primary endpoint(s) | Status |
| --- | --- | --- | --- | --- |
| US-CV-SUP-01 | Approve Site Leave | leave | same as IT-MGR-01 + min-manning rule | ✅ |
| US-CV-SUP-02 | Daily Site Report (DSR) | site | `POST /api/site/dsr` | ⬜ |
| US-CV-SUP-03 | Safety Inspections / Permits | site | `POST /api/site/inspections`, `/permits` | ⬜ |
| US-CV-SUP-04 | Worker Skill Records | user/training | `GET/POST /api/users/{id}/skills` | ⬜ |
| US-CV-SUP-05 | Raise Facility/Equipment Issues | helpdesk | `POST /api/helpdesk/tickets` | ✅ |

---

## Coverage summary

| Status | Count | Notes |
| --- | --- | --- |
| ✅ Implemented (full slice) | 16 stories across 8 modules | The working core, incl. all 8 mobile-flagged flows |
| 🟡 Data + API | 12 stories | Endpoint live, web placeholder |
| ⬜ Scaffolded | 22 stories | Model + extension point documented |

This is deliberate: rather than 50 half-broken features, you get a **solid, demonstrable
spine** (login → dashboard → attendance → leave → payroll → assets → helpdesk on web *and*
mobile) plus a clean, documented path to finish the rest.
