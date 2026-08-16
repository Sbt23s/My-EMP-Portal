# Pixous HR Portal — Flutter mobile app

A native client for the existing HR Portal backend. It calls the same APIs as the
web app, with the same authentication and the same authorisation. **No backend
change was required, and the web application was not touched.**

---

## Status

**Built, verified, and working end to end:**

| | |
|---|---|
| `flutter analyze` | **No issues found** |
| `flutter test` | **107 / 107 passing** |
| Architecture | Clean layers, Riverpod, repository pattern |
| APK | `dist/Pixous_HR_Portal_v1.0.apk` (release, installable on any Android phone) |
| Backend | Live at `https://pixoushrportal.pixous.info/api` (verified: login issues a real JWT) |

### Modules shipped (same feature set as the web portal)

| Area | What's in the app |
|---|---|
| **Auth** | Sign in, session restore, automatic token refresh, sign out, admin-refusal guidance |
| **Dashboard** | The employee's own summary (attendance, leave, tasks) |
| **Attendance** | Punch in / punch out with GPS + selfie (face-verified punch), today + month history |
| **Leave** | Balances, my requests, apply, withdraw |
| **Permissions** | My permission requests + apply |
| **Approvals** | Manager approvals queue |
| **Chat** | Company chat |
| **Calls** | **Audio + video calls (WebRTC)** — voice/video buttons in 1:1 chat, full-screen call overlay, mute/camera/hang-up, incoming-call handling |
| **Notifications** | List + unread state |
| **Profile** | Details, sign out |
| **Calendar** | Personal / work calendar |
| **Work Reports** | Submit daily reports, list |
| **Teams** | Team list, team attendance |
| **Complaints** | Raise + track complaints |
| **Helpdesk** | Raise support tickets |
| **Claims** | Submit expense/travel claims |
| **My team** | Team roster, upcoming birthdays/anniversaries, who is off today, one-tap team chat |
| **Safety** | Report incidents (type, severity, zone, anonymous), my reports, staff review + resolve |
| **Communities** | Create groups, add/remove members, delete groups, open group chat |
| **Employees** | Searchable company directory with contact details |
| **Audit log** | The security trail (who/what/when/where) for HR & admins |
| **AI assistant** | Conversational assistant over the portal knowledge base |
| **HR screens** | Leave policies, payroll, onboarding tasks, reports |
| **Themes** | Material 3, light and dark, company branding applied at the root |

**By design:** System admins and company admins sign in on the **web** — the
admin console (users, roles, branding, audit logs, module management) is built
for a wide window. The mobile app serves employees, team leads and HR. The
server is unchanged; an admin's credentials still work in the browser.

---

## The APK

The ready-to-install release build lives in this folder:

```
mobile-app/dist/Pixous_HR_Portal_v1.0.apk
```

Copy it to any Android phone and install (allow "install unknown apps"). It
points at the live hosted backend by default — no configuration needed.

**Rebuilding it:**

```bash
cd mobile-app
flutter pub get
flutter build apk --release
# -> build/app/outputs/flutter-apk/app-release.apk
```

The base URL is a compile-time constant, so a release build cannot be repointed
at runtime. For a local backend during development:

```bash
# Android emulator — 10.0.2.2 is the host machine as the emulator sees it
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:7060/api

# A physical device on the same wifi — use the machine's LAN address
flutter run --dart-define=API_BASE_URL=http://192.168.1.50:7060/api
```

> **On this machine**, `flutter` needs `C:\Windows\System32` on the PATH — it
> shells out to PowerShell and cannot find it otherwise. If `flutter` reports
> *"PowerShell executable not found"*, that is why.

---

## How it is put together

```
lib/
├── core/
│   ├── auth/         who may use the mobile app (employees, TLs, HR)
│   ├── branding/     company colours/logo applied at the root
│   ├── calls/        WebRTC call service
│   ├── config/       where the app points, and its timeouts
│   ├── error/        Failure types — what a screen can act on
│   ├── location/     GPS for punch-in / punch-out
│   ├── network/      Dio client, token refresh, response envelope
│   ├── realtime/     push notifications + realtime service
│   └── storage/      tokens in the keystore/keychain
├── models/           the API's shapes, parsed defensively
├── repositories/     the only place that talks to the network
├── providers/        Riverpod wiring and auth state
├── features/         one folder per screen
├── widgets/          loading, error and empty states; stat cards
├── themes/           Material 3, light and dark
└── routes/           the tab shell
```

**Riverpod throughout**, one solution used consistently. Nothing constructs its
own HTTP client; there is exactly one token store.

### Three things worth knowing

**A cached profile is never a session.** The token decides. On startup the app
asks the server who the token belongs to; a rejection clears everything before a
screen is drawn.

**Concurrent 401s share one refresh.** Six widgets loading at once would
otherwise fire six refresh calls, five of which fail because the first already
rotated the token. One refresh runs; everyone else waits for its result.

**A failure looks like a failure.** No screen substitutes plausible numbers for a
request that did not come back.

---

## Error handling

Every `DioException` becomes a `Failure` with a sentence worth showing:

| Failure | When | What the person sees |
|---|---|---|
| `NetworkFailure` | never reached the server | "No connection. Check your internet." |
| `TimeoutFailure` | too slow | "The server took too long." |
| `AuthFailure` | 401 after refresh failed | Signed out, back to login |
| `ForbiddenFailure` | 403 | "You don't have permission to do that." |
| `ValidationFailure` | 400 / 422 | The server's message, plus field errors |
| `ServerFailure` | 5xx | The message and the backend's reference id |

---

## Security

- Tokens in the Android keystore / iOS keychain, never in shared preferences
- Base URL fixed at compile time
- Permission checks only decide what to draw; **the server decides what is
  allowed**. A hidden button is a courtesy, not a control
- No demo credentials anywhere in the source

---

## Testing

```bash
flutter analyze     # No issues found
flutter test        # 107/107
```

The suite covers login flow, session restore, parsing of API payloads (incl.
nulls and missing fields), attendance, leave, work reports, branding, safety
incidents, celebrations, and more.

A full **web ↔ mobile parity audit** lives in `WEB_PARITY_AUDIT.md` — every web
module is mapped to its mobile screen, with what was added in this pass and how
to re-verify.
Add a module by following the pattern below and matching the API contract in
`backend/src/main/java/com/pixous/hrportal/modules/` rather than guessing —
several endpoints put their payload directly in the body instead of under
`data`, and `ApiEnvelope` copes with both.
