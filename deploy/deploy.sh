#!/usr/bin/env bash
#
# deploy.sh — production deployment orchestrator for the Pixous HR Portal.
#
# Runs ON THE EC2 HOST. Invoked by .github/workflows/deploy.yml over SSH, or
# manually:  ~/hr-portal/deploy/deploy.sh
#
# Sequence:
#   1. acquire exclusive lock        (no concurrent deploys)
#   2. pre-deploy.sh                 (validate, record rollback point, backup)
#   3. git fetch + reset --hard      (match origin exactly)
#   4. docker compose pull + build   (base images + app images)
#   5. docker compose up -d          (recreate changed containers)
#   6. health-check.sh               (verify the stack actually works)
#   7. post-deploy.sh                (prune, version record, notify)
#
# Any failure from step 3 onward triggers rollback.sh (unless ROLLBACK_ENABLED=0).
# Exit code is non-zero on failure so GitHub Actions marks the run red.
#
set -Eeuo pipefail

# ---------------------------------------------------------------------------
# Configuration — every value can be overridden from the environment.
# ---------------------------------------------------------------------------
DEPLOY_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="${APP_DIR:-$(cd -- "$DEPLOY_DIR/.." && pwd)}"

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-.env}"
DEPLOY_BRANCH="${DEPLOY_BRANCH:-main}"
GIT_REMOTE="${GIT_REMOTE:-origin}"

# Space-separated list of compose services that are built from source.
BUILT_SERVICES="${BUILT_SERVICES:-backend web}"
# Compose project name (the `name:` key in docker-compose.prod.yml). Built
# images are tagged "<project>-<service>:latest" by Compose.
COMPOSE_PROJECT="${COMPOSE_PROJECT:-hr-portal}"

LOG_DIR="${LOG_DIR:-$APP_DIR/deploy-logs}"
STATE_DIR="${STATE_DIR:-$APP_DIR/.deploy-state}"
LOCK_FILE="${LOCK_FILE:-/tmp/hrportal-deploy.lock}"
LOCK_WAIT_SECONDS="${LOCK_WAIT_SECONDS:-900}"

ROLLBACK_ENABLED="${ROLLBACK_ENABLED:-1}"
GIT_CLEAN_ENABLED="${GIT_CLEAN_ENABLED:-1}"
BUILD_TIMEOUT_SECONDS="${BUILD_TIMEOUT_SECONDS:-3600}"

export APP_DIR COMPOSE_FILE ENV_FILE DEPLOY_BRANCH GIT_REMOTE
export BUILT_SERVICES COMPOSE_PROJECT LOG_DIR STATE_DIR

# Compose profiles (e.g. COMPOSE_PROFILES=analytics) are honoured natively by
# Docker Compose v2 when exported.
export COMPOSE_PROFILES="${COMPOSE_PROFILES:-}"

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------
mkdir -p "$LOG_DIR" "$STATE_DIR"
DEPLOY_ID="$(date -u +%Y%m%d-%H%M%S)-$$"
LOG_FILE="$LOG_DIR/deploy-$DEPLOY_ID.log"
export DEPLOY_ID LOG_FILE

# Mirror all stdout/stderr into the log file while still streaming to the caller
# (so GitHub Actions shows live output).
exec > >(tee -a "$LOG_FILE") 2>&1

log()  { printf '[%s] %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$*"; }
step() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
ok()   { printf '\033[1;32m  ✔ %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m  ! %s\033[0m\n' "$*"; }
fail() { printf '\033[1;31m  ✘ %s\033[0m\n' "$*" >&2; }
export -f log step ok warn fail 2>/dev/null || true

# ---------------------------------------------------------------------------
# Compose wrapper — resolves `docker compose` (v2 plugin) or `docker-compose`.
# ---------------------------------------------------------------------------
if docker compose version >/dev/null 2>&1; then
    DC_BIN=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
    DC_BIN=(docker-compose)
else
    fail "Neither 'docker compose' nor 'docker-compose' is available on this host."
    exit 2
fi
export DC_BIN_STR="${DC_BIN[*]}"

compose() { "${DC_BIN[@]}" -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"; }

# ---------------------------------------------------------------------------
# Failure handling
# ---------------------------------------------------------------------------
DEPLOY_PHASE="init"
ROLLBACK_ARMED=0   # set to 1 once we have a recorded rollback point

on_error() {
    local exit_code=$?
    local line=${1:-?}
    fail "Deployment failed (exit $exit_code) during phase '$DEPLOY_PHASE' at line $line."

    if [[ "$ROLLBACK_ENABLED" == "1" && "$ROLLBACK_ARMED" == "1" ]]; then
        step "Automatic rollback"
        if [[ -x "$DEPLOY_DIR/rollback.sh" ]]; then
            if "$DEPLOY_DIR/rollback.sh" --auto; then
                fail "Deployment failed — the previous version was restored and is serving traffic."
            else
                fail "Deployment failed AND rollback failed. MANUAL INTERVENTION REQUIRED."
                fail "Host: $(hostname)  App dir: $APP_DIR  Log: $LOG_FILE"
            fi
        else
            fail "rollback.sh not found or not executable at $DEPLOY_DIR/rollback.sh"
        fi
    else
        warn "Rollback not performed (ROLLBACK_ENABLED=$ROLLBACK_ENABLED, armed=$ROLLBACK_ARMED)."
    fi

    notify_failure "$exit_code" "$DEPLOY_PHASE"
    log "Deployment $DEPLOY_ID FAILED. Full log: $LOG_FILE"
    exit "$exit_code"
}
trap 'on_error $LINENO' ERR

# Notification helper (no-op unless a webhook is configured).
notify_failure() {
    local code="$1" phase="$2"
    local text="🔴 HR Portal deploy FAILED on $(hostname) — phase '${phase}', exit ${code}. Commit: $(git -C "$APP_DIR" rev-parse --short HEAD 2>/dev/null || echo unknown)"
    _post_webhook "$text" || true
}

_post_webhook() {
    local text="$1"
    local payload
    payload=$(printf '{"text":%s}' "$(printf '%s' "$text" | sed 's/\\/\\\\/g; s/"/\\"/g; s/^/"/; s/$/"/')")
    if [[ -n "${SLACK_WEBHOOK_URL:-}" ]]; then
        curl -fsS -m 15 -X POST -H 'Content-Type: application/json' \
             -d "$payload" "$SLACK_WEBHOOK_URL" >/dev/null 2>&1 || true
    fi
    if [[ -n "${TEAMS_WEBHOOK_URL:-}" ]]; then
        curl -fsS -m 15 -X POST -H 'Content-Type: application/json' \
             -d "$payload" "$TEAMS_WEBHOOK_URL" >/dev/null 2>&1 || true
    fi
}
export -f _post_webhook 2>/dev/null || true

# ---------------------------------------------------------------------------
# Concurrency protection — only one deploy at a time, cluster-wide on this host.
# ---------------------------------------------------------------------------
exec 200>"$LOCK_FILE"
if ! flock -w "$LOCK_WAIT_SECONDS" 200; then
    fail "Another deployment has held the lock ($LOCK_FILE) for more than ${LOCK_WAIT_SECONDS}s. Aborting."
    exit 75   # EX_TEMPFAIL
fi
printf 'deploy_id=%s pid=%s started=%s\n' "$DEPLOY_ID" "$$" "$(date -u +%FT%TZ)" >&200 || true

cd "$APP_DIR"

log "======================================================================"
log "HR Portal deployment $DEPLOY_ID"
log "  host        : $(hostname)"
log "  app dir     : $APP_DIR"
log "  compose file: $COMPOSE_FILE"
log "  branch      : $GIT_REMOTE/$DEPLOY_BRANCH"
log "  profiles    : ${COMPOSE_PROFILES:-<none>}"
log "  triggered by: ${DEPLOY_TRIGGERED_BY:-manual}"
log "======================================================================"

# ---------------------------------------------------------------------------
# 1. Pre-deploy: validation, rollback point, database backup
# ---------------------------------------------------------------------------
DEPLOY_PHASE="pre-deploy"
step "Pre-deploy checks"
"$DEPLOY_DIR/pre-deploy.sh"
ROLLBACK_ARMED=1
ok "Pre-deploy checks passed; rollback point recorded."

PREVIOUS_SHA="$(cat "$STATE_DIR/previous_sha" 2>/dev/null || echo unknown)"

# ---------------------------------------------------------------------------
# 2. Sync source to origin/<branch>
# ---------------------------------------------------------------------------
DEPLOY_PHASE="git-sync"
step "Syncing source from $GIT_REMOTE/$DEPLOY_BRANCH"

if ! git rev-parse --git-dir >/dev/null 2>&1; then
    fail "$APP_DIR is not a git repository."
    exit 2
fi

log "Fetching..."
if ! git fetch --prune --tags "$GIT_REMOTE" "$DEPLOY_BRANCH"; then
    fail "git fetch failed. Check network access and the repository credentials"
    fail "(deploy key at ~/.ssh/ or a credential helper). See deploy/README.md."
    exit 3
fi

if ! git rev-parse --verify --quiet "$GIT_REMOTE/$DEPLOY_BRANCH" >/dev/null; then
    fail "Remote branch $GIT_REMOTE/$DEPLOY_BRANCH does not exist."
    exit 3
fi

TARGET_SHA="$(git rev-parse "$GIT_REMOTE/$DEPLOY_BRANCH")"
log "Previous commit: $PREVIOUS_SHA"
log "Target commit  : $TARGET_SHA"

if [[ "$PREVIOUS_SHA" == "$TARGET_SHA" ]]; then
    log "Working tree is already at the target commit — continuing anyway"
    log "(images may still need rebuilding, e.g. after a failed prior deploy)."
fi

log "Resetting working tree to $GIT_REMOTE/$DEPLOY_BRANCH (discarding local changes)..."
git reset --hard "$GIT_REMOTE/$DEPLOY_BRANCH"

if [[ "$GIT_CLEAN_ENABLED" == "1" ]]; then
    # NOTE: deliberately NOT -x. Ignored files (.env, deploy-logs/, .deploy-state/)
    # must survive; -x would delete the production .env and break the stack.
    log "Removing untracked files (ignored files are preserved)..."
    git clean -fd
fi

git --no-pager log -1 --pretty=format:'  commit  %H%n  author  %an%n  date    %ad%n  subject %s%n' || true
ok "Source synced to $TARGET_SHA"

# ---------------------------------------------------------------------------
# 3. Validate the compose file BEFORE touching running containers
# ---------------------------------------------------------------------------
DEPLOY_PHASE="compose-validate"
step "Validating compose configuration"
if ! compose config --quiet; then
    fail "docker compose config validation failed for $COMPOSE_FILE."
    fail "The committed compose file or the .env on this host is invalid."
    exit 4
fi
ok "Compose configuration is valid."

# ---------------------------------------------------------------------------
# 4. Pull base images, then build application images
# ---------------------------------------------------------------------------
DEPLOY_PHASE="pull"
step "Pulling upstream base images"
# --ignore-buildable: only pull images we do NOT build (mysql). Older Compose
# versions lack the flag, so fall back to a best-effort pull.
if ! compose pull --ignore-buildable 2>/dev/null; then
    warn "'--ignore-buildable' unsupported; falling back to best-effort pull."
    compose pull --ignore-pull-failures || warn "Some images could not be pulled; using local copies."
fi
ok "Base images up to date."

DEPLOY_PHASE="build"
step "Building application images"
log "This can take several minutes (Maven + npm builds)."
if ! timeout "$BUILD_TIMEOUT_SECONDS" bash -c "$(printf '%q ' "${DC_BIN[@]}") -f $(printf '%q' "$COMPOSE_FILE") --env-file $(printf '%q' "$ENV_FILE") build --pull"; then
    rc=$?
    if [[ $rc -eq 124 ]]; then
        fail "Build exceeded BUILD_TIMEOUT_SECONDS=${BUILD_TIMEOUT_SECONDS}s and was killed."
    else
        fail "docker compose build failed (exit $rc). See the build output above."
    fi
    exit 5
fi
ok "Images built."

# ---------------------------------------------------------------------------
# 5. Start / recreate containers
# ---------------------------------------------------------------------------
DEPLOY_PHASE="up"
step "Starting containers"
compose up -d --remove-orphans
ok "Containers started."

compose ps

# ---------------------------------------------------------------------------
# 6. Health verification
# ---------------------------------------------------------------------------
DEPLOY_PHASE="health-check"
step "Verifying application health"
"$DEPLOY_DIR/health-check.sh"
ok "All health checks passed."

# ---------------------------------------------------------------------------
# 7. Post-deploy: cleanup, version record, notification
# ---------------------------------------------------------------------------
DEPLOY_PHASE="post-deploy"
step "Post-deploy tasks"
DEPLOYED_SHA="$TARGET_SHA" PREVIOUS_SHA="$PREVIOUS_SHA" "$DEPLOY_DIR/post-deploy.sh"

# Disarm rollback — we are live and healthy.
ROLLBACK_ARMED=0
trap - ERR

log "======================================================================"
ok "DEPLOYMENT SUCCESSFUL"
log "  deploy id : $DEPLOY_ID"
log "  commit    : $TARGET_SHA"
log "  previous  : $PREVIOUS_SHA"
log "  log file  : $LOG_FILE"
log "======================================================================"
exit 0
