# API Contract

Base URL (dev): `http://localhost:7060`
All protected endpoints require `Authorization: Bearer <accessToken>`.
All responses are wrapped:

```json
{ "success": true, "message": "OK", "data": { ... }, "timestamp": "2026-..." }
```

Errors:

```json
{ "success": false, "message": "Insufficient leave balance", "errorCode": "LEAVE_BALANCE", "data": null }
```

## Compatibility map — existing PHP API → new Spring Boot API

The Spring Boot service preserves the semantics of every existing Postman request so your
current flows have a direct equivalent.

| Existing (PHP)                              | New (Spring Boot)                                  | Notes |
| ------------------------------------------- | -------------------------------------------------- | ----- |
| `POST /api/create_user.php`                 | `POST /api/auth/signup`                            | Same body fields (Aadhaar etc.). |
| `POST /api/auth/aadhar_login.php`           | `POST /api/auth/login`                             | `{ aadhar, password }` → tokens. |
| `POST /api/phonenumbervalidate.php`         | `POST /api/auth/validate-phone`                    | `{ phone }` → `{ available }`. |
| `POST /api/user/change_password.php`        | `POST /api/auth/change-password`                   | `{ currentPassword, newPassword }`. |
| `POST /api/user/update_photo.php`           | `POST /api/users/me/photo`                         | `{ photo: <base64> }`. |
| `POST /api/user/update_profile.php`         | `PUT  /api/users/me`                               | Demographic + address. |
| `GET  /api/profile.php`                     | `GET  /api/users/me`                               | Current profile. |
| `POST /api/bank/index.php` (action=add)     | `POST   /api/users/{id}/bank`                      | RESTful split of the `action` param. |
| `POST /api/bank/index.php` (action=update)  | `PUT    /api/users/{id}/bank/{bankId}`             | |
| `POST /api/bank/index.php` (action=delete)  | `DELETE /api/users/{id}/bank/{bankId}`             | |
| `POST /api/bank/index.php` (action=view)    | `GET    /api/users/{id}/bank`                      | |
| `POST /api/payslip/index.php?action=generate` | `POST /api/payroll/payslips`                     | Same components (basic/hra/allowances/deductions). |
| `GET  /api/payslip/index.php?action=list`   | `GET  /api/payroll/payslips`                       | Optional `?userId=`. |
| `POST /api/dropdown.php`                     | `POST /api/dropdown`                               | **Identical** `{ type }` or `{ type: [..] }` body. |

## Auth

### `POST /api/auth/login`
```json
{ "aadhar": "123456789022", "password": "Test1234@" }
```
→
```json
{ "success": true, "data": {
  "accessToken": "ey...", "refreshToken": "ey...",
  "tokenType": "Bearer", "expiresIn": 900,
  "user": { "id": 1, "name": "Arun Kuma", "roles": ["IT_EMP"] }
}}
```

### `POST /api/auth/refresh`
```json
{ "refreshToken": "ey..." }   →   { "accessToken": "ey...", "expiresIn": 900 }
```

## Dropdown (master data) — unchanged contract
`POST /api/dropdown`
```json
{ "type": "department" }
// or
{ "type": ["department", "designation", "blood_group"] }
```
Supported types: `blood_group, office_location, department, designation, employment_status,
position, leave_type, shift, asset_category, asset_status`.

## Attendance
| Method | Path | Body / notes |
| --- | --- | --- |
| POST | `/api/attendance/punch-in` | `{ latitude, longitude, mode: OFFICE\|WFH\|SITE, siteId? }` → validates geo-fence |
| POST | `/api/attendance/punch-out` | `{ latitude, longitude }` → computes hours + overtime |
| GET  | `/api/attendance/me/today` | today's record |
| GET  | `/api/attendance/me/calendar?month=&year=` | colour-coded month |
| GET  | `/api/attendance/team/today` | manager view (role-gated) |

## Leave
| Method | Path | Body / notes |
| --- | --- | --- |
| GET  | `/api/leave/types` | active leave types + balances for current user |
| POST | `/api/leave/requests` | `{ leaveTypeId, fromDate, toDate, reason, attachmentId? }` |
| GET  | `/api/leave/requests/me` | history with status |
| POST | `/api/leave/requests/{id}/cancel` | only while Pending |
| GET  | `/api/leave/requests/pending` | manager/supervisor inbox |
| POST | `/api/leave/requests/{id}/action` | `{ decision: APPROVE\|REJECT, comment }` |
| POST | `/api/leave/requests/bulk-action` | `{ ids: [], decision, comment }` |

## Payroll
| Method | Path | Body / notes |
| --- | --- | --- |
| POST | `/api/payroll/payslips` | generate (same components as PHP) |
| GET  | `/api/payroll/payslips?userId=` | list (all or per-user) |
| GET  | `/api/payroll/payslips/{id}` | detail with full breakdown |
| GET  | `/api/payroll/payslips/{id}/pdf` | password-protected PDF (US-IT-EMP-04 AC3) |

## Assets
| Method | Path | Body / notes |
| --- | --- | --- |
| GET  | `/api/assets?category=&status=` | registry (IT / INFRA / MACHINERY) |
| POST | `/api/assets` | create (auto Asset ID + QR) |
| GET  | `/api/assets/{id}/qr` | PNG QR code |
| POST | `/api/assets/{id}/allocate` | `{ userId }` → Assigned |
| POST | `/api/assets/{id}/return` | `{ condition }` |

## Helpdesk
| Method | Path | Body / notes |
| --- | --- | --- |
| POST | `/api/helpdesk/tickets` | `{ title, description, category, priority, type }` |
| GET  | `/api/helpdesk/tickets/me` | my tickets |
| GET  | `/api/helpdesk/tickets` | all (agent/admin, role-gated) |
| POST | `/api/helpdesk/tickets/{id}/comments` | add comment |
| POST | `/api/helpdesk/tickets/{id}/status` | `{ status }` |

## Dashboard
| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/dashboard/me` | employee widgets (attendance, pending leave, announcements) |
| GET | `/api/dashboard/executive` | CEO KPIs (headcount, attrition, payroll trend) — role-gated |

Full live reference: run the backend and open **Swagger UI** at
`http://localhost:7060/swagger-ui.html`.
