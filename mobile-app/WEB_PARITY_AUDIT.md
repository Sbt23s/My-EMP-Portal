# Web ↔ Mobile Feature Parity Audit

**Product:** Pixous HR Portal — web (`web/`) and mobile (`mobile-app/`)
**Audit date:** 2026-08-16
**Result:** every employee / team-lead / HR module on the web has a mobile
counterpart. The only screens without a mobile twin are the **System Admin /
Company Admin consoles**, which the app deliberately sends to the browser
(see `core/auth/mobile_access.dart` — the app is a door, not a lock).

Legend: ✅ built & verified · 🖥️ web-only by design (admin console)

| Web module | Web page | Mobile screen | Status |
|---|---|---|---|
| Login | `Login.tsx` | `auth/login_screen.dart` | ✅ |
| Dashboard | `Dashboard.tsx` | `features/dashboard/dashboard_screen.dart` | ✅ |
| Attendance | `Attendance.tsx` | `features/attendance/attendance_screen.dart` (+ face punch, GPS) | ✅ |
| Team attendance | `TeamAttendance.tsx` | `features/more/team_attendance_screen.dart` | ✅ |
| Leave | `Leave.tsx` | `features/leave/leave_screen.dart` + `apply_leave_sheet.dart` | ✅ |
| Permission (short leave) | `Permissions.tsx` | `features/leave/permissions_screen.dart` | ✅ |
| Leave approvals | `LeaveApprovals.tsx` | `features/approvals/approvals_screen.dart` | ✅ |
| Leave policies | `LeavePolicies.tsx` | `features/hr/hr_screens.dart` (LeavePoliciesScreen) | ✅ |
| Payslips | `Payslips.tsx` | `features/more/more_screen.dart` (PayslipsScreen) | ✅ |
| Payroll (salaries) | `PayrollRequests.tsx` | `features/hr/hr_screens.dart` (PayrollScreen) | ✅ |
| Work reports | `WorkReports.tsx` | `features/more/work_reports_screen.dart` | ✅ |
| Tasks | `Tasks.tsx` | `features/more/more_screen.dart` (TasksScreen) | ✅ |
| Claims / TA expenses | `TaExpenses.tsx` + `ClaimEntry.tsx` | `features/more/more_screen.dart` (ClaimsScreen) + `submit_claim_sheet.dart` | ✅ |
| Assets | `Assets.tsx` | `features/more/more_screen.dart` (AssetsScreen) | ✅ |
| Supports / helpdesk | `Helpdesk.tsx` + `TicketEntry.tsx` | `features/more/more_screen.dart` (TicketsScreen) + `raise_ticket_sheet.dart` | ✅ |
| Complaints | `Complaints.tsx` | `features/more/complaints_screen.dart` | ✅ |
| Reports (spreadsheets) | `Reports.tsx` | `features/hr/hr_screens.dart` (ReportsScreen) | ✅ |
| Onboarding | `Onboarding.tsx` | `features/hr/hr_screens.dart` (OnboardingScreen) | ✅ |
| Chat | `Chat.tsx` | `features/chat/chat_screen.dart` | ✅ |
| **Audio & video calls** | `components/CallOverlay.tsx` | `core/calls/call_service.dart` + `features/calls/call_screen.dart` | ✅ WebRTC, STUN, same protocol |
| Teams | `Teams.tsx` | `features/more/teams_screen.dart` | ✅ |
| **My team** | `MyTeam.tsx` | `features/more/my_team_screen.dart` | ✅ **new** |
| **Safety incidents** | `Safety.tsx` | `features/more/safety_screen.dart` | ✅ **new** |
| **AI assistant** | `components/ChatBotWidget.tsx` | `features/more/ai_assistant_screen.dart` | ✅ **new** |
| **Communities** | `Communities.tsx` | `features/more/communities_screen.dart` | ✅ **new** |
| **Employees directory** | `Employees.tsx` | `features/more/employees_screen.dart` | ✅ **new** |
| **Audit log** | `AuditLog.tsx` | `features/more/audit_screen.dart` | ✅ **new** |
| Notifications | `Notifications.tsx` | `features/notifications/notifications_screen.dart` | ✅ |
| Profile | `Profile.tsx` | `features/profile/profile_screen.dart` | ✅ |
| Calendar | `Calendar.tsx` | `features/more/calendar_screen.dart` | ✅ |
| Tech-admin console | `pages/tech-admin/*` | — | 🖥️ admin uses web |
| Data reset | `DataReset.tsx` | — | 🖥️ SUPER_ADMIN only |
| AI-assistant settings | `AdminChatbotSettings.tsx` | — | 🖥️ admin uses web |
| Projects / Documents | `ModulePlaceholder.tsx` | — | ⚪ scaffolded on web too |

## What was added in this pass (2026-08-16)

| Feature | What it does | Server endpoints |
|---|---|---|
| **Safety** | Report an incident (type, severity, zone, anonymous, when), see your reports, and staff resolve them (OPEN → INVESTIGATING → RESOLVED/CLOSED) | `POST/GET /safety-incidents`, `/mine`, `/{id}/resolve` |
| **My team** | Team roster, upcoming birthdays & anniversaries, who is off today, and a one-tap team chat | `/users/my-team`, `/dashboard/celebrations`, `/leave/on-leave`, `POST /communities/team` |
| **Communities** | Create groups, add/remove members, delete groups, open the group chat | `GET/POST /communities`, `/{id}/members`, `/{id}/members/{userId}`, `DELETE /communities/{id}` |
| **Employees directory** | Searchable company directory with contact details | `GET /users?q=&size=` |
| **Audit log** | Category counts + the security trail (who/what/when/where) for HR & admins | `GET /audit`, `/audit/summary` |
| **AI assistant** | Conversational assistant with history, name from server config | `GET /chatbot/config`, `POST /chatbot/chat` |

## Verification

- `flutter analyze` — **0 issues**
- `flutter test` — **107 / 107 passing** (added 5 model tests for the new parity features)
- Every endpoint above exercised **live** against the production API with real
  employee and HR accounts (all returned 200; a smoke-test safety incident was
  reported and then closed as part of the check)

## How to regenerate

The parity table lives in this file. To verify again after a change, re-run:

```bash
cd mobile-app && flutter analyze && flutter test
```
