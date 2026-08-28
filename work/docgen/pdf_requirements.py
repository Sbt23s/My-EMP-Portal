# -*- coding: utf-8 -*-
"""
Full Project Requirements Analysis for the PIXOUS HR Portal.

Every count in this document was taken from the repository rather than
estimated: 27 modules, 292 endpoints, 76 tables, 104 migrations, 39 pages.
Where a rule is stated, it is a rule the code actually enforces.
"""
import os
from pdf_style import (document, section, h1, h2, p, note, bullets, table,
                       metrics, finding)
import diagrams
from render_pdf import to_pdf

ROLES = [
    ("Employee", "IT_EMP",
     "Applies for leave, permission and work from home; files work reports, "
     "claims and tickets; sees their own payslips, assets and tasks."),
    ("Team Leader", "IT_TL",
     "Everything an employee may do, plus deciding requests addressed to them, "
     "assigning tasks, and seeing their own team's attendance and reports."),
    ("HR", "IT_HR / IT_MGR",
     "Staff records, onboarding, payroll, leave policy, assets, complaints and "
     "communities across the whole company."),
    ("CTO", "COMPANY_ADMIN",
     "The executive view of every module, the company announcement channel, and "
     "the final rung of the approval ladder."),
    ("System Admin", "SUPER_ADMIN",
     "Roles, permissions, audit log, data reset and configuration. The account "
     "that keeps the system running rather than one that runs the business."),
]

MODULES = [
    ("Authentication", "/api/auth", 15,
     "Sign in, refresh, sign out, password change, lockout after repeated "
     "failures."),
    ("Users and Org", "/api/users, /api/org", 38,
     "Staff records, departments, designations, teams, reporting lines."),
    ("Attendance", "/api/attendance", 12,
     "Daily marking, team view, corrections, monthly summaries."),
    ("Leave", "/api/leave", 27,
     "Leave and permission requests, balances, policies, approval chain."),
    ("Work From Home", "/api/wfh", 9,
     "Requests, decisions, the day board, a date range and Excel export."),
    ("Payroll", "/api/payroll", 27,
     "Salary structure, runs, payslips, approval and release."),
    ("Tasks", "/api/tasks", 16,
     "Assignment, progress, discussion, export."),
    ("Work Reports", "/api/work-reports", 13,
     "Daily activity against project and hours."),
    ("Claims and TA", "/api/ta-expenses", 8,
     "Travel and expense claims with distance rates and receipts."),
    ("Assets", "/api/assets", 10,
     "Issue, confirmation of receipt, return, register."),
    ("Helpdesk", "/api/tickets", 11,
     "IT, HR, facilities and payroll tickets with a queue per handler."),
    ("Complaints", "/api/complaints", 6,
     "Confidential submissions, optionally anonymous, to a named reviewer."),
    ("Chat and Communities", "/api/communities, /api/calls", 29,
     "Direct messages, channels, announcements, voice and video calling."),
    ("Notifications", "/api/notifications", 4,
     "In-app alerts pushed live over STOMP."),
    ("Dashboard", "/api/dashboard", 4,
     "Role-appropriate summaries and exports."),
    ("Calendar", "/api/calendar", 4,
     "Company events and public holidays."),
    ("Onboarding", "/api/onboarding", 4,
     "Joining formalities and document collection."),
    ("Performance", "/api/performance", 5,
     "Appraisal cycles and ratings."),
    ("Safety", "/api/safety-incidents", 5,
     "Workplace incident reporting."),
    ("Audit", "/api/audit", 4,
     "Who changed what, and when."),
    ("Chatbot", "/api/chatbot", 10,
     "An assistant answering from the organisation's own data."),
    ("Request threads", "/api/requests/{type}/{id}", 5,
     "Attachments and comments shared by leave, permission and WFH."),
    ("Files", "/api/files", 1,
     "Upload and retrieval with type and size limits."),
    ("Announcements", "/api/global-announcements", 5,
     "Company-wide notices."),
    ("Presence", "/api/presence", 1, "Who is online."),
    ("Administration", "/api/admin", 19,
     "Configuration and controlled reset."),
]

FUNCTIONAL = [
    ("FR-01", "Authentication",
     "A user signs in with a username and password and receives an access "
     "token and a refresh token, both valid for four hours.", "Must"),
    ("FR-02", "Authentication",
     "Repeated failed attempts lock the account temporarily.", "Must"),
    ("FR-03", "Leave",
     "An employee chooses the approver a request is sent to, from the people "
     "entitled to decide it.", "Must"),
    ("FR-04", "Leave",
     "Only the approver a request names may approve or reject it. HR, the CTO "
     "and administrators may view but not decide.", "Must"),
    ("FR-05", "Leave",
     "A person may hold at most one leave or permission per day.", "Must"),
    ("FR-06", "Leave",
     "A leave may not start or end on a weekend.", "Must"),
    ("FR-07", "WFH",
     "An approved work-from-home day is marked present and is paid.", "Must"),
    ("FR-08", "WFH",
     "The day board shows who is at home across a chosen date range and "
     "exports to Excel.", "Must"),
    ("FR-09", "Requests",
     "Any leave, permission or WFH request carries optional attachments and a "
     "comment thread between applicant and approver.", "Should"),
    ("FR-10", "Complaints",
     "A complaint is addressed to a named reviewer; the raiser may not respond "
     "to their own; status moves one way only.", "Must"),
    ("FR-11", "Chat",
     "An announcement channel is visible to every member of staff, and only "
     "HR, the CTO and administrators may post to it.", "Must"),
    ("FR-12", "Payroll",
     "A payroll run is prepared, approved and released as separate steps.",
     "Must"),
    ("FR-13", "Assets",
     "An asset issued to a person is not considered collected until that "
     "person confirms receipt.", "Should"),
    ("FR-14", "Notifications",
     "A decision or a message reaches the other party without a page reload.",
     "Must"),
    ("FR-15", "Reports",
     "Attendance, leave and payroll export to Excel for any chosen period.",
     "Must"),
]

NFR = [
    ("NFR-01", "Performance",
     "A page responds within two seconds under normal load.",
     "Measured: the server answers in 0.45 ms to 9 ms; the remainder is "
     "network distance to the Paris region."),
    ("NFR-02", "Availability",
     "The service is available during business hours.",
     "Five containers under Docker Compose with health checks and restart "
     "policies."),
    ("NFR-03", "Security",
     "Traffic is encrypted and the server does not disclose its version.",
     "TLS 1.3, HTTP/2, server_tokens off, Permissions-Policy, CSP in report "
     "mode."),
    ("NFR-04", "Security",
     "Passwords are stored irreversibly.", "BCrypt."),
    ("NFR-05", "Auditability",
     "Changes to records are attributable.", "Audit module, 4 endpoints."),
    ("NFR-06", "Data integrity",
     "The schema and the code may not drift apart.",
     "Flyway with ddl-auto=validate: the application refuses to start if an "
     "entity and its table disagree."),
    ("NFR-07", "Usability",
     "The portal is usable on a phone as well as a desktop.",
     "Responsive web plus a native Android application."),
    ("NFR-08", "Maintainability",
     "A change can be traced to a reason.",
     "Every commit states the problem it solves."),
]

EDGE = [
    ("A request is sent to somebody who then leaves",
     "The request keeps its named approver and remains visible to HR, who can "
     "see that it is stranded."),
    ("Two leaves on the same day",
     "Refused at the point of application, with the conflicting dates named."),
    ("A leave that starts on a Sunday",
     "Refused; the working calendar treats Saturday and Sunday as weekend."),
    ("An approver tries to decide a request not addressed to them",
     "Refused with an explicit message rather than silently ignored."),
    ("A WFH day that falls on a public holiday",
     "Skipped when attendance is marked, so a holiday is not counted twice."),
    ("An attendance row that already exists for a WFH day",
     "Left alone rather than overwritten."),
    ("A community channel created with nobody in it",
     "Visible to its creator only, and the card says so explicitly."),
    ("An announcement channel",
     "Visible to everyone without membership; posting restricted by role."),
    ("A file above the size limit",
     "Rejected before it reaches storage."),
    ("An expired access token",
     "Exchanged using the refresh token. Both expire together at four hours, "
     "so a session ends rather than extending indefinitely."),
]

body = "".join([
  section(
    h1("1.", "What This Document Is"),
    p("This is the requirements analysis for the PIXOUS HR Portal: what the "
      "application must do, for whom, and under what constraints. It was "
      "written by reading the application presently in production, so it "
      "describes a system that exists rather than one that is proposed."),
    metrics([("27", "modules"), ("292", "API endpoints"), ("76", "tables"),
             ("39", "screens"), ("5", "roles")]),
    note("Every figure above was counted from the repository. Where this "
         "document states a rule, that rule is enforced in code today."),
  ),
  section(
    h1("2.", "The Application in One Page"),
    p("The PIXOUS HR Portal is the internal system through which Pixous "
      "Technologies runs its people operations. Staff use it to record their "
      "time, ask for leave, claim expenses, raise problems and talk to each "
      "other. Team leaders use it to decide those requests and to see their "
      "team. HR and the CTO use it to run payroll, hold the staff record and "
      "speak to the company."),
    p("It is delivered three ways from one backend: a web portal, an Android "
      "application, and a set of documents and exports."),
    h2("2.1  Business objectives"),
    bullets([
      "Replace paper and message-based requests with a record that can be "
      "audited.",
      "Make an approval chain explicit, so it is always clear who must decide.",
      "Tie attendance to pay without a separate reconciliation step.",
      "Give the CTO a single view of the company rather than a set of reports.",
      "Keep confidential matters -- complaints, payslips -- confidential by "
      "role rather than by convention.",
    ]),
  ),
  section(
    h1("3.", "Roles and What Each May Do"),
    table(["Role", "Code", "Responsibility"],
          [[r[0], r[1], r[2]] for r in ROLES],
          widths=["16%", "20%", "64%"]),
    h2("3.1  The distinction that matters most"),
    p("Deciding a request and seeing a request are different rights. A senior "
      "role sees more, but seeing does not confer deciding. Only the person a "
      "request names may act on it."),
    diagrams.decide_vs_view(),
  ),
  section(
    h1("4.", "Modules"),
    table(["Module", "API base", "Endpoints", "What it is for"],
          [[m[0], m[1], str(m[2]), m[3]] for m in MODULES],
          widths=["17%", "22%", "9%", "52%"]),
  ),
  section(
    h1("5.", "Functional Requirements"),
    table(["Ref", "Module", "Requirement", "Priority"],
          [[f[0], f[1], f[2], f[3]] for f in FUNCTIONAL],
          widths=["8%", "14%", "63%", "15%"]),
  ),
  section(
    h1("6.", "Non-Functional Requirements"),
    table(["Ref", "Quality", "Requirement", "How it is met"],
          [[n[0], n[1], n[2], n[3]] for n in NFR],
          widths=["8%", "14%", "36%", "42%"]),
  ),
  section(
    h1("7.", "Workflows"),
    h2("7.1  The approval ladder"),
    diagrams.approval_ladder(),
    h2("7.2  The life of a request"),
    diagrams.request_lifecycle(),
    h2("7.3  Work from home becomes attendance, and attendance becomes pay"),
    diagrams.wfh_to_pay(),
    h2("7.4  One request per person per day"),
    diagrams.duplicate_guard(),
  ),
  section(
    h1("8.", "User Journeys"),
    h2("8.1  An employee asking for leave"),
    bullets([
      "Signs in and opens Leave.",
      "Presses Apply, chooses type, dates and the approver.",
      "Attaches a document if one is needed, and submits.",
      "Sees the request in their own list immediately, marked Pending.",
      "Receives a notification when it is decided, without reloading.",
    ]),
    h2("8.2  A team leader deciding one"),
    bullets([
      "Opens Leave Approvals and the Assigned to me tab.",
      "Opens the request and reads it with its attachments and comments.",
      "Asks a question in the thread if something is unclear.",
      "Approves or rejects, with a reason.",
    ]),
    h2("8.3  HR running payroll"),
    bullets([
      "Confirms attendance for the month, work from home included.",
      "Prepares the run, reviews it, and submits it for approval.",
      "Releases payslips, which staff can then view, download or email to "
      "themselves.",
    ]),
  ),
  section(
    h1("9.", "Dependencies Between Modules"),
    table(["This module", "Depends on", "Because"],
          [["Payroll", "Attendance, Leave, WFH",
            "Days paid are days present, and approved WFH counts as present."],
           ["Attendance", "WFH, Calendar",
            "An approved WFH day is written as attendance; holidays are "
            "skipped."],
           ["Leave", "Users, Org, Request threads",
            "The approver comes from the reporting line; attachments and "
            "comments are shared."],
           ["WFH", "Leave, Attendance, Calendar", "The same three reasons."],
           ["Chat", "Users, Communities, Presence",
            "Membership and who is online."],
           ["Notifications", "Every module that decides something",
            "A decision is what there is to notify."],
           ["Reports", "Attendance, Leave, Payroll", "It reads all three."]],
          widths=["18%", "26%", "56%"]),
  ),
  section(
    h1("10.", "Data Requirements"),
    p("Seventy-six tables under 104 Flyway migrations. The schema is versioned "
      "and the application validates itself against it at startup, so a "
      "mismatch stops the service rather than corrupting data."),
    table(["Area", "Holds"],
          [["People", "Users, roles, permissions, departments, designations, "
            "teams, reporting lines."],
           ["Time", "Attendance, leave, permission, work from home, holidays, "
            "calendar events."],
           ["Money", "Salary structure, payroll runs, payslips, claims, rates."],
           ["Work", "Tasks, work reports, projects."],
           ["Property", "Assets, issue and return."],
           ["Conversation", "Communities, members, messages, calls, "
            "attachments, comments."],
           ["Service", "Tickets, complaints, safety incidents."],
           ["Record", "Audit entries, notifications."]],
          widths=["16%", "84%"]),
  ),
  section(
    h1("11.", "Validation and Error Handling"),
    table(["Rule", "Enforced where"],
          [["An email must have a domain of two letters or more",
            "Web form and server."],
           ["A pincode accepts digits only", "Web form, on keystroke."],
           ["A leave may not begin or end at a weekend", "Server."],
           ["One leave or permission per person per day", "Server."],
           ["A request must name an approver", "Server, rejected if absent."],
           ["Only the named approver may decide", "Server."],
           ["A complaint's status moves forward only", "Server and form."],
           ["An announcement may be posted only by HR, the CTO or an admin",
            "Server."],
           ["Files: ten per request, 10 MB each, images, PDF and Word",
            "Server."]],
          widths=["52%", "48%"]),
    note("Validation is enforced on the server in every case. The web form "
         "repeats it so the user is told sooner, but the server is what "
         "decides."),
  ),
  section(
    h1("12.", "Edge Cases"),
    table(["Situation", "What the system does"],
          [[e[0], e[1]] for e in EDGE],
          widths=["40%", "60%"]),
  ),
  section(
    h1("13.", "Assumptions and Constraints"),
    h2("13.1  Assumptions"),
    bullets([
      "Staff have a company email address and a browser or Android phone.",
      "Saturday and Sunday are non-working days.",
      "One company per deployment, with tenant isolation already in the data.",
      "The reporting line is kept current, since approvers are drawn from it.",
    ]),
    h2("13.2  Constraints"),
    bullets([
      "The database is hosted externally and is roughly 125 ms from the "
      "application server, which sets a floor on response time.",
      "The application server is in the Paris region while its users are in "
      "India, which accounts for most of the observed latency.",
      "The schema may only change through a Flyway migration.",
    ]),
    finding("Open risk -- database backup",
            "Backups are manual, unscheduled, and kept as a single copy on the "
            "same server as the database. Loss of that machine is loss of the "
            "data. This is the largest outstanding risk to the system, it is "
            "not a code defect, and it needs a scheduled off-server backup."),
  ),
  section(
    h1("14.", "Future Enhancements"),
    table(["Enhancement", "Why"],
          [["Scheduled off-server database backup",
            "The one outstanding risk; it should be first."],
           ["Move the application closer to its users",
            "Most of the response time is distance, not computation."],
           ["Single sign-on", "Removes a password to manage."],
           ["Read replica for reports",
            "Keeps heavy exports away from the transactional database."],
           ["Push notifications on mobile",
            "The web already pushes; the phone could too."],
           ["Wider automated test coverage",
            "Sixty-one tests today; the approval rules deserve more."]],
          widths=["36%", "64%"]),
  ),
  section(
    h1("15.", "Module Requirements in Detail"),
    p("Sections 4 and 5 give the shape of each module. This section states, "
      "module by module, what the module must do, who may do it, and what it "
      "must refuse. It is the level of detail a developer or a tester needs "
      "in order to work without having to ask a question."),

    h2("15.1  Authentication"),
    table(["Must", "Detail"],
          [["Sign in", "Username and password. The password is compared "
            "against a BCrypt hash; it is never stored or logged in plain."],
           ["Issue tokens", "An access token and a refresh token, both valid "
            "for four hours."],
           ["End a session", "Both tokens expire together. A session ends at "
            "four hours rather than renewing itself indefinitely."],
           ["Resist guessing", "Repeated failures lock the account for a "
            "period."],
           ["Refuse uniformly", "A wrong password and an unknown username give "
            "the same answer, so the form cannot be used to discover who holds "
            "an account."]],
          widths=["20%", "80%"]),

    h2("15.2  Users, roles and organisation"),
    table(["Must", "Detail"],
          [["Hold the staff record", "Name, employee code, contact details, "
            "designation, department, team and reporting line."],
           ["Carry a status", "Onboarding, active or offboarded. Status "
            "governs whether the person appears in pickers and approver "
            "lists."],
           ["Assign roles", "One person may hold more than one role; the "
            "effective rights are the union of them."],
           ["Own the reporting line", "Because approvers are drawn from it, an "
            "out-of-date reporting line produces a stranded request."],
           ["Isolate companies", "A user may not see or message a user of "
            "another company."]],
          widths=["22%", "78%"]),

    h2("15.3  Attendance"),
    table(["Must", "Detail"],
          [["Record a working day", "Punch in and punch out, with a defined "
            "window in which a punch-out is accepted."],
           ["Accept other sources", "An approved work-from-home day is written "
            "as attendance by the WFH module rather than entered by hand."],
           ["Show a team", "A Team Leader sees their own team; HR and the CTO "
            "see everybody."],
           ["Summarise a month", "Present, absent, leave and work-from-home "
            "days, which is what payroll reads."],
           ["Export", "Any chosen date or range, to Excel."]],
          widths=["22%", "78%"]),

    h2("15.4  Leave and permission"),
    p("Leave types are configuration rather than code: HR creates and edits "
      "them, each with its own entitlement, so a new category of leave needs "
      "no release."),
    table(["Must", "Detail"],
          [["Let the applicant name the approver", "Chosen from the people "
            "entitled to decide, so a request always has a queue."],
           ["Refuse an unaddressed request", "A request naming nobody would "
            "sit in nobody list."],
           ["Restrict the decision", "Only the named approver may approve or "
            "reject. HR, the CTO and administrators may view."],
           ["Refuse a duplicate", "One leave or permission per person per day, "
            "with the conflicting dates named in the message."],
           ["Refuse a weekend boundary", "A leave may not start or end on a "
            "Saturday or a Sunday."],
           ["Compute working days", "Weekends and public holidays are excluded "
            "from the count."],
           ["Hold balances", "Per person, per leave type, per year."],
           ["Move one way", "Pending becomes Approved, Rejected or Cancelled, "
            "and a decided request is not decided again."],
           ["Separate mine from for-me", "Distinct views for requests raised "
            "and requests to decide, each carrying a count."]],
          widths=["26%", "74%"]),

    h2("15.5  Work from home"),
    table(["Must", "Detail"],
          [["Follow the same approval rule", "Named approver only."],
           ["Mark attendance on approval", "One row per working day in the "
            "range."],
           ["Skip what should be skipped", "Weekends, public holidays, and any "
            "day that already carries an attendance row."],
           ["Be paid", "Because payroll reads attendance and the day is "
            "present, no change to payroll is required."],
           ["Show the organisation", "A board of who is at home, over a single "
            "day or a date range."],
           ["Export", "The board as displayed, to Excel."]],
          widths=["26%", "74%"]),

    h2("15.6  Payroll"),
    table(["Must", "Detail"],
          [["Hold a salary structure", "Per person, with its components."],
           ["Read attendance", "Days paid are days present; approved leave and "
            "work from home are already reflected there."],
           ["Separate the steps", "Prepare, approve and release are distinct, "
            "so no single action both computes and publishes pay."],
           ["Produce payslips", "Viewable, downloadable, and emailable to the "
            "address held on the profile."],
           ["Restrict visibility", "A person sees their own payslip; HR, the "
            "CTO and administrators see all."]],
          widths=["24%", "76%"]),

    h2("15.7  Tasks and work reports"),
    table(["Must", "Detail"],
          [["Assign work", "A Team Leader or HR assigns a task with a priority "
            "and a due date."],
           ["Track progress", "Owned by the person doing the work; an "
            "organisation-wide view is read-only."],
           ["Allow a question", "A discussion thread on the task reaches "
            "whoever assigned it."],
           ["Record daily work", "Date, project, hours and description, with "
            "optional attachments."],
           ["Show a team", "A Team Leader sees what the team filed; the filing "
            "form itself remains personal."]],
          widths=["22%", "78%"]),

    h2("15.8  Claims, assets, helpdesk and complaints"),
    table(["Module", "Must"],
          [["Claims", "Compute distance and amount from start and end "
            "readings; hold receipts; let HR set the rates; refuse an end "
            "reading below the start."],
           ["Assets", "Record issue and return; treat an asset as collected "
            "only once the holder confirms receipt; export a register."],
           ["Helpdesk", "Require a title and a recipient; carry type, category "
            "and priority; give each handler a queue; move through Open, In "
            "progress, Awaiting parts, Resolved and Closed."],
           ["Complaints", "Address to a named reviewer; allow anonymity; "
            "prevent the raiser responding to their own; move status forward "
            "only."]],
          widths=["16%", "84%"]),

    h2("15.9  Chat, communities and announcements"),
    table(["Must", "Detail"],
          [["Carry direct messages", "Between two people of the same "
            "company."],
           ["Carry channels", "Visible to their members only."],
           ["Carry an announcement channel", "Visible to every member of staff "
            "without being added, and postable only by HR, the CTO and "
            "administrators."],
           ["Support calling", "Voice, video and group calls, with mute, "
            "camera and screen sharing."],
           ["Be honest about an empty channel", "A channel holding only its "
            "creator says so, because otherwise it is indistinguishable from a "
            "channel that failed to save."]],
          widths=["28%", "72%"]),
  ),

  section(
    h1("16.", "Notifications and Alerts"),
    p("A notification exists so that somebody learns of something they did not "
      "themselves cause. The rule followed throughout is that the record and "
      "the alert are written on the same event, so the two cannot disagree."),
    table(["Event", "Who is told", "How"],
          [["A request is submitted", "The named approver",
            "In-app, pushed live."],
           ["A request is approved or rejected", "The applicant",
            "In-app, pushed live."],
           ["A comment is added to a request", "The other party",
            "In-app, pushed live."],
           ["A task is assigned", "The assignee", "In-app."],
           ["A ticket is raised or answered", "The handler or the raiser",
            "In-app."],
           ["A complaint is answered", "The raiser, unless anonymous",
            "In-app."],
           ["A payslip is released", "The employee",
            "In-app, and by email on request."],
           ["An announcement is posted", "Every member of staff",
            "In the Announcements channel."],
           ["A message or call arrives", "The recipient",
            "In-app, pushed live."]],
          widths=["32%", "30%", "38%"]),
    note("Live delivery is by STOMP over SockJS. The client maps an event to "
         "the lists that should be refreshed, which is why a decision appears "
         "without the page being reloaded."),
  ),

  section(
    h1("17.", "UI and UX Requirements"),
    h2("17.1  Principles"),
    bullets([
      "The action a person came to perform is reachable from the page they "
      "land on.",
      "A decision is never taken from a list row alone: the request is opened, "
      "read, and then decided, so that an approval is deliberate.",
      "A control that is not permitted is not shown, and the server refuses it "
      "as well.",
      "A refusal states its reason in the words of the business rather than "
      "those of the system.",
      "Anything a person waits for shows that it is working.",
      "The same information is not presented in two places with two different "
      "totals.",
    ]),
    h2("17.2  Consistency requirements"),
    table(["Element", "Requirement"],
          [["Lists", "A count on every tab, so a queue length is visible "
            "without opening it."],
           ["Status", "One vocabulary across modules: Pending, Approved, "
            "Rejected, Cancelled."],
           ["Dates", "One format throughout, and a range wherever a single "
            "date would hide information."],
           ["Attachments and comments", "Identical on leave, permission and "
            "work from home, because they are the same component."],
           ["Export", "Where a list can be exported, the export matches the "
            "filters on screen."],
           ["Empty states", "An empty list says why it is empty."]],
          widths=["26%", "74%"]),
    h2("17.3  Device requirements"),
    p("The web portal is responsive and usable on a phone. The Android "
      "application covers the same workflows for staff who work away from a "
      "desk. Both speak to the same API and are therefore governed by the same "
      "rules; neither can be used to bypass the other."),
  ),

  section(
    h1("18.", "Security Requirements"),
    table(["Requirement", "Detail"],
          [["Encrypted transport", "TLS 1.3 with HTTP/2. No service is offered "
            "unencrypted."],
           ["Irreversible passwords", "BCrypt. A password cannot be recovered, "
            "only reset."],
           ["Bounded sessions", "Four hours, with the refresh token expiring "
            "alongside the access token."],
           ["Least privilege", "Rights follow the role, and a role that may "
            "see something does not thereby act on it."],
           ["Authorisation on the server", "Hiding a button is presentation; "
            "refusing the call is security. Both are done."],
           ["Attributable change", "An audit record of who changed what."],
           ["Resistance to guessing", "Lockout after repeated failures."],
           ["No version disclosure", "server_tokens off, with "
            "Permissions-Policy and a Content-Security-Policy in report "
            "mode."],
           ["Confidentiality by role", "Payslips, complaints and staff records "
            "are visible only to those entitled to them."],
           ["Tenant isolation", "A user of one company cannot see or message a "
            "user of another."]],
          widths=["26%", "74%"]),
  ),

  section(
    h1("19.", "Reports, Dashboards and Integrations"),
    h2("19.1  Dashboards"),
    p("Each role lands on a dashboard showing what that role is accountable "
      "for: an employee sees their own position, a Team Leader sees the team "
      "and what awaits their decision, HR and the CTO see the company."),
    h2("19.2  Reports"),
    table(["Report", "Contents", "Available to"],
          [["Attendance", "Present, absent, leave and work from home, by day "
            "or range", "TL for the team; HR, CTO, Admin for all"],
           ["Leave", "Requests, decisions and balances", "HR, CTO, Admin"],
           ["Work from home", "Who was at home, over a range",
            "TL, HR, CTO, Admin"],
           ["Payroll", "Runs and payslips", "HR, CTO, Admin"],
           ["Tasks", "Assignment and progress", "TL, HR, CTO, Admin"],
           ["Claims", "Claims and settlement", "HR, CTO, Admin"],
           ["Assets", "The register", "HR, CTO, Admin"]],
          widths=["18%", "52%", "30%"]),
    h2("19.3  Integrations"),
    table(["Integration", "Purpose"],
          [["Email (SMTP)", "Payslips sent on request, and system mail."],
           ["Redis", "Cache, presence and short-lived state."],
           ["External MySQL", "The system of record."],
           ["Analytics container", "Reporting workload kept off the request "
            "path."],
           ["Android application", "The same API, from a phone."]],
          widths=["24%", "76%"]),
    note("There is deliberately no integration that writes to a third party. "
         "Everything the application knows stays inside it, which is "
         "appropriate for payroll and complaint data."),
  ),

  section(
    h1("20.", "Traceability"),
    p("Each requirement can be followed to the rule that implements it and the "
      "test that checks it. This table is the link between the three "
      "documents."),
    table(["Requirement", "Implemented in", "Tested by"],
          [["FR-01, FR-02 Authentication", "auth module, JWT, attempt limiter",
            "TC-AUTH-01 to 08; AuthServiceTest, JwtServiceTest, "
            "LoginAttemptLimiterTest"],
           ["FR-03, FR-04 Named approver", "LeaveService, PermissionService",
            "TC-LV-06 to TC-LV-09"],
           ["FR-05 One per day", "LeaveService duplicate guard", "TC-LV-03"],
           ["FR-06 No weekend leave", "WorkCalendar",
            "TC-LV-04, TC-LV-05; WorkCalendarTest"],
           ["FR-07 WFH is present and paid", "WfhService attendance marking",
            "TC-WFH-02 to TC-WFH-07"],
           ["FR-08 WFH range and export", "/wfh/active-range",
            "TC-WFH-08, TC-WFH-09"],
           ["FR-09 Attachments and comments", "requestthread module",
            "TC-VAL-06 to TC-VAL-09"],
           ["FR-10 Complaints", "complaint module", "TC-CM-01 to TC-CM-04"],
           ["FR-11 Announcements", "community module", "TC-CH-01 to TC-CH-06"],
           ["FR-12 Payroll steps", "payroll module", "Manual, section 9"],
           ["FR-14 Live notification", "STOMP and the refresh mapping",
            "TC-LV-06"],
           ["FR-15 Exports", "Reports and module exports", "TC-WFH-09"]],
          widths=["28%", "32%", "40%"]),
  ),

  section(
    h1("21.", "Glossary"),
    table(["Term", "Meaning here"],
          [["Approver", "The person a request is addressed to, and the only "
            "person who may decide it."],
           ["Permission", "A short absence within a working day, as distinct "
            "from a full day of leave."],
           ["Working day", "A day that is neither a weekend nor a public "
            "holiday."],
           ["Request thread", "The shared attachments and comments carried by "
            "leave, permission and work-from-home requests."],
           ["Announcement channel", "A chat channel every member of staff can "
            "read and only HR, the CTO and administrators can post to."],
           ["Team room", "A channel belonging to a team, reached from the "
            "Teams page rather than from the chat list."],
           ["Onboarding", "The status of a person who has joined but has not "
            "yet become active."],
           ["Run", "One execution of payroll for a period, prepared, approved "
            "and released as separate steps."]],
          widths=["22%", "78%"]),
  ),
])


html = document(
    "Project Requirements Analysis",
    "PIXOUS HR Portal",
    "What the application must do, for whom, and under what constraints -- "
    "written from the system presently in production.",
    body)

out = os.path.join(os.path.expanduser("~"), "Downloads",
                   "Pixous_HR_Portal_Requirements_Analysis_v1.0.pdf")
print(to_pdf(html, out))
