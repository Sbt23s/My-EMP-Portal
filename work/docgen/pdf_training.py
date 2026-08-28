"""User Training Document — PIXOUS HR Portal.

Every page name, every role gate and every approval chain in this document was
read from the deployed application, not written from memory. Where a rule is
stated, it is the rule the server enforces.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from diagrams import (approval_ladder, decide_vs_view, duplicate_guard,
                      request_lifecycle, wfh_to_pay)
from pdf_style import (Raw, bullets, document, h1, h2, metrics, note, p, pill,
                       section, table)
from render_pdf import to_pdf

OUT = os.path.join(os.path.expanduser("~"), "Downloads",
                   "Pixous_HR_Portal_User_Training_Guide_v1.0.pdf")

YES = lambda: pill("pass", "Yes")
NO = lambda: pill("warn", "—")
VIEW = lambda: pill("warn", "View only")


def steps(items: list[str]) -> str:
    """A numbered walk-through. Numbered because order matters in a task."""
    rows = "".join(
        f'<li style="margin-bottom:1.6mm">{i}</li>' for i in items)
    return f'<ol style="margin:0 0 4mm;padding-left:6mm">{rows}</ol>'


body = "".join([

# ─────────────────────────────────────────────────────────── 1 ──
section(
  h1("1.", "How to Read This Guide"),
  p("This guide covers every page of the PIXOUS HR Portal, what each role can "
    "do on it, and the exact steps for the tasks people carry out daily. It is "
    "written to be read in order by somebody new, and dipped into by somebody "
    "looking for one answer."),

  metrics([("26", "pages"), ("5", "roles"),
           ("63", "staff accounts"), ("3", "approval chains")]),

  h2("1.1  The one idea worth understanding first"),
  p("Almost everything in this portal is a request that travels one rung up a "
    "ladder. Leave, short permission and work from home all behave the same "
    "way, so learning one teaches the other two."),
  Raw(approval_ladder()),
  p("The rung is decided by the server from the applicant's own role. Nobody "
    "picks their own approver, and nobody can be skipped."),

  h2("1.2  Seeing and deciding are different rights"),
  p("This distinction runs through the whole system and explains most of what "
    "people ask about."),
  Raw(decide_vs_view()),
  note("HR, the CTO and the System Administrator can see every request in the "
       "company. None of them can approve a request that was addressed to "
       "somebody else. That is deliberate: the chain exists so a request "
       "reaches a person who can actually judge it."),

  h2("1.3  Signing in"),
  table(["Step", "What to do"],
        [["1", "Open https://pixoushrportal.pixous.info in any modern browser"],
         ["2", "Enter your username — usually your employee code in lower case, "
               "such as pix-e009"],
         ["3", "Enter your password and press Sign in"],
         ["4", "Change your password from My Profile once you are in"]],
        widths=["10%", "90%"]),
  note("The same credentials work on the Android app. Repeated wrong passwords "
       "lock the account for a short period — this is deliberate, and it "
       "clears by itself."),
),

# ─────────────────────────────────────────────────────────── 2 ──
section(
  h1("2.", "The Five Roles"),
  p("A role is a bundle of permissions rather than a title, and the portal "
    "decides what to show from the permissions rather than the name. Two "
    "people with different titles and the same permissions see the same "
    "screens."),
  table(["Role", "Who they are", "In this company", "What is theirs to do"],
        [["Employee", "Everybody who is not something else below", "49 accounts",
          "Own attendance, leave, permission, WFH, payslip, claims, tasks, "
          "tickets"],
         ["Team Leader", "Runs a team; approves for that team", "7 accounts",
          "Everything an employee has, plus approving their team’s requests "
          "and assigning tasks"],
         ["HR", "Runs the people processes", "4 accounts",
          "Company-wide visibility, payroll, employee records, approving Team "
          "Leaders’ requests"],
         ["CTO", "The company head — PIX-E100", "1 account",
          "Approves HR’s own requests; executive visibility over everything"],
         ["System Admin", "Keeps the portal running", "2 accounts",
          "Configuration, audit log, user accounts. Sees requests; decides none"]],
        widths=["14%", "26%", "14%", "46%"]),
  note("The CTO is identified by employee code rather than by role, because "
       "that account also carries an employee role — reading the role list "
       "alone would file the company head as an employee."),

  h2("2.1  Which pages each role sees"),
  p("Taken from the portal's own navigation rules. A dash means the page is "
    "not shown to that role at all."),
  table(["Page", "Employee", "Team Leader", "HR", "CTO", "Admin"],
        [["Dashboard", YES(), YES(), YES(), YES(), YES()],
         ["Attendance", YES(), YES(), YES(), YES(), NO()],
         ["Employee Attendance", NO(), YES(), YES(), YES(), YES()],
         ["Leave", YES(), YES(), YES(), YES(), NO()],
         ["Permission", YES(), YES(), YES(), YES(), YES()],
         ["Work From Home", YES(), YES(), YES(), YES(), YES()],
         ["Approvals", NO(), YES(), YES(), YES(), NO()],
         ["Leave Policies", NO(), NO(), YES(), YES(), YES()],
         ["Payroll", NO(), NO(), YES(), YES(), YES()],
         ["Payslips", YES(), YES(), NO(), YES(), NO()],
         ["Work Reports", YES(), YES(), YES(), YES(), YES()],
         ["Tasks", YES(), YES(), YES(), YES(), YES()],
         ["Claims", YES(), YES(), YES(), YES(), YES()],
         ["Assets", YES(), YES(), YES(), YES(), YES()],
         ["Supports", YES(), YES(), YES(), YES(), YES()],
         ["Complaints", YES(), YES(), YES(), YES(), YES()],
         ["Reports", NO(), YES(), YES(), YES(), NO()],
         ["Chat", YES(), YES(), YES(), YES(), YES()],
         ["Communities", NO(), NO(), YES(), YES(), YES()],
         ["Calendar", YES(), YES(), YES(), YES(), YES()],
         ["Teams", YES(), YES(), YES(), YES(), YES()],
         ["Employees", NO(), NO(), YES(), YES(), YES()],
         ["Audit Log", NO(), NO(), NO(), NO(), YES()],
         ["Fresh Start", NO(), NO(), NO(), NO(), YES()]],
        widths=["28%", "14%", "16%", "12%", "12%", "18%"]),
  note("Payslips is hidden from HR and the administrators on purpose: they "
       "reach payroll through the Payroll page, which shows everybody rather "
       "than one person."),
),

# ─────────────────────────────────────────────────────────── 3 ──
section(
  h1("3.", "Everyday Tasks, Step by Step"),

  h2("3.1  Marking attendance"),
  steps([
    "Open <b>Attendance</b> from the left menu.",
    "Choose the <b>Mode</b>: Office, Work From Home, Site or Biometric.",
    "Press <b>Punch in</b>. Your location is captured — allow it when the "
    "browser asks.",
    "Press <b>Punch out</b> when you finish. Hours are worked out for you.",
  ]),
  note("If the company requires a face check, the punch buttons ask for a "
       "selfie and compare it with your enrolled photo. Ask HR to enrol your "
       "face if the page says it is not registered."),

  h2("3.2  Applying for leave"),
  steps([
    "Open <b>Leave Management → Leave</b>.",
    "Press <b>Apply for leave</b>.",
    "Choose the leave type. Your remaining balance is shown on the cards above.",
    "Choose <b>From</b> and <b>To</b>. Weekends and public holidays are left "
    "out of the day count automatically.",
    "Choose <b>Request to</b> — the list shows the approver for the dates you "
    "picked, and nobody else.",
    "Write the <b>Reason</b>. This is what your approver reads first.",
    "Attach a photo or document if you have one — a medical certificate, for "
    "example. This is optional.",
    "Press <b>Submit request</b>.",
  ]),
  table(["If you see", "It means", "What to do"],
        [["“Leave cannot start on a Saturday”",
          "Weekends are not working days", "Pick a weekday"],
         ["“You already have … on that date”",
          "One leave per person per day, whatever the type",
          "Cancel the other request, or pick other dates"],
         ["“No leave left …”", "The quarterly limit for that type is used up",
          "The message says when it is next available"],
         ["Your request is not in the list",
          "The From / To range above the list may not cover it",
          "Widen the range — it defaults to this year plus the year ahead"]],
        widths=["30%", "34%", "36%"]),

  h2("3.3  Applying for a short permission"),
  p("Permission is time off within a working day — an hour at the bank, a "
    "school run — rather than a whole day."),
  steps([
    "Open <b>Leave Management → Permission</b>, then <b>Apply for permission</b>.",
    "Choose the date, and the <b>From</b> and <b>To</b> times. The hours are "
    "calculated for you.",
    "Set the <b>Priority</b> and write the <b>Reason</b>.",
    "Choose who it goes to, attach anything relevant, and submit.",
  ]),
  note("One permission per person per day, for the same reason as leave: two "
       "requests for one absence leave an approver choosing between them."),

  h2("3.4  Applying to work from home"),
  steps([
    "Open <b>Leave Management → Work From Home</b>, then <b>Apply for WFH</b>.",
    "Choose the date range. Weekends and holidays are excluded from the count.",
    "Write the reason, and add remarks if there is anything else to say.",
    "The dialog names who it will go to. Press <b>Submit request</b>.",
  ]),
  Raw(wfh_to_pay()),
  note("An approved work-from-home day is written to your attendance as a "
       "working day. It is counted present, not absent, and it is paid."),
),

# ─────────────────────────────────────────────────────────── 4 ──
section(
  h1("4.", "Approving — for Team Leaders, HR and the CTO"),
  Raw(request_lifecycle()),

  h2("4.1  What arrives, and where"),
  table(["You are", "You decide", "You see but cannot decide"],
        [["Team Leader", "Your own team’s leave, permission and WFH",
          "Nothing beyond your team"],
         ["HR", "Team Leaders’ requests", "Every request in the company"],
         ["CTO", "HR’s own requests", "Every request in the company"],
         ["System Admin", "Nothing", "Every request in the company"]],
        widths=["18%", "40%", "42%"]),

  h2("4.2  Deciding a request"),
  steps([
    "Open <b>Approvals</b>, or the <b>Pending my approval</b> tab on Permission "
    "or Work From Home.",
    "Press <b>View</b> on the row. The whole request opens — dates, reason, "
    "hours, who it went to, and anything attached.",
    "Read what was attached. Photographs open full size; documents open in a "
    "new tab.",
    "Use <b>Comments</b> to ask a question. The applicant is told, and can "
    "answer in the same place.",
    "Press <b>Approve</b> or <b>Reject</b> in that dialog.",
    "A rejection needs a reason — the applicant is owed one. An approval may "
    "carry an optional note.",
  ]),
  note("Approve and Reject appear only on requests addressed to you. On "
       "anybody else's request you will see View alone. That is not a fault: "
       "it is the chain working."),

  h2("4.3  Why a row has no buttons"),
  table(["What you see", "Why"],
        [["View only, on a pending request",
          "It was addressed to somebody else"],
         ["View only, on your own request",
          "Nobody approves their own request"],
         ["“Not decided in time”",
          "A permission whose date has passed can no longer be decided"],
         ["No Approvals page at all",
          "The role has no approving to do"]],
        widths=["36%", "64%"]),
),

# ─────────────────────────────────────────────────────────── 5 ──
section(
  h1("5.", "The Rules That Refuse Things"),
  p("Most refusals people meet are one of these four. Each exists to stop a "
    "record that would be wrong."),
  Raw(duplicate_guard()),
  table(["Rule", "Applies to", "Why it exists"],
        [["One request per person per day",
          "Leave, Permission, WFH",
          "Two records for one absence deduct from two balances and leave an "
          "approver choosing between them"],
         ["No weekend start or end",
          "Leave, Permission, WFH",
          "A Saturday is already not a working day, so there is nothing to "
          "take off"],
         ["Only the named approver decides",
          "Leave, Permission, WFH",
          "A chain that anybody can step into is not a chain"],
         ["A rejection needs a reason",
          "All three",
          "The applicant is owed an explanation they can act on"]],
        widths=["26%", "22%", "52%"]),
  note("A rejected or cancelled request never claimed the day, so it does not "
       "block a fresh one for the same date."),
),

# ─────────────────────────────────────────────────────────── 6 ──
section(
  h1("6.", "The Other Modules, Briefly"),
  table(["Module", "What it is for", "Who uses it"],
        [["Dashboard", "Today at a glance: attendance, pending items, balances",
          "Everyone"],
         ["Work Reports", "What you did, day by day, with attachments",
          "Everyone files; TL and HR review"],
         ["Tasks", "Work assigned to you, with progress",
          "Everyone; TL and HR assign"],
         ["Claims", "Travel and expense claims, with receipts",
          "Everyone files; HR approves"],
         ["Assets", "Equipment issued to you; confirm receipt on collection",
          "Everyone; HR issues"],
         ["Supports", "Raise an IT or facilities ticket and track it",
          "Everyone; HR and admins resolve"],
         ["Complaints", "Raise something with HR or the CTO, confidentially",
          "Everyone; HR, CTO and admins review"],
         ["Payslips", "Your monthly pay — view, download or email to yourself",
          "Employees and Team Leaders"],
         ["Payroll", "Runs, requests and approvals for the whole company",
          "HR, CTO, Admin"],
         ["Chat", "One-to-one and group messaging, with calls",
          "Everyone"],
         ["Communities", "Company channels and announcements",
          "HR and CTO post; everyone reads"],
         ["Calendar", "Company events and public holidays", "Everyone"],
         ["Teams", "The staff directory, by team", "Everyone"],
         ["Employee Attendance", "Who was in, by day or range",
          "TL for their team; HR and CTO for everyone"],
         ["Reports", "Exports across attendance, leave and payroll",
          "TL, HR, CTO"],
         ["Employees", "Employee records, onboarding, credentials",
          "HR, CTO, Admin"],
         ["Audit Log", "Who did what, and when", "System Admin"]],
        widths=["20%", "44%", "36%"]),

  h2("6.1  Attachments and comments"),
  p("Leave, permission and work-from-home requests all carry the same two "
    "panels, so they behave identically wherever you meet them."),
  bullets([
    "<b>Photos and documents</b> — images, PDF or Word. Up to ten files, 10 MB "
    "each. Optional everywhere.",
    "<b>Comments</b> — a conversation between the applicant and the approver. "
    "Both see it; the other person is notified.",
    "A file may be removed only by whoever uploaded it.",
    "Attaching stays open while a request is pending, so a document can be "
    "added after an approver asks for it.",
  ]),
),

# ─────────────────────────────────────────────────────────── 7 ──
section(
  h1("7.", "Sign-in Details"),
  p("Usernames as they stand today. Passwords are issued separately and are "
    "deliberately not printed here — see the note below."),

  h2("7.1  CTO"),
  table(["Employee code", "Name", "Username"],
        [["PIX-E100", "CEO", "pix-e100"]],
        widths=["24%", "38%", "38%"]),

  h2("7.2  System Administrators"),
  table(["Employee code", "Name", "Username"],
        [["ADM0001", "System Admin", "admin"],
         ["EMP0005", "Venkateshwaran S V.", "venkateshwaran"]],
        widths=["24%", "38%", "38%"]),

  h2("7.3  HR"),
  table(["Employee code", "Name", "Username", "Designation"],
        [["HR0001", "HR", "hr", "—"],
         ["PIX-E001", "VANARAJA D", "pix-e001", "Office Administrator"],
         ["PIX-E037", "Bhagavathi", "pix-e037", "Reporting Manager"],
         ["PIX-E040", "Shaminthran RD", "pix-e040", "Dev-Ops Lead"]],
        widths=["20%", "30%", "24%", "26%"]),

  h2("7.4  Team Leaders"),
  table(["Employee code", "Name", "Username", "Designation"],
        [["PIX-E009", "Loganathan Ramanujam", "pix-e009", "Software Developer"],
         ["PIX-E016", "Meena Sudhakaran Kanchana", "pix-e016", "UI/UX Designer"],
         ["PIX-E023", "Ranjith Gurusamy", "pix-e023", "—"],
         ["PIX-E039", "Amutha Kumari G", "pix-e039", "Mobile Developer"],
         ["PIX-E048", "Subash Chakravarthi", "pix-e048", "Devops Engineer"],
         ["PIX-E055", "Harish C", "pix-e055", "Ai Engineer"],
         ["PIX-E056", "KARTHIGAISELVAN", "pix-e056", "Digital Marketing"]],
        widths=["20%", "32%", "22%", "26%"]),

  h2("7.5  Employees"),
  p("Forty-nine further accounts follow the same pattern: the username is the "
    "employee code in lower case, such as pix-e057."),

  note("Passwords are not printed in this document on purpose. A training "
       "guide is circulated, forwarded and left on desks; a password list "
       "should not be. The portal generates a separate credentials document "
       "for that, which an administrator produces on demand and hands over "
       "person by person."),
),

# ─────────────────────────────────────────────────────────── 8 ──
section(
  h1("8.", "If Something Does Not Work"),
  table(["What happens", "Most likely cause", "What to do"],
        [["“Invalid credentials”", "Password already changed",
          "Ask HR or an administrator for a reset"],
         ["Account locked", "Several wrong passwords in a row",
          "Wait a few minutes — it clears by itself"],
         ["Signed in, but very few pages",
          "The role has not been assigned yet", "Ask HR to check your role"],
         ["Punch buttons refuse",
          "Face not enrolled, or location blocked",
          "Ask HR to enrol your face; allow location in the browser"],
         ["A request is missing from the list",
          "The date range above the list does not cover it",
          "Widen From / To"],
         ["Approve and Reject are missing",
          "The request is not addressed to you", "This is correct behaviour"],
         ["An upload is refused",
          "Wrong type, or over 10 MB",
          "Images, PDF or Word only; a phone photo can be sent smaller"],
         ["A page is blank after a release",
          "The browser is holding an old copy",
          "Press Ctrl + Shift + R"]],
        widths=["27%", "31%", "42%"]),

  h1("9.", "Support"),
  table(["Need", "Where to go"],
        [["A password reset, or a role change", "HR"],
         ["Something broken in the portal",
          "Supports → New ticket. It reaches HR or the administrator"],
         ["Something to raise confidentially",
          "Complaints. Choose who it goes to; only they can respond"],
         ["A decision that has not moved",
          "Comments on the request itself — the approver is notified"]],
        widths=["38%", "62%"]),
  p("This guide describes the portal as deployed at "
    "https://pixoushrportal.pixous.info. Where the portal and this document "
    "disagree, the portal is right and this document needs reissuing."),
),
])

html = document(
    "User Training Guide",
    "Role-wise Workflow & Operating Manual",
    "Every page, every role and every approval chain of the PIXOUS HR Portal, "
    "with step-by-step instructions for the tasks people carry out daily.",
    body,
    meta_extra=[("Audience", "All staff — Employee, Team Leader, HR, CTO, Admin")])

print(to_pdf(html, OUT))
