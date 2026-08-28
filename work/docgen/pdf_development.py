# -*- coding: utf-8 -*-
"""
Full Application Development Document for the PIXOUS HR Portal.

Written from the repository and the running production system. Versions,
counts and settings are read rather than recalled.
"""
import os
from pdf_style import (document, section, h1, h2, p, note, bullets, table,
                       metrics, finding)
import diagrams
from render_pdf import to_pdf

BACKEND_STACK = [
    ("Language", "Java 17", "Long-term support release."),
    ("Framework", "Spring Boot 3.5.0",
     "Web, Security, Data JPA, Validation, WebSocket, Actuator."),
    ("Persistence", "Hibernate / JPA",
     "ddl-auto=validate -- the schema is owned by Flyway, never by Hibernate."),
    ("Migrations", "Flyway, V1 to V104",
     "Forward-only. A change to the schema is a new file, never an edit."),
    ("Database", "MySQL 8", "Externally hosted, roughly 125 ms away."),
    ("Pooling", "HikariCP",
     "validation-timeout 1000 ms, declared in application.yml."),
    ("Cache", "Redis", "Sessions, presence and short-lived lookups."),
    ("Realtime", "STOMP over SockJS", "Notifications, chat and presence."),
    ("Auth", "JWT", "Access and refresh tokens, both four hours."),
    ("Build", "Maven", "mvn test, mvn package."),
]

FRONTEND_STACK = [
    ("Library", "React 19", "Function components and hooks throughout."),
    ("Language", "TypeScript 5.7", "Strict; the build fails on a type error."),
    ("Bundler", "Vite 6", "Development server and production build."),
    ("Server state", "TanStack Query 5",
     "Fetching, caching and invalidation. Not a general state store."),
    ("Forms", "react-hook-form 7", "With shared validation rules."),
    ("Styling", "Tailwind CSS 3.4", "Utility classes; no separate stylesheet."),
    ("HTTP", "axios 1.7",
     "One shared client that attaches the token and refreshes it."),
    ("Charts", "Recharts 2.15", "Dashboards and reports."),
    ("Realtime", "@stomp/stompjs 7 with sockjs-client 1.6", "Live updates."),
    ("Dates", "dayjs 1.11", "Formatting and ranges."),
]

LAYERS = [
    ("Controller", "*Controller.java",
     "HTTP only: bind the request, call the service, wrap the result. "
     "Authorisation by role happens here with @PreAuthorize."),
    ("Service", "*Service.java",
     "All business rules. Anything that decides whether an action is allowed "
     "on grounds other than role lives here."),
    ("Repository", "*Repository.java",
     "Spring Data interfaces. Derived queries where they read clearly, @Query "
     "where they do not."),
    ("Entity", "@Entity classes",
     "Mapped to tables that Flyway created. 62 entities across 76 tables."),
    ("DTO", "dto/*Dtos.java",
     "What crosses the HTTP boundary. Entities are never returned directly."),
]

CONVENTIONS = [
    ("Authorisation is checked twice",
     "Role at the controller with @PreAuthorize, and the specific rule in the "
     "service. A role that may see a queue is not thereby allowed to decide an "
     "item in it."),
    ("The server is the authority on validation",
     "Forms repeat the rules so a user is told sooner, but the server decides. "
     "A rule enforced only in the browser is not enforced."),
    ("The schema belongs to Flyway",
     "ddl-auto=validate. If an entity and its table disagree the application "
     "refuses to start, which is preferable to writing into the wrong shape."),
    ("Additive over replacing",
     "A new behaviour gets a new endpoint beside the old one where callers "
     "already exist -- /wfh/active-range was added beside /wfh/active rather "
     "than changing it."),
    ("A commit explains the problem",
     "The subject says what changed; the body says what was wrong and why the "
     "fix is the one chosen."),
    ("Comments explain the reason, not the mechanism",
     "The code says what it does. A comment is for what the reader could not "
     "have inferred."),
]

PITFALLS = [
    ("Lombok renames boolean accessors",
     "A field isAnnouncement gets isAnnouncement/setAnnouncement, so Jackson "
     "binds the JSON key announcement and silently ignores isAnnouncement. "
     "This defeated the announcement tick box entirely. Check the wire name, "
     "not the field name."),
    ("A shared axios client sets Content-Type",
     "The client sets application/json globally, which overrides the multipart "
     "boundary on a file upload. Each upload call must set its own header."),
    ("Hikari ignores some settings silently",
     "keepalive-time has a floor of 30000 ms and must be below max-lifetime. "
     "Out of range it is dropped without an error."),
    ("YAML wins over properties",
     "application.yml and application.properties both declaring "
     "spring.datasource.hikari means the properties file is inert. Settings "
     "were being changed in the file that does not win."),
    ("curl normalises paths",
     "A traversal probe written with ../ is collapsed by the client before it "
     "is sent, so a 200 proves nothing. Encode the segments to test properly."),
]

DEPLOY_STEPS = [
    "Run the tests: mvn test in backend, tsc --noEmit and npm run build in web.",
    "Commit, with a message that states the problem being solved.",
    "Build the web bundle and upload it.",
    "Extract into the web container and reload nginx, which is a signal rather "
    "than a restart and does not drop connections.",
    "For a backend change, rebuild the image and replace the container: remove "
    "it and bring it up, rather than --force-recreate, which has been observed "
    "to leave it in Created.",
    "Verify: the site returns 200, the containers are healthy, and the specific "
    "change is present in the served bundle.",
]

body = "".join([
  section(
    h1("1.", "Scope"),
    p("This is the technical document for the PIXOUS HR Portal: how it is "
      "built, how it is deployed, and the conventions a developer joining the "
      "project is expected to follow. It describes the system in production."),
    metrics([("27", "modules"), ("41", "controllers"), ("62", "entities"),
             ("104", "migrations"), ("292", "endpoints")]),
  ),
  section(
    h1("2.", "Architecture"),
    p("A single Spring Boot service behind nginx, with a React single-page "
      "application served as static files by the same nginx, and an Android "
      "application speaking to the same API. Five containers run under Docker "
      "Compose on one host."),
    table(["Container", "Role"],
          [["web", "nginx 1.27.5. Serves the built React bundle and proxies "
                   "/api to the backend. TLS 1.3, HTTP/2, gzip."],
           ["backend", "The Spring Boot application. All business logic."],
           ["mysql", "Local MySQL container. The production data lives in an "
                     "external MySQL 8 instance."],
           ["redis", "Cache, presence and session support."],
           ["analytics", "Reporting workload, kept off the request path."]],
          widths=["16%", "84%"]),
    note("The web tier holds no logic. Everything that decides anything is in "
         "the backend, because the browser and the phone must be governed by "
         "the same rules."),
  ),
  section(
    h1("3.", "Technology Stack"),
    h2("3.1  Backend"),
    table(["Concern", "Choice", "Note"],
          [[b[0], b[1], b[2]] for b in BACKEND_STACK],
          widths=["16%", "24%", "60%"]),
    h2("3.2  Frontend"),
    table(["Concern", "Choice", "Note"],
          [[f[0], f[1], f[2]] for f in FRONTEND_STACK],
          widths=["16%", "28%", "56%"]),
    h2("3.3  Mobile"),
    p("Flutter 3.44 with Riverpod for state and Dio for HTTP, packaged as an "
      "Android application. It calls the same endpoints as the web portal and "
      "is therefore subject to the same server-side rules."),
  ),
  section(
    h1("4.", "Code Structure"),
    p("The backend is organised by module, not by layer: every module owns its "
      "controller, service, repository, entity and DTOs together, so a change "
      "to one feature is a change within one directory."),
    table(["Layer", "File", "Responsibility"],
          [[l[0], l[1], l[2]] for l in LAYERS],
          widths=["14%", "20%", "66%"]),
    h2("4.1  The 27 modules"),
    p("admin, announcement, asset, attendance, audit, auth, calendar, chatbot, "
      "community, complaint, dashboard, expense, file, helpdesk, leave, "
      "notification, onboarding, org, payroll, performance, presence, "
      "requestthread, safety, task, user, wfh, workreport."),
    h2("4.2  Frontend"),
    p("Thirty-nine pages under web/src/pages, with shared components, a single "
      "axios client under web/src/lib, shared validation rules, and hooks that "
      "map a pushed event to the queries it should refresh."),
  ),
  section(
    h1("5.", "Authentication and Authorisation"),
    h2("5.1  Authentication"),
    bullets([
      "Username and password, verified against a BCrypt hash.",
      "On success the server issues an access token and a refresh token, both "
      "valid for four hours.",
      "Both expire together deliberately: a session ends at four hours rather "
      "than extending itself indefinitely.",
      "Repeated failures lock the account for a period.",
      "The session is stateless -- SessionCreationPolicy.STATELESS -- so any "
      "instance can serve any request.",
    ]),
    h2("5.2  Authorisation"),
    p("Two independent checks, and both must pass."),
    bullets([
      "Role, at the controller, with @PreAuthorize and a named authority: "
      "USER_MANAGE, LEAVE_APPROVE, PAYROLL_RUN, DASHBOARD_EXEC, and others.",
      "The specific rule, in the service. The clearest example: a request "
      "names its approver, and only that person may decide it -- a senior role "
      "may see the request but may not act on it.",
    ]),
    diagrams.decide_vs_view(),
    note("This separation is the reason a Team Leader can no longer approve "
         "their own permission, and the reason an administrator cannot quietly "
         "override an approval chain."),
  ),
  section(
    h1("6.", "API"),
    p("292 endpoints across 27 modules, all under /api, all returning a "
      "consistent envelope with the payload under data. Documented at runtime "
      "through OpenAPI annotations."),
    table(["Module", "Base path", "Endpoints"],
          [["Authentication", "/api/auth", "15"],
           ["Users", "/api/users", "25"],
           ["Org", "/api/org, /api/cache", "13"],
           ["Leave and permission", "/api/leave", "27"],
           ["Work from home", "/api/wfh", "9"],
           ["Payroll", "/api/payroll", "27"],
           ["Community and calls", "/api/communities, /api/calls", "29"],
           ["Administration", "/api/admin", "19"],
           ["Tasks", "/api/tasks", "16"],
           ["Attendance", "/api/attendance", "12"],
           ["Work reports", "/api/work-reports", "13"],
           ["Helpdesk", "/api/tickets", "11"],
           ["Assets", "/api/assets", "10"],
           ["Chatbot", "/api/chatbot", "10"],
           ["Remaining 13 modules", "various", "56"]],
          widths=["30%", "44%", "26%"]),
  ),
  section(
    h1("7.", "Business Logic Worth Knowing"),
    h2("7.1  Approval"),
    p("A request carries requestedTo. If it is set, that person and only that "
      "person may decide. There is no administrative override: an override "
      "makes an approval chain advisory, and an advisory chain is not a "
      "chain."),
    h2("7.2  Work from home becomes attendance"),
    p("On approval, attendance rows are written for each working day in the "
      "range. Weekends are skipped, public holidays are skipped, and an "
      "existing row is left alone rather than overwritten. Payroll then reads "
      "attendance as it always did, so an approved WFH day is present and "
      "paid without payroll needing to know that WFH exists."),
    diagrams.wfh_to_pay(),
    h2("7.3  One request per person per day"),
    p("Checked at application time across both leave and permission, with the "
      "conflicting dates named in the message."),
    diagrams.duplicate_guard(),
    h2("7.4  Shared request threads"),
    p("Attachments and comments are keyed on (request_type, request_id), so "
      "leave, permission and work from home share one implementation and one "
      "table. A fourth request type would need no new code here."),
  ),
  section(
    h1("8.", "Database"),
    p("76 tables under 104 forward-only Flyway migrations. A schema change is "
      "always a new migration file; existing files are never edited, because "
      "an edited migration has already run elsewhere."),
    table(["Rule", "Reason"],
          [["Migrations are forward-only",
            "An applied migration cannot be changed retroactively."],
           ["ddl-auto=validate",
            "Hibernate never alters the schema; a mismatch stops startup."],
           ["An entity must match its table exactly",
            "Including column names and nullability."],
           ["Indexes are added in migrations",
            "So a slow query has a versioned remedy."]],
          widths=["36%", "64%"]),
  ),
  section(
    h1("9.", "Realtime, Files and Notifications"),
    h2("9.1  Realtime"),
    p("STOMP over SockJS. The server publishes an event when something is "
      "decided or sent; the client maps the event type to the queries that "
      "should be refreshed, so a list updates without a reload and without "
      "polling."),
    h2("9.2  Files"),
    bullets([
      "Images, PDF and Word documents; ten per request; 10 MB each.",
      "Type and size are checked on the server before anything is stored.",
      "A file may be removed only by the person who uploaded it.",
      "Uploads are multipart and must set their own Content-Type, because the "
      "shared axios client otherwise imposes application/json and destroys the "
      "boundary.",
    ]),
    h2("9.3  Notifications"),
    p("Written to the notification table and pushed on the same event, so the "
      "record and the alert cannot disagree."),
  ),
  section(
    h1("10.", "Security Implementation"),
    table(["Control", "Implementation"],
          [["Transport", "TLS 1.3 with HTTP/2 over ALPN."],
           ["Passwords", "BCrypt."],
           ["Tokens", "JWT, four-hour access and refresh."],
           ["Session", "Stateless; no server-side session to fixate."],
           ["CSRF", "Disabled deliberately -- there is no cookie session to "
                    "forge; the token is sent explicitly."],
           ["CORS", "Explicit configuration source, not a wildcard."],
           ["Headers", "server_tokens off, Permissions-Policy, and a "
                       "Content-Security-Policy in report-only mode."],
           ["Brute force", "Login attempt limiter with lockout."],
           ["Audit", "A dedicated module recording who changed what."],
           ["Authorisation", "Role at the controller and rule in the service."]],
          widths=["18%", "82%"]),
  ),
  section(
    h1("11.", "Error Handling, Logging and Monitoring"),
    bullets([
      "A business rule violation raises a typed exception carrying a message "
      "written for the person reading it, not for the developer.",
      "The response envelope is the same shape whether the call succeeded or "
      "failed, so the client has one path.",
      "Actuator provides health, and the container health check uses it.",
      "Application logs are read from the running container; a setting that "
      "was silently ignored has been found this way more than once.",
    ]),
  ),
  section(
    h1("12.", "Performance"),
    p("Measured rather than assumed. The server answers in 0.45 ms to 9 ms. "
      "End-to-end from India is around 620 ms, of which roughly 98 per cent is "
      "network distance to the Paris region and to a database about 125 ms "
      "away."),
    bullets([
      "The largest available improvement is geographic, not algorithmic.",
      "gzip and HTTP/2 at the edge; the bundle is code-split by route.",
      "Redis absorbs repeated lookups.",
      "Heavy reporting runs in its own container, off the request path.",
    ]),
    note("This is why optimisation work has concentrated on payload size and "
         "round-trip count rather than on server processing: there is little "
         "server time left to remove."),
  ),
  section(
    h1("13.", "Development Standards"),
    table(["Standard", "What it means in practice"],
          [[c[0], c[1]] for c in CONVENTIONS],
          widths=["30%", "70%"]),
  ),
  section(
    h1("14.", "Pitfalls Already Encountered"),
    p("Each of these cost real time and is easy to repeat."),
    table(["Pitfall", "What happens and what to do"],
          [[p0, p1] for p0, p1 in PITFALLS],
          widths=["28%", "72%"]),
  ),
  section(
    h1("15.", "Version Control"),
    bullets([
      "Work on main for this project, with each change committed separately.",
      "A commit subject names the change; the body states the problem.",
      "The test suite must pass before committing: 61 backend tests, plus the "
      "web typecheck and build.",
      "Generated artefacts and build caches are not committed.",
    ]),
  ),
  section(
    h1("16.", "Deployment"),
    p("Production is a single EC2 host in the Paris region, reached over SSH, "
      "running five containers under Docker Compose."),
    bullets(DEPLOY_STEPS),
    finding("A deployment lesson paid for once",
            "docker compose up --force-recreate has twice left a container in "
            "Created rather than Running, taking the site down for about a "
            "minute. Removing the container and bringing it up again is the "
            "sequence that works."),
  ),
  section(
    h1("17.", "Environment Configuration"),
    table(["Setting", "Where", "Note"],
          [["Database URL and credentials", "Environment variables",
            "Never in the repository."],
           ["ACCESS_TOKEN_TTL_SECONDS", "Environment, default 14400",
            "Four hours."],
           ["REFRESH_TOKEN_TTL_SECONDS", "Environment, default 14400",
            "Expires with the access token by design."],
           ["Hikari pool", "application.yml",
            "The yml wins over the properties file; change it there."],
           ["nginx", "web/nginx.conf", "TLS, headers, gzip, proxy rules."]],
          widths=["28%", "30%", "42%"]),
  ),
  section(
    h1("18.", "Maintenance and Support"),
    bullets([
      "Check container health and the site before and after any change.",
      "Read the application log when a setting appears to have no effect; "
      "several have been silently ignored.",
      "Keep Flyway forward-only, whatever the temptation.",
      "Prefer adding an endpoint to changing one that already has callers.",
    ]),
    finding("Outstanding operational risk -- backup",
            "The database backup is manual, unscheduled, and stored as a "
            "single copy on the same server as the database. This is the "
            "largest risk to the system. It is an operational gap rather than "
            "a defect, and it should be closed with a scheduled off-server "
            "backup before any further migration work."),
  ),
  section(
    h1("19.", "Module Implementation Notes"),
    p("What a developer needs to know before changing each area. These are the "
      "things that are not obvious from reading the code alone."),

    h2("19.1  auth"),
    bullets([
      "JwtService signs and verifies; JwtAuthenticationFilter puts the "
      "authenticated user on the security context for every request.",
      "SecurityUtils.currentUserId() is how a service learns who is calling. "
      "Never take a user id from the request body.",
      "Token lifetimes come from environment variables with a four-hour "
      "default, and the refresh token expires with the access token on "
      "purpose.",
      "LoginAttemptLimiter holds failures; it is in-memory, so it resets when "
      "the container restarts.",
    ]),

    h2("19.2  leave and permission"),
    bullets([
      "LeaveService.canApproveLeave is the rule: if requestedTo is set, only "
      "that person may decide. There is no administrative override, and adding "
      "one would make the whole chain advisory.",
      "PermissionService enforces the same thing separately, because a "
      "permission is a different entity; a fix to one is not a fix to both.",
      "The duplicate guard checks leave and permission together, since a "
      "person cannot be twice absent on one day.",
      "WorkCalendar.isWeekend is shared with WFH and attendance. Changing what "
      "counts as a weekend changes three modules at once.",
    ]),

    h2("19.3  wfh"),
    bullets([
      "markAttendance runs on approval and is deliberately forgiving: it skips "
      "weekends, skips holidays, skips a day that already has attendance, and "
      "swallows its own failure rather than undoing an approval that has "
      "already succeeded.",
      "That last decision is a trade: an approval never fails because of "
      "attendance, but a failure is silent, so attendance is worth checking "
      "after an approval in testing.",
      "/wfh/active-range was added beside /wfh/active rather than replacing "
      "it, because the single-day route already had callers.",
    ]),

    h2("19.4  community and chat"),
    bullets([
      "Visibility is one expression: a group is seen if it is direct, or an "
      "announcement channel, or the caller is a member. An announcement "
      "channel therefore needs no membership at all.",
      "canAnnounce decides who may post: SUPER_ADMIN, COMPANY_ADMIN, IT_HR, "
      "IT_MGR, and the company head by employee code.",
      "Team rooms are excluded from the chat list because they are reached "
      "from the Teams page.",
      "/api/communities/diagnose reports, for the caller, why each group is "
      "kept or dropped. It is read-only and exists because guessing at a "
      "working page is worse than asking it.",
    ]),

    h2("19.5  requestthread"),
    p("Attachments and comments are keyed on (request_type, request_id). "
      "Leave, permission and work from home therefore share one table and one "
      "implementation, and a fourth request type would need no new code here "
      "at all."),

    h2("19.6  payroll"),
    bullets([
      "Payroll reads attendance and knows nothing about work from home. That "
      "is the point: WFH writes attendance, so payroll needed no change.",
      "Prepare, approve and release are separate endpoints so that no single "
      "action both computes and publishes pay.",
    ]),
  ),

  section(
    h1("20.", "Frontend Implementation Notes"),
    h2("20.1  Server state"),
    p("TanStack Query holds anything that came from the server. It is not a "
      "general state store, and copying server data into component state is "
      "how two parts of a page come to disagree."),
    bullets([
      "A mutation invalidates the queries its change affects, rather than "
      "editing the cache by hand.",
      "A pushed event maps to the queries it should refresh, in "
      "useNotifications. This mapping is what makes the application live; "
      "without it the socket delivers events nothing acts on.",
      "Where a filter can be applied to rows already fetched, it is, rather "
      "than issuing another request -- the Assigned to me tab is filtered "
      "client-side for this reason.",
    ]),
    h2("20.2  The shared axios client"),
    bullets([
      "It attaches the access token, and refreshes it once on a 401 before "
      "giving up.",
      "It sets Content-Type: application/json globally, which is correct for "
      "almost every call and wrong for every upload. A multipart call must set "
      "its own header or the boundary is destroyed.",
      "It unwraps the response envelope, so pages work with data rather than "
      "with the wrapper.",
    ]),
    h2("20.3  Forms and validation"),
    p("react-hook-form with rules shared from web/src/lib/validation.ts, so "
      "that two forms asking for an email agree on what an email is. The "
      "server validates independently in every case."),
    h2("20.4  Naming across the boundary"),
    p("The JSON name is not always the Java field name. Lombok renames boolean "
      "accessors, so a field isAnnouncement travels as announcement. When a "
      "value is silently ignored, compare the wire name against the accessor "
      "name before looking anywhere else."),
  ),

  section(
    h1("21.", "Adding a Feature End to End"),
    p("The order below is the one that produces the fewest surprises, and it "
      "is the order actually used for the work-from-home module."),
    table(["Step", "What to do"],
          [["1. Migration", "Add a new Flyway file. Never edit an applied "
            "one."],
           ["2. Entity", "Map it exactly -- names and nullability -- or the "
            "application will refuse to start."],
           ["3. Repository", "Derived queries where they read clearly, @Query "
            "where they do not."],
           ["4. Service", "The rules. Everything that decides whether an "
            "action is permitted on grounds other than role."],
           ["5. DTOs", "What crosses the boundary. Entities are never "
            "returned directly."],
           ["6. Controller", "@PreAuthorize for the role, then delegate. No "
            "logic here."],
           ["7. Test", "The rules, not the plumbing."],
           ["8. Frontend", "Query and mutation, then the page."],
           ["9. Live refresh", "Map the pushed event to the queries it "
            "affects, or the page will not update by itself."],
           ["10. Verify", "Against production after deploying: the site "
            "answers, and the specific change is present."]],
          widths=["18%", "82%"]),
  ),

  section(
    h1("22.", "Debugging Practices That Have Paid Off"),
    table(["Practice", "Why it matters"],
          [["Read the running log before changing anything",
            "Two settings were found to be silently ignored this way; neither "
            "produced an error."],
           ["Prove the server is right before fixing the page",
            "A leave list appeared broken; the API was correct and the page "
            "date range was wrong. The fix was one line, in the other file."],
           ["Compare the wire name with the accessor name",
            "The announcement flag was dropped by Jackson because Lombok had "
            "renamed the property."],
           ["Check the rendered output, not the source",
            "A diagram read correctly in code and drew the wrong arrow."],
           ["Ask the system rather than guess",
            "A read-only diagnostic endpoint answered in one call what "
            "several hours of reading had not."],
           ["Verify after deploying, not before",
            "A change that passes every test and is not correctly deployed "
            "is not delivered."]],
          widths=["34%", "66%"]),
  ),

  section(
    h1("23.", "Operational Runbook"),
    h2("23.1  Routine checks"),
    bullets([
      "Containers: five, all running, backend healthy.",
      "The site returns 200 and a real login returns a token.",
      "Flyway is at the expected version with no validation errors.",
    ]),
    h2("23.2  Common situations"),
    table(["Situation", "First thing to do"],
          [["The site is unreachable after a deployment",
            "Check whether the container is in Created rather than Running. "
            "Remove it and bring it up again."],
           ["A setting appears to have no effect",
            "Read the log. Check whether application.yml is overriding the "
            "properties file."],
           ["The application will not start after a schema change",
            "ddl-auto=validate has found a mismatch. The message names the "
            "column."],
           ["A page does not update by itself",
            "The event is probably arriving but is not mapped to a query."],
           ["An upload fails",
            "Check that the call sets its own multipart Content-Type."]],
          widths=["36%", "64%"]),
    h2("23.3  What not to do"),
    bullets([
      "Do not edit an applied migration.",
      "Do not add an administrative override to an approval rule.",
      "Do not change an endpoint that has callers when adding one will do.",
      "Do not enforce a rule only in the browser.",
      "Do not deploy without verifying afterwards.",
    ]),
  ),
])


html = document(
    "Application Development Document",
    "PIXOUS HR Portal",
    "How the application is built, deployed and maintained -- written from the "
    "repository and the running production system.",
    body)

out = os.path.join(os.path.expanduser("~"), "Downloads",
                   "Pixous_HR_Portal_Development_Document_v1.0.pdf")
print(to_pdf(html, out))
