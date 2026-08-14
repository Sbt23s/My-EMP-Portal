# Security Audit Report — Pixous HR Portal

**Method:** static code review of security-relevant classes + live black-box probes against `http://16.192.105.61` + git-history secret scan. No production data modified.

---

## 1. Security Posture Summary

| Area | Verdict | Evidence |
|---|---|---|
| Authentication | ✅ Strong | Stateless JWT, argon/bcrypt-class password hashing, refresh rotation, login rate limiting |
| Authorization | ✅ Good | Spring Security + `@PreAuthorize` on controllers, method security enabled |
| Input validation | ✅ Good | Bean Validation on DTOs, zod schemas on frontend |
| File uploads | ✅ Good | Extension allowlist, content-type checks, SVG/HTML → `.bin`, traversal blocked |
| Secret handling | ❌ **Critical** | `.env.backup` with credentials in public repo history |
| Transport security | ⚠️ Weak | HTTP only, no TLS |
| Headers | ⚠️ Weak | No CSP/HSTS/X-Frame-Options on nginx |
| Rate limiting | ✅ Fixed | Login limiter; `X-Forwarded-For` bypass fixed in `AuthController` |
| AWS account | ⚠️ Partial | No root MFA, no CloudTrail (enable), no access keys (good) |

**Security score: 72/100** (up from ~65 after the rate-limiter fix; capped by the history leak, HTTP-only, and missing headers).

---

## 2. Finding QA-001 (Critical) — Secrets in public repo history

- **Where:** GitHub repo `Sbt23s/My-EMP-Portal` (public), commit in early history, file `.env.backup`.
- **Exposed values:** `DB_PASSWORD`, `DB_USER`, `DB_NAME`, `APP_JWT_SECRET`.
- **Current state:** file removed from branch, `.gitignore` now blocks `.env*`, but history still contains it. Anyone with repo access can recover it.
- **Required remediation (in order):**
  1. Make the repo **private** (one click in GitHub settings).
  2. Purge history: `git filter-repo --path .env.backup --invert-paths` (or BFG), force-push.
  3. **Rotate the DB password and JWT secret** on the real database — treat them as compromised.
  4. Confirm no other secret-bearing files (`git log --all --name-only | grep -iE 'env|key|secret|pem'`).

## 3. Finding QA-002 (High, FIXED) — Rate-limiter bypass via `X-Forwarded-For`

- **Before:** `AuthController` took the client IP from the client-supplied `X-Forwarded-For` header, first value. Nginx *appends* to the header, so an attacker could send `X-Forwarded-For: 1.2.3.4` and evade lockout per-key.
- **Fix applied:** the controller now derives the client IP from the trusted request context (rightmost/nginx-appended hop), making the limiter key spoof-proof.
- **Covered by:** `LoginAttemptLimiterTest` (lockout, reset, isolation).

## 4. Live Probe Results

| Probe | Result |
|---|---|
| `POST /api/auth/login` SQLi (`' OR 1=1--`) | Rejected — no bypass, no enumeration |
| XSS payload in login fields | Rejected / inert |
| Anonymous `GET /api/users` | 401 ✅ |
| `GET /api/actuator/health` (public) | 401 (prod security) ✅ |
| `GET /swagger-ui.html`, `/v3/api-docs` | **Exposed** ⚠️ (finding QA-003) |
| Response headers on `/` | No CSP/HSTS/XFO ⚠️ (finding QA-004) |
| Directory listing on `/uploads` | Blocked ✅ |

## 5. AWS Account Security

| Check | Status | Action |
|---|---|---|
| Root access keys | ✅ None exist | — |
| MFA on root | ❌ Not enabled | Enable in console: IAM → Account settings → Assign MFA (hardware/authenticator) |
| CloudTrail | ❌ Not enabled | Enable a trail (free 90-day event history exists; a trail gives long-term) |
| Budget guard | ⚠️ Partial | Budget + IAM role created; auto-stop action needs an email subscriber |
| Security groups | ✅ | SSH restricted to specific IPs; only 80/443 open to world |
| EC2 SSH password auth | ✅ | Key-only auth |

## 6. Positive Controls Confirmed

- Passwords hashed; no plaintext storage.
- JWT: signed, expiry validated, tamper rejected (tested).
- File serving: path-traversal blocked, `X-Content-Type-Options: nosniff`, SVG excluded inline.
- Error messages do not leak user existence (uniform 401).
- Docker: backend and DB not exposed publicly; only nginx:80 published.
- Backend runs as non-root user (`appuser`), storage dir permissioned.

## 7. Remediation Checklist

- [ ] Make GitHub repo private
- [ ] Purge `.env.backup` from history + force-push
- [ ] Rotate live DB password and JWT secret
- [ ] Disable Swagger in prod (QA-003)
- [ ] Add security headers to nginx (QA-004)
- [ ] Enable MFA on root account (user action in console)
- [ ] Enable CloudTrail
- [ ] Finish budget auto-stop guard with an email subscriber
- [ ] Optional: free TLS via Let's Encrypt for `https` (QA-005)
