# Employee Management System — Persona & Workflow Requirements

**Version 1.0 · 30 July 2026 · Condensed companion to the full SRS**

This document states the requirements **from the user's side**: who each persona is, what they can reach, and the workflows they actually perform, step by step. It replaces module-by-module reading with role-by-role reading.

- Personas are numbered `P-n`. Workflows are numbered `W-<PERSONA>-nn`.
- Cross-persona end-to-end journeys are numbered `J-n`.
- Each workflow states its **trigger**, **steps**, the **rules the system enforces**, and the **result**.
- Full detail — every numbered functional requirement, the data dictionary, and the non-functional requirements — lives in *Employee Management System — Software Requirements Specification*.

---

## 1. Persona Catalogue

| # | Persona | Role code(s) | Displayed as | Core job in the system |
|---|---|---|---|---|
| **P-1** | Employee | `IT_EMP`, `CV_EMP` | Employee / Field Employee | Own attendance, leave, tasks, claims, payslips, requests |
| **P-2** | Team Leader | `IT_TL` | Team Leader | Runs the day's work for one team; first-level approver |
| **P-3** | HR | `IT_MGR` | HR | Employee lifecycle, policy, approvals, payroll requests |
| **P-4** | HR Head | `IT_HR`, `CV_HR` | HR Head | Same as HR, senior escalation |
| **P-5** | Administrator | `SUPER_ADMIN`, `BOARD_ADMIN` | Super Admin / Board Admin | Configuration, payroll runs, oversight of every queue |
| **P-6** | Finance Officer | `IT_FIN` | Finance Officer | Payroll run approval, financial reporting |
| **P-7** | Executive | `IT_CEO` | CEO | Organisation-wide dashboard and reports |
| **P-8** | Asset Manager | `IT_AST`, `CV_AST` | Asset Manager | Asset registry, allocation, returns |
| **P-9** | Site Supervisor | `CV_SUP`, `CV_ADM` | Site Supervisor / Facilities Admin | Site attendance, site leave, safety, facilities |

### 1.1 What each persona can reach

| Capability | P-1 | P-2 | P-3 / P-4 | P-5 | P-6 | P-7 | P-8 | P-9 |
|---|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| Own attendance, leave, payslips, tasks, claims, tickets | ● | ● | ● | | | | | ● |
| Approve leave & hourly permission | | ● | ● | ● | | | | ● |
| View team attendance | | ● | ● | ● | | | | ● |
| Assign tasks | | ● | ● | ● | | | | |
| View / export all tasks | | | ● | ● | | | | |
| Create & edit employees | | | ● | ● | | | | |
| Create & delete teams | | | ● | ● | | | | |
| Leave policy & balance allocation | | | ● | ● | | | | ● |
| Salary structures, payslip requests | | | ● | ● | | | | |
| Approve payroll run | | | | | ● | | | |
| Decide expense claims | | | ● | ● | | | | |
| Helpdesk agent | | | ● | ● | | | ● | ● |
| Respond to complaints | | | ● | ● | | | | |
| Manage assets | | | ● | ● | | | ● | ● |
| Calendar entries | | | ● | ● | | | | ● |
| Reports & exports | | ● | ● | ● | ● | ● | | ● |
| Executive dashboard | | | | ● | | ● | | |
| System & AI settings, channels, deletion | | | | ● | | | | |

**R-1** Every capability above is enforced on the server at the endpoint. Hiding a menu entry is convenience only and is never the only control.
**R-2** Where a screen returns other people's records, the result set is additionally narrowed to what that persona may see — a Team Leader never receives another team's rows.
**R-3** No persona can ever decide their own request, in any workflow.

---

## 2. Approval Routing — the rule behind every request

**R-4** Routing is **single-level**: for any one request exactly one class of approver can act, so a requester can never pick a route that skips a level. The picker offered to the requester is computed by the same rule the decision endpoint enforces, so it can never offer a recipient whose decision would then be refused.

| Request | Raised by | Goes to — and only to |
|---|---|---|
| Leave, up to 3 working days | Employee | A Team Leader **of that employee's own team** |
| Leave, more than 3 working days | Employee | HR |
| Leave, any length | Team Leader | HR |
| Leave, any length | HR | The single named escalation approver |
| Hourly permission | Employee | A Team Leader of their own team |
| Hourly permission | Team Leader | HR |
| Hourly permission | HR | The single named escalation approver |
| Support ticket / complaint / need | Employee or Team Leader | HR |
| Support ticket / complaint / need | HR | The single named escalation approver |
| Payslip request | Anyone | Every holder of payroll-run capability |
| Expense claim | Anyone | HR and Administrators |

**R-5** Where a request names a specific recipient, only that recipient may act. Where none was named, the role-and-duration rule decides.
**R-6** Administrators may **view** any queue for oversight; rows they may not decide are read-only.
**R-7** Rejecting anything — leave, permission, claim, payslip request — requires a written reason.

---
---

# P-1 · Employee

The self-service persona. Everything here is also available to every other persona for their own records.

### W-EMP-01 · Sign in

**Trigger** Employee opens the portal.

1. Enter username and password.
2. System verifies the password, clears any failed-attempt count, records the sign-in time.
3. Personal dashboard opens.

**Rules** 5 consecutive failures lock the account for 15 minutes · a disabled account is refused with an instruction to contact HR · every attempt is recorded with IP address and device · the same message is shown for an unknown user and a wrong password · session lasts 4 hours and renews silently within that window, then returns to sign-in.

**Result** Dashboard showing: punch state for today, leave balances, pending leave count, open ticket count, assets held, and the five latest notifications.

---

### W-EMP-02 · Punch in and punch out

**Trigger** Employee arrives / leaves.

1. Open Attendance, choose mode — Office, Work From Home or Site.
2. Press Punch In. The browser or app supplies location.
3. System checks the location against the office or site geofence.
4. At end of day, press Punch Out.

**Rules** One punch-in and one punch-out per day; a second is refused · Work-From-Home punches are not location-checked · **no location never blocks a punch** — it is accepted and flagged as outside the geofence · default geofence radius 200 m where the location defines none · office hours 09:00–18:00, grace zero, so a punch after 09:00 is late by the difference · time past 18:00 is overtime · punch-out requires a punch-in.

**Result** Attendance row with mode, status, late minutes, worked minutes, overtime and both coordinates.

---

### W-EMP-03 · Check the month

**Trigger** Employee wants to know where they stand.

1. Open Attendance → monthly summary.

**Result** Present days, work-from-home days, late days, absent days, total late minutes, total overtime, and the number of working days.

**Rules** Working days exclude **Sundays and holidays; Saturday counts as a working day** · the current month counts only up to today, so a mid-month view never shows future absences.

---

### W-EMP-04 · Apply for leave

**Trigger** Employee needs time off.

1. Open Leave. Current balances are shown per type.
2. Pick a leave type, a from and to date, write a reason, attach a document if needed.
3. The system computes the working days in the range and offers **only the valid approvers** for that duration.
4. Choose the approver and submit.

**Rules enforced in order**

| Check | Refused when |
|---|---|
| Date order | End date is before the start date |
| Gender restriction | Type is restricted and the profile does not match |
| Past dates | Start date is in the past and the type does not allow it |
| Minimum notice | Future-dated and the notice is shorter than the type requires |
| Working days | The range contains no working day |
| Quarterly cap | Casual and Sick leave already used once in this calendar quarter — the message names the date the next one becomes available |
| Balance | No allocation exists, or availability is less than the days requested (Loss of Pay is exempt) |

**Result** Request created as Pending. The chosen approver gets an in-app notification and an SMS naming the applicant, type, days and dates.

---

### W-EMP-05 · Cancel leave

1. Open Leave → own history → Cancel.

**Rules** Only Pending or Approved leave can be cancelled, and only by its owner · cancelling an **approved** leave refunds the consumed balance · the approver who was handling it is told it was withdrawn.

---

### W-EMP-06 · Request an hour or two off

**Trigger** Employee needs a short absence inside a working day.

1. Open Leave → Permission.
2. Enter the date, a start and end time in `HH:mm`, and a reason.
3. The system computes the hours and offers the valid approver. Submit.

**Rules** End time must be after the start time · hours are computed to two decimals · **a pending request whose date has passed becomes overdue and can no longer be decided by anyone** · only Pending requests can be cancelled, and only by the owner.

---

### W-EMP-07 · Log the day's work

1. Open Work Reports → add a row: date, project, hours, task or module.

**Rules** Own rows only — editing or deleting another employee's report is refused.

**Result** The row appears in the employee's own list and in their Team Leader's and HR's roll-up.

---

### W-EMP-08 · Work assigned tasks

**Trigger** Task-assigned notification and SMS arrives.

1. Open Tasks. Each card shows title, description, priority, due date and who assigned it.
2. Drag progress or set a percentage as work proceeds.
3. Mark complete, or set progress to 100.

**Rules** Only the assignee may report progress or complete · progress and status stay in step — 0 is Pending, 1–99 is In Progress, 100 is Completed · **state moves forward only**; a completed or started task cannot be pushed back, in the screen or through the API.

**Result** The assigner is notified of each progress change and of completion, in-app and by SMS.

---

### W-EMP-09 · Get a payslip

**Trigger** Employee needs a payslip for a month.

1. Open Payslips → Request, choose month and year, add a note.
2. Wait for the decision notification.
3. On approval, download the PDF.

**Rules** No request for a month **before the joining month** — the message names the earliest permissible month · one pending request per month · **only the requester can see or download that payslip**; another employee's request for it is refused · the PDF is regenerated at download so corrected designation, department and bank details always appear.

---

### W-EMP-10 · Claim travel and expenses

1. Open Claims → New. Enter date, location, category, odometer start and end, hills and plains kilometres, bus fare, other amounts, remarks.
2. Attach the fuel receipt and photographs.
3. Submit.

**Rules** The owner may correct the claim **only while it is still pending**; once reviewed, it is refused with that explanation · HR and Administrators may correct any claim at any time, and the owner is told what changed · the owner may delete their own claim.

**Result** Every approver is notified in-app and by SMS with the date and gross total.

---

### W-EMP-11 · Raise a support request

1. Open Supports → New. Title, description, attachments, priority.
2. The system addresses it to HR and sets the response deadline from the priority.
3. Follow the thread; reply as needed.
4. Once resolved, give a rating.

**Rules** The category is set from the employee's own division — Digital or Infra — never chosen · deadline is 4 h critical, 8 h high, 24 h medium, 48 h otherwise · the raiser may edit the ticket **only while it is still Open**; after that, add a reply instead · **the raiser can never decide their own ticket** · only the raiser may rate, and only once resolved or closed.

---

### W-EMP-12 · Raise a complaint or a need

1. Open Complaints → New. Choose Complaint or Need, a category, subject, description, priority.
2. Choose the HR recipient offered. Submit.

**Rules** A reference code is issued · the submitter is never notified of their own submission · HR's response and status change come back as a notification and an SMS.

---

### W-EMP-13 · Report a safety incident

1. Open Safety → Report. Choose the incident type and severity, describe it, name the zone, set when it happened.
2. Optionally mark it **anonymous**.

**Rules** An anonymous report **never reveals its reporter**, in any view, notification or export · every safety reviewer is notified in-app and by SMS · the reporter is told when the investigation status changes.

---

### W-EMP-14 · Receive an asset

**Trigger** Asset-assigned notification and SMS arrives.

1. Open Assets → My Assets.
2. Press Acknowledge to confirm receipt.

**Rules** Only the named assignee may acknowledge.

---

### W-EMP-15 · Keep the record straight

1. **Profile** — update name, date of birth, gender, e-mail, address.
2. **Photo** — upload, replace or remove; removing restores the initials avatar.
3. **Bank** — add, edit, delete accounts; marking one primary demotes the others.
4. **Password** — change it by supplying the current one.

**Rules** A password change signs the account out everywhere else · the IFSC code is stored upper-cased · at most one primary bank account.

---

### W-EMP-16 · Stay in touch

- **Announcements** — every employee is enrolled automatically on creation and cannot miss a broadcast.
- **Team room** — a private channel for the employee's own team, created on demand with every teammate enrolled.
- **Direct messages** — one-to-one conversations with any colleague, backed by a hidden two-member room.
- Text, voice notes and file attachments up to 25 MB per file.

**Rules** A conversation with oneself is refused · a member may delete only their own message · new messages arrive live and raise a notification.

---

### W-EMP-17 · Ask the assistant

1. Open the assistant from any screen.
2. Ask in English, Tamil or Hindi — by typing or by voice.

**Rules** The assistant answers from a guide to the system plus an HR-curated knowledge base · it offers only what is actually configured — speech and transcription appear only when available · an unreachable provider produces a graceful reply, never a failure.

---

### W-EMP-18 · See the organisation

Available to every employee without special permission:

| View | Shows |
|---|---|
| Absent today | Everyone with neither a punch nor approved leave today |
| Team presence | Which teammates have punched in, and when |
| On leave today | Everyone whose approved leave covers today |
| Celebrations | Birthdays and anniversaries in the next 60 days, twelve at a time |
| Calendar | Holidays alongside approved leave |

**Rules** A daily job at 09:00 tells the whole workforce about that day's birthdays and anniversaries, in-app and by one bulk SMS, skipping the celebrant themselves.

---
---

# P-2 · Team Leader

Everything an Employee can do, plus first-level authority over one team. **The team is defined by shared designation title.**

### W-TL-01 · Decide short leave

**Trigger** Own-team leave request arrives.

1. Open Leave → Approvals. Pending, Approved and Rejected tabs.
2. Review the applicant, type, dates, working days and reason.
3. Approve, or reject with a reason.

**Rules** A Team Leader may decide **only own-team leave of 3 working days or less**; anything longer belongs to HR and does not appear as actionable · balance is re-checked at the moment of approval and a shortfall that appeared in the meantime is refused · a rejection requires a reason · approval consumes the balance.

---

### W-TL-02 · Decide hourly permission

1. Open Leave → Permission → requests addressed to me.
2. Approve, or reject with a reason.

**Rules** Only own-team requests reach a Team Leader · an overdue request — its date already past — can no longer be decided.

---

### W-TL-03 · Assign and track team tasks

1. Open Tasks → Assign. Choose the team member, title, description, due date, priority.
2. Track progress on the grouped view.
3. Edit title, description, due date or priority as things change.

**Rules** A Team Leader may assign **only to their own team members**; anyone else is refused with that message · a due date in the past is refused · priority is Low, Medium or High, defaulting to Medium · progress and status remain the assignee's to set · only the assigner, or an Administrator, may edit a task.

**Result** The assignee gets a notification and an SMS naming the task, their employee code and the due date. Edits notify them too.

---

### W-TL-04 · Watch the team

| Workflow | Screen | Scope |
|---|---|---|
| **W-TL-05** Team attendance for a date or a range | Team Attendance | Own team only |
| **W-TL-06** Team work reports, grouped with hours totalled | Work Reports | Own team only |
| **W-TL-07** Team expense claims | Claims | Own team only |
| **W-TL-08** Team leave calendar | Calendar | Own team and self |

**Rules** Every one of these is narrowed to teammates sharing the Team Leader's designation title. Another team's rows are never returned.

---
---

# P-3 / P-4 · HR and HR Head

The lifecycle persona: brings people in, keeps the record right, decides most requests, and takes them out.

### W-HR-01 · Bring a new employee in

**Trigger** A joining is confirmed.

1. Open Employees → Add. Complete the joining form:
   - identity — username, password, name, date of birth, gender, national identifier, phone, e-mail;
   - address block;
   - employment — division, department, team, office location, reporting manager, employment type, joining date;
   - statutory — PAN, provident-fund number, blood group;
   - contact — alternate phone, emergency contact and relation, personal e-mail;
   - role.
2. Add the bank account on the same form.
3. Upload documents — offer letter, identity scan, certificates.
4. Hand the username and password to the employee.

**Rules** A blank or duplicate username is refused; so is a duplicate national identifier or phone · **the employee code is generated automatically** · the division labels Digital and Infra are stored canonically so display text never leaks into data · status defaults to Active; an Offboarded status disables the account · joining date defaults to today · an unknown role code is refused · **the new employee joins the announcement channel automatically**.

---

### W-HR-02 · Import many at once

1. Open Employees → Import. The screen shows the exact expected sheet and column layout and offers a blank template.
2. Upload the workbook.
3. Review the per-row result and distribute the credentials.

**Rules** Sheet and column names must match exactly; anything unrecognised is ignored rather than rejected · **each row is created independently, so one bad row never rolls back the batch** · the result names every row, whether it succeeded, and why not.

---

### W-HR-03 · Run onboarding

1. Open Onboarding → Start for the new employee.
2. A checklist is created with the standard items: Document Collection, Bank Details, IT Asset Assignment, Team Introduction, Security Training.
3. Tick items off as they complete.

**Rules** Onboarding cannot be started twice · the checklist closes itself once every item is ticked.

---

### W-HR-04 · Keep the record right

| Workflow | Action | Rules |
|---|---|---|
| **W-HR-05** | Edit any field on any employee record | Only supplied fields change; status and enablement stay consistent |
| **W-HR-06** | Reset username or password | A username held by another account is refused; the reset is what the employee then signs in with |
| **W-HR-07** | Read an employee's current password | A deliberate, separate action — the value never travels with an ordinary profile read, a listing or an export |
| **W-HR-08** | Upload further documents | Stored as a path list on the employee |
| **W-HR-09** | Search the directory | Filter by text, division, department and status |

---

### W-HR-10 · Manage teams

1. Open Teams. Create a team by name; delete one by name.
2. Move employees between teams by editing the designation on their record; detach with Remove from team.

**Rules** A blank name is refused; a duplicate name is refused · **deleting a team first detaches every member** so nobody is left pointing at a team that no longer exists.

---

### W-HR-11 · Decide leave and permission

1. Open Leave → Approvals for leave of **more than 3 days**, and all Team Leaders' leave.
2. Open Leave → Permission for Team Leaders' hourly requests.
3. Approve, or reject with a reason.

**Rules** Routing is enforced by the API, not just the screen — a request outside HR's level is refused even if submitted directly · balance is re-checked at approval · HR's *own* leave and permission go to the single named escalation approver, not to another HR.

---

### W-HR-12 · Set leave policy and hand out balances

1. Open Leave → Leave Policies. Create or edit a leave type: name, code, days per year, carry-forward, encashable, gender restriction, past-dates allowance, accrual, minimum notice, per-quarter cap, paid flag.
2. Once a year, press Allocate defaults to give every enabled employee their annual entitlement.

**Rules** Codes are unique; a code matching a **deleted** type reactivates and updates it rather than failing · deleting a type is a soft delete, so history survives · allocation **never overwrites an existing balance**, so it is safe to run repeatedly · allocation is refused for a year already past · types with no cap are skipped.

---

### W-HR-13 · Turn payslip requests around

**Trigger** Payslip-request notification arrives.

1. Open Payroll → Requests.
2. Either reject with a note, or approve by completing the payslip form:
   - earnings — basic, house rent allowance, allowances, overtime, performance, expenses;
   - deductions — provident fund, ESI, professional tax, tax deducted at source, health insurance, salary advance, other;
   - loss-of-pay days, working days, pay date;
   - optional overrides — company name, logo, tax registration, address, bank, designation, department, employee name and code.
3. Approve. The PDF is produced and linked to the request.

**Rules** A request already decided is refused · gross is the sum of the six earnings; total deduction the sum of the seven deductions, salary advance included; net is the difference · **the payslip stores exactly what was entered**, so the employee's download reproduces that format · money is held to two decimals, rounded half-up.

**Result** The requester is told the payslip is ready, in-app and by SMS.

---

### W-HR-14 · Maintain pay

1. Open Payroll → Salaries. Set an employee's basic, house rent allowance, allowances, provident-fund amount, ESI applicability and professional tax.

**Rules** The provident-fund figure is a **flat rupee amount**, not a percentage · ESI is 0.75 % of gross, applied only when marked applicable and gross is within the 21 000 ceiling · overtime is priced at monthly gross ÷ 240 per hour · automatic attendance-driven loss of pay is **not** deducted, so it cannot double-count with the manual entry.

---

### W-HR-15 · Other HR queues

| Workflow | Queue | Action | Key rule |
|---|---|---|---|
| **W-HR-16** | Claims | Approve, reject or return to pending | Rejection needs a reason; the owner is told the outcome and the reason |
| **W-HR-17** | Supports | Move a ticket along its states | Only the recipient may progress it; the raiser never decides their own |
| **W-HR-18** | Complaints | Respond and set status | The submitter is notified of every change |
| **W-HR-19** | Tasks | Assign to Team Leaders; view and export everyone's | **HR may assign only to Team Leaders**, of any team |
| **W-HR-20** | Assets | Register, allocate, receive returns | See P-8 |
| **W-HR-21** | Calendar | Add or remove a holiday | Adding one notifies **every** employee and sends one bulk SMS |
| **W-HR-22** | Attendance | Whole-organisation attendance, by date or range | — |
| **W-HR-23** | Work reports | Everyone's, grouped and searchable, exportable | — |

---

### W-HR-24 · Take an employee out

1. Open Employees → the record → Offboard. Enter relieving date, reason, notes.

**Rules** Status becomes Offboarded, **the account is disabled immediately**, and an offboarding record is created with full-and-final pending · offboarding someone already offboarded is refused · offboarded staff drop out of headcount, celebrations and the absentee list.

---
---

# P-5 · Administrator

Configuration and oversight. Sees every queue; decides what is theirs to decide.

| Workflow | Action | Rules |
|---|---|---|
| **W-ADM-01** | View every approval queue for oversight | Rows outside their authority are read-only |
| **W-ADM-02** | Run monthly payroll: create the run, review the preview, confirm it | One run per month · a payslip is attempted for every enabled employee; those without an active salary structure are **skipped, not failed** · confirm is refused unless the run is in Preview |
| **W-ADM-03** | Generate a payslip directly for one employee | Requires an active salary structure · refused for a month before the joining month · one payslip per employee per month |
| **W-ADM-04** | Configure the AI assistant — provider, keys, models, languages, knowledge base | Keys are never shown in full, only masked · a blank value is ignored so a key is never wiped by accident · ingest a website into the knowledge base |
| **W-ADM-05** | Configure system settings, including travel rates per kilometre | Secrets are never returned to the browser |
| **W-ADM-06** | Create and delete community channels, add and remove members | Duplicate channel names refused |
| **W-ADM-07** | Reset an employee's leave — zero the used amounts and clear history | — |
| **W-ADM-08** | Delete an employee permanently | Every reference is cleared first — projects, teams, messages, tickets, subordinates' reporting line — each checked to exist before it is touched, so the deletion never half-completes |
| **W-ADM-09** | Maintain master data — departments, designations, positions, statuses, blood groups, locations, shifts, sites | Only active entries are offered in pickers |

---
---

# P-6 · Finance Officer

### W-FIN-01 · Approve the payroll run

1. Open Payroll → Runs. Review the confirmed run and its payslips.
2. Give finance approval.

**Rules** Approval is refused unless the run is Confirmed · the approver and time are recorded · **every employee in the run is then notified that their payslip is ready.**

Run states: `Preview → Confirmed → Finance Approved → Paid`.

### W-FIN-02 · Export for the books

Attendance for a date range, leave for a date range, payroll for a month — each as a spreadsheet with a header row and sized columns.

### W-FIN-03 · Decide general expense claims

Manager and finance decisions are recorded independently on the same claim.

---
---

# P-7 · Executive

### W-EXE-01 · Read the organisation at a glance

1. Open the dashboard. Filter to Digital, Infra, or overall.

**Shows** Headcount, present today, attendance percentage, pending approvals, open tickets, assets assigned and in stock, with departmental, attendance-trend, leave-utilisation and payroll-cost breakdowns.

**Rules** Headcount excludes offboarded staff, so the total and the percentage agree with the administrative views · the attendance percentage is present ÷ headcount to one decimal, and zero when headcount is zero · the division filter applies to every counted record.

> Four breakdown panels — department, attendance trend, leave utilisation and payroll cost — currently return fixed sample figures rather than live data. See limitation LM-002 in the full SRS.

### W-EXE-02 · Reports

The same three exports as P-6.

---
---

# P-8 · Asset Manager

### W-AST-01 · Register an asset

1. Open Assets → Add. Category — IT, Infra or Machinery — type, brand, model, serial number, registration number, purchase date and cost, warranty and contract expiry, site, depreciation rate, quantity.

**Rules** The asset code is generated with a category prefix, the year and a sequence · a QR tag is produced and attached, downloadable at label-printing size · quantity defaults to one; zero means out of stock.

### W-AST-02 · Allocate

1. Find the asset — by list, by search, or by scanning its QR code.
2. Allocate to an employee.

**Rules** Refused when out of stock, exhausted, retired or lost · quantity decrements and the asset goes out of stock at zero · the assignee is notified in-app and by SMS and asked to acknowledge.

### W-AST-03 · Receive a return

1. Record the return and its condition.

**Rules** Requires an active allocation · quantity increments · **the condition sets the status** — Lost stays lost, Damaged becomes Under Repair, otherwise back In Stock · the assignee is cleared.

### W-AST-04 · Maintain and retire

Log faults, vendor, turnaround, cost and preventive intervals. Track software licences with seats purchased and consumed. Deleting an asset removes its allocation history first.

### W-AST-05 · Work the helpdesk

Asset Managers hold helpdesk agency and progress tickets as in W-HR-17.

---
---

# P-9 · Site Supervisor and Facilities Admin

The field equivalents of P-2 and P-3, for the Infra division.

| Workflow | Action | Notes |
|---|---|---|
| **W-SUP-01** | Approve site leave and hourly permission | Same single-level routing as P-2 |
| **W-SUP-02** | View site attendance for a date or range | Site punches are checked against the **site's own** geofence radius, which is typically wider than an office's |
| **W-SUP-03** | Investigate and resolve safety incidents | Open → Investigating → Resolved → Closed; the reporter is told, unless anonymous |
| **W-SUP-04** | Maintain sites, facilities and the infra asset registry | Facilities Admin holds master-data and asset capability |
| **W-SUP-05** | Progress facility tickets | Category is derived as Infra for Civil staff |

---
---

# 3. End-to-End Journeys Across Personas

### J-1 · Joining
```
HR creates the account (W-HR-01)
   → employee code generated, announcement channel joined
   → HR starts the onboarding checklist (W-HR-03)
   → HR hands over username + password
   → Employee signs in (W-EMP-01), completes profile and bank (W-EMP-15)
   → Asset Manager allocates equipment (W-AST-02)
   → Employee acknowledges receipt (W-EMP-14)
   → HR ticks the checklist closed
```

### J-2 · Leave
```
Employee applies (W-EMP-04)  — quota, notice, gender, quarterly cap and balance all checked
   → routed to exactly one approver by duration and role
   → ≤3 days: own-team Team Leader decides (W-TL-01)
     >3 days: HR decides (W-HR-11)
   → approval consumes the balance; rejection requires a reason
   → Employee notified in-app and by SMS
   → (optional) Employee cancels → balance refunded, approver told
```

### J-3 · Payslip
```
Employee requests a month (W-EMP-09)
   → every payroll runner notified
   → HR or Admin approves with the payslip form (W-HR-13) or rejects with a note
   → PDF rendered and linked to the request
   → Employee notified, downloads it — and only they can
```

### J-4 · Monthly payroll
```
Admin creates the run (W-ADM-02) → Preview, payslips generated for all enabled staff
   → Admin confirms → Confirmed
   → Finance approves (W-FIN-01) → Finance Approved
   → every employee in the run notified their payslip is ready
```

### J-5 · Task
```
HR assigns to a Team Leader (W-HR-19)   [HR may assign only to leaders]
   → Team Leader assigns to a team member (W-TL-03)   [own team only]
   → Employee reports progress, forward only (W-EMP-08)
   → assigner notified at each step and at completion
   → HR exports the picture by division and date range
```

### J-6 · Support request
```
Employee raises it (W-EMP-11)  — category set from their division, deadline from priority
   → addressed to HR; only HR may progress it
   → Open → In Progress → (Awaiting Parts) → Resolved → Closed, forward only
   → Employee replies on the thread throughout
   → Employee rates it once resolved
```

### J-7 · Expense claim
```
Employee submits with receipts (W-EMP-10)
   → HR and Admins notified in-app and by SMS
   → Employee may still correct it while pending
   → HR approves or rejects with a reason (W-HR-16)
   → Employee notified of the outcome
```

### J-8 · Exit
```
HR offboards (W-HR-24)
   → status Offboarded, account disabled the same moment
   → offboarding record created, full-and-final pending
   → the person drops out of headcount, absentee lists and celebrations
   → (Admin only, if the record is to be expunged) permanent delete (W-ADM-08)
```

---

# 4. What the System Does on Its Own

**R-8** No notification, SMS or side effect ever blocks or fails the action that raised it.

| Trigger | Who hears about it | How |
|---|---|---|
| Any request raised or decided | The counterpart | In-app, live push, and SMS |
| Task assigned, edited, progressed, completed | Assignee or assigner | In-app and SMS |
| Asset allocated | Assignee | In-app and SMS |
| Payslip ready or refused | Requester | In-app and SMS |
| Payroll run finance-approved | Every employee in the run | In-app |
| Holiday added | Every active employee | In-app and one bulk SMS |
| Birthday or anniversary today | The whole workforce, except the celebrant | Daily at 09:00, in-app and one bulk SMS |
| New chat message | Channel members | Live push and in-app |

---

# 5. Numbers the System Runs On

| Setting | Value |
|---|---|
| Session length | 4 hours, renewed silently inside the window |
| Account lock | 5 failed attempts → 15 minutes |
| Office hours · lateness grace | 09:00–18:00 · 0 minutes |
| Weekly off | Sunday only — **Saturday is a working day** |
| Geofence radius, default | 200 m |
| Casual / Sick leave cap | 1 per calendar quarter |
| Leave approval threshold | 3 working days — Team Leader at or below, HR above |
| ESI | 0.75 % of gross, up to a 21 000 ceiling |
| Overtime rate | Monthly gross ÷ 240 per hour |
| Ticket deadline | 4 h critical · 8 h high · 24 h medium · 48 h otherwise |
| Upload limit | 25 MB per file, 60 MB per request |
| Celebrations | 60-day horizon, 12 shown, daily job at 09:00 |
| Money | Two decimals, rounded half-up |

---

# 6. State Sequences

| Thing | States | Direction |
|---|---|---|
| Leave / permission | Pending → Approved or Rejected; Pending or Approved → Cancelled | Decision is final |
| Task | Pending → In Progress → Completed | **Forward only** |
| Support ticket | Open → In Progress → Awaiting Parts → Resolved → Closed | **Forward only, one step** — except In Progress may skip straight to Resolved |
| Payroll run | Preview → Confirmed → Finance Approved → Paid | Each step validates the current state |
| Payslip request | Pending → Approved or Rejected | Approval produces the payslip |
| Complaint / need | Open → In Review → Resolved or Rejected | Returning clears the resolution time |
| Safety incident | Open → Investigating → Resolved → Closed | Returning clears the resolution time |
| Asset | In Stock ↔ Assigned / Out of Stock; → Under Repair, Retired, Lost | Return condition sets the next state |
| Employee | Pending → Active → Inactive → Offboarded | Offboarded disables the account |

---

*Full requirement numbering, data dictionary, non-functional requirements, deployment detail and known limitations: see* **Employee Management System — Software Requirements Specification** *in this folder.*
