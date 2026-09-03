# Super Admin Control Center — design prototype

`super-admin-control-center.html` is a **self-contained, interactive prototype**
of the Super Admin control plane described in section 5 of the V2 requirements
specification. Open it in any browser; it needs no build step and no server.

It is a **design artefact, not production code.** Nothing in it is wired to the
backend, and no application code was changed to produce it. It exists so the
information architecture, the permission model and the governance flows can be
reviewed and corrected before any of it is built.

## What it covers

36 screens across the ten control groups from the specification:

| Group | Screens |
|---|---|
| Dashboard | Control Center |
| Identity & Access | Users, Roles, Permissions, Role Matrix, Full Control Matrix, Role Hierarchy, Access Simulator, Delegation |
| Application | Modules, Pages, Buttons / Actions, Fields, Feature Flags |
| Master Data | Master Data (22 masters), Global Settings |
| Workflow | Workflow Builder, Workflow Versions, SLA & Escalation |
| Policy | Policy Studio, Rule Builder, Policy Simulation |
| Communication | Notification Templates |
| Security | Security Settings, Authentication & SSO |
| Integration | Applications, Integration Health |
| Data | Import / Export, Retention & Archival |
| System | Background Jobs, Backup Status, System Alerts |
| Governance | Change Requests, Audit Logs, Config Versions, Danger Zone |

## The data is real

Permission codes, role codes, module names, scheduled jobs and integration
endpoints are taken from the live schema and seed migrations rather than
invented. `USER_MANAGE`, `LEAVE_APPROVE`, `PAYROLL_RUN`, `IT_TL`, `PIX-E100`
and the rest all exist. That matters: a prototype built on plausible-looking
fictional codes would validate an architecture that does not fit the system it
is meant to configure.

Several screens also encode defects found during the V1 and V2 analysis, so the
prototype shows the intended end state rather than an idealised one:

- **Backup Status** reports the backup as never run — which is currently true,
  and is the highest-severity open finding.
- **Integration Health** shows the SMS gateway failing on a rejected API key.
- **Security Settings** notes the 8-versus-4 character password inconsistency
  and the reversible password vault, both incompatible with SSO.
- **Full Control Matrix** includes the "not own claim" and "addressed to CTO"
  rules, each of which was a live authorisation bug before it became a rule.

## What it is not

- Not connected to any API. Buttons raise a toast describing what would happen.
- Not an authorisation implementation. Every screen states the same principle
  the backend already follows: hiding a control is not security, and the server
  decides.
- Not a schema. The ~25 configuration tables this implies are listed in section
  62 of the specification and are not created here.

## Suggested next step

Review the information architecture and the permission model first. Those two
are expensive to change once tables exist; the visual design is not. When the
model is agreed, build it behind a feature flag so the live portal is unaffected
until the work is complete.
