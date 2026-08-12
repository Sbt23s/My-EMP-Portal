#!/usr/bin/env bash
#
# rollback.sh — restore the previously-running version of the stack.
#
# Called automatically by deploy.sh when a deployment fails its health checks
# (with --auto), or run by hand at any time:
#
#   ~/hr-portal/deploy/rollback.sh              # interactive, asks to confirm
#   ~/hr-portal/deploy/rollback.sh --auto       # no prompt (used by deploy.sh)
#   ~/hr-portal/deploy/rollback.sh --to <sha>   # roll back to a specific commit
#
# How it works:
#   pre-deploy.sh recorded the previous commit SHA and retagged the then-running
#   images as "<project>-<service>:rollback". Rolling back therefore means:
#     1. reset the working tree to the previous commit
#     2. restore the :rollback images over :latest
#     3. `up -d --no-build` so Compose uses those restored images (fast — no rebuild)
#     4. re-run the health checks to confirm the old version is genuinely serving
#
# If no rollback images exist (e.g. the very first deploy), it falls back to a
# rebuild from the previous commit.
#
set -Eeuo pipefail

DEPLOY_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="${APP_DIR:-$(cd -- "$DEPLOY_DIR/.." && pwd)}"

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-.env}"
BUILT_SERVICES="${BUILT_SERVICES:-backend web}"
COMPOSE_PROJECT="${COMPOSE_PROJECT:-hr-portal}"
STATE_DIR="${STATE_DIR:-$APP_DIR/.deploy-state}"
LOG_DIR="${LOG_DIR:-$APP_DIR/deploy-logs}"

AUTO=0
TARGET_SHA=""

while (( $# > 0 )); do
    case "$1" in
        --auto)  AUTO=1; shift ;;
        --to)    TARGET_SHA="${2:-}"; shift 2 ;;
        -h|--help)
            sed -n '2,30p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
            exit 0 ;;
        *)
            printf 'Unknown argument: %s (try --help)\n' "$1" >&2
            exit 64 ;;
    esac
done

log()  { printf '[%s] %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$*"; }
step() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
ok()   { printf '\033[1;32m  ✔ %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m  ! %s\033[0m\n' "$*"; }
fail() { printf '\033[1;31m  ✘ %s\033[0m\n' "$*" >&2; }

cd "$APP_DIR"
mkdir -p "$LOG_DIR" "$STATE_DIR"

ROLLBACK_LOG="$LOG_DIR/rollback-$(date -u +%Y%m%d-%H%M%S).log"
exec > >(tee -a "$ROLLBACK_LOG") 2>&1

if docker compose version >/dev/null 2>&1; then
    DC=(docker compose)
else
    DC=(docker-compose)
fi
compose() { "${DC[@]}" -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"; }

# ---------------------------------------------------------------------------
# Determine the rollback target
# ---------------------------------------------------------------------------
if [[ -z "$TARGET_SHA" ]]; then
    if [[ -f "$STATE_DIR/previous_sha" ]]; then
        TARGET_SHA="$(cat "$STATE_DIR/previous_sha")"
    elif [[ -f "$STATE_DIR/current_sha" ]]; then
        # No prior deploy point, but we know what is running now — a rollback with
        # nothing earlier to go to becomes an in-place restore of the current
        # version (restore images + restart), which is still safe and useful.
        TARGET_SHA="$(cat "$STATE_DIR/current_sha")"
        warn "No previous_sha recorded; falling back to the currently recorded commit ($TARGET_SHA)."
    elif TARGET_SHA="$(git rev-parse --verify --quiet HEAD)" && [[ -n "$TARGET_SHA" ]]; then
        # Brand-new host with no deploy state at all. Restore/restart the checked
        # out commit rather than failing the whole pipeline.
        warn "No deploy state recorded; falling back to the current git HEAD ($TARGET_SHA)."
    else
        # Genuinely nothing to act on. In --auto (called from a failed deploy or a
        # manual rollback trigger) do NOT hard-fail the pipeline over a missing
        # rollback point — the running stack, whatever it is, stays untouched.
        if (( AUTO == 1 )); then
            warn "No rollback point recorded and no git HEAD — nothing to roll back to."
            warn "Leaving the currently running stack untouched."
            exit 0
        fi
        fail "No rollback point recorded ($STATE_DIR/previous_sha is missing)."
        fail "Specify one explicitly:  $0 --to <commit-sha>"
        exit 2
    fi
fi

if [[ "$TARGET_SHA" == "unknown" || -z "$TARGET_SHA" ]]; then
    fail "Recorded rollback commit is 'unknown' — cannot roll back automatically."
    fail "Pick a commit from:  git log --oneline -20"
    exit 2
fi

if ! git cat-file -e "${TARGET_SHA}^{commit}" 2>/dev/null; then
    fail "Commit $TARGET_SHA does not exist in this repository."
    fail "Try:  git fetch origin && $0 --to <sha>"
    exit 2
fi

CURRENT_SHA="$(git rev-parse HEAD 2>/dev/null || echo unknown)"

step "Rollback plan"
log "  current commit : $CURRENT_SHA"
log "  rollback to    : $TARGET_SHA"
log "  recorded at    : $(cat "$STATE_DIR/previous_sha.timestamp" 2>/dev/null || echo 'n/a')"
log "  log file       : $ROLLBACK_LOG"
git --no-pager log -1 --pretty=format:'  target subject : %s%n' "$TARGET_SHA" 2>/dev/null || true

if [[ "$CURRENT_SHA" == "$TARGET_SHA" ]]; then
    warn "Already at $TARGET_SHA — restoring images and restarting anyway."
fi

if (( AUTO == 0 )); then
    printf '\nProceed with rollback? [y/N] '
    read -r answer
    if [[ ! "$answer" =~ ^[Yy]$ ]]; then
        log "Rollback cancelled."
        exit 0
    fi
fi

# ---------------------------------------------------------------------------
# 1. Restore the source tree
# ---------------------------------------------------------------------------
step "Restoring source to $TARGET_SHA"
if ! git reset --hard "$TARGET_SHA"; then
    fail "git reset to $TARGET_SHA failed."
    exit 3
fi
ok "Working tree is at $TARGET_SHA"

# ---------------------------------------------------------------------------
# 2. Restore the previously-running images
# ---------------------------------------------------------------------------
step "Restoring images"
restored=0
missing=0
for svc in $BUILT_SERVICES; do
    rollback_img="${COMPOSE_PROJECT}-${svc}:rollback"
    live_img="${COMPOSE_PROJECT}-${svc}:latest"
    if docker image inspect "$rollback_img" >/dev/null 2>&1; then
        docker image tag "$rollback_img" "$live_img"
        ok "  $rollback_img → $live_img"
        restored=$((restored + 1))
    else
        warn "  no rollback image for '$svc' ($rollback_img not found)"
        missing=$((missing + 1))
    fi
done

REBUILD=0
if (( restored == 0 )); then
    warn "No rollback images available — rebuilding from source at $TARGET_SHA."
    warn "This is slower but produces the same result."
    REBUILD=1
elif (( missing > 0 )); then
    warn "Only some rollback images were found; rebuilding to keep the stack consistent."
    REBUILD=1
fi

# ---------------------------------------------------------------------------
# 3. Bring the stack back up
# ---------------------------------------------------------------------------
step "Restarting the stack"
if ! compose config --quiet; then
    fail "The compose file at $TARGET_SHA is invalid — cannot roll back to it."
    exit 4
fi

if (( REBUILD == 1 )); then
    log "Rebuilding images from $TARGET_SHA ..."
    if ! compose build; then
        fail "Rebuild during rollback failed."
        exit 5
    fi
    compose up -d --remove-orphans
else
    # --no-build is essential: without it Compose may rebuild from the current
    # source and silently defeat the image restore.
    compose up -d --no-build --remove-orphans
fi
ok "Containers restarted."

compose ps

# ---------------------------------------------------------------------------
# 4. Verify the restored version is actually healthy
# ---------------------------------------------------------------------------
step "Verifying the rolled-back version"
if [[ -x "$DEPLOY_DIR/health-check.sh" ]]; then
    if "$DEPLOY_DIR/health-check.sh"; then
        ok "Rolled-back version is healthy and serving traffic."
    else
        fail "The rolled-back version is ALSO unhealthy."
        fail "The stack is down. Investigate immediately:"
        fail "  docker compose -f $COMPOSE_FILE logs --tail 200"
        fail "  Restore the database if a migration ran: see deploy/README.md"
        exit 6
    fi
else
    warn "health-check.sh not found — skipping verification."
fi

# ---------------------------------------------------------------------------
# 5. Record the rollback
# ---------------------------------------------------------------------------
{
    printf 'timestamp=%s\n' "$(date -u +%FT%TZ)"
    printf 'event=rollback\n'
    printf 'from_sha=%s\n' "$CURRENT_SHA"
    printf 'to_sha=%s\n' "$TARGET_SHA"
    printf 'rebuilt=%s\n' "$REBUILD"
    printf 'host=%s\n' "$(hostname)"
} >> "$STATE_DIR/deployment-history.log"

printf '%s\n' "$TARGET_SHA" > "$STATE_DIR/current_sha"

if [[ -n "${SLACK_WEBHOOK_URL:-}${TEAMS_WEBHOOK_URL:-}" ]]; then
    msg="🟠 HR Portal ROLLED BACK on $(hostname): ${CURRENT_SHA:0:7} → ${TARGET_SHA:0:7}"
    payload="$(printf '{"text":"%s"}' "$msg")"
    [[ -n "${SLACK_WEBHOOK_URL:-}" ]] && curl -fsS -m 15 -X POST -H 'Content-Type: application/json' -d "$payload" "$SLACK_WEBHOOK_URL" >/dev/null 2>&1 || true
    [[ -n "${TEAMS_WEBHOOK_URL:-}" ]] && curl -fsS -m 15 -X POST -H 'Content-Type: application/json' -d "$payload" "$TEAMS_WEBHOOK_URL" >/dev/null 2>&1 || true
fi

step "Rollback complete"
log "  now running : $TARGET_SHA"
log "  log file    : $ROLLBACK_LOG"
warn "The database was NOT rolled back. If the failed deploy ran a Flyway"
warn "migration, restore the pre-deploy dump — see deploy/README.md."
if [[ -f "$STATE_DIR/last_backup" ]]; then
    warn "  Latest pre-deploy backup: $(cat "$STATE_DIR/last_backup")"
fi
exit 0
