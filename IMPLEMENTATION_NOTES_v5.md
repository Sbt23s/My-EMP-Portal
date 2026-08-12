# HR Portal — v5 Implementation Notes

This pass implements the four feature areas you asked for, end-to-end. It builds
on `hr-portal-fixed-v4`. Everything on the **web frontend is build-verified**
(`npm run build` → exit 0, TypeScript type-check clean). The **backend changes
could not be run** here (no Maven Central, MySQL or Redis in this sandbox — same
limitation noted in the previous pass), so backend code is written to the
codebase's own patterns and statically checked (schema ↔ entity column parity,
method/type references, brace balance), but not runtime-tested.

---

## 1) Payslip request → approve → customizable generate → download  ✅

**Flow:** Employee / HR / Manager click **"Request payslip"** (Payslips page) →
the request routes to **Admin** (holders of `PAYROLL_RUN`) → Admin opens
**Payslip Requests** page, fills the **fully customizable payslip form**, and
clicks **Generate & send** → a payslip is created + PDF rendered → **only the
requester** sees a **Download** button for it.

**Customizable fields (match the Pixous format you sent):**
- Company: name, **logo upload**, GSTIN, address
- Employee: name, **Employee ID**, designation, department, bank name, bank A/C
- Pay period: pay date, working days, LOP days
- Earnings (each an input box): Basic, HRA, Allowances, Overtime, **Performance Pay**, Expenses
- Deductions (each an input box): PF, ESI, Professional Tax, TDS, Health Insurance, **Salary Advance**, Other
- Live preview on the right updates as you type; totals + net pay auto-calculate.

**Requester-only download** is enforced by the existing owner check on
`GET /api/payroll/payslip/{id}/pdf` (a user can only download their own payslip).

**New backend:**
- `V15` migration: `payslip_requests` table; ~15 customizable columns on `payslips`.
- `PayslipRequest` entity + repository; `PayslipRequestService`.
- DTOs: `CreatePayslipRequestDto`, `PayslipRequestResponse`, `ApprovePayslipRequestDto`; extended `PayslipResponse`.
- `PayrollController` endpoints: `POST /requests`, `GET /requests/me`, `GET /requests`,
  `POST /requests/logo`, `POST /requests/{id}/approve`, `POST /requests/{id}/reject`.
- `ReportService.payslipPdfBytes` rewritten to honor the custom company fields,
  embed an uploaded logo, and show only the non-zero extra lines.

**New frontend:** `Payslips.tsx` (Request button + My Requests table + owner
download), `PayrollRequests.tsx` (admin inbox + customizable form + preview).

> Note: HR (`IT_HR`) also holds `PAYROLL_RUN` in the seed data, so HR can also
> approve payslip requests alongside Admin. If you want this to be **admin-only**,
> remove `PAYROLL_RUN` from the `IT_HR` role grant in `V8__seed.sql` (or a new
> migration) — I left the existing role model untouched to avoid side-effects.

---

## 2) Leave — admin-only routing + 1 casual / 1 sick per month  ✅

- **Routing:** a leave application now notifies **admins** (`LEAVE_APPROVE`
  holders), not the reporting manager. Leave is confirmed only once an admin
  approves. The **Approvals** inbox shows **all** pending requests to admins
  (`USER_MANAGE`); non-admin approvers still see only their direct reports.
- **Monthly cap:** `leave_types.monthly_limit` (new column) = **1** for Casual
  (`CL`) and Sick (`SL`). Applying beyond the cap is blocked with:
  _"No Casual Leave left: you have already used your 1 Casual Leave for this month"_.
  The cap counts pending + approved requests in that calendar month.

**Changed:** `LeaveType` entity (+`monthlyLimit`), `LeaveService.apply`
(cap check + admin routing) and `pendingForManager` (admin sees all),
`LeaveRequestRepository` (+`countMonthlyConsuming`, `findAllPending`),
`LeaveTypeRequest`/`LeaveTypeResponse` DTOs.

---

## 3) Work Reports — spreadsheet-style entry + Employee Work List  ✅

- **Employee:** a spreadsheet-style table (S.No, Date, Project, Hours,
  Task/Module) with an **entry row of input boxes** to save new rows; each saved
  row can be deleted. Matches the shared-sheet layout you sent, but editable.
- **HR / Admin:** an **"Employee Work List"** section listing **all employees**
  (name + code shown like the sheet), each expandable to their rows, with a
  **search box** (by name or employee ID).

**New backend:** `work_reports` table (V15); `WorkReport` entity + repository;
`WorkReportService`; `WorkReportController` (`GET /me`, `POST`, `PUT/{id}`,
`DELETE/{id}`, and `GET /all?q=` gated by `REPORT_VIEW`/`USER_MANAGE`);
DTOs `WorkReportRequest`, `WorkReportResponse`, `EmployeeWorkList`.

**New frontend:** `WorkReports.tsx`.

---

## 4) Team Attendance + Payroll Runs  ✅ (wiring)

- **Team Attendance:** added a **search box** (by employee name) beside the date
  filter; list filters live. (`TeamAttendance.tsx`.)
- **Payroll Runs** already had a working generate → confirm → finance-approve
  flow wired to `/api/payroll/runs…`; left intact and reachable for
  `PAYROLL_RUN`/`PAYROLL_APPROVE` roles.

---

## Navigation / routing

Added nav items: **Payslip Requests** (admin, `PAYROLL_RUN`) and **Work Reports**
(everyone). Routes added in `router.tsx`.

---

## How to run (unchanged from before)
```bash
docker compose up -d                 # MySQL + Redis
cd backend && mvn spring-boot:run    # http://localhost:7060
cd web && npm install && npm run dev # http://localhost:5174
```
Demo logins (password `Test1234@`): `ADM0001` (admin), `EMP0004` (HR),
`EMP0003` (manager), `EMP0001`/`EMP0002` (employees). Flyway applies `V15`
automatically on first backend start.

## What I could NOT verify
- Backend compile/run (no Maven/DB in sandbox). Static-checked only.
- The PDF's exact visual output (needs the backend running). The layout code
  follows the existing working payslip renderer and adds the custom fields.
