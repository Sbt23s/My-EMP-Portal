# The live database, and the cache in front of it

## Which database is the real one

```
host      mysql1002.site4now.net
port      3306
database  db_ab2fe4_ems
user      ab2fe4_ems
```

The password is not in this repository and must not be put in it. It goes in the
`.env` next to `docker-compose.prod.yml`, which is git-ignored.

**The username is `ab2fe4_ems`.** `live_ems` is refused by the server — worth
stating because it is an easy thing to be told and to believe.

Everything in the company is in this one database: 68 employees, their attendance,
payroll, leave, chat. The hosting account offers no restore point, so it is also
the only copy unless somebody makes one.

### Two constraints that shape everything below

**Twenty connections, in total.** Not per application — per account, across every
process anywhere that uses these credentials. Exceed it and the server refuses
*every* new connection, including the portal's own, with:

```
ERROR 1226 (42000): User 'ab2fe4_ems' has exceeded the 'max_user_connections'
resource (current value: 20)
```

So `DB_POOL_MAX=6`, and automatic restart-on-save is off when running against this
database. A restart builds a fresh connection pool, and a pool whose context
failed to start is not always closed; the server's `interactive_timeout` is eight
hours, so nothing clears the leftovers. An afternoon of editing files locks the
portal out of its own database, and only killing the sessions by hand fixes it.
This has already happened once — seventeen abandoned sessions.

To see what is holding them, and to release the ones from a machine you recognise:

```sql
SELECT id, host, command, time FROM information_schema.processlist
WHERE user = 'ab2fe4_ems' ORDER BY time DESC;
-- then, for a session you are certain is abandoned:
KILL <id>;
```

**The account cannot create a database.** The connector's
`createDatabaseIfNotExist=true` asks the server to do exactly that on every
connect, and the server refuses — so the application will not start against a
database that already exists and is perfectly healthy. `DB_PARAMS` exists to leave
that parameter out:

```
DB_PARAMS=useSSL=false&serverTimezone=Asia/Kolkata&allowPublicKeyRetrieval=true
```

## Running against it

```powershell
.\run-live-db.ps1          # prompts for the password
```

It refuses to send SMS, so no test action reaches a real employee's phone, and it
turns restart-on-save off for the reason above.

Take a backup first if you are about to change anything:

```powershell
.\deploy\live-dump.ps1     # writes to %USERPROFILE%\hr-portal-backups
```

That file contains every employee's Aadhaar, bank details and salary. Treat it as
a printed payroll: not in the repository, not on a shared drive, not in email.

## The cache

Redis had been running for months holding nothing: the dependency was on the
classpath, the container was up, and no code had ever put a value in it. It does
now.

### What is cached, and what deliberately is not

| Cache      | Holds                                              | TTL |
|------------|----------------------------------------------------|-----|
| `masters`  | departments, teams, positions, blood groups, employment statuses, shifts, sites, offices | 30 min |
| `holidays` | the holiday calendar, per year                     | 6 h |
| `settings` | the twenty-one system settings                     | 15 min |

Nothing else. Attendance, payroll, leave and chat are read from the database every
single time, because a stale punch or a stale payslip is a wrong answer rather
than a fast one.

The TTLs are a backstop, not the mechanism. Every write path evicts what it
changed, so a new team or a moved office is visible on the next request. A TTL is
only reached when something changed the data behind the application's back — a
manual `UPDATE`, or a second instance.

### It cannot break the portal

Three separate reasons, because "the cache is down" must never mean "the portal is
down":

1. If there is no Redis at `REDIS_HOST` when the application starts, it caches in
   its own memory instead and logs that it did. Correct; just not shared between
   instances. This is the normal state on Windows hosting, where there is no Redis
   to install.
2. Every cache failure after startup — Redis restarting, its memory filling, a
   value written by an older version of a class — is logged and ignored, and the
   request goes to the database. A cache that cannot answer is indistinguishable
   from a cache that has nothing, and the application already knows how to handle
   that.
3. `CACHE_ENABLED=false` switches the whole thing off, for ruling it out while
   diagnosing something else.

### Checking it is actually doing something

```
GET    /api/cache/status     which backend, how many keys, memory used
DELETE /api/cache            empty it; the next read comes from the database
```

Both need `USER_MANAGE` or `ORG_MANAGE` — an admin, not HR.

From the Redis side, `keyspace_hits` is the honest number:

```powershell
docker exec hrportal-redis-local redis-cli --scan --pattern 'hrportal:*'
docker exec hrportal-redis-local redis-cli INFO stats | Select-String keyspace
```

Measured against the live database: three requests for eight dropdown lists each —
twenty-four lookups the database would otherwise have served — produced **24 Redis
hits and 0 misses**.

### One thing that was not a cache

Authentication runs on every authenticated request, and it was making three
round trips to the database rather than one: roles and permissions are mapped
`EAGER`, which sounds like one query and is not — Hibernate fetches the user, then
the roles, then each role's permissions. Against a database on the same machine
that is invisible. Against a hosted one it is a few hundred milliseconds each.

Fixed with a fetch join (`UserRepository.findByIdWithAuthorities`), not with a
cache, on purpose: an account that has just been disabled, locked after failed
logins, or offboarded has to stop working on the very next request, not when a
cache entry expires.
