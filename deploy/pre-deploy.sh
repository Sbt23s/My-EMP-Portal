#!/usr/bin/env bash
#
# pre-deploy.sh — everything that must be verified or preserved BEFORE the
# working tree is reset and containers are rebuilt.
#
# Runs standalone:  ~/hr-portal/deploy/pre-deploy.sh
#
# Responsibilities:
#   1. verify tooling (docker, compose, git) and daemon access
#   2. verify the .env file exists and has no unfilled placeholders
#   3. verify free disk space and available memory
#   4. record the current commit SHA as the rollback point
#   5. retag the currently-running images as ":rollback"
#   6. take a MySQL dump before any schema migration runs
#
# Exits non-zero (with an explanatory message) if the host is not fit to deploy.
#
set -Eeuo pipefail

DEPLOY_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="${APP_DIR:-$(cd -- "$DEPLOY_DIR/.." && pwd)}"

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-.env}"
BUILT_SERVICES="${BUILT_SERVICES:-backend web}"
COMPOSE_PROJECT="${COMPOSE_PROJECT:-hr-portal}"
STATE_DIR="${STATE_DIR:-$APP_DIR/.deploy-state}"

BACKUP_DIR="${BACKUP_DIR:-$HOME/backups}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
BACKUP_BEFORE_DEPLOY="${BACKUP_BEFORE_DEPLOY:-1}"
# Below this much free space, reclaim Docker build cache before dumping.
BACKUP_MIN_FREE_MB="${BACKUP_MIN_FREE_MB:-2048}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-hrportal-mysql}"

MIN_FREE_DISK_MB="${MIN_FREE_DISK_MB:-6000}"
MIN_FREE_MEM_MB="${MIN_FREE_MEM_MB:-400}"

# Required keys in .env, and the placeholder values that mean "not filled in".
REQUIRED_ENV_KEYS="${REQUIRED_ENV_KEYS:-MYSQL_ROOT_PASSWORD DB_PASSWORD APP_JWT_SECRET}"

log()  { printf '[%s] %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$*"; }
ok()   { printf '\033[1;32m  ✔ %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m  ! %s\033[0m\n' "$*"; }
fail() { printf '\033[1;31m  ✘ %s\033[0m\n' "$*" >&2; }

cd "$APP_DIR"
mkdir -p "$STATE_DIR"

# ---------------------------------------------------------------------------
# 1. Tooling
# ---------------------------------------------------------------------------
log "Checking tooling..."

command -v git >/dev/null 2>&1 || { fail "git is not installed."; exit 2; }

if docker compose version >/dev/null 2>&1; then
    DC=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
    DC=(docker-compose)
else
    fail "Docker Compose is not installed (need the docker-compose-plugin)."
    exit 2
fi

if ! docker info >/dev/null 2>&1; then
    fail "Cannot talk to the Docker daemon as user '$(id -un)'."
    fail "Either the daemon is stopped (sudo systemctl start docker) or this user"
    fail "is not in the 'docker' group (sudo usermod -aG docker $(id -un), then re-login)."
    exit 2
fi
ok "docker, ${DC[*]} and git are available."

compose() { "${DC[@]}" -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"; }

# ---------------------------------------------------------------------------
# 2. Environment file
# ---------------------------------------------------------------------------
log "Validating $ENV_FILE..."

if [[ ! -f "$ENV_FILE" ]]; then
    fail "$APP_DIR/$ENV_FILE is missing."
    fail "Create it once on this host:  cp .env.production.example .env && nano .env"
    fail "It is git-ignored on purpose and is NEVER shipped by the deployment."
    exit 3
fi

# Permissions: the file holds the DB and JWT secrets.
env_perms="$(stat -c '%a' "$ENV_FILE" 2>/dev/null || echo '')"
if [[ -n "$env_perms" && "$env_perms" != "600" && "$env_perms" != "400" ]]; then
    warn "$ENV_FILE is mode $env_perms; tightening to 600."
    chmod 600 "$ENV_FILE" || warn "Could not chmod $ENV_FILE."
fi

missing_keys=()
placeholder_keys=()
for key in $REQUIRED_ENV_KEYS; do
    line="$(grep -E "^[[:space:]]*${key}=" "$ENV_FILE" | tail -n1 || true)"
    if [[ -z "$line" ]]; then
        missing_keys+=("$key")
        continue
    fi
    value="${line#*=}"
    value="${value%\"}"; value="${value#\"}"
    value="${value%\'}"; value="${value#\'}"
    if [[ -z "$value" ]] || [[ "$value" == CHANGE_ME* ]] || [[ "$value" == change-this* ]]; then
        placeholder_keys+=("$key")
    fi
done

if (( ${#missing_keys[@]} > 0 )); then
    fail "Missing required keys in $ENV_FILE: ${missing_keys[*]}"
    exit 3
fi
if (( ${#placeholder_keys[@]} > 0 )); then
    fail "These keys still hold placeholder values in $ENV_FILE: ${placeholder_keys[*]}"
    fail "Generate real values:  openssl rand -base64 36"
    exit 3
fi

# JWT secret must be long enough for HS256 or the backend refuses to start.
jwt_line="$(grep -E '^[[:space:]]*APP_JWT_SECRET=' "$ENV_FILE" | tail -n1 || true)"
jwt_value="${jwt_line#*=}"
if (( ${#jwt_value} < 32 )); then
    fail "APP_JWT_SECRET is only ${#jwt_value} characters; it must be at least 32."
    exit 3
fi
ok "$ENV_FILE present and populated."

# ---------------------------------------------------------------------------
# 3. Host capacity
# ---------------------------------------------------------------------------
log "Checking host capacity..."

free_disk_mb="$(df -Pm "$APP_DIR" | awk 'NR==2 {print $4}')"
if (( free_disk_mb < MIN_FREE_DISK_MB )); then
    fail "Only ${free_disk_mb} MB free on the volume holding $APP_DIR (need ${MIN_FREE_DISK_MB} MB)."
    fail "Reclaim space:  docker image prune -af && docker builder prune -af"
    fail "Inspect usage:  docker system df"
    exit 4
fi
ok "Disk: ${free_disk_mb} MB free."

# Docker's own data root can live on a different filesystem.
docker_root="$(docker info --format '{{.DockerRootDir}}' 2>/dev/null || echo '/var/lib/docker')"
if [[ -d "$docker_root" ]]; then
    docker_free_mb="$(df -Pm "$docker_root" | awk 'NR==2 {print $4}')"
    if (( docker_free_mb < MIN_FREE_DISK_MB )); then
        fail "Only ${docker_free_mb} MB free on Docker's data root ($docker_root); need ${MIN_FREE_DISK_MB} MB."
        exit 4
    fi
    ok "Docker root ($docker_root): ${docker_free_mb} MB free."
fi

if [[ -r /proc/meminfo ]]; then
    avail_mem_mb=$(( $(awk '/MemAvailable/ {print $2}' /proc/meminfo) / 1024 ))
    swap_total_mb=$(( $(awk '/SwapTotal/ {print $2}' /proc/meminfo) / 1024 ))
    if (( avail_mem_mb < MIN_FREE_MEM_MB )); then
        if (( swap_total_mb > 0 )); then
            warn "Only ${avail_mem_mb} MB RAM available, but ${swap_total_mb} MB swap exists. Continuing."
        else
            fail "Only ${avail_mem_mb} MB RAM available (need ${MIN_FREE_MEM_MB} MB) and NO swap is configured."
            fail "The Maven/npm builds will be OOM-killed. Add swap:"
            fail "  sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile"
            exit 4
        fi
    else
        ok "Memory: ${avail_mem_mb} MB available, ${swap_total_mb} MB swap."
    fi
fi

# ---------------------------------------------------------------------------
# 4. Record the rollback point (current commit)
# ---------------------------------------------------------------------------
log "Recording rollback point..."

current_sha="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
printf '%s\n' "$current_sha" > "$STATE_DIR/previous_sha"
printf '%s\n' "$(date -u +%FT%TZ)" > "$STATE_DIR/previous_sha.timestamp"
ok "Rollback commit recorded: $current_sha"

# ---------------------------------------------------------------------------
# 5. Retag currently-running images as :rollback
# ---------------------------------------------------------------------------
log "Tagging current images for rollback..."

tagged=0
for svc in $BUILT_SERVICES; do
    img="${COMPOSE_PROJECT}-${svc}:latest"
    if docker image inspect "$img" >/dev/null 2>&1; then
        docker image tag "$img" "${COMPOSE_PROJECT}-${svc}:rollback"
        ok "  $img → ${COMPOSE_PROJECT}-${svc}:rollback"
        tagged=$((tagged + 1))
    else
        warn "  $img not present (first deploy?) — no rollback image for '$svc'."
    fi
done

if (( tagged == 0 )); then
    warn "No images were tagged. Image-level rollback is unavailable for this deploy."
    printf '0\n' > "$STATE_DIR/rollback_images_available"
else
    printf '1\n' > "$STATE_DIR/rollback_images_available"
fi

# ---------------------------------------------------------------------------
# 6. Database backup (before Flyway migrations run against it)
# ---------------------------------------------------------------------------
if [[ "$BACKUP_BEFORE_DEPLOY" != "1" ]]; then
    warn "BACKUP_BEFORE_DEPLOY=0 — skipping the pre-deploy database backup."
elif ! docker ps --format '{{.Names}}' | grep -qx "$MYSQL_CONTAINER"; then
    warn "MySQL container '$MYSQL_CONTAINER' is not running — skipping backup (first deploy?)."
else
    log "Backing up the database before migrations..."
    mkdir -p "$BACKUP_DIR"

    # Retention runs BEFORE the dump, not only after it. Rotating first means a
    # host that has filled up with old backups can still make room for the new
    # one, instead of failing the deploy while holding a fortnight of dumps.
    rotated="$(find "$BACKUP_DIR" -name 'hr-predeploy-*.sql.gz' -mtime "+$BACKUP_RETENTION_DAYS" -print -delete 2>/dev/null | wc -l)"
    (( rotated > 0 )) && log "Rotated out $rotated backup(s) older than ${BACKUP_RETENTION_DAYS} days."

    # A dump needs headroom. When the disk is nearly full the usual culprit is
    # Docker's build cache and dangling images, both safe to drop — named volumes
    # (the database itself) are never touched.
    avail_mb="$(df -Pm "$BACKUP_DIR" | awk 'NR==2 {print $4}')"
    if [[ -n "$avail_mb" ]] && (( avail_mb < BACKUP_MIN_FREE_MB )); then
        warn "Only ${avail_mb}MB free on $(df -P "$BACKUP_DIR" | awk 'NR==2 {print $6}') — reclaiming Docker space."
        docker builder prune -af >/dev/null 2>&1 || true
        docker image prune -f  >/dev/null 2>&1 || true
        avail_mb="$(df -Pm "$BACKUP_DIR" | awk 'NR==2 {print $4}')"
        log "Free space after reclaim: ${avail_mb}MB."
    fi

    backup_file="$BACKUP_DIR/hr-predeploy-$(date -u +%Y%m%d-%H%M%S).sql.gz"

    # Credentials are read from the container's own environment; they are never
    # placed on the command line (where `ps` would expose them).
    if docker exec "$MYSQL_CONTAINER" sh -c \
        'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --quick --routines --events "$MYSQL_DATABASE"' \
        2>"$STATE_DIR/backup.err" | gzip > "$backup_file"; then

        size="$(du -h "$backup_file" | cut -f1)"
        # A dump that is suspiciously small usually means it failed silently.
        bytes="$(stat -c '%s' "$backup_file")"
        if (( bytes < 1024 )); then
            fail "Backup file $backup_file is only ${bytes} bytes — the dump almost certainly failed."
            fail "mysqldump stderr: $(cat "$STATE_DIR/backup.err" 2>/dev/null | head -5)"
            fail "Free space: $(df -Ph "$BACKUP_DIR" | awk 'NR==2 {print $4" on "$6}')"
            exit 5
        fi
        printf '%s\n' "$backup_file" > "$STATE_DIR/last_backup"
        ok "Database backed up: $backup_file ($size)"
    else
        fail "mysqldump failed. Refusing to deploy without a database backup."
        fail "stderr: $(cat "$STATE_DIR/backup.err" 2>/dev/null | head -5)"
        fail "Free space: $(df -Ph "$BACKUP_DIR" | awk 'NR==2 {print $4" on "$6}')"
        fail "Override for this run with BACKUP_BEFORE_DEPLOY=0 if you accept the risk."
        exit 5
    fi

    # Retention
    deleted="$(find "$BACKUP_DIR" -name 'hr-predeploy-*.sql.gz' -mtime "+$BACKUP_RETENTION_DAYS" -print -delete 2>/dev/null | wc -l)"
    (( deleted > 0 )) && log "Pruned $deleted backup(s) older than ${BACKUP_RETENTION_DAYS} days."
fi

ok "Pre-deploy checks complete."
exit 0
