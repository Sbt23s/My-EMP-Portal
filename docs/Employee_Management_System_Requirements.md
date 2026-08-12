# Employee Management System — Software Requirements Specification

**Product:** Employee Management System (EMS) — Pixous HR Portal
**Document type:** Software Requirements Specification (SRS)
**Version:** 1.0
**Status:** Baseline — derived from the implemented source code
**Date:** 30 July 2026

---

## Table of Contents

**Part I — Introduction and Overview**
1. [Introduction](#1-introduction)
2. [Overall Description](#2-overall-description)
3. [External Interface Requirements](#3-external-interface-requirements)
4. [Users, Roles and Access Control](#4-users-roles-and-access-control)

**Part II — Functional Requirements**
5. [Authentication and Session Management](#5-authentication-and-session-management)
6. [Employee Management](#6-employee-management)
7. [Organisation and Master Data](#7-organisation-and-master-data)
8. [Attendance Management](#8-attendance-management)
9. [Leave Management](#9-leave-management)
10. [Hourly Permission Requests](#10-hourly-permission-requests)
11. [Payroll and Payslips](#11-payroll-and-payslips)
12. [Task Management](#12-task-management)
13. [Work Reports](#13-work-reports)
14. [Travel and Expense Claims](#14-travel-and-expense-claims)
15. [Asset Management](#15-asset-management)
16. [Support Helpdesk](#16-support-helpdesk)
17. [Complaints and Needs](#17-complaints-and-needs)
18. [Safety Incident Management](#18-safety-incident-management)
19. [Onboarding and Offboarding](#19-onboarding-and-offboarding)
20. [Performance Management](#20-performance-management)
21. [Dashboards and Analytics](#21-dashboards-and-analytics)
22. [Notifications and Alerts](#22-notifications-and-alerts)
23. [Internal Communication](#23-internal-communication)
24. [AI Assistant](#24-ai-assistant)
25. [Reports and Data Export](#25-reports-and-data-export)
26. [Calendar and Holidays](#26-calendar-and-holidays)
27. [File Storage and Documents](#27-file-storage-and-documents)
28. [System Settings](#28-system-settings)
29. [Mobile Application](#29-mobile-application)
30. [Biometric and Document Intelligence Service](#30-biometric-and-document-intelligence-service)

**Part III — Data, Rules and Quality**
31. [Data Requirements](#31-data-requirements)
32. [Consolidated Business Rules](#32-consolidated-business-rules)
33. [Non-Functional Requirements](#33-non-functional-requirements)
34. [Deployment and Operations](#34-deployment-and-operations)
35. [Constraints, Assumptions and Known Limitations](#35-constraints-assumptions-and-known-limitations)
36. [Traceability Matrix](#36-traceability-matrix)
37. [Appendices](#37-appendices)

---
---

# Part I — Introduction and Overview

## 1. Introduction

### 1.1 Purpose

This document specifies the complete functional and non-functional requirements of the **Employee Management System (EMS)** — a multi-role, multi-division human-resources platform that manages the full employee lifecycle from joining to exit.

The specification describes **what the system does and must continue to do**: the actors it serves, the data it holds, the rules it enforces, the interfaces it exposes, and the quality attributes it must satisfy. It is written to be authoritative for development, maintenance, enhancement, contract acceptance, and operational hand-over.

### 1.2 Product Scope

The EMS is a single platform serving an organisation that operates two business divisions:

| Division | Internal code | Displayed as | Workforce character |
|---|---|---|---|
| Information Technology / Digital | `IT` | Digital | Desk-based staff at fixed office locations |
| Civil / Infrastructure | `CIVIL` | Infra | Field and site-based staff across project sites |

Both divisions share one deployment, one identity store, one permission model and one database, with division-aware behaviour where the two differ (site-based versus office-based attendance, machinery versus IT assets, site safety reporting).

**In scope.** Identity and access control; employee records and documents; organisation master data; GPS-validated attendance; leave entitlements, applications and multi-level approvals; hourly permission requests; salary structures, payslip generation, payslip request workflow and payroll runs; task assignment and progress tracking; daily work reporting; travel/expense claims; asset registry, allocation and QR tagging; a support helpdesk with SLA and satisfaction rating; complaints and needs; safety incidents; onboarding checklists and offboarding records; performance goals and reviews; role-aware dashboards; real-time notifications with SMS fan-out; internal group and direct messaging; a multilingual AI assistant; spreadsheet exports; the holiday calendar; system settings; a companion mobile application; and a biometric/document-intelligence side service.

**Out of scope.** Recruitment and applicant tracking; a learning-management system; statutory e-filing and tax return submission; general ledger and accounts integration; multi-tenant or multi-company isolation; biometric hardware device integration; and full offline operation.

### 1.3 Definitions, Acronyms and Abbreviations

| Term | Meaning |
|---|---|
| **EMS** | Employee Management System — this product |
| **RBAC** | Role-Based Access Control |
| **JWT** | JSON Web Token — the bearer credential for API calls |
| **Access token** | Short-lived signed token proving identity on each request |
| **Refresh token** | Opaque, database-backed token exchanged for a new access token |
| **Permission** | A named, atomic capability (e.g. `LEAVE_APPROVE`) checked at the endpoint |
| **Role** | A named bundle of permissions assigned to a user (e.g. `IT_HR`) |
| **Team** | A group of employees sharing the same designation title |
| **Designation title** | Free-text team/job name carried on the employee record; the primary team key |
| **TL** | Team Leader (role `IT_TL`) |
| **Geofence** | A circular boundary (centre coordinate + radius in metres) around an office or site |
| **Punch** | A single attendance event — punch-in or punch-out |
| **WFH** | Work From Home attendance mode |
| **LOP** | Loss of Pay — an unpaid absence day that reduces net pay |
| **Permission request** | An hours-wise short absence during a working day (distinct from an RBAC permission) |
| **TA claim** | Travel Allowance / travel-and-conveyance expense claim |
| **SLA** | Service Level Agreement — the due-by time for a support ticket |
| **PF / ESI / PT / TDS** | Provident Fund / Employees' State Insurance / Professional Tax / Tax Deducted at Source |
| **Payslip request** | An employee-initiated request that results in an admin-generated payslip |
| **Payroll run** | A batch generation of payslips for one month, moving through a status workflow |
| **Announcement channel** | A broadcast messaging group every employee automatically belongs to |
| **Direct room** | A hidden two-member messaging group backing a one-to-one conversation |
| **STOMP** | Simple Text Oriented Messaging Protocol, used over WebSocket for live push |
| **Master data** | Reference lists (departments, designations, shifts, sites, blood groups, …) |
| **Vault copy** | A reversibly-encrypted copy of a password, readable by HR and administrators |

### 1.4 Document Conventions and Requirement Identifiers

- **"shall"** denotes a mandatory requirement.
- **"should"** denotes a recommended requirement.
- **"may"** denotes an optional or permitted behaviour.
- Identifiers are stable and must not be renumbered; superseded requirements are marked, never deleted.

| Prefix | Category | Example |
|---|---|---|
| `FR-<MOD>-nnn` | Functional requirement, per module | `FR-ATT-004` |
| `NFR-<CAT>-nnn` | Non-functional requirement | `NFR-SEC-003` |
| `BR-nnn` | Business rule | `BR-021` |
| `DR-nnn` | Data requirement | `DR-007` |
| `IR-nnn` | Interface requirement | `IR-005` |
| `OR-nnn` | Operational requirement | `OR-004` |
| `CN-nnn` | Constraint | `CN-002` |
| `AS-nnn` | Assumption | `AS-003` |
| `LM-nnn` | Known limitation | `LM-005` |

Module codes used in functional identifiers:

`AUTH` authentication · `EMP` employee management · `ORG` organisation master data · `ATT` attendance · `LV` leave · `PRM` hourly permissions · `PAY` payroll · `TSK` tasks · `WRP` work reports · `CLM` travel & expense claims · `AST` assets · `HD` helpdesk · `CMP` complaints & needs · `SAF` safety · `ONB` onboarding & offboarding · `PRF` performance · `DSH` dashboards · `NOT` notifications · `CHT` communication · `BOT` AI assistant · `RPT` reports · `CAL` calendar · `FIL` files · `SET` settings · `MOB` mobile · `BIO` biometric service

### 1.5 Intended Audience and Reading Order

| Audience | Suggested reading |
|---|---|
| Business sponsors, HR leadership | §1, §2, §4, §5–§30 headings, §33 |
| Solution architects | §2, §3, §31, §33, §34 |
| Developers | §3 onwards in full, especially §32 and §37 |
| Operations and support | §3.3–§3.5, §28, §33, §34, §37 |
| Compliance and audit | §4, §31.5, §32, §33.3 |
| Acceptance reviewers | §5–§32, §36 |

### 1.6 References

| Ref | Document / artefact |
|---|---|
| R1 | Application source repository (backend, web, mobile, analytics service) |
| R2 | Database migration set — the authoritative schema definition |
| R3 | OpenAPI / Swagger UI published by the running API at `/swagger-ui.html` |
| R4 | Architecture notes and setup guide under `docs/` |
| R5 | Deployment guide, container composition and deployment scripts |
| R6 | Requirements analysis and traceability notes under `docs/` |

---

## 2. Overall Description

### 2.1 Product Perspective

The EMS replaces a legacy PHP-based employee API and consolidates several manual, spreadsheet-driven HR processes into one system of record. It deliberately preserves the shape of a number of legacy contracts so that existing integrations and habits carry over:

- master data is served by a single **dropdown-by-type** endpoint;
- bank details follow an **add / update / delete / view** shape;
- payslips are composed of the same **basic / HRA / allowances / deductions** components;
- profile and photo updates map one-to-one onto the legacy self-service calls.

On top of that foundation the system layers attendance, leave, tasks, claims, assets, helpdesk, complaints, safety, messaging and dashboards.

### 2.2 System Context

```
                      ┌──────────────────────────────────────────────┐
   Employees          │            EMS Web Application               │
   Team Leaders  ────▶│   Browser SPA · responsive · light/dark      │
   HR / Admins        └──────────────────┬───────────────────────────┘
                                         │ HTTPS  REST + WebSocket
   Employees          ┌──────────────────▼───────────────────────────┐
   (on the move) ────▶│         EMS Mobile Application               │
                      │   Dashboard · GPS punch · Leave · Payslips   │
                      └──────────────────┬───────────────────────────┘
                                         │
                      ┌──────────────────▼───────────────────────────┐
                      │              EMS API Service                 │
                      │  Auth/RBAC · 20+ functional modules · jobs   │
                      └──┬────────┬────────┬────────┬────────┬───────┘
                         │        │        │        │        │
              ┌──────────▼─┐ ┌────▼───┐ ┌──▼─────┐ ┌▼──────┐ ┌▼─────────────┐
              │ Relational │ │ Object │ │  SMS   │ │ SMTP  │ │  Biometric / │
              │  Database  │ │ Store  │ │Gateway │ │ Mail  │ │  OCR service │
              └────────────┘ └────────┘ └────────┘ └───────┘ └──────────────┘
                         │        │
              ┌──────────▼─┐ ┌────▼─────────────────────────────────┐
              │   Cache    │ │  AI providers: chat · speech · crawl │
              └────────────┘ └──────────────────────────────────────┘
```

**IR-001** The system shall present exactly one public network entry point in production; all internal services shall be unreachable from outside the deployment network.

**IR-002** The web application, the mobile application and any future client shall consume the same versioned REST API and the same response envelope.

### 2.3 Product Functions Summary

| # | Capability | Primary actors |
|---|---|---|
| 1 | Username/password sign-in, token refresh, self password change, account lockout | All users |
| 2 | Employee creation (single and bulk), profile maintenance, credential reset, document storage, deletion | HR, Admin |
| 3 | Master data: departments, designations/teams, positions, employment statuses, blood groups, office locations, shifts, sites, holidays | HR, Admin |
| 4 | GPS/geofence attendance punch, WFH and site modes, lateness and overtime, monthly summary, team and organisation views, absentee list | All users |
| 5 | Leave types and policies, annual balance allocation, application with quota/notice/gender/quarterly checks, single-level approval routing, cancellation, calendar | All users |
| 6 | Hourly permission requests with single-level routing and overdue protection | All users |
| 7 | Salary structures, statutory computation, PDF payslips, payslip request/approve workflow, monthly payroll runs with finance approval | Admin, HR, Finance |
| 8 | Task assignment with scope rules, priority, forward-only progress, export | HR, Team Leaders, Employees |
| 9 | Daily work reports with team and organisation roll-ups and export | All users |
| 10 | Travel/expense claims with kilometre and fare breakdown, attachments, approval | All users, HR |
| 11 | Asset registry with quantity, QR tags, allocation, acknowledgement and return | Asset managers, HR |
| 12 | Support tickets with SLA, ordered status workflow, threaded comments, rating | All users, HR agents |
| 13 | Complaints and needs with reference codes and HR response | All users, HR |
| 14 | Safety incident reporting with optional anonymity and investigation workflow | All users, safety staff |
| 15 | Onboarding checklists and offboarding records | HR, Admin |
| 16 | Performance goals and appraisal reviews | Employees, Managers |
| 17 | Personal dashboard, executive dashboard, celebrations panel | All users, Executives |
| 18 | Persistent in-app notifications, live push, SMS fan-out, scheduled celebration alerts | System |
| 19 | Group channels, announcement broadcast, direct messages, team rooms, voice notes, attachments, call signalling | All users |
| 20 | Multilingual AI assistant with knowledge ingestion, translation, speech synthesis and transcription | All users, Admin |
| 21 | Attendance, leave and payroll spreadsheet exports | Report viewers |
| 22 | Holiday calendar with organisation-wide notification | HR, Admin |
| 23 | Configurable system settings and provider credentials | Admin |

### 2.4 Operating Environment

**Server side**

| Element | Requirement |
|---|---|
| Runtime | Java 17 or later, Spring Boot 3.5.x application server |
| Database | MySQL 8.4 (InnoDB, `utf8mb4`) |
| Cache | Redis (optional at runtime; absence shall not affect health) |
| Event streaming | Kafka (optional; used for chat event distribution) |
| Mail | SMTP relay (optional at runtime) |
| Side service | Python 3 FastAPI service for face matching and OCR (optional profile) |
| Reverse proxy | Nginx terminating public traffic and routing to internal services |
| Container platform | Docker with Compose orchestration on a single host |
| Server time zone | `Asia/Kolkata` for all containers that record or render timestamps |

**Client side**

| Element | Requirement |
|---|---|
| Web | Current versions of Chrome, Edge, Firefox and Safari |
| Web features used | Fetch/XHR, WebSocket, `localStorage`, Geolocation, MediaDevices (camera/microphone), Web Speech synthesis |
| Viewport | Usable from 320 px width upwards through desktop |
| Mobile | Android and iOS via a React Native (Expo) application |
| Mobile features used | Secure credential storage, foreground location |

### 2.5 Design and Implementation Constraints

**CN-001** The relational schema shall be owned exclusively by versioned forward-only migrations; the ORM shall validate against the schema and shall never create or alter it.

**CN-002** Every migration shall be idempotent and safe to apply to both an empty database and a populated production database.

**CN-003** All API responses shall use a single uniform envelope, and all paginated responses a single uniform page wrapper.

**CN-004** Authorisation shall be expressed declaratively at the endpoint using permission or role expressions, never by ad-hoc checks scattered through presentation code.

**CN-005** The API shall be stateless; no server-side HTTP session shall be created.

**CN-006** No secret — signing key, database password, SMS key, AI provider key — shall be committed to source control; all shall be supplied by environment variables or database-held settings.

**CN-007** The token signing secret shall have no fallback default outside the development profile; the application shall refuse to start without it.

**CN-008** Configurable operational values (geofence radius, office hours, lateness grace, lock thresholds, SLA windows, accrual caps) shall be held in configuration or master data, never hard-coded in business logic.

**CN-009** Money shall be represented and computed as fixed-point decimal values scaled to two places with half-up rounding; floating-point arithmetic shall not be used for monetary results.

**CN-010** Geographic coordinates shall be stored with seven decimal places of precision.

**CN-011** Uploaded content shall never be executed or interpreted; stored files shall be served as opaque byte streams.

### 2.6 Assumptions and Dependencies

**AS-001** The organisation observes a single weekly off on **Sunday**; **Saturday is a working day**.
**AS-002** Standard office hours are **09:00 to 18:00** local time unless a shift overrides the start.
**AS-003** Currency is Indian Rupees; statutory deductions follow Indian norms.
**AS-004** Mobile numbers are Indian ten-digit numbers with country code `+91`.
**AS-005** Employees possess a device capable of supplying location for field attendance; absence of location shall degrade gracefully rather than block a punch.
**AS-006** SMS delivery is best-effort and depends on an external gateway; failure shall never block the originating business action.
**AS-007** AI assistant capability is optional and depends on externally supplied provider credentials.
**AS-008** Only one organisation is served per deployment.

### 2.7 User Documentation

**FR-DOC-001** The system shall publish live, interactive API documentation for every endpoint, grouped by functional area, with request and response schemas.
**FR-DOC-002** The repository shall carry a setup guide, an architecture description, a deployment guide and this specification.
**FR-DOC-003** Screens that accept bulk uploads shall display the exact expected workbook layout and shall offer a downloadable blank template that matches the parser.

---

## 3. External Interface Requirements

### 3.1 User Interface Requirements

**FR-UI-001** The web application shall present a persistent left navigation sidebar, a top bar carrying the notification bell, theme toggle and account menu, and a routed content area.

**FR-UI-002** Navigation entries shall be filtered by the signed-in user's permissions and roles. An entry the user cannot use shall not be rendered.

**FR-UI-003** Navigation shall support collapsible groups. Leave Management and Payroll shall be groups, expanded automatically when the current route falls inside them.

**FR-UI-004** The following navigation structure shall be provided:

| Entry | Route | Visibility |
|---|---|---|
| Dashboard | `/` | All |
| Employees | `/employees` | `USER_MANAGE` or `ATTENDANCE_TEAM` or `REPORT_VIEW`; hidden from Team Leaders |
| Attendance | `/attendance` | All except Super Admin |
| Employee Attendance / Team Attendance | `/team-attendance` | `ATTENDANCE_TEAM`; labelled "Team Attendance" for Team Leaders, "Employee Attendance" otherwise |
| Leave Management → Leave | `/leave` | All except Super Admin |
| Leave Management → Permission | `/leave/permissions` | All |
| Leave Management → Approvals | `/leave/approvals` | `LEAVE_APPROVE` |
| Leave Management → Leave Policies | `/leave/policies` | `ORG_MANAGE` |
| Payroll → Payroll | `/payroll/requests` | `PAYROLL_RUN` |
| Payroll → Payslips | `/payslips` | All except Super Admin, HR roles |
| Payroll Runs | `/payroll/run` | `PAYROLL_RUN` or `PAYROLL_APPROVE` |
| Work Reports | `/work-reports` | All |
| Tasks | `/tasks` | All |
| Teams | `/teams` | All — all-teams view for `USER_MANAGE`/`IT_MGR`, own-team view otherwise |
| Documents | `/documents` | `ORG_MANAGE`; hidden from Super Admin |
| Calendar | `/calendar` | All |
| Claims | `/ta-expenses` | All |
| Assets | `/assets` | All |
| Supports | `/helpdesk` | All |
| Complaints | `/complaints` | All |
| Reports | `/reports` | `REPORT_VIEW`; hidden from Super Admin |
| Communities | `/communities` | `ORG_MANAGE` |
| Chat | `/chat` | All |
| Onboarding | `/onboarding` | `USER_MANAGE` |
| AI Assistant Settings | `/admin/ai-assistant` | `USER_MANAGE` |
| Notifications | `/notifications` | All |
| Profile | `/profile` | All |

**FR-UI-005** Unauthenticated access to any protected route shall redirect to the sign-in screen. An authenticated user reaching a route their permissions exclude shall be shown an access-denied result rather than a blank screen.

**FR-UI-006** The interface shall offer light and dark themes, shall remember the chosen theme across sessions, and shall render legibly in both.

**FR-UI-007** Every data grid shall support pagination, and where the underlying endpoint supports it, text search and filtering.

**FR-UI-008** Every asynchronous action shall present a loading state, and shall present success and failure feedback as a transient notification carrying the server's message.

**FR-UI-009** Role labels displayed on screen shall be humanised. Specifically, the stored code `IT_MGR` shall be displayed as `IT_HR` wherever a raw role code is shown, and the account role shown beside the user's name shall use the friendly names: Board Admin, Super Admin, HR Head, HR, Team Leader, Employee, Site Supervisor, Field Employee.

**FR-UI-010** The notification bell shall show an unread count, shall list recent notifications with a per-type icon and colour, shall allow marking one or all as read, and shall navigate to the notification's target route when selected.

**FR-UI-011** An AI assistant launcher shall be available from every authenticated screen.

**FR-UI-012** Empty result sets shall render an explanatory empty state rather than an empty table.

**FR-UI-013** Screens presenting images shall offer a full-screen lightbox view.

### 3.2 Hardware Interface Requirements

**IR-003** The client shall request device geolocation for attendance punches. If permission is refused or unavailable, the punch shall still be accepted and shall be recorded as outside the geofence with an exception flag.

**IR-004** The client shall be able to capture still images from a device camera for face-based verification and document capture, and audio from a device microphone for voice messages and speech input.

**IR-005** Generated asset QR codes shall be renderable at a size suitable for physical label printing and scannable by commodity scanners.

### 3.3 Software Interface Requirements

| Ref | Interface | Direction | Purpose | Failure behaviour |
|---|---|---|---|---|
| IR-006 | MySQL 8.4 | Outbound | System of record | Fatal — application unhealthy |
| IR-007 | Redis | Outbound | Cache | Non-fatal; excluded from health |
| IR-008 | Kafka | Outbound/inbound | Chat event publication and consumption | Non-fatal; direct delivery continues |
| IR-009 | SMTP | Outbound | E-mail delivery | Non-fatal; excluded from health |
| IR-010 | Primary SMS gateway | Outbound | Transactional SMS on the quick route for ten-digit Indian numbers | Logged and skipped |
| IR-011 | Secondary SMS gateway | Outbound | Fallback SMS to E.164 numbers | Logged and skipped |
| IR-012 | LLM chat providers | Outbound | AI assistant conversation | Assistant returns a graceful message |
| IR-013 | Speech-to-text provider | Outbound | Voice input transcription | Feature reported unavailable |
| IR-014 | Text-to-speech provider | Outbound | Spoken assistant replies | Feature reported unavailable |
| IR-015 | Web crawl provider | Outbound | Ingest a website into the assistant knowledge base | Ingestion fails with a message |
| IR-016 | Biometric/OCR service | Outbound | Face enrolment and verification, numeric OCR, analytics | Feature reported unavailable |
| IR-017 | Object/file storage | Outbound | Documents, photos, payslip PDFs, QR images, attachments | Fatal for the affected operation only |

**IR-018** SMS dispatch shall be asynchronous and shall never propagate an exception into the calling business transaction.

**IR-019** The primary SMS gateway shall be used whenever it is enabled and holds a key; otherwise the secondary gateway shall be used; if neither is configured the attempt shall be logged and skipped.

**IR-020** AI provider credentials shall be stored server-side and shall never be returned to a client in full; any display shall be masked to a short prefix and suffix.

### 3.4 Communication Interface Requirements

**IR-021** All client–server traffic shall use HTTP over TLS in production.

**IR-022** The API shall be rooted at `/api` and shall accept and return JSON, except for file downloads and uploads.

**IR-023** Cross-origin access shall be restricted to an explicitly configured list of allowed web origins.

**IR-024** A STOMP-over-WebSocket endpoint shall be exposed at `/ws` with SockJS fallback, brokering `/topic` and `/queue` destinations, accepting client sends under `/app` and resolving user destinations under `/user`.

**IR-025** The server shall push each notification to both the recipient's user-scoped queue and a per-user public topic, so that clients subscribing either way receive it.

**IR-026** File uploads shall accept up to **25 MB per file** and **60 MB per request**; the reverse proxy shall permit the same.

### 3.5 API Conventions

**IR-027** Every response shall be wrapped as:

```json
{
  "success":   true,
  "message":   "OK",
  "data":      { },
  "errors":    null,
  "timestamp": "2026-07-30T10:15:30Z"
}
```

Null members shall be omitted from serialisation.

**IR-028** Every paginated response shall carry `content`, `page`, `size`, `totalElements`, `totalPages` and `last`.

**IR-029** Errors shall be mapped from canonical error codes to HTTP statuses:

| Error code | HTTP status |
|---|---|
| `VALIDATION_ERROR` | 400 Bad Request |
| `BAD_CREDENTIALS` | 401 Unauthorized |
| `UNAUTHENTICATED` | 401 Unauthorized |
| `TOKEN_EXPIRED` | 401 Unauthorized |
| `ACCOUNT_LOCKED` | 423 Locked |
| `ACCESS_DENIED` | 403 Forbidden |
| `NOT_FOUND` | 404 Not Found |
| `CONFLICT` | 409 Conflict |
| `GEOFENCE_VIOLATION` | 422 Unprocessable Entity |
| `BUSINESS_RULE` | 422 Unprocessable Entity |
| `INTERNAL` | 500 Internal Server Error |

**IR-030** A request with no credentials or an expired token shall receive **401** with a message directing the user to sign in again. A request from an authenticated user lacking the required permission shall receive **403**. The two shall never be conflated, because clients treat 401 as "refresh and retry" and 403 as "not allowed".

**IR-031** Field validation failures shall return 400 with a map of field name to message.

**IR-032** Unhandled exceptions shall return 500 with a generic message; internal detail shall be logged, not returned.

**IR-033** The following paths shall be publicly reachable without a token: the root path, all `/api/auth/**` endpoints, `/api/files/**`, the API documentation paths, the WebSocket handshake, the health endpoint and the error path. Every other path shall require authentication.

**IR-034** A health endpoint shall be exposed at `/actuator/health` reporting database reachability, without detail disclosure, and excluding optional cache and mail indicators.

---

## 4. Users, Roles and Access Control

### 4.1 User Classes

| Class | Roles | Characteristics |
|---|---|---|
| **Employee** | `IT_EMP`, `CV_EMP` | Self-service only: own attendance, leave, permissions, payslips, tasks, work reports, claims, assets, tickets, complaints, chat |
| **Team Leader** | `IT_TL` | Employee capabilities plus own-team attendance, short-leave and small-leave approval, task assignment within team, team claim and work-report visibility |
| **HR** | `IT_MGR`, `IT_HR`, `CV_HR` | Employee lifecycle, teams, leave and claim decisions, payroll requests, helpdesk agency, complaints, assets, calendar, task assignment to leaders, organisation-wide visibility |
| **Supervisor** | `CV_SUP` | Site leave approval, site attendance, site reporting |
| **Finance** | `IT_FIN` | Payroll run approval and financial reporting |
| **Executive** | `IT_CEO` | Executive dashboard and reports |
| **Asset Manager** | `IT_AST`, `CV_AST` | Asset lifecycle and helpdesk agency |
| **Facilities Admin** | `CV_ADM` | Sites, facilities, assets, helpdesk agency, master data |
| **Administrator** | `SUPER_ADMIN`, `BOARD_ADMIN` | Full configuration and organisation-wide access |

### 4.2 Role Catalogue

**FR-RBAC-001** The system shall provide the following roles, each scoped to a division:

| Code | Name | Division |
|---|---|---|
| `SUPER_ADMIN` | Platform Super Admin | Both |
| `BOARD_ADMIN` | Board Admin (equivalent access to Super Admin) | Both |
| `IT_EMP` | IT Employee | IT |
| `IT_TL` | Team Leader | IT |
| `IT_MGR` | IT Manager / Team Lead — displayed as HR | IT |
| `IT_HR` | IT HR / Payroll Manager — displayed as HR Head | IT |
| `IT_FIN` | Finance Officer | IT |
| `IT_CEO` | CEO | IT |
| `IT_AST` | IT Asset Manager | IT |
| `CV_EMP` | Civil Site Employee | Civil |
| `CV_SUP` | Civil Supervisor | Civil |
| `CV_HR` | Civil HR Manager | Civil |
| `CV_ADM` | Civil / Facilities Admin | Civil |
| `CV_AST` | Civil Asset Manager | Civil |

### 4.3 Permission Catalogue

**FR-RBAC-002** The system shall provide the following permissions:

| Code | Capability granted |
|---|---|
| `USER_MANAGE` | Full employee administration, including deletion, offboarding and team detachment |
| `EMPLOYEE_MANAGE` | Create and edit employee accounts and their bank details and documents |
| `TEAM_MANAGE` | Create and delete teams |
| `ORG_MANAGE` | Maintain organisation master data and leave policies |
| `CALENDAR_MANAGE` | Add and remove calendar entries |
| `LEAVE_APPLY` | Apply for leave |
| `LEAVE_APPROVE` | Decide leave and hourly permission requests |
| `ATTENDANCE_SELF` | Record own attendance |
| `ATTENDANCE_TEAM` | View team and organisation attendance |
| `PAYROLL_VIEW` | View payslips and salary structures |
| `PAYROLL_RUN` | Maintain salary structures, generate payslips, run payroll, decide payslip requests |
| `PAYROLL_APPROVE` | Give finance approval to a payroll run |
| `CLAIM_APPROVE` | Approve or reject expense claims |
| `ASSET_MANAGE` | Maintain the asset registry, allocate and receive returns |
| `HELPDESK_RAISE` | Raise support tickets |
| `HELPDESK_AGENT` | Act on support tickets |
| `COMPLAINT_MANAGE` | Review and respond to complaints and needs |
| `TASK_ASSIGN` | Assign tasks |
| `TASK_VIEW_ALL` | View and export all employees' tasks |
| `REPORT_VIEW` | View and export reports; investigate safety incidents |
| `DASHBOARD_EXEC` | View the executive dashboard |

### 4.4 Role to Permission Matrix

**FR-RBAC-003** The system shall seed the following grants. `SUPER_ADMIN` shall hold every permission.

| Permission | `IT_EMP` `CV_EMP` | `IT_TL` | `IT_MGR` `IT_HR` | `CV_HR` | `CV_SUP` | `IT_FIN` | `IT_CEO` | `IT_AST` `CV_AST` | `CV_ADM` |
|---|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| `LEAVE_APPLY` | ● | ● | ● | | ● | | | | |
| `ATTENDANCE_SELF` | ● | ● | ● | | ● | | | | |
| `PAYROLL_VIEW` | ● | | ● | | ● | | | | |
| `HELPDESK_RAISE` | ● | ● | ● | | ● | | | | |
| `LEAVE_APPROVE` | | ● | ● | ● | ● | | | | |
| `ATTENDANCE_TEAM` | | ● | ● | ● | ● | | | | |
| `REPORT_VIEW` | | ● | ● | ● | ● | ● | ● | | |
| `TASK_ASSIGN` | | ● | ● | | | | | | |
| `TASK_VIEW_ALL` | | | ● | | | | | | |
| `USER_MANAGE` | | | ● | ● | | | | | |
| `EMPLOYEE_MANAGE` | | | ● | ● | | | | | |
| `TEAM_MANAGE` | | | ● | ● | | | | | |
| `CALENDAR_MANAGE` | | | ● | ● | | | | | |
| `COMPLAINT_MANAGE` | | | ● | ● | | | | | |
| `CLAIM_APPROVE` | | | ● | ● | | | | | |
| `HELPDESK_AGENT` | | | ● | | | | | ● | ● |
| `ASSET_MANAGE` | | | ● | ● | | | | ● | ● |
| `ORG_MANAGE` | | | ● | ● | | | | | ● |
| `PAYROLL_RUN` | | | ● | ● | | | | | |
| `PAYROLL_APPROVE` | | | | | | ● | | | |
| `DASHBOARD_EXEC` | | | | | | | ● | | |

> Note: `USER_MANAGE`, `ORG_MANAGE` and `PAYROLL_RUN` are seeded to the HR roles of both divisions; `PAYROLL_RUN` was subsequently extended to `IT_MGR`. `TASK_VIEW_ALL`, `TASK_ASSIGN`, `HELPDESK_AGENT`, `CLAIM_APPROVE`, `COMPLAINT_MANAGE`, `CALENDAR_MANAGE`, `EMPLOYEE_MANAGE`, `TEAM_MANAGE` and `ASSET_MANAGE` were added incrementally to the HR roles.

### 4.5 Endpoint Authorisation Rules

**FR-RBAC-004** Authorisation shall be enforced on the server at the endpoint. Client-side hiding of navigation shall be treated as convenience only and shall never be the sole control.

**FR-RBAC-005** The following endpoint guards shall apply:

| Guard expression | Endpoints protected |
|---|---|
| `USER_MANAGE` | Team detachment, offboarding, leave reset, AI assistant settings and knowledge, system settings, task deletion |
| `USER_MANAGE` or `EMPLOYEE_MANAGE` | Employee creation, bulk creation, profile edit by id, credential set, document upload, bank add for another employee, employee deletion, password reveal |
| `USER_MANAGE`, `ATTENDANCE_TEAM` or `DASHBOARD_EXEC` | Employee directory, employee profile by id, team attendance for a date and for a range |
| `LEAVE_APPROVE` | Pending leave queue, approver queue, leave decision, bulk leave decision, permission pending and for-me queues, permission decision |
| `LEAVE_APPROVE`, `USER_MANAGE` or `DASHBOARD_EXEC` | Organisation leave calendar |
| `ORG_MANAGE` | Leave type create, update and delete; default balance allocation; master data maintenance |
| `ORG_MANAGE` or `CALENDAR_MANAGE` | Holiday creation and deletion |
| `USER_MANAGE`, `ORG_MANAGE` or `TEAM_MANAGE` | Team (designation) creation and deletion |
| `PAYROLL_RUN` | Payslip generation, salary structure upsert, payroll run creation and confirmation, payslip request inbox and decisions, logo upload, loss-of-pay preview |
| `PAYROLL_VIEW` | Salary structure read, salary list, month payslip view, another employee's payslip list |
| `PAYROLL_APPROVE` | Payroll run finance approval |
| `PAYROLL_RUN` or `PAYROLL_APPROVE` | Payroll run list and detail |
| `PAYROLL_RUN`, `PAYROLL_VIEW`, `USER_MANAGE` or `EMPLOYEE_MANAGE` | Another employee's bank accounts |
| `ASSET_MANAGE` | Asset list, create, delete, allocate, return |
| `HELPDESK_AGENT` | Agent queue, ticket status change |
| `HELPDESK_AGENT`, `USER_MANAGE` or `DASHBOARD_EXEC` | All tickets |
| `USER_MANAGE` or `COMPLAINT_MANAGE` | All complaints, complaint response |
| `USER_MANAGE`, `DASHBOARD_EXEC` or `CLAIM_APPROVE` | All claims, claim decision |
| `USER_MANAGE` or `TASK_ASSIGN` | Task creation and edit |
| `USER_MANAGE`, `TASK_ASSIGN` or `TASK_VIEW_ALL` | All tasks grouped by employee |
| `USER_MANAGE` or `TASK_VIEW_ALL` | Task export |
| `REPORT_VIEW` | Attendance, leave and payroll exports; safety incident list and resolution |
| `REPORT_VIEW` or `USER_MANAGE` | All work reports and work report export |
| `DASHBOARD_EXEC` | Executive dashboard |
| role `SUPER_ADMIN` | Community group creation and deletion, community membership management |
| `USER_MANAGE` or role `IT_MGR` or role `IT_HR` | All hourly permission requests overview |

**FR-RBAC-006** Where an endpoint returns records belonging to other people, the service layer shall additionally scope the result set to what the caller is entitled to see, independently of the endpoint guard.

**FR-RBAC-007** A user shall never be able to decide their own request, in any workflow.

### 4.6 Approval Routing Hierarchy

**FR-RBAC-008** Approval routing shall be single-level: for any given request, exactly one class of approver shall be able to act, so that a requester cannot choose a route that skips a level.

**Leave approval routing**

| Applicant | Duration | Sole valid approver |
|---|---|---|
| Employee | up to 3 working days | a Team Leader **of the applicant's own team** |
| Employee | more than 3 working days | a Manager (`IT_MGR`) |
| Team Leader | any | a Manager (`IT_MGR`) |
| HR / Manager | any | the single designated escalation approver, identified by employee code |
| Administrator | any | not handled by this workflow |

**Hourly permission routing**

| Requester | Sole valid approver |
|---|---|
| Employee | a Team Leader of the requester's own team |
| Team Leader | HR (`IT_MGR` or `IT_HR`) |
| HR / Manager | the single designated escalation approver, identified by employee code |

**Support ticket and complaint routing**

| Raiser | Valid recipients |
|---|---|
| Employee or Team Leader | HR only |
| HR | the single designated escalation approver, identified by employee code |

**FR-RBAC-009** The approver picker offered to a requester shall be computed by the same rule that the decision endpoint enforces, so the list can never offer a recipient whose decision would then be rejected.

**FR-RBAC-010** When a request names a specific recipient, only that recipient shall be able to act on it. When no recipient was named, the role-and-duration rule shall determine who may act.

**FR-RBAC-011** Administrators shall be able to view the entire approval queue for oversight, with rows they may not decide presented as read-only.

---
---

# Part II — Functional Requirements

## 5. Authentication and Session Management

### 5.1 Sign-in

**FR-AUTH-001** The system shall authenticate a user by **username and password**.

**FR-AUTH-002** Where the supplied identifier is not a known username, the system shall attempt a case-insensitive match against the employee's full name, and shall accept it only when exactly one employee bears that name. Ambiguous or absent matches shall be rejected.

**FR-AUTH-003** Passwords shall be verified against a BCrypt hash. The stored hash shall be the sole authority for sign-in.

**FR-AUTH-004** On successful sign-in the system shall reset the failed-attempt counter, clear any lock, record the last sign-in timestamp, and return an access token, a refresh token and the authenticated user's profile summary including role codes and effective permission codes.

**FR-AUTH-005** A sign-in attempt against a disabled account shall be refused with an instruction to contact HR.

**FR-AUTH-006** Every sign-in attempt, successful or not, shall be recorded with the identifier used, the outcome, the client IP address and the user agent. The IP shall be taken from the forwarding header's first entry when present, otherwise from the socket address. The user agent shall be truncated to a safe length.

### 5.2 Account Lockout

**FR-AUTH-007** After **5** consecutive failed attempts the account shall be locked for **15** minutes, the counter reset, and the lock recorded on the account. Both values shall be configurable.

**FR-AUTH-008** Sign-in during an active lock shall be refused with a distinct locked status, and the attempt shall still be recorded.

### 5.3 Tokens and Session Lifetime

**FR-AUTH-009** The access token shall be a signed JWT carrying the user identifier as subject, the issuer, the username and the role codes, with issue and expiry timestamps, signed with an HMAC-SHA key derived from the configured secret.

**FR-AUTH-010** The access token lifetime shall default to **4 hours** and the refresh token lifetime to **4 hours**, both configurable. The refresh window shall not silently extend a session beyond the access window.

**FR-AUTH-011** A refresh token shall be an opaque, database-persisted value with an expiry and a revocation flag.

**FR-AUTH-012** Exchanging a refresh token shall validate that it exists, is not revoked and is not expired; shall revoke the presented token; and shall issue a fresh token pair. Presenting a revoked or expired token shall fail with an instruction to sign in again.

**FR-AUTH-013** Sign-out shall revoke **all** refresh tokens held by the user.

**FR-AUTH-014** A successful password change shall revoke all refresh tokens, forcing re-authentication everywhere else.

**FR-AUTH-015** The client shall attach the access token as a bearer credential to every API call, shall transparently refresh once on a 401 and retry the original request, shall coalesce concurrent refreshes into a single in-flight attempt, and shall clear stored credentials and return to sign-in when refresh fails.

### 5.4 Self-service Registration

**FR-AUTH-016** The system shall provide a self-registration endpoint accepting username, name, password, optional date of birth, gender, national identifier, phone, e-mail, a full address block, division, and optional department, designation and office-location references.

**FR-AUTH-017** Registration shall reject a duplicate username, a duplicate national identifier and a duplicate phone number, each with a distinct conflict message.

**FR-AUTH-018** A self-registered account shall receive division-appropriate default self-service role — `CV_EMP` for the Civil division, `IT_EMP` otherwise — profile status `ACTIVE`, and a joining date of the current day.

**FR-AUTH-019** Registration shall return an authenticated session, unlike administrative employee creation.

### 5.5 Password Management

**FR-AUTH-020** A signed-in user shall be able to change their own password by supplying the current password and a new one. An incorrect current password shall be rejected.

**FR-AUTH-021** Every time a password is set — at creation, at administrative reset, and at self-change — the system shall additionally store a reversibly-encrypted copy of it.

**FR-AUTH-022** The reversible copy shall be encrypted with AES-GCM using a 256-bit key derived by SHA-256 over a fixed label concatenated with the application signing secret, with a fresh 12-byte random initialisation vector per value and a 128-bit authentication tag, stored Base64-encoded with the vector prefixed.

**FR-AUTH-023** Failure to produce or read the reversible copy shall never prevent a password from being set or a sign-in from succeeding.

**FR-AUTH-024** Accounts whose password was last set before the reversible copy existed shall report the password as not recorded until it is set again.

**FR-AUTH-025** Holders of `USER_MANAGE` or `EMPLOYEE_MANAGE` shall be able to read an employee's current password through a dedicated endpoint that is called only on explicit request, so that the value never travels with an ordinary profile read, a directory listing or an export.

### 5.6 Availability Checks

**FR-AUTH-026** The system shall expose an unauthenticated check reporting whether a username is available.
**FR-AUTH-027** The system shall expose a check reporting whether a phone number is already registered.

### 5.7 Current User

**FR-AUTH-028** The system shall expose an endpoint returning the signed-in user's identifier, employee code, username, name, national identifier, e-mail, phone, division, photo path, role codes and the distinct union of permission codes across their roles.

---

## 6. Employee Management

### 6.1 Administrative Employee Creation

**FR-EMP-001** Holders of `USER_MANAGE` or `EMPLOYEE_MANAGE` shall be able to create an employee account with a username and password, without signing that account in, and shall receive the created account summary so the credentials can be handed over.

**FR-EMP-002** Creation shall accept: username, name, password, date of birth, gender, national identifier, phone, e-mail; the full address block; division, department, designation and office-location references; reporting manager; PAN; provident-fund number; alternate phone; emergency contact and relation; blood group; personal e-mail; document paths; free-text designation, department and position titles; profile status; joining date; and an explicit role code.

**FR-EMP-003** Creation shall reject a blank username, a duplicate username, a duplicate national identifier and a duplicate phone number.

**FR-EMP-004** Blank optional string values shall be normalised to absent rather than stored as empty text.

**FR-EMP-005** Gender shall be stored as a single upper-case character derived from the first character of the supplied value.

**FR-EMP-006** Division shall be canonicalised on write: the display labels `DIGITAL` and `INFRA` shall be stored as `IT` and `CIVIL` respectively, so display labels never leak into stored data. An absent division shall default to `IT`.

**FR-EMP-007** Profile status shall default to `ACTIVE`, shall be stored upper-cased, and the account shall be enabled unless the status is `OFFBOARDED`.

**FR-EMP-008** Joining date shall default to the current day when not supplied.

**FR-EMP-009** The role shall be the explicitly supplied role when it names an existing role, and otherwise the division-appropriate self-service role. An unknown role code shall be rejected.

**FR-EMP-010** An employee code shall be generated automatically on creation by incrementing the highest existing code bearing the standard prefix and formatting the sequence to four digits, and shall be unique.

**FR-EMP-011** Every newly created employee shall be added automatically to the organisation-wide announcement channel, so that no employee can miss a broadcast.

### 6.2 Bulk Employee Creation

**FR-EMP-012** Holders of `USER_MANAGE` or `EMPLOYEE_MANAGE` shall be able to submit a list of employee creation requests in one call.

**FR-EMP-013** Each row shall be created in its own transaction, so that one invalid row does not roll back the batch.

**FR-EMP-014** The response shall report, per row, the username, the name, whether creation succeeded and the failure reason when it did not; and shall summarise how many of how many rows succeeded.

**FR-EMP-015** The web application shall accept a spreadsheet workbook, shall parse sheets and columns by exact name, shall ignore unrecognised sheets and columns rather than rejecting them, shall report per sheet whether it was found and how many rows it held, and shall present the resulting credentials for distribution.

**FR-EMP-016** The import screen shall display the expected workbook layout and shall offer a matching blank template for download.

### 6.3 Employee Directory

**FR-EMP-017** Holders of `USER_MANAGE`, `ATTENDANCE_TEAM` or `DASHBOARD_EXEC` shall be able to browse a paged employee directory ordered by name ascending, with a default page size of 20.

**FR-EMP-018** The directory shall support filtering by free-text query, division, department and profile status, with blank filter values treated as absent.

**FR-EMP-019** Each directory row shall carry the identifier, employee code, name, username, e-mail, phone, division, department reference, profile status, photo path, date of birth, role codes, designation reference, designation title and technology stack.

### 6.4 Profile Read and Maintenance

**FR-EMP-020** A signed-in user shall be able to read their own full profile.

**FR-EMP-021** A signed-in user shall be able to update their own name, date of birth, gender, e-mail and full address block. Only supplied members shall be changed; absent members shall be left untouched.

**FR-EMP-022** Holders of `USER_MANAGE`, `ATTENDANCE_TEAM` or `DASHBOARD_EXEC` shall be able to read any employee's profile by identifier.

**FR-EMP-023** Holders of `USER_MANAGE` or `EMPLOYEE_MANAGE` shall be able to update any employee's profile, including identity fields, statutory identifiers, contact and emergency details, blood group, documents, free-text titles, technology stack, the full address block, division, department, designation, office location, reporting manager, employment type, joining date, profile status, employee code and role set. Only supplied members shall be changed.

**FR-EMP-024** Setting profile status through an administrative update shall enable or disable the account consistently: `OFFBOARDED` shall disable it, any other status shall enable it.

**FR-EMP-025** Replacing the role set shall resolve role codes to roles and shall replace the account's roles wholesale.

**FR-EMP-026** A profile shall report: identifier, employee code, username, name, date of birth, gender, national identifier, phone, e-mail, photo path, the address block, department, designation, office-location and reporting-manager references, division, employment type, joining date, profile status, PAN, provident-fund number, alternate phone, emergency contact and relation, blood group, personal e-mail, the three free-text titles, role codes and document paths.

### 6.5 Credentials Administration

**FR-EMP-027** Holders of `USER_MANAGE` or `EMPLOYEE_MANAGE` shall be able to set an employee's username, reset their password, or both, in one call.

**FR-EMP-028** A username already held by another account shall be rejected.

**FR-EMP-029** A reset password shall be at least 4 characters.

**FR-EMP-030** A call supplying neither a username nor a password shall be rejected.

**FR-EMP-031** A password reset shall update both the hash and the reversible copy, so that what HR can read is what the employee actually signs in with.

### 6.6 Photograph and Documents

**FR-EMP-032** A signed-in user shall be able to upload or replace their profile photograph, and shall receive the stored path.
**FR-EMP-033** A signed-in user shall be able to remove their profile photograph, after which an initials avatar shall be presented.
**FR-EMP-034** Holders of `USER_MANAGE` or `EMPLOYEE_MANAGE` shall be able to upload one employee document at a time and shall receive its stored path; the caller shall collect paths and persist them on the employee as a comma-separated list.

### 6.7 Bank Details

**FR-EMP-035** A signed-in user shall be able to list, add, update and delete their own bank accounts.
**FR-EMP-036** A bank account shall carry bank name, branch name, account number, IFSC code, account holder name and a primary flag.
**FR-EMP-037** The IFSC code shall be stored upper-cased.
**FR-EMP-038** Marking an account primary shall demote every other account of that employee, so at most one is primary.
**FR-EMP-039** Update and delete shall operate only on an account belonging to the calling user.
**FR-EMP-040** Holders of `USER_MANAGE` or `EMPLOYEE_MANAGE` shall be able to add a bank account on an employee's behalf, since HR completes the joining form.
**FR-EMP-041** Holders of `PAYROLL_RUN`, `PAYROLL_VIEW`, `USER_MANAGE` or `EMPLOYEE_MANAGE` shall be able to read another employee's bank accounts.

### 6.8 Teams

**FR-EMP-042** A signed-in employee shall be able to read their own team and its members.
**FR-EMP-043** The team name shall be resolved from the employee's designation title, falling back to the linked designation record's name, falling back to a generic label.
**FR-EMP-044** Teammates shall be matched by designation **title** or by the designation reference, so that members carrying only a title are included. Where no teammate is found, the team shall consist of the employee alone.
**FR-EMP-045** Holders of `USER_MANAGE` or role `IT_MGR` shall see the all-teams view; all other users shall see their own team only.
**FR-EMP-046** Holders of `USER_MANAGE` shall be able to detach an employee from their team by clearing the designation reference.

### 6.9 Offboarding and Deletion

**FR-EMP-047** Holders of `USER_MANAGE` shall be able to offboard an employee, supplying a relieving date, a reason and notes.
**FR-EMP-048** Offboarding shall set profile status to `OFFBOARDED`, disable the account, and create an offboarding record with full-and-final status `PENDING`.
**FR-EMP-049** Offboarding an already-offboarded employee shall be rejected.
**FR-EMP-050** Holders of `USER_MANAGE` or `EMPLOYEE_MANAGE` shall be able to delete an employee record permanently.
**FR-EMP-051** Deletion shall first clear or remove every reference to the employee that is not covered by a cascading database constraint: legacy project and team leadership references shall be nulled; the employee's own messages and the groups they created shall be deleted; ticket assignment and comment authorship shall be nulled; and subordinates' reporting-manager reference shall be cleared.
**FR-EMP-052** Each such clean-up statement shall be preceded by an existence check against the schema catalogue, so that a statement which could fail is never executed and the deletion transaction is never poisoned.
**FR-EMP-053** After clean-up, pending changes shall be flushed and the employee deleted, allowing database-level cascades to remove dependent rows.

---

## 7. Organisation and Master Data

### 7.1 Reference Lists

**FR-ORG-001** The system shall serve master data by type through a single endpoint, addressable both by path segment and by query parameter, and shall additionally accept an array of types returning a map keyed by type.

**FR-ORG-002** The following types shall be supported: `blood_group`, `department`, `designation`, `employment_status`, `position`, `office_location`, `shift`, `site`. Type names shall be matched case-insensitively with hyphens treated as underscores. An unknown type shall be rejected with a clear message.

**FR-ORG-003** Only active entries shall be returned, ordered by name ascending, each as an identifier and a display name.

**FR-ORG-004** Designation lookups shall accept an optional division filter, canonicalising the display labels `DIGITAL` and `INFRA` to `IT` and `CIVIL`; an absent filter shall return every active designation.

**FR-ORG-005** The system shall serve the list of active sites and the list of active office locations, each with its coordinates and geofence radius.

### 7.2 Teams as Designations

**FR-ORG-006** Holders of `USER_MANAGE`, `ORG_MANAGE` or `TEAM_MANAGE` shall be able to create a team, supplying a name and an optional division.
**FR-ORG-007** A blank team name shall be rejected; a name matching an existing active team, case-insensitively, shall be rejected as a conflict.
**FR-ORG-008** A team code shall be derived from the name by upper-casing, replacing runs of non-alphanumeric characters with a single underscore, trimming leading and trailing underscores, and truncating to the column length.
**FR-ORG-009** Team division shall default to `IT` when not supplied.
**FR-ORG-010** Deleting a team by name shall first detach every member — clearing both the designation title and the designation reference — and shall then remove every designation record bearing that name, so no member is left pointing at a deleted team.

### 7.3 Locations, Sites and Shifts

**DR-001** An office location shall carry a name, an address, a latitude, a longitude and a geofence radius in metres.
**DR-002** A site shall carry a name, a code, an address, a latitude, a longitude, a geofence radius in metres and a project start date.
**DR-003** A shift shall carry a name, a start time, an end time and a night-shift flag.

**FR-ORG-011** The system shall be seeded with at least two office locations, three shifts covering general, morning and night patterns, and at least two project sites.

---

## 8. Attendance Management

### 8.1 Punch In

**FR-ATT-001** A signed-in employee shall be able to punch in once per calendar day. A second punch-in on the same day shall be rejected.

**FR-ATT-002** A punch-in shall record the employee, the work date, the punch-in timestamp, the mode, the supplied coordinates and the shift reference.

**FR-ATT-003** The mode shall be one of `OFFICE`, `WFH`, `SITE` or `BIOMETRIC`, defaulting to `OFFICE`, and shall be stored upper-cased.

**FR-ATT-004** A `WFH` punch shall be given status `WFH` and shall not be geofence-evaluated; its geofence result shall be recorded as not applicable.

**FR-ATT-005** Any other mode shall be geofence-evaluated and given status `PRESENT`.

### 8.2 Geofence Evaluation

**FR-ATT-006** Distance between two coordinates shall be computed by the haversine formula over a mean Earth radius of 6 371 000 metres.

**FR-ATT-007** A punch shall be within the geofence when the distance from the reference centre is less than or equal to the applicable radius.

**FR-ATT-008** For a `SITE` punch the reference shall be the supplied site, falling back to the employee's assigned site; the site reference shall be recorded on the attendance row.

**FR-ATT-009** For an `OFFICE` or `BIOMETRIC` punch the reference shall be the supplied office location, falling back to the employee's assigned office location.

**FR-ATT-010** Where the referenced location defines no radius, the configured default of **200 metres** shall be used.

**FR-ATT-011** Where no coordinates are supplied, the punch shall be accepted, marked as outside the geofence, and flagged as a geofence exception. It shall never be blocked and shall never raise an error.

**FR-ATT-012** Any punch found outside the geofence shall be flagged as a geofence exception.

### 8.3 Lateness

**FR-ATT-013** The office start time for a punch shall be the assigned shift's start time when a shift is supplied and defines one, and otherwise the configured office start, defaulting to 09:00.

**FR-ATT-014** Lateness shall be measured in whole minutes past the office start plus the configured grace period. The grace shall default to **zero minutes**, so a punch at or before the office start is on time and anything after it is late by the difference.

**FR-ATT-015** The attendance row shall record both the late flag and the late minutes.

### 8.4 Punch Out

**FR-ATT-016** A punch-out shall require an existing punch-in for the day; absence of one shall be rejected. A punch-out when one already exists shall be rejected.

**FR-ATT-017** A punch-out shall record the timestamp and the supplied coordinates.

**FR-ATT-018** Worked minutes shall be the whole minutes between punch-in and punch-out, floored at zero.

**FR-ATT-019** Overtime shall be the whole minutes worked past the configured office end time, defaulting to 18:00. Nothing before the office end shall count. Where the punch-in itself falls after the office end, overtime shall be measured from the actual start.

### 8.5 Views and Summaries

**FR-ATT-020** An employee shall be able to read their own attendance record for today, which shall be absent when no punch exists.

**FR-ATT-021** An employee shall be able to read their own attendance rows across a date range, most recent first.

**FR-ATT-022** An employee shall be able to obtain a monthly summary reporting: present days, work-from-home days, late days, absent days, total overtime minutes, total late minutes and the number of working days.

**FR-ATT-023** Working days for a summary shall be counted from the first of the month up to the earlier of today and the month end, excluding Sundays and configured holidays.

**FR-ATT-024** Absent days shall be the working days minus present days minus work-from-home days, floored at zero.

**FR-ATT-025** Every signed-in employee shall be able to read the list of everyone absent today: enabled, non-offboarded employees who neither punched in nor hold approved or pending leave covering today. Each row shall carry the identifier, name, employee code and designation title. The list shall reflect actual punch and leave data regardless of weekday, since some teams work Saturdays.

**FR-ATT-026** Every signed-in employee shall be able to read their own team's punch-in status for today, scoped to teammates sharing their designation title, each row reporting whether the teammate has punched in and when.

**FR-ATT-027** Holders of `ATTENDANCE_TEAM`, `USER_MANAGE` or `DASHBOARD_EXEC` shall be able to read attendance for a supplied set of members on a given date, and across a date range for download.

**FR-ATT-028** An attendance record shall report: identifier, employee, work date, punch-in and punch-out timestamps, mode, status, late flag, late minutes, geofence result, geofence-exception flag, worked minutes, overtime minutes, and the in and out coordinates.

### 8.6 Face-based Verification

**FR-ATT-029** The system shall provide a face enrolment interface capturing an image from the device camera and registering it against an employee.
**FR-ATT-030** The system shall provide a face verification interface capturing an image and matching it against the enrolled image for an employee.
**FR-ATT-031** Face capabilities shall depend on the biometric side service; when it is unavailable the feature shall report so and shall not prevent an ordinary punch.

---

## 9. Leave Management

### 9.1 Leave Types and Policy

**FR-LV-001** The system shall maintain leave types, each carrying a name, a unique code, a maximum days per year, a carry-forward flag, an encashable flag, an optional gender restriction, a past-dates flag, an accrual type of `ANNUAL`, `MONTHLY` or `MANUAL`, a minimum notice in days, a per-period limit, a paid flag and an active flag.

**FR-LV-002** The system shall be seeded with Casual Leave, Sick Leave, Earned Leave, Compensatory Off, Maternity Leave, Paternity Leave and Loss of Pay, with entitlements, restrictions and notice periods appropriate to each.

**FR-LV-003** Any signed-in user shall be able to list active leave types, ordered by name.

**FR-LV-004** Holders of `ORG_MANAGE` shall be able to create, update and delete leave types.

**FR-LV-005** Leave type codes shall be unique, matched case-insensitively.

**FR-LV-006** Creating a type whose code matches a **soft-deleted** type shall reactivate and update that type rather than fail on the uniqueness constraint. Creating one whose code matches an **active** type shall be rejected.

**FR-LV-007** Updating a type shall reject a code already held by a different type.

**FR-LV-008** Deleting a leave type shall be a soft delete, marking it inactive and preserving history.

### 9.2 Balances and Allocation

**FR-LV-009** A leave balance shall be held per employee, per leave type, per year, carrying an allocated quantity and a used quantity; the available quantity shall be the difference.

**FR-LV-010** An employee shall be able to read their own balances for a year, defaulting to the current year, each row reporting the type name and code alongside the allocated, used and available quantities.

**FR-LV-011** Holders of `ORG_MANAGE` shall be able to allocate default annual balances to every enabled employee in one action, using each active type's configured maximum days per year as the allocated quantity.

**FR-LV-012** Types with no cap or a zero cap shall be skipped by bulk allocation.

**FR-LV-013** Bulk allocation shall never overwrite an existing balance row, so that it is safe to run repeatedly, and shall report how many rows were created, how many employees were covered and for which year.

**FR-LV-014** Allocation shall be permitted only for the current year or a later one; a year already past shall be rejected with a clear message.

**FR-LV-015** Holders of `USER_MANAGE` shall be able to reset an employee's leave, zeroing every used quantity and clearing their leave request history.

### 9.3 Working-day Calculation

**FR-LV-016** Working days within a date range shall be counted excluding **Sundays** and configured **holidays**. Saturday shall count as a working day.

### 9.4 Applying for Leave

**FR-LV-017** An employee shall be able to apply for leave supplying a leave type, a from date, a to date, a reason, an optional attachment path and an optional named approver.

**FR-LV-018** An end date earlier than the start date shall be rejected.

**FR-LV-019** Where the leave type carries a gender restriction and the applicant's gender is known, a mismatch shall be rejected with a message stating the type is not applicable to their profile.

**FR-LV-020** Unless the type explicitly allows past dates, a start date before today shall be rejected.

**FR-LV-021** Where the type defines a minimum notice and the start date is in the future, an application with less notice than required shall be rejected, naming the required notice.

**FR-LV-022** The working days of the request shall be computed and stored. A range containing no working days shall be rejected.

**FR-LV-023** Where the type defines a per-period limit, the limit shall be applied **per calendar quarter**: the number of existing requests for that type whose start date falls within the quarter containing the requested start date shall not exceed the limit. Exceeding it shall be rejected with a message naming the allowance and the date from which the next one becomes available.

**FR-LV-024** For every type other than Loss of Pay, a balance row for the applicant, the type and the year of the start date shall be required; its absence shall be rejected. The available quantity shall be at least the requested working days; a shortfall shall be rejected, naming both figures.

**FR-LV-025** A submitted request shall be created with status `PENDING` and shall record the applicant, the type, the dates, the working days, the reason, the attachment path and the named approver.

### 9.5 Approval Notification

**FR-LV-026** On submission, where a specific approver was named, only that person shall be notified. Where none was named, every holder of `LEAVE_APPROVE` shall be notified.

**FR-LV-027** Each notification shall name the applicant, the leave type, the number of working days and the date or date range, shall be recorded in the notification feed, shall be pushed live, and shall link to the approvals screen.

**FR-LV-028** Each notified approver holding a usable mobile number shall additionally receive an SMS.

### 9.6 Own Requests and Cancellation

**FR-LV-029** An employee shall be able to browse their own leave requests, paged, most recent first, each row naming the applicant and the leave type.

**FR-LV-030** An employee shall be able to cancel their own request. Cancelling another employee's request shall be rejected.

**FR-LV-031** Only a `PENDING` or `APPROVED` request shall be cancellable.

**FR-LV-032** Cancelling an approved request shall refund the consumed balance, floored at zero, for every type other than Loss of Pay.

**FR-LV-033** Cancellation shall notify whoever was handling the request — the named approver, or failing that the decider — unless that person is the applicant, stating who cancelled, whether it had been approved, the type and the dates.

### 9.7 Approver Queues

**FR-LV-034** Holders of `LEAVE_APPROVE` shall be able to read the pending queue. Rows shall be included where the approver may act; administrators shall additionally see the entire queue, with rows they may not decide marked read-only.

**FR-LV-035** Holders of `LEAVE_APPROVE` shall be able to read their full queue across all statuses, supporting pending, approved and rejected views, most recent first, resolving in one pass the names of applicants, named approvers and deciders.

**FR-LV-036** A queue row shall additionally report the applicant's highest leave-routing role, whether the viewer may act on it, the named approver's name, the decider's name and the applicant's designation title.

**FR-LV-037** An employee shall be able to obtain the list of approvers valid for a leave of a given number of days, computed by the same routing rule the decision endpoint enforces, each entry carrying the identifier, name and employee code.

**FR-LV-038** Every signed-in user shall be able to read who is on approved leave today — where today falls within the from and to dates — each row naming the person, their designation title and their employee code.

### 9.8 Organisation Leave Calendar

**FR-LV-039** Holders of `LEAVE_APPROVE`, `USER_MANAGE` or `DASHBOARD_EXEC` shall be able to read all leave overlapping a date range.

**FR-LV-040** Calendar visibility shall be scoped: holders of `USER_MANAGE`, administrators, managers and HR shall see the whole organisation; a Team Leader shall see only their own team and themselves.

### 9.9 Decisions

**FR-LV-041** Holders of `LEAVE_APPROVE` shall be able to decide a single request, and shall be able to decide many at once.

**FR-LV-042** A bulk decision shall act only on requests still `PENDING` and shall report how many were decided.

**FR-LV-043** A decision on a request that is not `PENDING` shall be rejected, naming its current state.

**FR-LV-044** A decision shall be rejected where the decider is not the person entitled to act on that request under the routing rule, so that the API enforces routing independently of the screen.

**FR-LV-045** A decision shall be either `APPROVED` or `REJECTED`; any other value shall be rejected.

**FR-LV-046** A rejection shall require a reason; a blank reason shall be rejected.

**FR-LV-047** On approval, for every type other than Loss of Pay, the balance row shall be required and the available quantity re-checked at decision time; an intervening shortfall shall be rejected. The used quantity shall then be increased by the request's working days.

**FR-LV-048** A decision shall record the outcome, the decider, the decision timestamp and the comment.

**FR-LV-049** The applicant shall be notified of the outcome in the feed, pushed live, linking to the leave screen; and shall additionally receive an SMS naming the type, the dates, the outcome and any comment.

### 9.10 Loss-of-Pay Preview

**FR-LV-050** Holders of `PAYROLL_RUN` shall be able to obtain a loss-of-pay preview for an employee and a month, reporting: unpaid approved leave days, paid approved leave days, total leave days, the number of approved leave requests, days actually attended, days neither worked nor covered by leave, and the number of working days in the month.

**FR-LV-051** Leave days shall be classified paid or unpaid by the leave type's paid flag.

**FR-LV-052** Attended days shall count both `PRESENT` and `WFH` records.

---

## 10. Hourly Permission Requests

**FR-PRM-001** An employee shall be able to request a short absence during a working day, supplying a date, a start time, an end time in `HH:mm`, a reason and an optional named approver.

**FR-PRM-002** Unparseable times shall be rejected with an instruction to use `HH:mm`. An end time not after the start time shall be rejected.

**FR-PRM-003** The duration in hours shall be computed from the minute difference, divided by sixty and rounded half-up to two decimal places.

**FR-PRM-004** A submitted request shall be created with status `PENDING`.

**FR-PRM-005** Where an approver was named, that person shall be notified in the feed, pushed live, linking to the permissions screen, and shall receive an SMS where a usable number exists; the message shall name the requester, the hours, the date and the time window.

**FR-PRM-006** A requester shall be able to list their own requests, most recent first.

**FR-PRM-007** A requester shall be able to obtain the list of approvers valid for them, computed by the routing rule of §4.6.

**FR-PRM-008** Holders of `LEAVE_APPROVE` shall be able to read the pending requests addressed to them, and the full history of requests addressed to them across all statuses.

**FR-PRM-009** Holders of `USER_MANAGE`, or role `IT_MGR`, or role `IT_HR`, shall be able to read every request as a read-only overview, most recent first.

**FR-PRM-010** Holders of `LEAVE_APPROVE` shall be able to approve or reject a request. A rejection shall require a reason.

**FR-PRM-011** A pending request whose date has already passed shall no longer be decidable; the attempt shall be rejected with a message stating the date and that the request is now overdue.

**FR-PRM-012** A decision shall record the outcome, the decider, the timestamp and the comment; and shall notify the requester in the feed, pushed live, and by SMS where a usable number exists.

**FR-PRM-013** A requester shall be able to cancel their own request while it is still `PENDING`; cancelling another's request or a decided request shall be rejected. Cancellation shall remove the request.

**FR-PRM-014** A request row shall report the requester's name, employee code and designation title, the named approver's name and the decider's name.

---

## 11. Payroll and Payslips

### 11.1 Salary Structures

**FR-PAY-001** Holders of `PAYROLL_RUN` shall be able to create or replace an employee's active salary structure, supplying basic salary, house-rent allowance, other allowances, a provident-fund amount, an ESI-applicability flag and a professional-tax amount.

**FR-PAY-002** Absent monetary members shall default to zero; ESI applicability shall default to applicable.

**FR-PAY-003** An upsert shall reuse the employee's existing active structure where one exists, shall mark the structure active, and shall set an effective-from date of today when none is present.

**FR-PAY-004** Holders of `PAYROLL_VIEW` shall be able to read one employee's active structure and to list every active structure, each reporting the employee's name and code, the components and the computed gross.

**FR-PAY-005** The provident-fund figure on a structure shall be treated as a **flat rupee amount**, not a percentage.

### 11.2 Direct Payslip Generation

**FR-PAY-006** Holders of `PAYROLL_RUN` shall be able to generate a payslip for an employee, a month and a year, supplying overtime hours, performance pay, tax deducted at source, an advance deduction, a manual loss-of-pay deduction and other deductions.

**FR-PAY-007** Generation shall be rejected for a month earlier than the employee's joining month, naming the joining date and the earliest permissible month.

**FR-PAY-008** Generation shall require an active salary structure; its absence shall be rejected, naming the employee.

**FR-PAY-009** Generation shall reuse an existing payslip row for the same employee, month and year where one exists.

**FR-PAY-010** Earnings shall be computed as follows:
- overtime pay = (basic + HRA + allowances) ÷ 240, rounded half-up, multiplied by the overtime hours;
- gross = basic + HRA + allowances + overtime pay + performance pay, floored at zero, scaled to two places.

**FR-PAY-011** Deductions shall be computed as follows:
- provident fund = the structure's flat amount;
- ESI = 0.75 % of gross, but only where the structure marks ESI applicable **and** gross does not exceed the wage ceiling of 21 000; otherwise zero;
- professional tax = the structure's amount;
- tax deducted at source, advance and other deductions as supplied;
- the manual loss-of-pay deduction shall be grouped with other deductions for storage;
- total deductions = provident fund + ESI + professional tax + tax + other + advance.

**FR-PAY-012** Net pay shall be gross minus total deductions, scaled to two places.

**FR-PAY-013** Attendance-driven automatic loss of pay shall not be deducted; loss of pay shall be entered manually, so that the two cannot double-count.

**FR-PAY-014** A generated payslip shall be auto-completed from the employee's record: the primary bank account's name and number, or the first account, or a placeholder where none exists; the designation title, falling back to the linked designation's name, falling back to a placeholder; the department title with the same fallback chain; the pay date set to today; and the working days set to the number of days in the month.

**FR-PAY-015** The payslip shall additionally record a computed loss-of-pay **day count**: working days in the month, up to today where the month is current, excluding Sundays and holidays, on which the employee has no punch-in.

**FR-PAY-016** Generation shall render a PDF and persist its path on the payslip.

### 11.3 Payslip Request Workflow

**FR-PAY-017** Any signed-in user shall be able to raise a payslip request for a month and a year with an optional note.

**FR-PAY-018** A request for a month earlier than the requester's joining month shall be rejected, naming the joining date and the earliest permissible month.

**FR-PAY-019** A second pending request for the same month shall be rejected, naming the month.

**FR-PAY-020** A raised request shall be created with status `PENDING` and shall notify every holder of `PAYROLL_RUN` in the feed, pushed live, linking to the payroll requests screen, and by SMS.

**FR-PAY-021** A requester shall be able to list their own requests, most recent first.

**FR-PAY-022** Holders of `PAYROLL_RUN` shall be able to read the request inbox, either pending only or complete, most recent first, each row naming the requester and their employee code.

**FR-PAY-023** Holders of `PAYROLL_RUN` shall be able to reject a request with a note. A request not `PENDING` shall be rejected, naming its state. Rejection shall record the outcome, the decider, the timestamp and the note, and shall notify the requester in the feed and by SMS, quoting the note.

**FR-PAY-024** Holders of `PAYROLL_RUN` shall be able to approve a request by completing a payslip form. Approval shall be rejected where the request is not `PENDING`.

**FR-PAY-025** Approval shall accept, as fully admin-entered values: basic salary, house-rent allowance, allowances, overtime pay, performance pay, expenses pay; provident fund, ESI, professional tax, tax deducted at source, health insurance, salary advance and other deductions; the loss-of-pay day count; and optional overrides for company name, logo, tax registration number and address, bank name and account, designation, department, working days, pay date, employee name, employee code and a decision note.

**FR-PAY-026** On approval, gross shall be the sum of the six earning members; total deductions the sum of the seven deduction members, with salary advance treated as a deduction line; and net pay the difference. All shall be scaled to two places.

**FR-PAY-027** Approval shall reuse an existing payslip row for the month where one exists, shall mark the payslip's source as request-driven, shall store every admin-entered value so the download reproduces exactly that format, shall render the PDF using the entered display name and code, shall link the payslip to the request, and shall close the request as `APPROVED` recording the decider, the timestamp and the note.

**FR-PAY-028** Approval shall notify the requester that the payslip is ready to download, in the feed and by SMS.

**FR-PAY-029** Holders of `PAYROLL_RUN` shall be able to upload a company logo for use on payslips.

### 11.4 Payslip Access

**FR-PAY-030** An employee shall be able to list their own payslips, most recent year and month first.
**FR-PAY-031** Holders of `PAYROLL_VIEW` shall be able to list another employee's payslips and to read the payslip summary map for a month keyed by employee.
**FR-PAY-032** A payslip shall be readable by its owner; a non-privileged caller requesting another employee's payslip shall be rejected.
**FR-PAY-033** A payslip PDF shall be downloadable by its owner, and by privileged callers. A non-privileged caller requesting another employee's PDF shall be rejected.
**FR-PAY-034** A payslip PDF shall be regenerated at download time, so that corrected designation, department and bank metadata are always reflected.

### 11.5 Payslip Document

**FR-PAY-035** The payslip PDF shall carry the company logo, name, address and tax registration number; a metadata grid identifying the employee, code, designation, department, pay period, pay date, working days, loss-of-pay days, bank name and account; an earnings and deductions table with totals; the net pay rendered in words; and a signature block naming the employer signatory, their title and contact.

**FR-PAY-036** The PDF shall embed fonts capable of rendering the currency symbol and Indian-language text.

### 11.6 Payroll Runs

**FR-PAY-037** Holders of `PAYROLL_RUN` shall be able to create a payroll run for a month and a year. A second run for the same month shall be rejected.

**FR-PAY-038** A created run shall be recorded with status `PREVIEW`, the initiator and the run timestamp.

**FR-PAY-039** A run shall attempt to generate a payslip for every enabled employee and shall link each generated payslip to the run. Employees for whom generation is not possible — for example those without an active salary structure — shall be skipped without failing the run.

**FR-PAY-040** Holders of `PAYROLL_RUN` shall be able to confirm a run. A run not in `PREVIEW` shall be rejected. Confirmation shall move it to `CONFIRMED`.

**FR-PAY-041** Holders of `PAYROLL_APPROVE` shall be able to give finance approval to a run. A run not in `CONFIRMED` shall be rejected. Approval shall move it to `FINANCE_APPROVED` and shall record the approver and the timestamp.

**FR-PAY-042** Finance approval shall notify every employee whose payslip belongs to the run that their payslip for that month is ready, linking to the payslips screen.

**FR-PAY-043** Holders of `PAYROLL_RUN` or `PAYROLL_APPROVE` shall be able to list runs, most recent period first, and to read one run with its payslips, each naming the employee and their code.

**FR-PAY-044** The run status sequence shall be `PREVIEW` → `CONFIRMED` → `FINANCE_APPROVED` → `PAID`.

---

## 12. Task Management

### 12.1 Assignment

**FR-TSK-001** Holders of `USER_MANAGE` or `TASK_ASSIGN` shall be able to assign a task, supplying a title, a description, an assignee, a due date, a priority and optional team-batch identity.

**FR-TSK-002** Assignment scope shall be enforced by capability:
- a holder of `USER_MANAGE` shall be unrestricted;
- a holder of `TASK_VIEW_ALL` without `USER_MANAGE` — that is, HR — shall be able to assign **only to Team Leaders**, of any team, and an attempt otherwise shall be rejected with that message;
- any other holder of `TASK_ASSIGN` — that is, a Team Leader — shall be able to assign **only within their own team**, matched by designation title, and an attempt otherwise shall be rejected with that message.

**FR-TSK-003** A due date earlier than today shall be rejected.

**FR-TSK-004** Priority shall be one of `LOW`, `MEDIUM` or `HIGH`, matched case-insensitively, defaulting to `MEDIUM` for any other value.

**FR-TSK-005** A task shall be created with status `PENDING`, recording the assignee, the assigner and the title trimmed of surrounding whitespace.

**FR-TSK-006** Where a team batch identifier is supplied, it shall be recorded together with an optional team name, so the members of one team assignment remain identifiable as a group.

**FR-TSK-007** Assignment shall notify the assignee in the feed, pushed live, linking to the tasks screen; and shall send an SMS naming the assignee, the task title, their employee code and the due date where one exists.

### 12.2 Progress and Completion

**FR-TSK-008** An assignee shall be able to mark their own task complete. Completing another employee's task shall be rejected.

**FR-TSK-009** Completion shall set status `COMPLETED`, progress to 100 and the completion timestamp, and shall be idempotent.

**FR-TSK-010** Completion shall notify the assigner in the feed and by SMS, naming who completed which task.

**FR-TSK-011** An assignee shall be able to report progress between 0 and 100 on their own task; values outside the range shall be clamped. Reporting progress on another employee's task shall be rejected.

**FR-TSK-012** Progress shall drive status: 100 shall mean `COMPLETED` with a completion timestamp; any value above zero shall mean `IN_PROGRESS`; zero shall mean `PENDING`. Moving away from completion shall clear the completion timestamp.

**FR-TSK-013** Task state shall be **forward-only**. A move to an earlier state — completed back to in-progress or pending, or in-progress back to pending — shall be rejected with a message naming both states. The rule shall be enforced by the API and not only by the interface.

**FR-TSK-014** Progress reporting shall notify the assigner of the new percentage in the feed.

### 12.3 Editing and Removal

**FR-TSK-015** Holders of `USER_MANAGE` or `TASK_ASSIGN` shall be able to edit a task's title, description, priority, due date and status. A non-administrator shall be able to edit only tasks they assigned; an attempt otherwise shall be rejected.

**FR-TSK-016** An edit that changes status shall obey the forward-only rule. Where a task is set to in-progress and its progress is zero or already complete, progress shall be set to a mid-point value; otherwise the assignee's reported progress shall be preserved.

**FR-TSK-017** An edit shall notify the assignee in the feed and by SMS, naming the editor, the task and the due date where one exists.

**FR-TSK-018** Holders of `USER_MANAGE` shall be able to delete a task, and to delete every member task belonging to one team assignment by its batch identifier. Deleting a batch that holds no tasks shall report not found.

### 12.4 Views and Export

**FR-TSK-019** An employee shall be able to list their own tasks, most recent first, each naming the assigner.

**FR-TSK-020** Holders of `USER_MANAGE`, `TASK_ASSIGN` or `TASK_VIEW_ALL` shall be able to read tasks grouped by employee, most recent first within each group, each group reporting the employee's name, code and division together with total, pending and completed counts.

**FR-TSK-021** The grouped view shall be scoped: holders of `USER_MANAGE` or `TASK_VIEW_ALL` shall see everyone; any other viewer — that is, a Team Leader — shall see only employees sharing their designation title.

**FR-TSK-022** The grouped view shall support filtering by division, canonicalising display labels, and by a free-text query matched against the employee's name or code.

**FR-TSK-023** Holders of `USER_MANAGE` or `TASK_VIEW_ALL` shall be able to export tasks to a spreadsheet, filtered by division and by an assigned-date window, with columns: employee, employee code, team, task, description, status, priority, due date, assigned date and assigned by.

**FR-TSK-024** The exported team name shall be the batch team name where present, otherwise the employee's designation title.

**FR-TSK-025** Export column widths shall be auto-sized.

---

## 13. Work Reports

**FR-WRP-001** An employee shall be able to record a daily work report carrying a work date, a project name, hours worked and a task or module description.

**FR-WRP-002** Absent hours shall be stored as zero.

**FR-WRP-003** An employee shall be able to list their own reports, most recent date first and, within a date, most recent entry first.

**FR-WRP-004** An employee shall be able to edit and delete their own reports; editing or deleting another employee's report shall be rejected.

**FR-WRP-005** Holders of `REPORT_VIEW` or `USER_MANAGE` shall be able to read all reports grouped by employee, most recent first within each group, each group reporting the employee's name, code, the number of rows and the sum of hours.

**FR-WRP-006** The grouped view shall support a free-text query matched against the employee's name or code.

**FR-WRP-007** A Team Leader shall be able to read the grouped view restricted to their own team, matched by designation title; another team's reports shall never be exposed.

**FR-WRP-008** Holders of `REPORT_VIEW` or `USER_MANAGE` shall be able to export all reports to a spreadsheet, optionally filtered by a date window, with columns: date, employee, employee code, team, project, hours and task or module.

---

## 14. Travel and Expense Claims

### 14.1 Travel Claims

**FR-CLM-001** An employee shall be able to submit a travel claim carrying a date, a location, a category, starting and ending odometer readings, total kilometres, hills and plains kilometres, a computed total amount, bus fare, other amounts, a gross total, remarks, a fuel-receipt path and additional photograph paths.

**FR-CLM-002** A submitted claim shall be created with status `PENDING`.

**FR-CLM-003** Submission shall notify every approver — the union of `CLAIM_APPROVE` and `USER_MANAGE` holders, de-duplicated — in the feed, pushed live, linking to the claims screen, and by SMS, naming the submitter, the claim date and the gross total.

**FR-CLM-004** An employee shall be able to upload claim attachments and shall receive their stored paths.

**FR-CLM-005** An employee shall be able to correct their own claim **while it is still pending**; correcting a reviewed claim shall be rejected with that explanation, and correcting another employee's claim shall be rejected as access denied.

**FR-CLM-006** Holders of `CLAIM_APPROVE`, `USER_MANAGE` or `DASHBOARD_EXEC` shall be able to correct any claim regardless of its decision, since a wrong amount otherwise stays wrong and they answer for it.

**FR-CLM-007** Where the owner corrects a claim, every approver shall be notified that it changed. Where somebody else corrects it, the owner shall be notified in the feed and by SMS, naming who corrected it, the date and the amount.

**FR-CLM-008** An employee shall be able to list their own claims, most recent date first.

**FR-CLM-009** A Team Leader shall be able to list claims raised by their own team, matched by designation title, most recent date first; other teams' claims shall never be exposed. Where the caller has no team, only their own claims shall be returned.

**FR-CLM-010** Holders of `USER_MANAGE`, `DASHBOARD_EXEC` or `CLAIM_APPROVE` shall be able to list every claim.

**FR-CLM-011** Holders of `USER_MANAGE`, `DASHBOARD_EXEC` or `CLAIM_APPROVE` shall be able to set a claim's status to `APPROVED`, `REJECTED` or `PENDING`; any other value shall be rejected. A rejection shall require a reason.

**FR-CLM-012** A decision shall record the status, the comment, the decider and the decision timestamp; and, where the status is not pending, shall notify the owner of the outcome and the reason in the feed and by SMS.

**FR-CLM-013** An employee shall be able to delete their own claim; an administrator shall be able to delete any claim; any other deletion shall be rejected as access denied.

**FR-CLM-014** A claim row shall report the owner's name, employee code and designation title, the decision comment, the decider's name and the decision timestamp.

**FR-CLM-015** The system shall hold configurable per-kilometre rates for hills and plains travel as system settings.

**FR-CLM-016** The system shall be able to render a printable claim invoice.

### 14.2 General Expense Claims

**FR-CLM-017** An employee shall be able to submit a general expense claim carrying a category, an amount, a claim date and a receipt path, created with manager and finance statuses both `PENDING`.
**FR-CLM-018** An employee shall be able to list their own general expense claims, most recent first.
**FR-CLM-019** The system shall be able to list general expense claims awaiting either a manager or a finance decision.
**FR-CLM-020** The system shall support a manager decision and a finance decision on a general expense claim, recorded independently.

---

## 15. Asset Management

**FR-AST-001** Holders of `ASSET_MANAGE` shall be able to register an asset carrying a category of `IT`, `INFRA` or `MACHINERY`, an asset type, a brand, a model, a serial number, a registration number, a purchase date and cost, warranty and maintenance-contract expiry dates, a site reference, a depreciation rate and a quantity.

**FR-AST-002** An asset code shall be generated automatically with a category-derived prefix — `INF` for infrastructure, `MCH` for machinery, `AST` otherwise — followed by the current year and a five-digit sequence, and shall be unique.

**FR-AST-003** Quantity shall default to one. An asset registered with a quantity of zero or less shall be `OUT_OF_STOCK`; otherwise `IN_STOCK`.

**FR-AST-004** A QR tag shall be generated and attached on registration, and shall be downloadable as a PNG image encoding the asset code with an identifying prefix, at a size suitable for label printing.

**FR-AST-005** Holders of `ASSET_MANAGE` shall be able to browse assets, paged, filtered by status or by category.

**FR-AST-006** Any signed-in user shall be able to read one asset by identifier and to look an asset up by its code.

**FR-AST-007** Holders of `ASSET_MANAGE` shall be able to allocate an asset to an employee. Allocation shall be rejected where the asset is out of stock, where its quantity is exhausted, or where it is retired or lost, naming the reason.

**FR-AST-008** Allocation shall create an allocation record with an allocation timestamp, shall decrement the quantity, shall mark the asset out of stock when the quantity reaches zero, shall record the assignee and shall update the modification timestamp.

**FR-AST-009** Allocation shall notify the assignee in the feed and by SMS, naming the asset type and code and requesting acknowledgement of receipt.

**FR-AST-010** The assignee shall be able to acknowledge receipt. Acknowledgement shall require an active allocation and shall be rejected for anybody other than the assignee. It shall record the acknowledgement and its timestamp.

**FR-AST-011** Holders of `ASSET_MANAGE` shall be able to record a return with a condition. A return shall require an active allocation.

**FR-AST-012** A return shall set the return timestamp and condition, shall increment the quantity, shall clear the assignee, and shall set the asset status from the condition: `LOST` for a lost item, `UNDER_REPAIR` for a damaged item, `IN_STOCK` otherwise. An absent condition shall be treated as good.

**FR-AST-013** An employee shall be able to list the assets currently allocated to them.

**FR-AST-014** Holders of `ASSET_MANAGE` shall be able to delete an asset; allocation history shall be removed first so that referential constraints do not block the deletion.

**FR-AST-015** The system shall hold a maintenance log per asset carrying a fault description, a vendor, an estimated turnaround, a cost, a status, a scheduled date and preventive intervals in days and hours.

**FR-AST-016** The system shall hold a software licence register carrying a product name, a vendor, a licence key, a licence type, seats purchased and consumed, and an expiry date.

**FR-AST-017** Asset statuses shall be `IN_STOCK`, `ASSIGNED`, `OUT_OF_STOCK`, `UNDER_REPAIR`, `RETIRED`, `LOST`, `DEPLOYED` and `BREAKDOWN`.

---

## 16. Support Helpdesk

**FR-HD-001** Any signed-in user shall be able to raise a support ticket carrying a title, a description, attachments, a type, a priority and an addressed recipient.

**FR-HD-002** A ticket code shall be generated as a fixed prefix, the current year and a five-digit sequence, and shall be unique.

**FR-HD-003** The ticket type shall default to `IT` and shall be stored upper-cased. The priority shall default to `MEDIUM` and shall be stored upper-cased.

**FR-HD-004** The ticket **category shall be derived from the raiser's division**, not chosen manually: `Infra` for Civil staff, `Digital` for IT staff.

**FR-HD-005** A ticket shall be created with status `OPEN` and an SLA due time derived from the priority: 4 hours for critical, 8 hours for high, 24 hours for medium and 48 hours otherwise.

**FR-HD-006** Where the ticket names a recipient other than the raiser, that person shall be notified in the feed, pushed live, linking to the helpdesk screen, and by SMS, naming the raiser, the code and the title.

**FR-HD-007** Any signed-in user shall be able to obtain the list of valid recipients for their own ticket, computed by the routing rule of §4.6, each entry carrying the identifier, name, employee code and designation. Nobody shall appear in their own list.

**FR-HD-008** A raiser shall be able to correct their own ticket's title, description, attachments, type, priority and recipient **only while it is still open**. Once an agent has begun work the attempt shall be rejected with a message naming the current state and directing the raiser to add a reply instead.

**FR-HD-009** The ticket code, the category and the status shall not be alterable by the raiser.

**FR-HD-010** Changing the priority shall recompute the SLA due time.

**FR-HD-011** Correcting a ticket shall notify its current recipient that the details changed; where the recipient was changed, the previous recipient shall also be notified that it was handed on.

**FR-HD-012** A raiser shall be able to browse their own tickets, paged, most recent first.

**FR-HD-013** Holders of `HELPDESK_AGENT` shall be able to browse their queue — tickets addressed to them, or every ticket of a given status when a status filter is supplied — paged, most recent first.

**FR-HD-014** Holders of `HELPDESK_AGENT`, `USER_MANAGE` or `DASHBOARD_EXEC` shall be able to browse all tickets, paged, optionally filtered by status.

**FR-HD-015** Any signed-in user shall be able to read one ticket with its comment thread in chronological order.

**FR-HD-016** Any signed-in user shall be able to add a comment with an optional attachment. The counterparty — the recipient when the author is the raiser, the raiser otherwise — shall be notified in the feed and by SMS, unless they are the author.

**FR-HD-017** Holders of `HELPDESK_AGENT` shall be able to change a ticket's status. The raiser shall never be able to decide their own ticket; the attempt shall be rejected with an explanation that the person it was sent to will handle it.

**FR-HD-018** Where a ticket names a recipient, only that recipient shall be able to move it along; another agent's attempt shall be rejected. A ticket predating recipient recording shall remain open to any agent.

**FR-HD-019** Valid statuses shall be `OPEN`, `IN_PROGRESS`, `AWAITING_PARTS`, `RESOLVED` and `CLOSED`; any other value shall be rejected.

**FR-HD-020** Status transitions shall move forward only, one step at a time, with the single exception that `IN_PROGRESS` may move either to `AWAITING_PARTS` or directly to `RESOLVED`. Any other transition shall be rejected, naming both states.

**FR-HD-021** A status change may reassign the ticket. Reaching `RESOLVED` or `CLOSED` shall set the resolution timestamp.

**FR-HD-022** A status change shall notify the raiser in the feed and by SMS, naming the ticket code and the new state.

**FR-HD-023** Only the raiser shall be able to rate a ticket, and only once it is resolved or closed; other attempts shall be rejected.

**FR-HD-024** A ticket row shall report the raiser's name and employee code, the recipient's name and, on the detail view, the comment thread with each author's name.

**FR-HD-025** Any signed-in user shall be able to upload ticket attachments and shall receive their stored paths.

---

## 17. Complaints and Needs

**FR-CMP-001** Any signed-in user shall be able to submit either a **complaint** or a **need**, carrying a category, a subject, a description, a priority and an addressed recipient.

**FR-CMP-002** The kind shall be `COMPLAINT` or `NEED`, defaulting to complaint. The priority shall be `LOW`, `MEDIUM` or `HIGH`, defaulting to medium. Unrecognised values shall fall back to the default rather than being rejected.

**FR-CMP-003** Subject and description shall be trimmed of surrounding whitespace; a blank category shall be stored as absent.

**FR-CMP-004** A reference code shall be generated as a fixed prefix, the current year and a five-digit sequence, derived by **incrementing the highest existing code for the year** rather than by counting rows, so that deleting a row can never regenerate a used code.

**FR-CMP-005** A submission shall be created with status `OPEN`.

**FR-CMP-006** Where a recipient was named, only that person shall be notified. Where none was named, every HR and administrator — the de-duplicated union of `COMPLAINT_MANAGE` and `USER_MANAGE` holders — shall be notified. The submitter shall never be notified of their own submission.

**FR-CMP-007** Notification shall be recorded in the feed, pushed live, linked to the complaints screen, and sent by SMS, naming the submitter, the kind and the reference code.

**FR-CMP-008** Any signed-in user shall be able to obtain the list of valid recipients, computed by the routing rule of §4.6, each entry carrying the identifier, name, employee code and a role label; somebody holding both HR and administrative capability shall be labelled with the wider of the two. The requester shall be excluded. This list shall be available to every signed-in user because the full directory is not theirs to read.

**FR-CMP-009** A submitter shall be able to browse their own submissions, paged, most recent first.

**FR-CMP-010** Holders of `USER_MANAGE` or `COMPLAINT_MANAGE` shall be able to browse all submissions, paged, optionally filtered by status and by kind, and to read one submission.

**FR-CMP-011** Holders of `USER_MANAGE` or `COMPLAINT_MANAGE` shall be able to respond, setting a status of `OPEN`, `IN_REVIEW`, `RESOLVED` or `REJECTED` — any other value shall be rejected — and recording a response, the handler and the update timestamp.

**FR-CMP-012** Reaching `RESOLVED` or `REJECTED` shall set the resolution timestamp; returning to an earlier status shall clear it.

**FR-CMP-013** A response shall notify the submitter of the new state in the feed and by SMS.

**FR-CMP-014** A submission row shall report the submitter's name and employee code, their team, the handler's name and the named recipient's name.

---

## 18. Safety Incident Management

**FR-SAF-001** Any signed-in user shall be able to report a safety incident carrying an incident type, a description, a zone, an anonymity flag, a severity and an occurrence timestamp.

**FR-SAF-002** The incident type shall be one of `NEAR_MISS`, `MINOR_INJURY`, `MAJOR_INJURY`, `PROPERTY_DAMAGE` or `ENV_HAZARD`, defaulting to near miss. The severity shall be `LOW`, `MEDIUM`, `HIGH` or `CRITICAL`, defaulting to medium. Unrecognised values shall fall back to the default.

**FR-SAF-003** A reference code shall be generated as a fixed prefix, the current year and a five-digit sequence.

**FR-SAF-004** A report shall be created with status `OPEN`; the description shall be trimmed and a blank zone stored as absent.

**FR-SAF-005** Every holder of `REPORT_VIEW` other than the reporter shall be notified in the feed, pushed live, linking to the safety screen, and by SMS, naming the reference code and the incident type in readable form.

**FR-SAF-006** Where the report is marked anonymous, the reporter shall be presented as "Anonymous" in every notification and in every view.

**FR-SAF-007** A reporter shall be able to browse their own reports, paged, most recent first.

**FR-SAF-008** Holders of `REPORT_VIEW` shall be able to browse all incidents, paged, optionally filtered by status and by incident type, and to read one incident.

**FR-SAF-009** Holders of `REPORT_VIEW` shall be able to record an investigation outcome, setting a status of `OPEN`, `INVESTIGATING`, `RESOLVED` or `CLOSED` — any other value shall be rejected — with resolution notes, recording the resolver.

**FR-SAF-010** Reaching `RESOLVED` or `CLOSED` shall set the resolution timestamp; returning to an earlier status shall clear it.

**FR-SAF-011** An update shall notify the reporter of the new state in the feed and by SMS, unless the report was anonymous and no reporter is recorded.

---

## 19. Onboarding and Offboarding

**FR-ONB-001** Holders of `USER_MANAGE` shall be able to start onboarding for an employee. Starting it twice shall be rejected.

**FR-ONB-002** Starting onboarding shall create a checklist with status `IN_PROGRESS` and a start timestamp, and shall populate it with the standard tasks: Document Collection, Bank Details, IT Asset Assignment, Team Introduction and Security Training.

**FR-ONB-003** The system shall be able to list the employees currently in onboarding — those holding a checklist in progress.

**FR-ONB-004** The system shall be able to read an employee's checklist with its tasks, each reporting its name, description, completion state and completion timestamp.

**FR-ONB-005** A checklist task shall be markable complete, recording the completion timestamp. Completing a task that does not belong to the employee's checklist shall be rejected.

**FR-ONB-006** When every task on a checklist is complete, the checklist shall move to `COMPLETED` with a completion timestamp.

**FR-ONB-007** Offboarding shall be recorded as described in §6.9, carrying the relieving date, the reason, notes and a full-and-final status.

---

## 20. Performance Management

**FR-PRF-001** An employee shall be able to record a performance goal carrying a title, a description, a progress percentage and a status, defaulting to active.
**FR-PRF-002** An employee shall be able to list their own goals.
**FR-PRF-003** The system shall support a performance review for an employee and a manager covering a review period, carrying a self rating and comment, a manager rating and comment, and a status defaulting to draft.
**FR-PRF-004** An employee shall be able to list their own reviews.
**FR-PRF-005** A manager shall be able to list their team's reviews.

---

## 21. Dashboards and Analytics

### 21.1 Personal Dashboard

**FR-DSH-001** Every signed-in user shall have a personal dashboard reporting: their name and employee code; whether they have punched in today with the punch-in and punch-out times and worked minutes; their current-year leave balances; the number of their pending leave requests; the number of their tickets not yet closed; the number of assets assigned to them; and their five most recent notifications.

### 21.2 Executive Dashboard

**FR-DSH-002** Holders of `DASHBOARD_EXEC` shall have an executive dashboard, optionally filtered to one division, reporting: headcount, present today, attendance percentage, pending leave approvals, open tickets, assets assigned and assets in stock, together with departmental, attendance-trend, leave-utilisation and payroll-cost breakdowns.

**FR-DSH-003** Headcount shall exclude offboarded staff, so that the total and the attendance percentage agree with each other and with the administrative views.

**FR-DSH-004** Present-today, pending-approval and open-ticket counts shall be restricted to records whose owner falls within the selected division, with an unset filter meaning the whole organisation.

**FR-DSH-005** The attendance percentage shall be present today divided by headcount, expressed as a percentage rounded half-up to one decimal place, and shall be zero when headcount is zero.

**FR-DSH-006** Division membership shall be resolved once per request into an in-memory index so that per-record filtering does not repeatedly query the database.

### 21.3 Celebrations

**FR-DSH-007** Every signed-in user shall be able to read upcoming celebrations: birthdays and work anniversaries, optionally restricted to one division.

**FR-DSH-008** Only enabled, non-offboarded employees shall be considered.

**FR-DSH-009** An occurrence shall be projected onto the current year and rolled forward to the next year where it has already passed; 29 February shall be treated as 1 March.

**FR-DSH-010** Occurrences more than **60 days** away shall be excluded, so that the panel shows what is coming with enough notice for an anniversary worth marking.

**FR-DSH-011** An anniversary shall be reported only from the first completed year, and shall carry the number of completed years.

**FR-DSH-012** Results shall be ordered by days remaining and limited to **twelve** entries, with the division filter applied **before** the limit so that narrowing to a division returns twelve of that division.

**FR-DSH-013** Each entry shall carry the employee's identifier, name, employee code, team, photograph, the occurrence type, the date, the days remaining and, for anniversaries, the completed years.

### 21.4 Side-service Analytics

**FR-DSH-014** The biometric side service shall expose per-employee analytics and organisation-wide executive analytics, the latter accepting a division filter.

---

## 22. Notifications and Alerts

**FR-NOT-001** The system shall persist every notification with a recipient, a title, a body, a type, a target link and a read flag.

**FR-NOT-002** Notification creation shall be asynchronous, so that no business action — a leave approval, an asset allocation, a task assignment — is ever delayed by delivery.

**FR-NOT-003** Each notification shall be pushed to the recipient's user-scoped queue and to a per-user public topic, so that clients subscribing either way receive it.

**FR-NOT-004** A user shall be able to browse their notifications, paged, most recent first; to read their unread count; to mark one as read; and to mark all as read.

**FR-NOT-005** Marking a notification read shall apply only to the caller's own notification.

**FR-NOT-006** Notification types shall be at least: `LEAVE`, `PERMISSION`, `TASK`, `HELPDESK`, `CHAT`, `CELEBRATION`, `ASSET`, `PAYROLL`, `PAYSLIP`, `CLAIM`, `COMPLAINT`, `SAFETY`, `CALENDAR`, `ANNOUNCEMENT` and `SYSTEM`. Each shall be rendered with a distinguishing icon and colour.

**FR-NOT-007** A scheduled job shall run **once daily at 09:00 server time** and shall notify every active employee of every birthday and work anniversary falling that day.

**FR-NOT-008** The celebrant shall not be notified of their own day.

**FR-NOT-009** The daily celebration job shall additionally send one bulk SMS to the whole active workforce excluding the celebrant.

**FR-NOT-010** The web client shall subscribe to the live channel on sign-in, shall surface new notifications immediately without a page reload, and shall invalidate the affected cached queries so that the underlying screens refresh themselves.

**FR-NOT-011** Notification bodies shall be human-readable and shall name the subject, the action and the relevant dates or amounts.

---

## 23. Internal Communication

### 23.1 Channels

**FR-CHT-001** Administrators shall be able to create a community channel with a name, a description and an announcement flag; the creator shall be added as a member automatically.

**FR-CHT-002** A channel name that duplicates an existing name, case-insensitively, shall be rejected; a name using the reserved direct-message prefix shall be rejected.

**FR-CHT-003** Administrators shall be able to delete a channel and to add or remove members.

**FR-CHT-004** Any signed-in user shall be able to list the channels available to them and the channels they belong to, and to read a channel's members.

**FR-CHT-005** An organisation-wide announcement channel shall exist, and every newly created employee shall be enrolled in it automatically.

**FR-CHT-006** Direct one-to-one conversations shall be backed by hidden two-member rooms named with a reserved prefix, found or created on demand, kept out of the administrative channel listing, and guaranteed to contain exactly the two participants.

**FR-CHT-007** Starting a conversation with oneself shall be rejected.

**FR-CHT-008** Team conversations shall be backed by private per-team rooms named with a reserved prefix derived from the team title, found or created on demand, with every teammate enrolled. A user with no team shall be told they are not yet assigned to one. Team rooms shall be kept out of the general chat listing.

**FR-CHT-009** Any signed-in user shall be able to list their contacts for starting a conversation.

### 23.2 Messages

**FR-CHT-010** A member shall be able to read a channel's messages and to post a text message.
**FR-CHT-011** A member shall be able to post a voice note and file attachments.
**FR-CHT-012** A member shall be able to delete a message they sent.
**FR-CHT-013** New messages shall be pushed live to channel members and shall raise a notification of type `CHAT`.
**FR-CHT-014** Chat events shall be publishable to and consumable from an event stream, so that delivery can scale beyond a single application instance; absence of the stream shall not prevent direct delivery.
**FR-CHT-015** The system shall relay call signalling messages between participants to support voice or video calling.
**FR-CHT-016** Attachments shall respect the configured per-file and per-request size limits, accommodating short video clips.

---

## 24. AI Assistant

**FR-BOT-001** The system shall provide a conversational assistant available from every authenticated screen.

**FR-BOT-002** The assistant shall report its own capability set — whether it is enabled, which languages it offers, and whether speech synthesis and transcription are available — so that the interface only offers what is actually configured.

**FR-BOT-003** The assistant shall support at least English, Tamil and Hindi, and shall greet the user in the selected language by name.

**FR-BOT-004** The assistant shall answer questions using a built-in guide to the system's own functionality, augmented by an administrator-curated knowledge base.

**FR-BOT-005** The assistant shall retain a bounded conversation history — at most **8** turns — and shall bound the knowledge context it supplies to the model to at most **6 000** characters.

**FR-BOT-006** The assistant shall be able to answer with organisation context for privileged callers, and shall withhold that context otherwise.

**FR-BOT-007** The assistant shall expose a translation function.

**FR-BOT-008** The assistant shall expose speech synthesis, selecting a voice and model appropriate to the requested language and enforcing the correct model when a non-English language is requested.

**FR-BOT-009** The assistant shall expose speech-to-text over an uploaded audio file with an optional language hint.

**FR-BOT-010** Holders of `USER_MANAGE` shall be able to read and update assistant settings: the master enable switch, the chosen language-model provider, provider keys and model names for chat, transcription and speech, the crawl provider key, and the organisation website address.

**FR-BOT-011** Provider keys shall never be returned in full; any display shall be masked to a four-character prefix and suffix, or fully masked for short values.

**FR-BOT-012** A blank or absent setting value shall be ignored on update, so that a key is never wiped by accident.

**FR-BOT-013** Holders of `USER_MANAGE` shall be able to ingest a website into the knowledge base, bounded to at most **12 000** characters per ingestion, and shall be able to list and delete knowledge documents.

**FR-BOT-014** Where a provider is unreachable or unconfigured, the assistant shall degrade gracefully with an explanatory reply rather than failing the request.

---

## 25. Reports and Data Export

**FR-RPT-001** Holders of `REPORT_VIEW` shall be able to export an attendance report for a date range with an optional department filter, as a spreadsheet, delivered as a file attachment with a meaningful filename.

**FR-RPT-002** Holders of `REPORT_VIEW` shall be able to export a leave report for a date range with an optional department filter, as a spreadsheet attachment.

**FR-RPT-003** Holders of `REPORT_VIEW` shall be able to export a payroll report for a month and a year, as a spreadsheet attachment.

**FR-RPT-004** Task and work-report exports shall be provided as specified in §12.4 and §13.

**FR-RPT-005** Every export shall be generated in memory and streamed; no temporary file shall be left behind.

**FR-RPT-006** Every export shall carry a header row and auto-sized columns.

---

## 26. Calendar and Holidays

**FR-CAL-001** Any signed-in user shall be able to read the holiday calendar, optionally for a single year, ordered by date ascending.
**FR-CAL-002** Holders of `ORG_MANAGE` or `CALENDAR_MANAGE` shall be able to add a holiday with a name and a date, and to delete one.
**FR-CAL-003** Adding a holiday shall notify **every** active, non-offboarded employee in the feed, linking to the calendar, and shall send one bulk SMS naming the entry and its date.
**FR-CAL-004** Holidays shall be excluded from working-day counts everywhere they are computed — leave working days, monthly attendance summaries, loss-of-pay day counts and loss-of-pay previews.
**FR-CAL-005** The system shall be seeded with a national and regional holiday calendar so that the calendar screen carries real content from first run, each entry inserted only when the same date and name are absent.
**FR-CAL-006** The calendar screen shall present holidays alongside approved leave, so that absence is visible in context.

---

## 27. File Storage and Documents

**FR-FIL-001** The system shall store uploaded files under a configured storage root, creating it at startup where absent.
**FR-FIL-002** An uploaded file shall be stored under a folder-and-month path with a generated unique name preserving the original extension, and the relative path shall be returned to the caller.
**FR-FIL-003** An empty upload shall be rejected.
**FR-FIL-004** Generated artefacts — payslip PDFs and QR images — shall be written by the same mechanism under their own folders.
**FR-FIL-005** Stored files shall be readable by relative path; a missing file shall report not found.
**FR-FIL-006** File paths shall be normalised so that a request cannot escape the storage root.
**FR-FIL-007** File retrieval shall be publicly reachable so that images and documents render without a bearer token, and shall serve content as an opaque byte stream that is never executed or interpreted.
**FR-FIL-008** The storage mechanism shall be swappable — local disk, or an object store — behind a single abstraction, selected by configuration, without changes to callers.
**FR-FIL-009** Employee documents shall be recorded on the employee as a comma-separated list of stored paths, the same shape attachments take elsewhere in the system.

---

## 28. System Settings

**FR-SET-001** The system shall hold configurable settings as key-value pairs with a description and an update timestamp.
**FR-SET-002** Any signed-in user shall be able to read the settings map.
**FR-SET-003** Holders of `USER_MANAGE` shall be able to update settings.
**FR-SET-004** Settings shall include at least: the assistant enable switch, the assistant language-model provider, provider keys and model names for chat, transcription and speech synthesis, the crawl provider key, the organisation website address, and the per-kilometre travel rates for hills and plains.
**FR-SET-005** Settings holding secrets shall never be returned in full to a client.

---

## 29. Mobile Application

**FR-MOB-001** The mobile application shall support the four things employees most need away from a desk: their dashboard, attendance punching, leave, and payslips.
**FR-MOB-002** The mobile application shall authenticate against the same API and shall store credentials in the platform's secure store.
**FR-MOB-003** The mobile application shall present tabbed navigation across Home, Attendance, Leave and Payslips, and shall offer sign-out.
**FR-MOB-004** The mobile application shall obtain the device's location for a punch and shall submit it with the punch.
**FR-MOB-005** The mobile application shall present the signed-in employee's dashboard, leave balances and history, and payslip list with download.
**FR-MOB-006** The mobile application shall be built from a single codebase for both mobile platforms.
**FR-MOB-007** The mobile application shall show the sign-in screen whenever no authenticated session exists.

---

## 30. Biometric and Document Intelligence Service

**FR-BIO-001** The side service shall expose a health or root endpoint.
**FR-BIO-002** The side service shall enrol a face image against an employee identifier and shall persist the enrolment.
**FR-BIO-003** The side service shall verify a submitted face image against an employee's enrolment and shall report the match outcome.
**FR-BIO-004** The side service shall extract numeric text from an uploaded image.
**FR-BIO-005** The side service shall expose per-employee and executive analytics as described in §21.4.
**FR-BIO-006** The side service shall initialise its recognition models once at startup rather than per request.
**FR-BIO-007** The side service shall restrict cross-origin access to the configured web origins.
**FR-BIO-008** The side service shall be optional; the platform shall function fully without it, with dependent features reporting themselves unavailable.
**FR-BIO-009** The side service shall not be publicly reachable; access shall be routed through the single public entry point.

---
---

# Part III — Data, Rules and Quality

## 31. Data Requirements

### 31.1 Logical Entity Inventory

**DR-004** The system shall hold the following persistent entities, grouped by subject area:

| Area | Entities |
|---|---|
| Identity & security | `users`, `roles`, `permissions`, `role_permissions`, `user_roles`, `refresh_tokens`, `login_history`, `audit_log`, `otp_codes` |
| Employee detail | `bank_details`, `employee_documents`, `family_members`, `educations`, `experiences`, `worker_skills`, `offboarding_records` |
| Organisation | `companies`, `branches`, `departments`, `designations`, `positions`, `employment_statuses`, `blood_groups`, `office_locations`, `shifts`, `sites`, `holidays`, `system_settings` |
| Attendance | `attendance` |
| Leave | `leave_types`, `leave_balances`, `leave_requests`, `permission_requests` |
| Payroll | `salary_structures`, `payroll_runs`, `payslips`, `payslip_requests`, `investment_declarations` |
| Tasks & work | `tasks`, `work_reports` |
| Expenses | `ta_expenses`, `expense_claims` |
| Assets | `assets`, `asset_allocations`, `asset_maintenance`, `software_licenses` |
| Service management | `tickets`, `ticket_comments`, `complaints_needs`, `safety_incidents` |
| Lifecycle | `onboarding_checklists`, `onboarding_tasks` |
| Performance | `performance_goals`, `performance_reviews` |
| Engagement | `notifications`, `announcements`, `communities`, `community_members`, `community_messages` |
| AI | `chatbot_knowledge` |

### 31.2 Common Column Conventions

**DR-005** Every business entity shall carry a surrogate auto-increment primary key.
**DR-006** Auditable entities shall carry `created_by`, `updated_by`, `created_at` and `updated_at`, populated automatically.
**DR-007** Text storage shall use `utf8mb4`, so that Indian-language content and emoji are stored without loss.
**DR-008** Monetary columns shall be `DECIMAL(12,2)`; day quantities `DECIMAL(5,1)`; hour quantities `DECIMAL(4,1)` or `DECIMAL(4,2)`; coordinates `DECIMAL(10,7)`; percentages `DECIMAL(5,2)`.
**DR-009** Status and type columns shall be short variable-length strings holding upper-case codes, with the permitted set enforced in application logic.
**DR-010** Every foreign key shall declare its deletion behaviour; where a reference is intentionally weak, the application shall clear it explicitly on deletion.

### 31.3 Key Entity Definitions

**`users`** — the employee record and the identity.

| Group | Columns |
|---|---|
| Identity | `id`, `employee_code` (unique), `username` (unique), `name`, `dob`, `gender`, `aadhar` (unique), `phone` (unique), `email`, `personal_email`, `photo_path`, `documents` |
| Security | `password_hash`, `password_vault`, `enabled`, `failed_login_count`, `locked_until`, `last_login_at` |
| Address | `care_of`, `house`, `street`, `locality`, `vtc`, `district`, `state`, `country`, `pincode`, `post_office` |
| Statutory | `pan`, `pf_number`, `blood_group` |
| Contact | `alternate_phone`, `emergency_contact`, `emergency_contact_relation` |
| Employment | `blood_group_id`, `department_id`, `designation_id`, `office_location_id`, `employment_status_id`, `position_id`, `reporting_manager_id`, `site_id`, `employment_type`, `date_of_joining`, `industry` |
| Free-text titles | `designation_title`, `department_title`, `position_title`, `tech_stack` |
| Lifecycle | `profile_status` |
| Audit | `created_by`, `updated_by`, `created_at`, `updated_at` |

Indexed on department and reporting manager.

**`attendance`** — one row per employee per day, uniquely constrained on employee and work date so a duplicate punch-in cannot be recorded. Holds punch-in and punch-out timestamps, mode, in and out coordinates, site and shift references, geofence result and exception flag, status, late flag and late minutes, worked minutes and overtime minutes. Indexed on work date.

**`leave_types`** — name, unique code, maximum days per year, carry-forward, encashable, gender restriction, past-dates allowance, accrual type, minimum notice days, per-period limit, paid flag, active flag.

**`leave_balances`** — employee, leave type, year, allocated and used, uniquely constrained on the triple.

**`leave_requests`** — employee, leave type, from and to dates, working days, reason, attachment path, status, named approver, decider, decision timestamp and comment. Indexed on status and employee.

**`permission_requests`** — employee, request date, from and to times as `HH:mm`, hours, reason, status, named approver, decider, decision timestamp and comment.

**`salary_structures`** — employee, basic salary, house-rent allowance, allowances, provident-fund amount, ESI applicability, professional tax, effective-from date, active flag.

**`payroll_runs`** — pay month and year uniquely constrained, status, initiator, run timestamp, finance approver and approval timestamp.

**`payslips`** — payroll run reference, employee, pay month and year uniquely constrained together with the employee; the earnings members basic, house-rent allowance, allowances, overtime, performance and expenses; the deduction members provident fund, ESI, professional tax, tax deducted at source, health insurance, salary advance and other; gross, total deductions and net pay; loss-of-pay days; working days; pay date; the company overrides for name, logo, tax registration number and address; bank name and account; designation and department; the source marker; the generated PDF path; and the generation timestamp.

**`payslip_requests`** — employee, pay month and year, note, status, generated payslip reference, decider, decision timestamp and note.

**`tasks`** — title, description, assignee, assigner, status, priority, progress, due date, completion timestamp, team batch identifier and team name.

**`work_reports`** — employee, work date, project name, work hours, task description.

**`ta_expenses`** — employee, date, location, category, starting and ending odometer readings, total, hills and plains kilometres, total amount, bus fare, other amounts, gross total, remarks, status, fuel-receipt path, attachment paths, decision comment, decider and decision timestamp.

**`assets`** — unique asset code, category, asset type, brand, model, serial number, registration number, purchase date and cost, warranty and maintenance-contract expiry, status, quantity, site reference, assignee, QR path, depreciation rate.

**`asset_allocations`** — asset, employee, allocation timestamp, acknowledgement flag and timestamp, return timestamp and condition.

**`tickets`** — unique ticket code, raiser, title, description, attachments, type, category, priority, status, assignee, SLA due time, rating, resolution timestamp.

**`complaints_needs`** — unique reference code, raiser, kind, category, subject, description, priority, status, HR response, named recipient, handler, resolution timestamp.

**`safety_incidents`** — unique reference code, reporter, site, incident type, description, zone, anonymity flag, severity, status, occurrence timestamp, resolution notes, resolver and resolution timestamp.

**`notifications`** — recipient, title, body, type, link, read flag, creation timestamp.

**`communities`**, **`community_members`**, **`community_messages`** — channel name, description, creator, announcement flag; the membership pair with a join timestamp; and messages with sender, content, attachments, voice path and send timestamp.

### 31.4 Reference Data and Seeds

**DR-011** The system shall seed, on first run: the role catalogue, the permission catalogue and the role-permission grants; blood groups; departments; designations; employment statuses; positions; office locations with coordinates and geofence radii; shifts; project sites; the leave types with their entitlements and restrictions; a holiday calendar; announcements; and a set of demonstration accounts.

**DR-012** Seeded and administrative accounts shall exist for the board-level administrators, and the designated escalation approver shall hold administrative access.

**DR-013** Every seed shall be idempotent, inserting only where the row is absent.

**DR-014** Migrations shall be provided that clear transactional demonstration data for a fresh start without disturbing master data.

### 31.5 Audit, Retention and Integrity

**DR-015** Every sign-in attempt shall be retained with its outcome, identifier, IP address and user agent.
**DR-016** A general audit log shall be available recording the actor, the action, the entity type and identifier, free-text detail, the IP address and the timestamp, indexed on actor and on entity.
**DR-017** Every auditable entity shall retain who created and last updated it, and when.
**DR-018** Decisions shall retain the decider, the decision timestamp and the decision comment, so that every approval, rejection and status change is attributable.
**DR-019** Soft deletion shall be used where history matters — notably leave types — and hard deletion only where a record is genuinely to be expunged.
**DR-020** Generated documents shall be retained with their stored path so that a historical payslip remains retrievable.
**DR-021** Sequence-derived codes shall be generated by incrementing the observed maximum for the period, never by counting rows, so that deletion cannot cause a code to be reissued.

### 31.6 Schema Evolution

**DR-022** All schema change shall be delivered as numbered, forward-only migrations applied automatically at startup.
**DR-023** Migrations shall be permitted to baseline an existing database and to repair their own metadata, and shall not fail startup on checksum drift.
**DR-024** Where a migration must reconcile a live schema with the object model, it shall check the schema catalogue before each change so that it is safe on both an empty and a populated database.
**DR-025** The object model shall be validated against the schema at startup; a mismatch shall prevent the application from starting rather than silently corrupting data.

---

## 32. Consolidated Business Rules

### Calendar and time

| ID | Rule |
|---|---|
| **BR-001** | Sunday is the single weekly off; **Saturday is a working day**. |
| **BR-002** | Working days exclude Sundays and configured holidays. |
| **BR-003** | Office hours are 09:00–18:00 unless the assigned shift overrides the start. |
| **BR-004** | The lateness grace period is zero minutes: at or before the office start is on time; after it is late by the difference. |
| **BR-005** | Overtime accrues only past the office end; a person starting after the office end earns only from when they actually began. |
| **BR-006** | All recorded times are in the organisation's local time zone. |

### Attendance

| ID | Rule |
|---|---|
| **BR-007** | One punch-in and one punch-out per employee per day; duplicates are refused. |
| **BR-008** | A punch-out requires an existing punch-in. |
| **BR-009** | Work-from-home punches are not geofence-evaluated. |
| **BR-010** | Absence of coordinates never blocks a punch; it is recorded outside the geofence with an exception flag. |
| **BR-011** | The default geofence radius is 200 metres where the location defines none. |
| **BR-012** | Monthly working days are counted only up to today within the current month. |
| **BR-013** | Absentees are computed from actual punch and leave data regardless of weekday. |

### Leave

| ID | Rule |
|---|---|
| **BR-014** | Gender-restricted leave types are refused to a mismatched profile. |
| **BR-015** | Past-dated leave is refused unless the type explicitly allows it. |
| **BR-016** | Minimum notice applies only to future-dated leave. |
| **BR-017** | A range containing no working days is refused. |
| **BR-018** | Casual and sick leave are limited to one per calendar quarter, expressed through the type's per-period limit. |
| **BR-019** | Every type except Loss of Pay requires an allocated balance and sufficient availability, checked both at application and again at approval. |
| **BR-020** | Balance is consumed on approval, not on application. |
| **BR-021** | Cancelling an approved leave refunds the balance, floored at zero. |
| **BR-022** | Only pending or approved leave may be cancelled, and only by its owner. |
| **BR-023** | A rejection requires a reason. |
| **BR-024** | Bulk allocation never overwrites an existing balance and never allocates for a past year. |
| **BR-025** | Leave routing is single-level and duration-dependent: up to three days to the applicant's own Team Leader, beyond three days to a Manager; a Team Leader's leave to a Manager; HR's leave to one named escalation approver. |
| **BR-026** | Nobody may decide their own leave. |
| **BR-027** | Where a specific approver was named, only that person may act. |

### Hourly permissions

| ID | Rule |
|---|---|
| **BR-028** | The end time must be after the start time. |
| **BR-029** | Duration is computed to two decimal places from the minute difference. |
| **BR-030** | A pending request whose date has passed becomes overdue and can no longer be decided. |
| **BR-031** | Routing is single-level: employee to own-team Team Leader, Team Leader to HR, HR to the named escalation approver. |
| **BR-032** | Only pending requests may be cancelled, and only by their owner. |

### Payroll

| ID | Rule |
|---|---|
| **BR-033** | No payslip may exist for a month before the employee's joining month. |
| **BR-034** | Payslip generation requires an active salary structure. |
| **BR-035** | At most one payslip per employee per month. |
| **BR-036** | The provident-fund figure on a salary structure is a flat rupee amount. |
| **BR-037** | ESI is 0.75 % of gross, applied only where the structure marks it applicable and gross does not exceed the wage ceiling of 21 000. |
| **BR-038** | Overtime pay uses an hourly rate of gross monthly pay divided by 240. |
| **BR-039** | Automatic attendance-driven loss of pay is not deducted; loss of pay is entered manually so the two cannot double-count. |
| **BR-040** | The loss-of-pay **day count** is still computed from attendance for reporting, excluding Sundays, holidays and future days. |
| **BR-041** | Net pay is gross minus total deductions; salary advance is a deduction line. |
| **BR-042** | Monetary results are scaled to two places with half-up rounding. |
| **BR-043** | A payslip may only be read or downloaded by its owner, or by a privileged caller. |
| **BR-044** | A payslip PDF is regenerated at download so corrected metadata is always reflected. |
| **BR-045** | At most one payroll run per month. |
| **BR-046** | The run workflow is preview → confirmed → finance-approved → paid, and each transition validates the current state. |
| **BR-047** | A batch run skips employees for whom generation is not possible rather than failing. |
| **BR-048** | At most one pending payslip request per employee per month. |
| **BR-049** | An approved payslip request stores exactly the values the administrator entered, so the download reproduces that format. |

### Tasks

| ID | Rule |
|---|---|
| **BR-050** | HR may assign only to Team Leaders; a Team Leader may assign only within their own team; a full administrator is unrestricted. |
| **BR-051** | A due date may not be in the past. |
| **BR-052** | Task state moves forward only: pending → in progress → completed, never back. |
| **BR-053** | Progress and status stay consistent: zero is pending, 100 is completed, anything between is in progress. |
| **BR-054** | Only the assignee may report progress or complete a task. |
| **BR-055** | Only the assigner, or an administrator, may edit a task; progress and status remain the assignee's. |
| **BR-056** | A Team Leader sees only their own team's tasks. |

### Claims

| ID | Rule |
|---|---|
| **BR-057** | An owner may correct their claim only while it is pending; an approver may correct any claim at any time. |
| **BR-058** | A rejection requires a reason. |
| **BR-059** | A Team Leader sees only their own team's claims. |
| **BR-060** | An owner may delete their own claim; an administrator may delete any. |

### Assets

| ID | Rule |
|---|---|
| **BR-061** | An out-of-stock, exhausted, retired or lost asset cannot be allocated. |
| **BR-062** | Allocation decrements quantity and marks the asset out of stock at zero. |
| **BR-063** | Only the assignee may acknowledge receipt. |
| **BR-064** | A return increments quantity and sets status from the returned condition. |
| **BR-065** | Deleting an asset removes its allocation history first. |

### Helpdesk

| ID | Rule |
|---|---|
| **BR-066** | Ticket category is derived from the raiser's division, never chosen. |
| **BR-067** | The SLA due time follows the priority: 4, 8, 24 or 48 hours. |
| **BR-068** | A raiser may correct their ticket only while it is open. |
| **BR-069** | A raiser may never decide their own ticket. |
| **BR-070** | Where a ticket names a recipient, only that recipient may progress it. |
| **BR-071** | Status moves forward one step at a time, except that in-progress may go to awaiting-parts or straight to resolved. |
| **BR-072** | Only the raiser may rate, and only once resolved or closed. |

### Complaints, safety and general

| ID | Rule |
|---|---|
| **BR-073** | Unrecognised enumerated values fall back to a safe default rather than being rejected, for kind, priority, incident type and severity. |
| **BR-074** | An anonymous safety report never reveals its reporter. |
| **BR-075** | Reaching a terminal state sets the resolution timestamp; returning to an earlier state clears it. |
| **BR-076** | The submitter is never notified of their own submission. |
| **BR-077** | Notification and SMS failure never blocks the originating business action. |
| **BR-078** | Every newly created employee joins the organisation-wide announcement channel. |
| **BR-079** | Display labels for divisions are canonicalised on write so they never leak into stored data. |
| **BR-080** | Blank optional strings are stored as absent, never as empty text. |

---

## 33. Non-Functional Requirements

### 33.1 Performance

**NFR-PERF-001** An interactive read endpoint shall respond within **500 ms** at the 95th percentile under expected load, excluding network latency.
**NFR-PERF-002** A write endpoint shall respond within **1 s** at the 95th percentile, excluding asynchronous side effects.
**NFR-PERF-003** Notification delivery, SMS dispatch and other side effects shall be asynchronous and shall not contribute to request latency.
**NFR-PERF-004** Every list endpoint returning potentially unbounded data shall be paginated, with a default page size of 20.
**NFR-PERF-005** Aggregations shall resolve related entities in batch rather than per row; per-record loops shall not issue a query each.
**NFR-PERF-006** Lazy-loading outside a transaction shall be disabled, so that view rendering cannot trigger unbounded queries.
**NFR-PERF-007** The database connection pool shall be bounded, with a maximum of 10 connections and a minimum of 2 idle, and shall be named for observability.
**NFR-PERF-008** Indexes shall exist on every column used for routine filtering: work date, request status, employee references, department, reporting manager, audit actor and audit entity.
**NFR-PERF-009** Spreadsheet exports shall be generated in memory and streamed as a single response.
**NFR-PERF-010** PDF fonts and logos shall be loaded once at class initialisation, not per document.
**NFR-PERF-011** The web client shall cache server state and shall refetch on invalidation rather than polling.
**NFR-PERF-012** The web application shall be built as an optimised production bundle with code splitting.

### 33.2 Scalability and Capacity

**NFR-SCAL-001** The system shall support at least **1 000** employee records and **200** concurrent signed-in users on a single application instance.
**NFR-SCAL-002** The API shall be stateless, so that instances can be added behind a load balancer without session affinity.
**NFR-SCAL-003** Live message distribution shall be able to use an external event stream so that delivery scales beyond one instance.
**NFR-SCAL-004** File storage shall be swappable for an object store without code change, so that storage capacity is not bounded by the application host.
**NFR-SCAL-005** The application heap shall be explicitly bounded in production.

### 33.3 Security

**NFR-SEC-001** All external traffic shall use TLS in production.
**NFR-SEC-002** Passwords shall be hashed with BCrypt; the hash shall be the sole authority for authentication.
**NFR-SEC-003** The reversible password copy shall be encrypted with AES-GCM under a key derived from the application signing secret, so that a database dump alone — including a pre-deployment backup — carries nothing readable. Anyone holding both the dump and the application secret can read every password; this is the accepted cost of the reveal capability and shall be documented to the operator.
**NFR-SEC-004** The token signing secret shall be at least 32 random characters, shall be supplied only by environment variable outside development, and shall have no fallback default.
**NFR-SEC-005** Access tokens shall be short-lived and refresh tokens shall be rotated on every use and revoked wholesale on sign-out and password change.
**NFR-SEC-006** Authorisation shall be enforced server-side at every endpoint; client-side hiding shall never be the only control.
**NFR-SEC-007** Service-layer scoping shall additionally restrict result sets to what the caller may see, so that a broad endpoint guard cannot leak another team's or another division's data.
**NFR-SEC-008** Cross-origin access shall be restricted to an explicit allow-list.
**NFR-SEC-009** Cross-site request forgery protection shall be disabled only because the API is stateless and token-based; no cookie-borne credential shall be introduced without reinstating it.
**NFR-SEC-010** Input shall be validated declaratively at the boundary, and validation failures reported per field.
**NFR-SEC-011** Database access shall use parameter binding. Where a native statement must name a table or column, those names shall be fixed constants and never derived from user input.
**NFR-SEC-012** Stored file paths shall be normalised against the storage root so that traversal outside it is impossible.
**NFR-SEC-013** Uploaded content shall never be executed or interpreted.
**NFR-SEC-014** Upload sizes shall be bounded at both the application and the reverse proxy.
**NFR-SEC-015** Error responses shall not disclose stack traces, SQL, internal paths or configuration.
**NFR-SEC-016** Failed authentication shall not reveal whether the identifier exists; the same message shall be returned for an unknown user and a wrong password.
**NFR-SEC-017** Accounts shall lock after repeated failures to frustrate credential stuffing.
**NFR-SEC-018** Secrets shall never be committed to source control, never logged in full, and never returned to a client; any display shall be masked.
**NFR-SEC-019** Only the health endpoint shall be exposed among operational endpoints, and it shall not disclose detail.
**NFR-SEC-020** Internal services — database, application, side service — shall be unreachable from outside the deployment network.
**NFR-SEC-021** Sensitive reads — notably the password reveal — shall be separate, explicitly invoked endpoints so that the value never travels with an ordinary read, a listing or an export.
**NFR-SEC-022** Security-relevant events shall be logged: lockouts, sign-in attempts, and decision actions with their actor.
**NFR-SEC-023** The framework security log level shall be restrained in production so that logs do not accumulate sensitive detail.

### 33.4 Reliability and Availability

**NFR-REL-001** The system shall target **99.5 %** monthly availability during business hours.
**NFR-REL-002** Optional dependencies — cache, mail, event stream, side service, SMS gateway, AI providers — shall not affect reported health and shall not prevent core operation.
**NFR-REL-003** A failure in a side effect shall never fail the originating transaction.
**NFR-REL-004** Business operations shall be transactional; a partial write shall not be observable.
**NFR-REL-005** Batch operations shall isolate each item so that one failure does not roll back the batch.
**NFR-REL-006** Containers shall restart automatically unless deliberately stopped.
**NFR-REL-007** The database container shall report readiness through a health check before dependants start.
**NFR-REL-008** A deployment shall verify health after starting and shall roll back automatically on failure.
**NFR-REL-009** Uniqueness constraints shall prevent duplicate punches, duplicate payslips, duplicate balances and duplicate runs at the database level, not merely in application logic.

### 33.5 Usability and Accessibility

**NFR-USE-001** The interface shall be usable from a 320 px viewport upwards without horizontal scrolling of the page body.
**NFR-USE-002** Wide content — tables, wide grids — shall scroll within its own container.
**NFR-USE-003** Every action shall give immediate feedback: a loading state, then a success or failure message carrying the server's own wording.
**NFR-USE-004** Error messages shall be written for the person reading them, naming what went wrong and what to do, not internal codes.
**NFR-USE-005** Interactive controls shall be reachable and operable by keyboard, and shall carry accessible labels where their purpose is conveyed only by an icon.
**NFR-USE-006** Colour shall not be the sole carrier of meaning; status shall also be conveyed by text.
**NFR-USE-007** Text and interface contrast shall meet WCAG 2.1 AA in both light and dark themes.
**NFR-USE-008** The chosen theme shall persist across sessions.
**NFR-USE-009** Dates shall be presented consistently and unambiguously throughout.
**NFR-USE-010** Destructive actions shall require explicit confirmation.
**NFR-USE-011** Navigation shall never present a route the user cannot use.
**NFR-USE-012** Role names shown to users shall be the friendly business names, not internal codes.

### 33.6 Compatibility and Portability

**NFR-COMP-001** The web application shall function on current versions of the major browsers.
**NFR-COMP-002** The mobile application shall function on currently supported Android and iOS versions.
**NFR-COMP-003** The API shall preserve the legacy contract shapes it deliberately mirrors — dropdown by type, bank action semantics, payslip components — so that existing integrations have direct equivalents.
**NFR-COMP-004** The server side shall run wherever a compliant Java runtime and MySQL are available, and shall be deployable as containers.
**NFR-COMP-005** Configuration shall be supplied entirely by environment variables and database-held settings, so that the same image serves every environment.
**NFR-COMP-006** No feature shall depend on a specific host operating system.

### 33.7 Maintainability

**NFR-MAIN-001** Each functional area shall be a self-contained module comprising entity, repository, service, controller and data-transfer objects, with cross-cutting concerns held outside the modules.
**NFR-MAIN-002** Adding a module shall follow a documented, repeatable recipe requiring no architectural decision.
**NFR-MAIN-003** Business logic shall live in the service layer; controllers shall handle transport and authorisation only.
**NFR-MAIN-004** Data-transfer objects shall be immutable records carrying declarative validation.
**NFR-MAIN-005** Configuration shall be bound to strongly-typed objects rather than read as loose strings.
**NFR-MAIN-006** Every non-obvious decision shall be documented at the point of code that implements it, stating the reason.
**NFR-MAIN-007** External integrations shall sit behind an abstraction so that a provider can be replaced without touching callers.
**NFR-MAIN-008** Documentation-only changes shall not trigger a production rebuild.

### 33.8 Localisation and Internationalisation

**NFR-LOC-001** All storage shall be `utf8mb4`, so that Indian-language text and emoji round-trip without loss.
**NFR-LOC-002** The AI assistant shall converse in at least English, Tamil and Hindi.
**NFR-LOC-003** Generated documents shall embed fonts capable of rendering the currency symbol and Indian-language text.
**NFR-LOC-004** The system shall operate in the organisation's local time zone consistently across containers, so that a recorded punch time is the wall-clock time the employee saw.
**NFR-LOC-005** Monetary presentation shall use the Indian rupee.
**NFR-LOC-006** A translation resource shall be maintained for regional-language interface text.

### 33.9 Observability

**NFR-OBS-001** The application shall log at information level for its own packages and shall restrain framework logging.
**NFR-OBS-002** Every outbound integration attempt shall log its outcome, and a failure shall log enough to diagnose it without disclosing a secret.
**NFR-OBS-003** The active SMS provider and its configuration state shall be logged at startup, with the key masked, and a misconfiguration — enabled without a key — shall be logged as a warning.
**NFR-OBS-004** Unhandled exceptions shall be logged with their stack trace server-side while returning a generic message to the client.
**NFR-OBS-005** A health endpoint shall be available for uptime monitoring.
**NFR-OBS-006** Deployment scripts shall log each step and shall fail loudly rather than continuing after an error.

### 33.10 Backup and Recovery

**NFR-BAK-001** A database backup shall be taken automatically before every deployment.
**NFR-BAK-002** Backups shall be retained outside the application working directory.
**NFR-BAK-003** A documented rollback procedure shall restore both the previous application version and, where required, the pre-deployment database state.
**NFR-BAK-004** Database and file storage shall be held in durable volumes that survive container replacement.
**NFR-BAK-005** Recovery point objective shall be one day; recovery time objective shall be four hours.

### 33.11 Compliance and Privacy

**NFR-PRIV-001** Personal data — national identifier, PAN, provident-fund number, bank details, date of birth, address, emergency contact — shall be readable only by the employee themselves and by roles holding an explicit employee-management or payroll capability.
**NFR-PRIV-002** Bank details shall be readable by the owner and by payroll and employee-management roles only.
**NFR-PRIV-003** Safety reports marked anonymous shall never disclose their reporter, in any view, notification or export.
**NFR-PRIV-004** Every access to and change of employee data shall be attributable through the audit trail.
**NFR-PRIV-005** Statutory computations shall be configurable so that a change in rate or ceiling requires configuration, not code.
**NFR-PRIV-006** The reversible-password capability shall be documented as a deliberate, security-relevant trade-off, with its blast radius stated, so the operator can make an informed decision about retaining it.

---

## 34. Deployment and Operations

### 34.1 Environments

**OR-001** The system shall support at least three environments — development, staging and production — differentiated only by configuration.
**OR-002** The development profile shall supply its own fallback signing secret so that a local run works unconfigured; no other profile shall.
**OR-003** Production shall run as a container composition on a single host: database, application, optional side service, and a reverse proxy that is the only public entry point.
**OR-004** The database shall not publish a host port; only containers on the internal network shall reach it.

### 34.2 Configuration

**OR-005** The following shall be configurable by environment variable:

| Variable | Purpose |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Database connection |
| `APP_JWT_SECRET` | Token signing secret — mandatory outside development |
| `ACCESS_TOKEN_TTL_SECONDS`, `REFRESH_TOKEN_TTL_SECONDS` | Session lifetimes |
| `APP_CORS_ALLOWED_ORIGINS` | Allowed web origins |
| `STORAGE_PATH` | File storage root |
| `OFFICE_START`, `OFFICE_END` | Office hours |
| `REDIS_HOST`, `REDIS_PORT` | Cache |
| `KAFKA_HOST`, `KAFKA_PORT` | Event stream |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD` | Mail relay |
| `FAST2SMS_ENABLED`, `FAST2SMS_API_KEY`, `FAST2SMS_ROUTE`, `FAST2SMS_SENDER_ID` | Primary SMS gateway |
| `TWILIO_ENABLED`, `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM_NUMBER`, `TWILIO_DEFAULT_CC` | Secondary SMS gateway |
| `PORT` / `SERVER_PORT` | Listen port, with a platform-injected value taking precedence |
| `TZ`, `JAVA_OPTS` | Time zone and heap bounds |

**OR-006** Secrets shall be supplied from a local environment file that is never committed, populated from a secrets store.
**OR-007** An example environment file listing every required variable shall be maintained alongside the composition.
**OR-008** Assistant provider credentials and travel rates shall be held as database settings, administrable through the interface rather than requiring a redeployment.

### 34.3 Continuous Deployment

**OR-009** A push to the main branch shall trigger an automated deployment, and a manual trigger shall additionally offer rollback and optional service profiles.
**OR-010** The pipeline shall hold no privileges beyond reading the repository, and shall verify the target host key before connecting.
**OR-011** The deployment logic shall live on the server as scripts, so that the same code path can be run by hand during an incident.
**OR-012** Deployment shall: back up the database; fetch and reset to the target revision; build; start; verify health; and roll back automatically on failure.
**OR-013** Documentation-only changes shall be excluded from triggering a rebuild.
**OR-014** Schema migrations shall be applied automatically at application startup, before the application begins serving.

### 34.4 Operations

**OR-015** Health shall be verifiable by a single request that reports database reachability.
**OR-016** Containers that record or render timestamps shall run in the organisation's local time zone.
**OR-017** The reverse proxy shall permit the same upload sizes as the application.
**OR-018** Database and file-storage volumes shall be durable across container replacement.
**OR-019** Runbooks shall be maintained for deployment, rollback, health verification and backup restoration.
**OR-020** A documented procedure shall exist for clearing transactional data for a fresh start without disturbing master data.

---

## 35. Constraints, Assumptions and Known Limitations

### 35.1 Known Limitations

| ID | Limitation | Impact | Suggested resolution |
|---|---|---|---|
| **LM-001** | The reversible password copy allows any holder of both a database dump and the application signing secret to read every password. | High security exposure by design. | Review whether the reveal capability is required; if not, drop the column and the endpoint. |
| **LM-002** | Several executive-dashboard panels — departmental breakdown, monthly attendance trend, leave utilisation and payroll costs — return fixed placeholder values rather than computed figures. | The executive view is partly non-representative. | Replace each with a query over live data. |
| **LM-003** | Sign-in accepts a full-name match when the identifier is not a known username. | Weakens the uniqueness of the credential and is surprising. | Restrict sign-in to username only, or gate the fallback behind configuration. |
| **LM-004** | An administrative password reset enforces a minimum of only four characters, weaker than the policy applied elsewhere. | Weak credentials can be set administratively. | Apply one password policy to every path that sets a password. |
| **LM-005** | Approval routing for leave, permissions, tickets and complaints escalates to a **hard-coded employee code** for the HR level. | The escalation approver cannot be changed without a code change. | Move the escalation approver to a system setting or a role. |
| **LM-006** | The role code `IT_MGR` denotes HR and is relabelled in the interface; `IT_HR` is displayed as HR Head. | Persistent source of confusion between code and meaning. | Rename the roles in data with a migration and remove the display shim. |
| **LM-007** | Face enrolment and verification components exist but are not wired into any screen. | The capability is unavailable to users. | Wire the dialogs into the attendance and employee screens, or remove them. |
| **LM-008** | A start-up routine copies a fixed image between two absolute developer paths. | Environment-specific code in a service; no effect in production but wrong in principle. | Remove the routine and ship the asset with the web application. |
| **LM-009** | Recruitment, learning management, and several civil-specific site-operations areas are modelled but not implemented end-to-end. | Those stories cannot be completed in the product. | Build out following the documented module recipe. |
| **LM-010** | Automated test coverage is not established in the repository. | Regressions are detectable only by inspection. | Introduce a test suite covering the business rules of §32 first. |
| **LM-011** | Minimum-notice calculation approximates a month as thirty days. | Notice checks can be off by a day or two across month boundaries. | Compute the notice as a plain day difference. |
| **LM-012** | Some code sequences derive from a row count rather than the observed maximum. | A deletion can cause a code to be reissued. | Apply the maximum-plus-one approach used for complaint codes everywhere. |
| **LM-013** | Migration validation is disabled and repair enabled at startup. | Checksum drift passes silently. | Re-enable validation once the migration history is stable. |
| **LM-014** | Refresh tokens are persisted indefinitely and revoked rather than pruned. | The table grows without bound. | Add a scheduled purge of expired and revoked tokens. |
| **LM-015** | Full offline operation is not supported; only an installable shell is provided. | Field staff without connectivity cannot punch. | Add queued offline punches with deferred synchronisation. |

### 35.2 Explicit Non-Goals

**CN-012** The system shall not attempt multi-tenant isolation; one deployment serves one organisation.
**CN-013** The system shall not integrate with biometric attendance hardware; the `BIOMETRIC` mode denotes a punch source, not a device protocol.
**CN-014** The system shall not perform statutory filing or return submission.
**CN-015** The system shall not act as a general ledger or accounting system.

---

## 36. Traceability Matrix

| # | Capability | Requirements | Principal API surface | Web route | Primary data |
|---|---|---|---|---|---|
| 1 | Authentication and session | FR-AUTH-001…028 | `/api/auth/**` | `/login` | `users`, `refresh_tokens`, `login_history` |
| 2 | Employee management | FR-EMP-001…053 | `/api/users/**`, `/api/auth/employees`, `/api/auth/employees/bulk` | `/employees`, `/profile` | `users`, `bank_details`, `offboarding_records` |
| 3 | Master data | FR-ORG-001…011 | `/api/org/**` | master-data pickers | `departments`, `designations`, `positions`, `shifts`, `sites`, `office_locations`, `blood_groups`, `employment_statuses` |
| 4 | Attendance | FR-ATT-001…031 | `/api/attendance/**` | `/attendance`, `/team-attendance` | `attendance` |
| 5 | Leave | FR-LV-001…052 | `/api/leave/**` | `/leave`, `/leave/approvals`, `/leave/policies` | `leave_types`, `leave_balances`, `leave_requests` |
| 6 | Hourly permissions | FR-PRM-001…014 | `/api/leave/permissions/**` | `/leave/permissions` | `permission_requests` |
| 7 | Payroll | FR-PAY-001…044 | `/api/payroll/**` | `/payslips`, `/payroll/requests`, `/payroll/run` | `salary_structures`, `payslips`, `payslip_requests`, `payroll_runs` |
| 8 | Tasks | FR-TSK-001…025 | `/api/tasks/**` | `/tasks` | `tasks` |
| 9 | Work reports | FR-WRP-001…008 | `/api/work-reports/**` | `/work-reports` | `work_reports` |
| 10 | Travel and expense claims | FR-CLM-001…020 | `/api/ta-expenses/**`, `/api/finance/expenses/**` | `/ta-expenses`, `/ta-expenses/new` | `ta_expenses`, `expense_claims` |
| 11 | Assets | FR-AST-001…017 | `/api/assets/**` | `/assets` | `assets`, `asset_allocations` |
| 12 | Helpdesk | FR-HD-001…025 | `/api/tickets/**` | `/helpdesk`, `/helpdesk/new` | `tickets`, `ticket_comments` |
| 13 | Complaints and needs | FR-CMP-001…014 | `/api/complaints/**` | `/complaints` | `complaints_needs` |
| 14 | Safety incidents | FR-SAF-001…011 | `/api/safety-incidents/**` | safety screens | `safety_incidents` |
| 15 | Onboarding | FR-ONB-001…007 | `/api/onboarding/**` | `/onboarding` | `onboarding_checklists`, `onboarding_tasks` |
| 16 | Performance | FR-PRF-001…005 | `/api/performance/**` | performance screens | `performance_goals`, `performance_reviews` |
| 17 | Dashboards | FR-DSH-001…014 | `/api/dashboard/**` | `/` | aggregate across modules |
| 18 | Notifications | FR-NOT-001…011 | `/api/notifications/**`, `/ws` | `/notifications` | `notifications` |
| 19 | Communication | FR-CHT-001…016 | `/api/communities/**`, `/api/calls/**` | `/chat`, `/communities` | `communities`, `community_members`, `community_messages` |
| 20 | AI assistant | FR-BOT-001…014 | `/api/chatbot/**` | `/admin/ai-assistant`, global widget | `chatbot_knowledge`, `system_settings` |
| 21 | Reports | FR-RPT-001…006 | `/api/reports/**` | `/reports` | aggregate across modules |
| 22 | Calendar | FR-CAL-001…006 | `/api/org/holidays` | `/calendar` | `holidays` |
| 23 | Files | FR-FIL-001…009 | `/api/files/**`, upload endpoints | inline | storage volume |
| 24 | Settings | FR-SET-001…005 | `/api/settings` | `/admin/ai-assistant` | `system_settings` |
| 25 | Mobile | FR-MOB-001…007 | shared API | mobile tabs | as above |
| 26 | Biometric and OCR | FR-BIO-001…009 | side-service endpoints | face dialogs | enrolment store |
| 27 | Access control | FR-RBAC-001…011 | all endpoints | navigation and guards | `roles`, `permissions`, `role_permissions`, `user_roles` |

---

## 37. Appendices

### Appendix A — Status Enumerations

| Entity | States |
|---|---|
| Employee profile | `PENDING`, `ACTIVE`, `INACTIVE`, `EXITED`, `OFFBOARDED` |
| Attendance | `PRESENT`, `ABSENT`, `WFH`, `HOLIDAY`, `LEAVE`, `HALF_DAY` |
| Attendance mode | `OFFICE`, `WFH`, `SITE`, `BIOMETRIC` |
| Leave request | `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED` |
| Leave accrual | `ANNUAL`, `MONTHLY`, `MANUAL` |
| Permission request | `PENDING`, `APPROVED`, `REJECTED` |
| Payroll run | `PREVIEW`, `CONFIRMED`, `FINANCE_APPROVED`, `PAID` |
| Payslip request | `PENDING`, `APPROVED`, `REJECTED` |
| Task | `PENDING`, `IN_PROGRESS`, `COMPLETED` |
| Task priority | `LOW`, `MEDIUM`, `HIGH` |
| Claim | `PENDING`, `APPROVED`, `REJECTED` |
| Asset | `IN_STOCK`, `ASSIGNED`, `OUT_OF_STOCK`, `UNDER_REPAIR`, `RETIRED`, `LOST`, `DEPLOYED`, `BREAKDOWN` |
| Asset return condition | `GOOD`, `DAMAGED`, `LOST` |
| Ticket | `OPEN`, `IN_PROGRESS`, `AWAITING_PARTS`, `RESOLVED`, `CLOSED` |
| Ticket priority | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |
| Complaint / need kind | `COMPLAINT`, `NEED` |
| Complaint / need | `OPEN`, `IN_REVIEW`, `RESOLVED`, `REJECTED` |
| Safety incident type | `NEAR_MISS`, `MINOR_INJURY`, `MAJOR_INJURY`, `PROPERTY_DAMAGE`, `ENV_HAZARD` |
| Safety severity | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |
| Safety incident | `OPEN`, `INVESTIGATING`, `RESOLVED`, `CLOSED` |
| Onboarding checklist | `IN_PROGRESS`, `COMPLETED` |
| Performance goal | `ACTIVE`, `COMPLETED` |
| Performance review | `DRAFT`, `SUBMITTED`, `COMPLETED` |
| Employment type | `PERMANENT`, `CONTRACTUAL`, `DAILY_WAGE`, `SUBCONTRACTOR` |
| Division | `IT` (displayed Digital), `CIVIL` (displayed Infra) |

### Appendix B — State Machines

**Leave request**
```
            ┌── cancel (owner) ──────────────► CANCELLED
            │                                     ▲
  PENDING ──┤                                     │ cancel + refund
            ├── approve (routed approver) ──► APPROVED ──┘
            └── reject  (routed approver, reason required) ──► REJECTED
```

**Task** — forward only
```
  PENDING ──► IN_PROGRESS ──► COMPLETED
  (progress 0)   (1–99)        (100)
```

**Support ticket** — forward only, one step, with one permitted skip
```
  OPEN ──► IN_PROGRESS ──► AWAITING_PARTS ──► RESOLVED ──► CLOSED
                 └──────────────────────────────┘
                        (permitted skip)
```

**Payroll run**
```
  PREVIEW ──confirm──► CONFIRMED ──finance approve──► FINANCE_APPROVED ──► PAID
```

**Payslip request**
```
                  ┌── reject (note) ──► REJECTED
  PENDING ────────┤
                  └── approve (payslip form) ──► APPROVED ──► payslip + PDF
```

### Appendix C — Generated Identifier Formats

| Identifier | Format | Derivation |
|---|---|---|
| Employee code | `EMP0001` | Highest existing code with the prefix, plus one, four digits |
| Asset code | `AST-2026-00001`, `INF-…`, `MCH-…` | Category prefix, current year, five-digit sequence |
| Ticket code | `TKT-2026-00001` | Fixed prefix, current year, five-digit sequence |
| Complaint / need code | `CN-2026-00001` | Highest existing code for the year, plus one, five digits |
| Safety incident code | `SI-2026-00001` | Fixed prefix, current year, five-digit sequence |
| Refresh token | Two concatenated random UUIDs | Random |
| Stored file path | `<folder>/<yyyy-MM>/<uuid>.<ext>` | Folder, upload month, random name, original extension |

### Appendix D — Configuration Defaults

| Setting | Default |
|---|---|
| Access token lifetime | 4 hours |
| Refresh token lifetime | 4 hours |
| Maximum failed sign-in attempts | 5 |
| Account lock duration | 15 minutes |
| Default geofence radius | 200 metres |
| Lateness grace | 0 minutes |
| Office start / end | 09:00 / 18:00 |
| Standard work hours | 8 |
| Maximum upload per file / per request | 25 MB / 60 MB |
| Default page size | 20 |
| Database pool maximum / minimum idle | 10 / 2 |
| ESI wage ceiling / employee rate | 21 000 / 0.75 % |
| Overtime hourly divisor | 240 |
| Ticket SLA — critical / high / medium / other | 4 h / 8 h / 24 h / 48 h |
| Celebration horizon / list limit | 60 days / 12 entries |
| Assistant history / knowledge context / ingestion cap | 8 turns / 6 000 chars / 12 000 chars |
| Daily celebration job | 09:00 server time |
| Server time zone | `Asia/Kolkata` |
| Default listen port | 7060 |

### Appendix E — Notification Catalogue

| Trigger | Recipient | Type | Link |
|---|---|---|---|
| Leave applied | Named approver, or every leave approver | `LEAVE` | `/leave/approvals` |
| Leave decided | Applicant | `LEAVE` | `/leave` |
| Leave cancelled | Handling approver | `LEAVE` | `/leave/approvals` |
| Permission requested | Named approver | `PERMISSION` | `/leave/permissions` |
| Permission decided | Requester | `PERMISSION` | `/leave/permissions` |
| Payslip requested | Every payroll runner | `PAYROLL` | `/payroll/requests` |
| Payslip approved or rejected | Requester | `PAYROLL` | `/payslips` |
| Payroll run finance-approved | Every employee in the run | `PAYROLL` | `/payslips` |
| Task assigned, updated, progressed or completed | Assignee or assigner | `TASK` | `/tasks` |
| Claim submitted or updated | Approvers, or the owner | `CLAIM` | `/ta-expenses` |
| Claim decided | Owner | `CLAIM` | `/ta-expenses` |
| Asset allocated | Assignee | `ASSET` | `/assets` |
| Ticket raised, updated, reassigned, commented or progressed | Recipient, previous recipient, or raiser | `HELPDESK` | `/helpdesk` |
| Complaint or need submitted | Named recipient, or every HR and administrator | `COMPLAINT` | `/complaints` |
| Complaint or need updated | Submitter | `COMPLAINT` | `/complaints` |
| Safety incident reported | Every report viewer | `SAFETY` | `/safety-incidents` |
| Safety incident updated | Reporter | `SAFETY` | `/safety-incidents` |
| Holiday added | Every active employee | `CALENDAR` | `/calendar` |
| Birthday or work anniversary today | Every active employee except the celebrant | `CELEBRATION` | `/` |
| New chat message | Channel members | `CHAT` | `/chat` |

### Appendix F — SMS Catalogue

SMS is sent, in addition to the in-app notification, for: a leave application to its approver; a leave decision to the applicant; a permission request to its approver and its decision to the requester; a payslip request to payroll runners and its outcome to the requester; a task assignment, update and completion; a claim submission to approvers, a correction to the owner, and a decision to the owner; an asset allocation to the assignee; a ticket raised, commented and progressed; a complaint or need submitted and updated; a safety incident reported and updated; a new holiday, to the whole workforce; and the daily celebration, to the whole workforce except the celebrant.

Every SMS is prefixed with the organisation identifier, is dispatched asynchronously, and never blocks or fails the action that raised it.

### Appendix G — Requirement Count Summary

| Category | Count |
|---|---|
| Functional requirements | 340 |
| Non-functional requirements | 92 |
| Business rules | 80 |
| Data requirements | 25 |
| Interface requirements | 34 |
| Operational requirements | 20 |
| Constraints | 15 |
| Assumptions | 8 |
| Known limitations | 15 |

### Appendix H — Document Revision History

| Version | Date | Change |
|---|---|---|
| 1.0 | 30 July 2026 | Baseline specification derived from the implemented system: full functional decomposition across 26 capability areas, access-control model, data requirements, consolidated business rules, non-functional requirements, deployment and operations, traceability and appendices. |

---

*End of document.*
