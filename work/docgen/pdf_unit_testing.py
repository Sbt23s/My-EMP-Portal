# -*- coding: utf-8 -*-
"""
Complete Unit Testing Document for the PIXOUS HR Portal.

The automated figures are real: 61 backend tests across 8 classes and 36 web
tests across 4 files, all passing. The manual cases are written so a tester can
execute them without needing to read the code.
"""
import os
from pdf_style import (document, section, h1, h2, p, note, bullets, table,
                       metrics, finding, pill)
import diagrams
from render_pdf import to_pdf

AUTOMATED = [
    ("AuthServiceTest", 13, "Backend",
     "Sign in, wrong password, unknown user, locked account, token issue and "
     "refresh."),
    ("ChatbotOrgContextTest", 10, "Backend",
     "The assistant answers from organisation data and respects role limits."),
    ("StorageServiceTest", 8, "Backend",
     "File type, size limit, naming, retrieval and rejection."),
    ("AuthDtosValidationTest", 7, "Backend",
     "Request payload validation at the boundary."),
    ("PunchOutWindowTest", 7, "Backend",
     "The window within which attendance may be punched out."),
    ("JwtServiceTest", 6, "Backend",
     "Token signing, parsing, expiry and tampering."),
    ("LoginAttemptLimiterTest", 6, "Backend",
     "Lockout after repeated failures and release afterwards."),
    ("WorkCalendarTest", 4, "Backend",
     "Weekend detection, which underpins leave and work-from-home rules."),
    ("utils.test.ts", None, "Web", "Shared helpers."),
    ("dates.test.ts", None, "Web", "Date formatting and range handling."),
    ("branding.test.ts", None, "Web", "Company naming and labels."),
    ("Login.test.tsx", None, "Web", "The sign-in screen, rendered."),
]

AUTH_CASES = [
    ("TC-AUTH-01", "Sign in with a correct username and password",
     "Tokens are returned and the dashboard opens", "Positive"),
    ("TC-AUTH-02", "Sign in with a wrong password",
     "Refused, with no hint as to which field was wrong", "Negative"),
    ("TC-AUTH-03", "Sign in with an unknown username",
     "Refused, indistinguishable from a wrong password", "Negative"),
    ("TC-AUTH-04", "Repeat a wrong password past the limit",
     "The account locks temporarily", "Negative"),
    ("TC-AUTH-05", "Use the application for four hours continuously",
     "The session ends and the login page returns; it does not extend itself",
     "Boundary"),
    ("TC-AUTH-06", "Call an API without a token",
     "Refused with 401", "Negative"),
    ("TC-AUTH-07", "Call an API with an altered token",
     "Refused; the signature does not verify", "Security"),
    ("TC-AUTH-08", "Sign out and press the browser back button",
     "The protected page does not render from cache", "Negative"),
]

LEAVE_CASES = [
    ("TC-LV-01", "Apply for leave with valid dates and an approver",
     "Saved, shown immediately in the applicant's own list as Pending",
     "Positive"),
    ("TC-LV-02", "Apply without choosing an approver",
     "Refused; a request with no approver has no queue", "Negative"),
    ("TC-LV-03", "Apply for a second leave on a day already taken",
     "Refused, naming the conflicting dates", "Negative"),
    ("TC-LV-04", "Apply for leave starting on a Saturday",
     "Refused; the weekend is not a working day", "Negative"),
    ("TC-LV-05", "Apply for leave ending on a Sunday",
     "Refused, for the same reason", "Negative"),
    ("TC-LV-06", "Approve as the named approver",
     "Approved; the applicant is notified without reloading", "Positive"),
    ("TC-LV-07", "Approve as HR when HR is not the named approver",
     "Refused with an explicit message; HR may view only", "Negative"),
    ("TC-LV-08", "Approve as the CTO when not the named approver",
     "Refused; seniority does not confer the decision", "Negative"),
    ("TC-LV-09", "A Team Leader tries to approve their own request",
     "Refused; it is not addressed to them", "Negative"),
    ("TC-LV-10", "Reject with a reason",
     "Rejected, the reason stored and shown to the applicant", "Positive"),
    ("TC-LV-11", "Decide an already-decided request",
     "Refused; the status moves one way only", "Negative"),
    ("TC-LV-12", "Open the Assigned to me tab",
     "Only requests naming the signed-in user, with a count", "Positive"),
    ("TC-LV-13", "Apply for a single day",
     "Accepted; one day is a valid range", "Boundary"),
    ("TC-LV-14", "Apply with the end date before the start date",
     "Refused", "Negative"),
]

WFH_CASES = [
    ("TC-WFH-01", "Apply to work from home for a weekday range",
     "Saved and pending with the chosen approver", "Positive"),
    ("TC-WFH-02", "Approve the request",
     "Approved, and attendance is written for each working day", "Positive"),
    ("TC-WFH-03", "Check attendance for an approved WFH day",
     "The day counts as present", "Positive"),
    ("TC-WFH-04", "Run payroll over a month containing approved WFH",
     "Those days are paid, because payroll reads attendance", "Positive"),
    ("TC-WFH-05", "Approve a range containing a public holiday",
     "The holiday is skipped, not counted twice", "Edge"),
    ("TC-WFH-06", "Approve a range containing a weekend",
     "Weekend days are skipped", "Edge"),
    ("TC-WFH-07", "Approve a range where attendance already exists",
     "The existing row is left alone, not overwritten", "Edge"),
    ("TC-WFH-08", "Open the board with a From and To date",
     "Everybody at home in that range is listed", "Positive"),
    ("TC-WFH-09", "Export the board",
     "An Excel file matching what is on screen", "Positive"),
    ("TC-WFH-10", "Decide a request not addressed to you",
     "Refused", "Negative"),
]

CHAT_CASES = [
    ("TC-CH-01", "Open Chat as an employee",
     "The Announcements channel is visible without being added", "Positive"),
    ("TC-CH-02", "Try to post in Announcements as an employee",
     "The message box is unavailable; the server also refuses", "Negative"),
    ("TC-CH-03", "Post in Announcements as HR", "Posted", "Positive"),
    ("TC-CH-04", "Post in Announcements as the CTO", "Posted", "Positive"),
    ("TC-CH-05", "Create a channel and add nobody",
     "Visible to the creator only, and the card says so", "Edge"),
    ("TC-CH-06", "Create a channel with the announcement box ticked",
     "It is created as an announcement channel and everyone sees it",
     "Positive"),
    ("TC-CH-07", "Send a message and press Enter",
     "Sent; Shift and Enter together insert a line instead", "Positive"),
    ("TC-CH-08", "Start a voice call",
     "The other party is rung and may answer or decline", "Positive"),
    ("TC-CH-09", "Deny microphone permission and start a call",
     "A clear message rather than a silent failure", "Negative"),
]

OTHER_CASES = [
    ("TC-CM-01", "Raise a complaint addressed to HR",
     "Saved and visible in the raiser's My requests", "Positive"),
    ("TC-CM-02", "Respond to your own complaint",
     "Not offered; the raiser may view only", "Negative"),
    ("TC-CM-03", "Move a complaint from In review back to Open",
     "Refused; status moves forward only", "Negative"),
    ("TC-CM-04", "Submit anonymously",
     "The reviewer sees the complaint without the name", "Positive"),
    ("TC-TK-01", "Raise a ticket without a title",
     "Refused; the title is required", "Negative"),
    ("TC-TK-02", "Raise a ticket with Send to chosen",
     "It appears in that person's Assigned to me", "Positive"),
    ("TC-AS-01", "Confirm receipt of an issued asset",
     "The record changes from awaiting confirmation to held", "Positive"),
    ("TC-CL-01", "File a travel claim with start and end kilometres",
     "Distance and amount are computed", "Positive"),
    ("TC-CL-02", "File a claim with the end reading below the start",
     "Refused", "Negative"),
    ("TC-PS-01", "Email a payslip to yourself",
     "Sent to the address on the profile; no address is typed", "Positive"),
    ("TC-PS-02", "Open another person's payslip by changing the URL",
     "Refused", "Security"),
]

VALIDATION_CASES = [
    ("TC-VAL-01", "name@gmail.com", "Accepted", "Positive"),
    ("TC-VAL-02", "name@gmail.c", "Refused; a domain needs two letters or more",
     "Boundary"),
    ("TC-VAL-03", "name at gmail dot com", "Refused", "Negative"),
    ("TC-VAL-04", "Pincode 641001", "Accepted", "Positive"),
    ("TC-VAL-05", "Pincode 641ABC", "Letters are not accepted at all",
     "Negative"),
    ("TC-VAL-06", "A file of 9 MB", "Accepted", "Boundary"),
    ("TC-VAL-07", "A file of 11 MB", "Refused before storage", "Boundary"),
    ("TC-VAL-08", "An eleventh attachment", "Refused; ten is the limit",
     "Boundary"),
    ("TC-VAL-09", "An executable file", "Refused; type is checked", "Security"),
    ("TC-VAL-10", "A required field left empty",
     "Refused, naming the field", "Negative"),
]

ROLE_MATRIX = [
    ("Apply for leave, permission, WFH", "Yes", "Yes", "Yes", "Yes", "Yes"),
    ("Decide a request addressed to you", "No", "Yes", "Yes", "Yes", "Yes"),
    ("Decide a request addressed elsewhere", "No", "No", "No", "No", "No"),
    ("View every request", "No", "Team only", "Yes", "Yes", "Yes"),
    ("Run payroll", "No", "No", "Yes", "Yes", "Yes"),
    ("See own payslip", "Yes", "Yes", "Yes", "Yes", "Yes"),
    ("See another person's payslip", "No", "No", "Yes", "Yes", "Yes"),
    ("Post in Announcements", "No", "No", "Yes", "Yes", "Yes"),
    ("Read Announcements", "Yes", "Yes", "Yes", "Yes", "Yes"),
    ("Manage communities", "No", "No", "Yes", "Yes", "Yes"),
    ("Assign tasks", "No", "Yes", "Yes", "Yes", "Yes"),
    ("Manage staff records", "No", "No", "Yes", "Yes", "Yes"),
    ("Read the audit log", "No", "No", "No", "Yes", "Yes"),
]

body = "".join([
  section(
    h1("1.", "Purpose and Scope"),
    p("This document sets out how the PIXOUS HR Portal is tested: what is "
      "covered by automated tests today, and what a tester should execute by "
      "hand. It is written to be used, so every case states the action, the "
      "expected result and the kind of case it is."),
    metrics([("97", "automated tests"), ("61", "backend"), ("36", "web"),
             ("12", "test files"), ("0", "failing")]),
    note("Both suites were run while preparing this document. Backend: 61 "
         "tests, 0 failures, 0 errors. Web: 36 tests across 4 files, all "
         "passing."),
  ),
  section(
    h1("2.", "Strategy"),
    h2("2.1  What a unit test is here"),
    p("A test of one rule, in isolation, with no network and no database. The "
      "rules worth testing this way are the ones that decide something: who "
      "may approve, whether a date is a working day, whether a token is valid, "
      "whether a file is acceptable."),
    h2("2.2  Where testing effort belongs"),
    p("The application enforces its rules on the server, so that is where the "
      "tests are. A rule enforced only in the browser is not enforced at all, "
      "and testing the browser copy would give false confidence."),
    h2("2.3  Levels"),
    table(["Level", "What it covers", "Where"],
          [["Unit", "One rule, no I/O", "backend/src/test, web/src"],
           ["Integration", "A request through controller, service and "
            "repository", "Exercised against the running service"],
           ["Manual", "The cases in sections 5 to 10 of this document",
            "Executed by the tester"],
           ["Regression", "Section 12, before every release", "Both"]],
          widths=["16%", "50%", "34%"]),
  ),
  section(
    h1("3.", "Automated Coverage Today"),
    table(["Test class or file", "Tests", "Side", "Covers"],
          [[a[0], (str(a[1]) if a[1] else "--"), a[2], a[3]] for a in AUTOMATED],
          widths=["24%", "9%", "10%", "57%"]),
    h2("3.1  Honest assessment"),
    p("The automated suite covers authentication, tokens, lockout, file "
      "storage, the working calendar and the punch-out window well. It does "
      "not yet cover the approval rules, which are the most consequential "
      "logic in the application. Those rules have been verified against "
      "production by hand and are covered by the manual cases in section 5, "
      "but they deserve automated tests."),
    finding("Recommended next investment in testing",
            "Automate the approval rules first: only the named approver may "
            "decide, one request per person per day, no weekend leave, and "
            "approved work from home writing attendance exactly once. These "
            "are the rules whose failure costs money or trust, and every one "
            "of them is presently guarded by manual testing alone."),
  ),
  section(
    h1("4.", "Role Permission Matrix"),
    p("Every row is a test in itself: perform the action as each role and "
      "confirm the answer matches."),
    table(["Action", "Employee", "TL", "HR", "CTO", "Admin"],
          [list(r) for r in ROLE_MATRIX],
          widths=["36%", "13%", "13%", "12%", "12%", "14%"]),
    note("The third row is the one most often assumed wrong. No role, however "
         "senior, may decide a request addressed to somebody else."),
  ),
  section(
    h1("5.", "Authentication and Authorisation"),
    table(["Ref", "Action", "Expected result", "Type"],
          [[c[0], c[1], c[2], c[3]] for c in AUTH_CASES],
          widths=["11%", "31%", "44%", "14%"]),
  ),
  section(
    h1("6.", "Leave and Permission"),
    diagrams.approval_ladder(),
    table(["Ref", "Action", "Expected result", "Type"],
          [[c[0], c[1], c[2], c[3]] for c in LEAVE_CASES],
          widths=["10%", "31%", "45%", "14%"]),
    h2("6.1  The duplicate guard"),
    diagrams.duplicate_guard(),
  ),
  section(
    h1("7.", "Work From Home"),
    p("The cases below matter beyond their own module, because an approved "
      "work-from-home day becomes attendance, and attendance becomes pay. An "
      "error here is a payroll error."),
    diagrams.wfh_to_pay(),
    table(["Ref", "Action", "Expected result", "Type"],
          [[c[0], c[1], c[2], c[3]] for c in WFH_CASES],
          widths=["11%", "31%", "44%", "14%"]),
  ),
  section(
    h1("8.", "Chat, Communities and Announcements"),
    table(["Ref", "Action", "Expected result", "Type"],
          [[c[0], c[1], c[2], c[3]] for c in CHAT_CASES],
          widths=["10%", "32%", "44%", "14%"]),
  ),
  section(
    h1("9.", "Other Modules"),
    table(["Ref", "Action", "Expected result", "Type"],
          [[c[0], c[1], c[2], c[3]] for c in OTHER_CASES],
          widths=["10%", "32%", "44%", "14%"]),
  ),
  section(
    h1("10.", "Input Validation"),
    table(["Ref", "Input", "Expected result", "Type"],
          [[c[0], c[1], c[2], c[3]] for c in VALIDATION_CASES],
          widths=["11%", "26%", "49%", "14%"]),
    note("Every one of these must be re-tested against the API directly, not "
         "only through the form. A browser check confirms the form; only an "
         "API check confirms the rule."),
  ),
  section(
    h1("11.", "Recording Results"),
    p("Each case is recorded with its actual result and a verdict. A failure "
      "is only useful if it can be reproduced, so the record must carry enough "
      "to repeat it."),
    table(["Field", "What to write"],
          [["Ref", "The case reference, for example TC-LV-07."],
           ["Executed by", "Name and date."],
           ["Role used", "The account role, since most rules depend on it."],
           ["Actual result", "What happened, in the words on the screen."],
           ["Verdict", "Pass or Fail."],
           ["Evidence", "A screenshot, or the API response."],
           ["Defect", "The reference raised, if it failed."]],
          widths=["20%", "80%"]),
    h2("11.1  Verdict conventions"),
    table(["Verdict", "Meaning"],
          [[pill("pass", "Pass"), "The actual result matched the expected "
            "result exactly."],
           [pill("warn", "Pass with note"),
            "Correct behaviour, but something was unclear or slow enough to "
            "mention."],
           [pill("fail", "Fail"), "The actual result differed. A defect is "
            "raised, with steps to reproduce."]],
          widths=["22%", "78%"]),
  ),
  section(
    h1("12.", "Regression Set"),
    p("These are executed before every release regardless of what changed, "
      "because each has broken at least once."),
    bullets([
      "A newly applied leave appears in the applicant's own list immediately.",
      "Only the named approver can decide; HR, the CTO and admin see but "
      "cannot act.",
      "A Team Leader does not see their own request in their approval queue.",
      "An approved work-from-home day counts as present and is paid.",
      "A complaint reaches the person it was addressed to and appears in the "
      "list.",
      "The Announcements channel is visible to every role and postable only by "
      "HR, the CTO and admin.",
      "A file attaches successfully to a leave, permission and WFH request.",
      "The site is served, the containers are healthy, and login returns a "
      "token.",
    ]),
    finding("Why this list exists",
            "Every item is a defect that was found in production rather than "
            "in testing. A regression set earns its place by history, not by "
            "theory."),
  ),
  section(
    h1("13.", "Defect Management"),
    table(["Severity", "Meaning", "Example from this project"],
          [["Critical", "Data is wrong, or a rule that protects money or "
            "confidentiality fails",
            "An administrator could override an approval chain."],
           ["High", "A feature does not work for its intended users",
            "A complaint was saved but appeared to nobody."],
           ["Medium", "It works, but not as intended",
            "The status list allowed a change to the same status."],
           ["Low", "Cosmetic or wording",
            "A newly created channel gave no hint that it was empty."]],
          widths=["14%", "38%", "48%"]),
    h2("13.1  What a defect report must contain"),
    bullets([
      "The role and account used, because most rules depend on it.",
      "The exact steps, in order, from signing in.",
      "What was expected and what happened.",
      "The API response where the screen alone is ambiguous.",
      "Whether it reproduces every time or intermittently.",
    ]),
  ),
  section(
    h1("14.", "Completion Criteria"),
    p("Testing is complete for a release when all of the following hold."),
    table(["Criterion", "Measure"],
          [["Automated suites pass",
            "Backend 61 of 61; web 36 of 36; the web build and typecheck "
            "clean."],
           ["The regression set passes", "All eight items in section 12."],
           ["No open Critical or High defect", "Zero."],
           ["Every Must requirement has been exercised",
            "FR-01 to FR-15 in the requirements document."],
           ["The role matrix has been walked",
            "Every row in section 4, as every role."],
           ["Production verified after deployment",
            "Site returns 200, containers healthy, the specific change "
            "present."]],
          widths=["34%", "66%"]),
    note("The last criterion exists because a change that passes every test "
         "and is not correctly deployed is not delivered.")
  ),
  section(
    h1("15.", "Attendance, Payroll and Reports"),
    table(["Ref", "Action", "Expected result", "Type"],
          [["TC-AT-01", "Punch in at the start of the day",
            "Recorded with the time", "Positive"],
           ["TC-AT-02", "Punch out inside the permitted window",
            "Recorded and the day is complete", "Positive"],
           ["TC-AT-03", "Punch out outside the permitted window",
            "Refused, with the window stated", "Boundary"],
           ["TC-AT-04", "Punch in twice on one day",
            "The second is refused or ignored", "Negative"],
           ["TC-AT-05", "View team attendance as a Team Leader",
            "Only that team is shown", "Positive"],
           ["TC-AT-06", "View team attendance as an employee",
            "Not available", "Negative"],
           ["TC-AT-07", "Export attendance for a range",
            "An Excel file matching the filters on screen", "Positive"],
           ["TC-PR-01", "Prepare a payroll run for a month",
            "Computed from attendance for that month", "Positive"],
           ["TC-PR-02", "Release without approval",
            "Not permitted; the steps are separate", "Negative"],
           ["TC-PR-03", "Run payroll for a month containing approved leave",
            "Leave is reflected as attendance already records it", "Positive"],
           ["TC-PR-04", "Run payroll for a month containing approved WFH",
            "Those days are paid", "Positive"],
           ["TC-RP-01", "Open Reports as HR",
            "Attendance, leave and payroll for the whole company", "Positive"],
           ["TC-RP-02", "Open Reports as an employee",
            "Not available", "Negative"]],
          widths=["10%", "32%", "44%", "14%"]),
  ),

  section(
    h1("16.", "API-Level Testing"),
    p("The rules live on the server, so the server is where they must be "
      "proved. A test performed only through the browser confirms the form; "
      "the same test performed against the API confirms the rule."),
    h2("16.1  Cases that must be run against the API directly"),
    table(["Ref", "Test", "Why the browser is not enough"],
          [["TC-API-01", "Decide a request as a role that is not the named "
            "approver", "The button is hidden, so the browser cannot even "
            "attempt it. Only a direct call proves the server refuses."],
           ["TC-API-02", "Post to the Announcements channel as an employee",
            "The message box is not shown; the server rule is what matters."],
           ["TC-API-03", "Request another person payslip by id",
            "The link is never offered in the interface."],
           ["TC-API-04", "Apply for leave with a weekend date",
            "The picker may prevent selection; the rule must hold "
            "regardless."],
           ["TC-API-05", "Submit a request with no approver",
            "The form requires one; the server must too."],
           ["TC-API-06", "Call any endpoint with no token, an expired token "
            "and an altered token",
            "Three distinct refusals that no page exercises."],
           ["TC-API-07", "Upload a file above the size limit",
            "The form may filter it; storage must refuse it."]],
          widths=["12%", "34%", "54%"]),
    note("Each of these is a case where hiding a control is presentation and "
         "refusing the call is security. The tests above check the second."),
  ),

  section(
    h1("17.", "Database and Persistence Testing"),
    table(["Ref", "Test", "Expected result"],
          [["TC-DB-01", "Start the application after a schema change",
            "It starts only if every entity matches its table; otherwise it "
            "refuses, naming the column"],
           ["TC-DB-02", "Check the Flyway version after deployment",
            "At the expected version with no validation errors"],
           ["TC-DB-03", "Approve work from home and inspect attendance",
            "One row per working day, none for weekends or holidays"],
           ["TC-DB-04", "Approve work from home over a day that already has "
            "attendance", "The existing row is unchanged"],
           ["TC-DB-05", "Cancel a pending request",
            "Status becomes Cancelled; no attendance is written"],
           ["TC-DB-06", "Attach a file, then delete it as another user",
            "Refused; only the uploader may remove it"]],
          widths=["12%", "38%", "50%"]),
  ),

  section(
    h1("18.", "Notification and Realtime Testing"),
    p("These require two sessions open at once, as two different people. That "
      "is the point: the behaviour being tested is one person learning of "
      "something another person did."),
    table(["Ref", "Setup", "Action", "Expected result"],
          [["TC-RT-01", "Employee and their approver both signed in",
            "The employee submits a request",
            "It appears in the approver queue without a reload"],
           ["TC-RT-02", "Same", "The approver approves",
            "The employee sees the decision without a reload"],
           ["TC-RT-03", "Same", "A comment is added",
            "The other party is notified"],
           ["TC-RT-04", "Two people in a chat", "One sends a message",
            "The other sees it immediately"],
           ["TC-RT-05", "HR and an employee signed in",
            "HR posts in Announcements",
            "The employee sees the post"],
           ["TC-RT-06", "One session, connection interrupted",
            "Reconnect", "Updates resume without a manual reload"]],
          widths=["10%", "26%", "26%", "38%"]),
  ),

  section(
    h1("19.", "Security Testing"),
    table(["Ref", "Test", "Expected result"],
          [["TC-SEC-01", "Inspect the transport",
            "TLS 1.3 with HTTP/2; nothing served unencrypted"],
           ["TC-SEC-02", "Read the response headers",
            "No server version; Permissions-Policy and CSP present"],
           ["TC-SEC-03", "Alter a token payload and call an endpoint",
            "Refused; the signature does not verify"],
           ["TC-SEC-04", "Reuse a token after four hours",
            "Refused, and the refresh does not extend it"],
           ["TC-SEC-05", "Attempt a path traversal on a file route",
            "Refused. Note: a probe written with ../ is normalised by the "
            "client before sending, so the segments must be encoded or the "
            "test proves nothing"],
           ["TC-SEC-06", "Sign in repeatedly with a wrong password",
            "The account locks"],
           ["TC-SEC-07", "Request data belonging to another company",
            "Refused; tenant isolation holds"],
           ["TC-SEC-08", "Confirm a password is never returned or logged",
            "It is not"]],
          widths=["12%", "34%", "54%"]),
    note("TC-SEC-05 is recorded in this form deliberately: an earlier probe "
         "appeared to succeed because curl had collapsed the path and nginx "
         "served the application. The apparent finding was an artefact of the "
         "test, not a weakness in the system."),
  ),

  section(
    h1("20.", "Mobile Application Testing"),
    p("The Android application calls the same API, so every server-side rule "
      "already tested applies unchanged. What must be tested separately is the "
      "application itself."),
    table(["Ref", "Test", "Expected result"],
          [["TC-MOB-01", "Sign in on the phone",
            "The same credentials work and the same role is applied"],
           ["TC-MOB-02", "Apply for leave from the phone",
            "It reaches the named approver, exactly as from the web"],
           ["TC-MOB-03", "Approve from the phone as the named approver",
            "Approved"],
           ["TC-MOB-04", "Attempt to approve a request addressed to somebody "
            "else", "Refused, because the rule is on the server"],
           ["TC-MOB-05", "Raise a ticket from the phone",
            "The title and recipient are required, as on the web"],
           ["TC-MOB-06", "Lose connectivity mid-action",
            "A clear message rather than a silent failure"],
           ["TC-MOB-07", "Compare a list against the web portal",
            "The same data; there is one source"]],
          widths=["12%", "36%", "52%"]),
  ),

  section(
    h1("21.", "Test Data"),
    bullets([
      "One account per role: employee, Team Leader, HR, CTO and "
      "administrator. Several rules can only be tested by holding two of them "
      "at once.",
      "At least two employees reporting to the same Team Leader, so that a "
      "request addressed to one person can be attempted by another.",
      "A month containing a public holiday, so holiday handling in work from "
      "home and payroll is exercised.",
      "A date range spanning a weekend, for the same reason.",
      "A person with an existing attendance row on a day being requested as "
      "work from home.",
      "Files at 9 MB and 11 MB, for the size boundary.",
    ]),
    note("Testing must not be performed with production accounts belonging to "
         "real staff. A decision taken in testing is a real decision on a real "
         "record."),
  ),

  section(
    h1("22.", "Execution Cycle"),
    table(["When", "What is run"],
          [["On every change", "Both automated suites, the web typecheck and "
            "the build."],
           ["Before a release", "The regression set in section 12, plus the "
            "manual cases for whatever changed."],
           ["After a release", "Production verification: the site answers, "
            "the containers are healthy, and the specific change is present."],
           ["Monthly", "The full role matrix in section 4, walked as every "
            "role."],
           ["After a schema change", "The persistence cases in section 17."]],
          widths=["24%", "76%"]),
  ),
])


html = document(
    "Unit Testing Document",
    "PIXOUS HR Portal",
    "What is covered automatically, what must be tested by hand, and how a "
    "release is judged complete.",
    body)

out = os.path.join(os.path.expanduser("~"), "Downloads",
                   "Pixous_HR_Portal_Unit_Testing_v1.0.pdf")
print(to_pdf(html, out))
