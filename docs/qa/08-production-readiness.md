# Production Readiness, Risk & Roadmap — Pixous HR Portal

## 1. Risk Assessment

| Risk | Likelihood | Impact | Level | Mitigation |
|---|---|---|---|---|
| Credential leak from public repo history | Certain (already exposed) | Critical | **Critical** | Make repo private + purge history + rotate DB password & JWT secret (QA-001) |
| Live DB data loss during switch | Medium | Critical | **High** | Read-only backup before any switch; verify 5.7 compatibility; keep old DB until verified |
| Interception over HTTP (no TLS) | Medium | Medium | **Medium** | Free TLS via Let's Encrypt (QA-005) |
| Public API docs (Swagger) | Medium | Low-Med | **Medium** | Disable in prod (QA-003) |
| Cost surprise post free-tier | High (12 mo out) | Medium | **Medium** | Budget guard + auto-stop; mark calendar reminder |
| No load testing before scale | Medium | Medium | **Medium** | k6 baseline; pagination already in place |
| site4now remote-DB outage | Low | Medium | Low | Keep nightly mysqldump of live DB |

## 2. Production Readiness Score: 75%

**Ready for:** internal team use, pilot with real users against the live DB.
**Not ready for:** public launch — blocked primarily by QA-001 (repo hygiene) and QA-005 (TLS), plus the unconnected live DB (vendor gate).

## 3. Prioritized Fix Roadmap

### Phase 1 — This week (security criticals)
1. Make repo private, purge `.env.backup` from history, rotate credentials (QA-001)
2. Enable MFA on the AWS root account (user action)
3. Enable CloudTrail
4. Finish the budget auto-stop guard (add email subscriber)

### Phase 2 — This month (production hardening)
5. Disable Swagger in prod (QA-003)
6. Add security headers to nginx — CSP, HSTS, X-Frame-Options, X-Content-Type-Options (QA-004)
7. Set up HTTPS with a free Let's Encrypt certificate (QA-005)
8. Connect the live DB (after site4now whitelists `16.192.105.61`) with backup-first switch (QA-009)
9. nginx: enable gzip + cache immutable assets

### Phase 3 — Next quarter (quality & scale)
10. Route-level code splitting to kill the >500 kB chunk warning (QA-008)
11. Add E2E tests (Playwright) for the top 10 user journeys
12. Add a light load test (k6) and capture API response-time baselines
13. Accessibility pass: axe + keyboard walkthrough + contrast fixes (QA-011/012)
14. Shared `DataTable` component; drop one CSS framework (bootstrap vs tailwind)
15. `springdoc` off + custom `AuthenticationEntryPoint` for 401-vs-403 (QA-007)

## 4. Final Deployment Recommendation

**APPROVED for internal/team deployment with conditions.** The application is functionally sound, the deployment pipeline is verified end-to-end, and the audit added a real test suite (62 tests passing) plus one security fix. The blocker list is short and mostly operational (repo privacy, TLS, live-DB whitelist), not architectural.

**Sign-off criteria before public launch:**
- [ ] QA-001 resolved (repo private, history purged, credentials rotated)
- [ ] QA-005 resolved (HTTPS live)
- [ ] QA-003 resolved (Swagger off in prod)
- [ ] Live DB connected and verified with backup in place
- [ ] One week of pilot usage with zero critical bugs

## 5. Deliverables Index

| # | Document | File |
|---|---|---|
| 1 | Executive Summary | `00-executive-summary.md` |
| 2 | Complete QA Report + Bug Tracker | `01-full-qa-report.md` |
| 3 | Security Audit Report | `02-security-audit.md` |
| 4 | API Testing Report | `03-api-testing.md` |
| 5 | Performance & Build Audit | `04-performance-build.md` |
| 6 | Code Review Report | `05-code-review.md` |
| 7 | UI/UX & Accessibility Report | `06-ui-ux-accessibility.md` |
| 8 | Database Review | `07-database-review.md` |
| 9 | Production Readiness, Risk & Roadmap | `08-production-readiness.md` |
| 10 | Testing Requirements (full spec) | `TESTING-REQUIREMENTS.md` |
