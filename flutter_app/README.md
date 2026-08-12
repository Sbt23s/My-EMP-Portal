# Pixous HR Portal — Flutter mobile app

A native client for the existing HR Portal backend. It calls the same APIs as the
web app, with the same authentication and the same authorisation. **No backend
change was required, and the web application was not touched.**

---

## Where this actually stands

Read this part first.

**Built, verified, and working end to end:**

| | |
|---|---|
| `flutter analyze` | **No issues found** |
| `flutter test` | **5 / 5 passing** |
| Architecture | Clean layers, Riverpod, repository pattern |
| Auth | Sign in, session restore, automatic token refresh, sign out |
| Attendance | Punch in, punch out, today, this month |
| Leave | Balances, my requests, apply, withdraw |
| Dashboard | The employee's own summary |
| Profile | Details and sign out |
| Themes | Material 3, light and dark, both defined in full |

**Not built yet.** The web portal has sixteen modules; this app covers the
employee's daily journey. Still to come:

Payslip list and PDF · expense and travel claims · assets · helpdesk tickets ·
complaints · tasks · work reports · chat, calls and communities · documents ·
calendar · notifications over WebSocket · face-verified punch · manager
approvals · HR screens · reports.

Every one of them follows the pattern already laid down here: a model, a method
on a repository, a provider, a screen. Adding one is a small, well-shaped piece
of work — that was the point of building the foundation first.

The claim that this is "100% complete" would not be true, so it is not made. What
is here is finished and tested; what is missing is listed above.

---

## Running it

```bash
cd flutter_app
flutter pub get
flutter run
```

**Pointing it at a backend.** The default is the hosted one. For a local backend:

```bash
# Android emulator — 10.0.2.2 is the host machine as the emulator sees it
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:7060/api

# A physical device on the same wifi — use the machine's LAN address
flutter run --dart-define=API_BASE_URL=http://192.168.1.50:7060/api
```

The base URL is a compile-time constant, so a release build cannot be repointed
at runtime.

> **On this machine**, `flutter` needs `C:\Windows\System32` on the PATH — it
> shells out to PowerShell and cannot find it otherwise. If `flutter` reports
> *"PowerShell executable not found"*, that is why.

---

## How it is put together

```
lib/
├── core/
│   ├── config/       where the app points, and its timeouts
│   ├── error/        Failure types — what a screen can act on
│   ├── network/      Dio client, token refresh, response envelope
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
screen is drawn. The web client once treated a leftover cached user as being
signed in and showed an empty portal with no token behind it — that cannot
happen here.

**Concurrent 401s share one refresh.** Six widgets loading at once would
otherwise fire six refresh calls, five of which fail because the first already
rotated the token — and the session dies for no reason. One refresh runs;
everyone else waits for its result.

**A failure looks like a failure.** No screen substitutes plausible numbers for a
request that did not come back. The web dashboard used to invent "480 minutes
worked" when its request failed, on a card people read their own attendance
from. `EmployeeDashboard` here has no placeholder constructor, deliberately.

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

The reference matters: the backend now returns `(ref a1b2c3d4)` instead of an
exception dump, and that id matches its log.

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
flutter test        # 5/5
```

The tests cover parsing a login payload, surviving nulls and missing fields,
building initials, refusing an empty login form, and keeping the password
hidden. Thin on purpose — they are the shape for the rest, not the whole suite.
`docs/UNIT_TEST_SPECIFICATION.md` in the repository root lists what a full suite
should cover.

---

## Adding a module

1. **Model** in `models/` — parse defensively; a null must not throw
2. **Method** on a repository — return a model, let `ApiClient` map the errors
3. **Provider** — `FutureProvider.autoDispose`
4. **Screen** — `.when(loading:, error:, data:)`, with `ErrorState` and
   `EmptyState` kept distinct
5. **Tab** in `routes/app_shell.dart` if it needs one

Match the API contract to the controller in
`backend/src/main/java/com/pixous/hrportal/modules/` rather than guessing —
several endpoints put their payload directly in the body instead of under
`data`, and `ApiEnvelope` copes with both.
