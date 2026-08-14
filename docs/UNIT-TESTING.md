# Pixous HR Portal — Unit Testing Document

| Field | Value |
|---|---|
| Version | 1.0 |
| Date | 2026-08-14 |
| Backend framework | JUnit 5 + Mockito (Maven, `mvn test`) |
| Frontend framework | Vitest + React Testing Library + jsdom (`npm test`) |
| Current inventory | **62 automated tests: 40 backend + 22 frontend — all passing** |
| Coverage targets | Backend service layer ≥ 70%; frontend critical paths 100% |

---

## 1. Testing Strategy

### 1.1 Test Pyramid
```
        E2E (Playwright)          <- planned, top-10 journeys
     Integration (Spring slices)  <- planned, service + repository
  UNIT + Component  [THIS DOC]    <- 62 tests, the foundation
```

Unit tests are fast, deterministic, dependency-free (repos/interfaces mocked), and run in seconds. They are the gate for every commit: `mvn test` and `npm test` must be green before any release.

### 1.2 How to Run
```bash
# Backend (all 40 tests)
cd backend
mvn test

# Single class
mvn test -Dtest=JwtServiceTest

# Frontend (all 22 tests)
cd web
npm install        # first time only
npm test           # = vitest run

# Watch mode (development)
npx vitest
```

### 1.3 Conventions
- File layout mirrors `src/main`: `backend/src/test/java/...`, `web/src/**/*.test.ts(x)`.
- Naming: `methodName_scenario_expectedResult` (e.g. `login_wrongPassword_throws401`).
- One behaviour per test; assert on behaviour (exceptions, return values), not implementation.
- Mock every external dependency (repositories, file system, HTTP).
- Frontend component tests never hit the network — the API module is mocked.

---

## 2. Backend Unit Tests (40)

### 2.1 `security/JwtServiceTest` — JWT core
| # | Test case | Input | Expected |
|---|---|---|---|
| B-01 | `generatesTokenThatParsesBackToSameIdentity` | username, roles | Token round-trips to identical subject/roles |
| B-02 | `tokenExpiresAtConfiguredTtl` | TTL 30 s | After expiry, validation fails |
| B-03 | `rejectsTamperedPayload` | Token with payload edited | Signature mismatch → rejected |
| B-04 | `rejectsTokenSignedWithDifferentSecret` | Token from other key | Rejected |
| B-05 | `rejectsGarbageAndEmptyTokens` | `"abc"`, `""`, null | Rejected |
| B-06 | `missingRolesClaimYieldsEmptyList` | Token without roles claim | Empty role list, no exception |

### 2.2 `security/LoginAttemptLimiterTest` — brute-force guard
| # | Test case | Input | Expected |
|---|---|---|---|
| B-07 | `allowsAttemptsUpToThreshold` | 1–7 failures | Not blocked |
| B-08 | `blocksAfterEightFailures` | 8th failure | Blocked (lockout) |
| B-09 | `blocksEitherIdentityIndependently` | lock user A | B unaffected |
| B-10 | `successfulLoginClearsTheStreak` | 5 fails + success | Streak reset, not blocked |
| B-11 | `countingIsCaseInsensitiveOnUsername` | `Admin` vs `admin` | Same counter |
| B-12 | `differentUsersAreNotBlockedByEachOther` | users A, B | Isolation confirmed |

### 2.3 `common/StorageServiceTest` — file upload hardening
| # | Test case | Input | Expected |
|---|---|---|---|
| B-13 | `keepsAllowlistedExtension` | `report.pdf`, `photo.png` | Stored with original extension |
| B-14 | `htmlUploadIsStoredAsBin` | `page.html` | Stored as `.bin` |
| B-15 | `svgUploadIsStoredAsBin` | `logo.svg` | Stored as `.bin` (XSS guard) |
| B-16 | `doubleExtensionTakesLastPart` | `evil.jpg.exe` | Evaluated on final extension |
| B-17 | `uppercaseExtensionIsNormalized` | `PHOTO.PNG` | Normalised, allowed |
| B-18 | `noExtensionBecomesBin` | `README` | Stored as `.bin` |
| B-19 | `emptyFileIsRejected` | 0-byte upload | Rejected |
| B-20 | `uploadedFilenameIsNeverUsedInPath` | `../../etc/passwd` name | Path is generated, traversal impossible |

### 2.4 `modules/auth/AuthServiceTest` — authentication flows
| # | Test case | Input | Expected |
|---|---|---|---|
| B-21 | `loginRequiresBothFields` | Missing username/password | Validation failure |
| B-22 | `loginAcceptsValidCredentials` | Correct credentials | Success |
| B-23 | `loginSuccessIssuesTokensAndClearsFailures` | Correct after failures | Tokens issued; failure streak cleared |
| B-24 | `loginWithUnknownUserThrowsBadCredentialsAndRecordsFailure` | Unknown user | 401-style error; failure recorded |
| B-25 | `loginByFullNameWorksWhenSingleMatch` | Full name of unique user | Login succeeds |
| B-26 | `fiveWrongPasswordsLockTheAccount` | 5 wrong attempts | Account locked |
| B-27 | `disabledAccountRejectedEvenWithCorrectPassword` | Disabled + correct password | Rejected |
| B-28 | `legacyUserWithoutCompanyIsAutoAssignedToPixous` | User with null company | Assigned to default tenant |
| B-29 | `refreshRotatesTheTokenAndRevokesTheOldOne` | Valid refresh token | New pair issued; old revoked |
| B-30 | `refreshWithRevokedTokenIsRejected` | Revoked token | Rejected |
| B-31 | `refreshWithExpiredTokenIsRejected` | Expired token | Rejected |
| B-32 | `changePasswordRejectsWrongCurrentPassword` | Wrong current password | Rejected |
| B-33 | `changePasswordSuccessRevokesAllSessions` | Correct current + new | All old sessions revoked |
| B-34 | `signupRejectsDuplicateUsername` | Existing username | Rejected |
| B-35 | `signupAssignsDefaultRoleAndEncodesPassword` | Valid signup | Default role + hashed password |
| B-36 | `changePasswordRequiresNewPasswordAtLeastEightChars` | 7-char password | Rejected |
| B-37 | `signupRejectsShortUsernameAndPassword` | Short values | Rejected |
| B-38 | `signupRejectsMalformedAadhaar` | Bad Aadhaar format | Rejected |
| B-39 | `signupRejectsMalformedPhone` | Bad phone format | Rejected |
| B-40 | `signupAcceptsValidPayload` | Fully valid payload | Accepted |

---

## 3. Frontend Unit & Component Tests (22)

### 3.1 `src/lib/utils.test.ts` — shared utilities
| # | Test case | Input | Expected |
|---|---|---|---|
| F-01 | `merges and dedupes tailwind classes` | `cn("px-2","px-4")` | `"px-4"` |
| F-02 | `filters falsy values` | `cn("a",null,false,"b")` | `"a b"` |
| F-03 | `handles empty input` | `initials("")` | `"?"` |
| F-04 | `takes first letters of up to two words` | `"Sethuraman Balasubramanian"` | `"SB"` |
| F-05 | `uppercases and skips empty tokens` | `"  john   doe "` | `"JD"` |
| F-06 | `maps 1..12 to English month names` | `monthName(1)`, `(12)` | `"January"`, `"December"` |
| F-07 | `falls back to the number for out-of-range values` | `monthName(0)`, `(13)` | `"0"`, `"13"` |
| F-08 | `formats INR with Indian digit grouping` | `formatMoney(100000)` | `"₹1,00,000.00"` |
| F-09 | `accepts numeric strings and non-finite values` | `"5000"`, `undefined` | `"₹5,000.00"`, `"₹0.00"` |
| F-10 | `formats whole hours and minutes` | `minutesToHours(90)` | `"1h 30m"` |
| F-11 | `returns em dash for missing or non-positive values` | `null`, `0`, `-5` | `"—"` |

### 3.2 `src/lib/dates.test.ts` — date/time helpers
| # | Test case | Input | Expected |
|---|---|---|---|
| F-12 | `converts 24-hour times to 12-hour with AM/PM` | `"14:30"`, `"09:05"`, `"00:00"`, `"12:00"` | `"2:30 PM"`, `"9:05 AM"`, `"12:00 AM"`, `"12:00 PM"` |
| F-13 | `pads single-digit minutes` | `"08:5"` | `"8:05 AM"` |
| F-14 | `returns em dash for empty input` | `undefined`, `null`, `""` | `"—"` |
| F-15 | `returns input untouched when it is not parseable` | `"banana"` | `"banana"` |
| F-16 | `returns today in <input type=date> format` | — | matches `\d{4}-\d{2}-\d{2}` |
| F-17 | `returns this month in <input type=month> format` | — | matches `\d{4}-\d{2}` |

### 3.3 `src/pages/Login.test.tsx` — login page component
| # | Test case | Setup | Expected |
|---|---|---|---|
| F-18 | `renders the sign-in form` | Render page | Heading, username/password fields, submit button present |
| F-19 | `shows validation errors when submitting empty fields` | Click submit empty | "Username is required" + "Password is required"; login NOT called |
| F-20 | `calls login and navigates home on valid credentials` | Type + submit | `login("admin","Test1234@")` called; navigate to `/` |
| F-21 | `does not navigate when login fails, keeps form usable` | Mock login rejects | No navigation; button re-enabled for retry |
| F-22 | `toggles password visibility` | Click eye button | Input type `password` ↔ `text` |

---

## 4. Test Data & Fixtures

| Concern | Approach |
|---|---|
| Backend | In-memory mocks for repositories; fixed usernames/passwords; no DB required |
| Frontend | `localStorage.clear()` before each test; `useAuth` and router stubbed; API mocked |
| Determinism | Vitest `globals: false`; explicit RTL cleanup in `src/test/setup.ts` |
| Secrets | Test files never contain real credentials (placeholders only) |

## 5. CI Integration

- **Local gate:** `mvn test` + `npm test` must pass before push (watcher auto-pushes on save).
- **Docker build:** backend image builds with `-DskipTests` (fast deploys); tests are the developer/PR gate.
- **Recommended next step:** add a `test` job to `.github/workflows/deploy.yml` or a separate CI workflow that fails the build on test failure.

## 6. Coverage Report

```bash
# Backend line coverage (add jacoco to pom, then:)
mvn test jacoco:report
# -> target/site/jacoco/index.html

# Frontend coverage
cd web && npx vitest run --coverage
```

Targets: backend service layer ≥ 70% line coverage; frontend critical flows (auth, uploads, payroll) 100%.

## 7. Backlog — Next 90 Test Cases (prioritised)

| Priority | Area | Suggested cases |
|---|---|---|
| P1 | Company/Tenant service | Tenant creation, code generation, uniqueness, cross-tenant isolation (2 tenants in 1 test) |
| P1 | Leave service | Balance validation, overlap rejection, approval state machine, policy accrual |
| P1 | Payroll service | Run generation, duplicate-period guard, payslip totals/rounding |
| P1 | File controller | Unauthenticated access, upload validation, traversal via URL |
| P2 | Attendance service | Clock-in/out, duplicate shift, grace rules |
| P2 | User service | Pagination, filters, role assignment, self-demotion guard |
| P2 | Frontend: API client | Token attach, 401 → refresh retry, refresh failure → logout |
| P2 | Frontend: forms | Employee, leave, claim, payslip filter (validation + submit + toast) |
| P2 | Notification/Audit service | Fan-out, unread counts, audit entries by actor/action/date |
| P3 | Frontend: data tables | Pagination, sorting, search debounce, empty state |
| P3 | Chat/WS | Message send, reconnect (mock STOMP) |

## 8. Definition of Done

- [ ] All 62 existing tests still pass
- [ ] New code ships with at least one unit test per behaviour (positive + negative)
- [ ] No test uses real credentials or network access
- [ ] `mvn test` and `npm test` green on the machine before push
- [ ] Coverage never regresses below current level
