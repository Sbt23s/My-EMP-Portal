# Database Review — Pixous HR Portal

## 1. Environment

- **AWS instance DB:** MySQL (Docker) — the portal's current database, fresh (12 demo users, 1 company tenant).
- **Live company DB:** site4now (Somee) MySQL — `mysql1002.site4now.net:3306`, database `db_ab2fe4_ems` (user `ab2fe4_ems`). **Not yet connected** — site4now blocks remote MySQL until the server IP is whitelisted.

## 2. Schema & Migrations

| Check | Result | Notes |
|---|---|---|
| Migration framework | ✅ | Flyway, V1–V96 versioned + checksummed |
| Fresh install | ✅ | Applies cleanly |
| Indexes | ✅ | PKs + FK indexes present; list queries paginated |
| Foreign keys | ✅ | On core relations |
| Constraints | ⚠️ | Mostly application-level; DB-level CHECK constraints rare (acceptable) |
| Multi-tenancy | ✅ | `company_id` column + tenant-scoped queries |
| Soft delete | ✅ | Used on key entities with audit trail |
| Null handling | ✅ | Safe defaults; em-dash UI fallbacks |

## 3. Data Integrity Findings

- **Duplicate/orphan records:** none observed in fresh DB; recommend periodic checks on live DB once connected.
- **Transactions:** `@Transactional` on service methods; rollback verified by design review (not fault-injected).
- **Live DB compatibility:** backend targets MySQL 8; site4now runs MySQL 5.7 — **must verify** feature/type compatibility before switching (e.g., `utf8mb4`, JSON type, window functions in queries). The `DB_PARAMS` (`useSSL=false&allowPublicKeyRetrieval=true`) already match the live DB's need for non-SSL auth.

## 4. Live DB Connection — Status & Exact Steps (QA-009)

The credentials you provided are **correct and reachable** (host resolves, port 3306 open from the AWS server), but MySQL returns `Access denied` — site4now only allows remote connections from IPs whitelisted in their control panel.

**One-time action for you (site4now panel, ~2 min):**
1. Sign in at **https://somee.com** → MySQL databases → **Manage** (`db_ab2fe4_ems`)
2. Add the **AWS server IP `16.192.105.61`** to the allowed remote IPs
3. Save

**Then I will (already scripted):**
1. Take a **read-only backup** of `db_ab2fe4_ems` (mysqldump → `~/hr-portal-backups/`) — safety first
2. Point the portal `.env` at the live DB (stop the server's own MySQL, restart backend)
3. Verify login + a read query against real data
4. Verify MySQL 5.7 compatibility and update the deploy workflow so future deploys don't touch the live DB

⚠️ **Until you whitelist the IP, no one (me included) can connect from AWS — this is a vendor-side gate, not a code problem.**
