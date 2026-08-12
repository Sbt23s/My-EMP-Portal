#!/usr/bin/env bash
#
# post-deploy.sh — runs only after health checks have passed.
#
# Runs standalone:  ~/hr-portal/deploy/post-deploy.sh
#
# Responsibilities:
#   1. record the deployed version (history log + VERSION file)
#   2. reclaim disk: dangling images, stopped containers, stale build cache
#   3. rotate deployment logs
#   4. report the resulting disk / container state
#   5. send a success notification if a webhook is configured
#
# This script is deliberately forgiving: a cleanup failure must never fail a
# deployment that is already live and healthy.
#
set -Euo pipefail   # NOTE: no -e — cleanup is best-effort by design.

DEPLOY_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="${APP_DIR:-$(cd -- "$DEPLOY_DIR/.." && pwd)}"

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-.env}"
STATE_DIR="${STATE_DIR:-$APP_DIR/.deploy-state}"
LOG_DIR="${LOG_DIR:-$APP_DIR/deploy-logs}"

PRUNE_ENABLED="${PRUNE_ENABLED:-1}"
PRUNE_BUILD_CACHE="${PRUNE_BUILD_CACHE:-1}"
BUILD_CACHE_KEEP="${BUILD_CACHE_KEEP:-6GB}"
LOG_RETENTION_DAYS="${LOG_RETENTION_DAYS:-30}"

DEPLOYED_SHA="${DEPLOYED_SHA:-$(git -C "$APP_DIR" rev-parse HEAD 2>/dev/null || echo unknown)}"
PREVIOUS_SHA="${PREVIOUS_SHA:-$(cat "$STATE_DIR/previous_sha" 2>/dev/null || echo unknown)}"

log()  { printf '[%s] %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$*"; }
ok()   { printf '\033[1;32m  ✔ %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m  ! %s\033[0m\n' "$*"; }

cd "$APP_DIR"
mkdir -p "$STATE_DIR" "$LOG_DIR"

if docker compose version >/dev/null 2>&1; then
    DC=(docker compose)
else
    DC=(docker-compose)
fi
compose() { "${DC[@]}" -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"; }

# ---------------------------------------------------------------------------
# 1. Version tracking
# ---------------------------------------------------------------------------
log "Recording deployed version..."

commit_subject="$(git -C "$APP_DIR" log -1 --pretty=format:'%s' 2>/dev/null || echo unknown)"
commit_author="$(git -C "$APP_DIR" log -1 --pretty=format:'%an' 2>/dev/null || echo unknown)"
commit_date="$(git -C "$APP_DIR" log -1 --pretty=format:'%aI' 2>/dev/null || echo unknown)"
deployed_at="$(date -u +%FT%TZ)"

printf '%s\n' "$DEPLOYED_SHA" > "$STATE_DIR/current_sha"

# A human-readable VERSION file, handy when SSHing in to ask "what is running?"
cat > "$STATE_DIR/VERSION" <<EOF
commit=$DEPLOYED_SHA
commit_short=${DEPLOYED_SHA:0:7}
subject=$commit_subject
author=$commit_author
committed_at=$commit_date
deployed_at=$deployed_at
deployed_by=${DEPLOY_TRIGGERED_BY:-manual}
deploy_id=${DEPLOY_ID:-n/a}
previous=$PREVIOUS_SHA
host=$(hostname)
EOF

{
    printf 'timestamp=%s ' "$deployed_at"
    printf 'event=deploy '
    printf 'from_sha=%s ' "$PREVIOUS_SHA"
    printf 'to_sha=%s ' "$DEPLOYED_SHA"
    printf 'deploy_id=%s ' "${DEPLOY_ID:-n/a}"
    printf 'by=%s ' "${DEPLOY_TRIGGERED_BY:-manual}"
    printf 'subject=%q\n' "$commit_subject"
} >> "$STATE_DIR/deployment-history.log"

ok "Version recorded: ${DEPLOYED_SHA:0:7} — $commit_subject"

# ---------------------------------------------------------------------------
# 2. Disk cleanup
# ---------------------------------------------------------------------------
if [[ "$PRUNE_ENABLED" != "1" ]]; then
    warn "PRUNE_ENABLED=0 — skipping Docker cleanup."
else
    log "Reclaiming disk space..."

    before_mb="$(df -Pm "$APP_DIR" | awk 'NR==2 {print $4}')"

    # Dangling images only. A bare `image prune -a` would delete the :rollback
    # images (nothing references them until a rollback happens), destroying the
    # ability to roll back — so it is deliberately NOT used here.
    if pruned="$(docker image prune -f 2>&1)"; then
        reclaimed="$(printf '%s' "$pruned" | grep -i 'reclaimed' || true)"
        ok "Dangling images pruned. ${reclaimed:-nothing to reclaim}"
    else
        warn "docker image prune failed (continuing)."
    fi

    # Stopped containers not owned by any compose project.
    if docker container prune -f >/dev/null 2>&1; then
        ok "Stopped containers pruned."
    else
        warn "docker container prune failed (continuing)."
    fi

    # Unused networks (orphans from renamed/removed services).
    if docker network prune -f >/dev/null 2>&1; then
        ok "Unused networks pruned."
    else
        warn "docker network prune failed (continuing)."
    fi

    # Build cache: keep a working set so the next build stays fast, drop the rest.
    if [[ "$PRUNE_BUILD_CACHE" == "1" ]]; then
        if docker builder prune -f --keep-storage "$BUILD_CACHE_KEEP" >/dev/null 2>&1; then
            ok "Build cache trimmed (keeping $BUILD_CACHE_KEEP)."
        elif docker builder prune -f >/dev/null 2>&1; then
            ok "Build cache pruned."
        else
            warn "docker builder prune failed (continuing)."
        fi
    fi

    # NOTE: volumes are NEVER pruned. mysql_data / backend_storage / faces_data
    # hold production data; `docker volume prune` could destroy the database.

    after_mb="$(df -Pm "$APP_DIR" | awk 'NR==2 {print $4}')"
    freed=$(( after_mb - before_mb ))
    if (( freed > 0 )); then
        ok "Disk free: ${before_mb} MB → ${after_mb} MB (+${freed} MB)."
    else
        ok "Disk free: ${after_mb} MB."
    fi
fi

# ---------------------------------------------------------------------------
# 3. Log rotation
# ---------------------------------------------------------------------------
log "Rotating deployment logs older than ${LOG_RETENTION_DAYS} days..."
removed="$(find "$LOG_DIR" -maxdepth 1 -name '*.log' -mtime "+$LOG_RETENTION_DAYS" -print -delete 2>/dev/null | wc -l)"
ok "Removed ${removed} old log file(s)."

# Keep the history log bounded (last 1000 entries).
hist="$STATE_DIR/deployment-history.log"
if [[ -f "$hist" ]] && (( $(wc -l < "$hist") > 1000 )); then
    tail -n 1000 "$hist" > "$hist.tmp" && mv "$hist.tmp" "$hist"
    ok "Deployment history trimmed to the last 1000 entries."
fi

# ---------------------------------------------------------------------------
# 4. Final state report
# ---------------------------------------------------------------------------
printf '\n\033[1;36m--- Post-deploy state ---\033[0m\n'
compose ps --format 'table {{.Name}}\t{{.Service}}\t{{.Status}}' 2>/dev/null \
    || compose ps

printf '\nDocker disk usage:\n'
docker system df 2>/dev/null | sed 's/^/  /' || warn "docker system df unavailable."

printf '\nRollback images retained:\n'
docker images --filter 'reference=*:rollback' \
    --format '  {{.Repository}}:{{.Tag}}  {{.Size}}  (created {{.CreatedSince}})' 2>/dev/null \
    || warn "could not list rollback images"

# ---------------------------------------------------------------------------
# 5. Success notification
# ---------------------------------------------------------------------------
if [[ -n "${SLACK_WEBHOOK_URL:-}${TEAMS_WEBHOOK_URL:-}" ]]; then
    log "Sending deployment notification..."
    text="✅ HR Portal deployed to $(hostname) — ${DEPLOYED_SHA:0:7} \"${commit_subject}\" by ${commit_author} (triggered by ${DEPLOY_TRIGGERED_BY:-manual})"
    escaped="$(printf '%s' "$text" | sed 's/\\/\\\\/g; s/"/\\"/g')"
    payload="$(printf '{"text":"%s"}' "$escaped")"

    if [[ -n "${SLACK_WEBHOOK_URL:-}" ]]; then
        curl -fsS -m 15 -X POST -H 'Content-Type: application/json' \
             -d "$payload" "$SLACK_WEBHOOK_URL" >/dev/null 2>&1 \
            && ok "Slack notified." || warn "Slack notification failed (ignored)."
    fi
    if [[ -n "${TEAMS_WEBHOOK_URL:-}" ]]; then
        curl -fsS -m 15 -X POST -H 'Content-Type: application/json' \
             -d "$payload" "$TEAMS_WEBHOOK_URL" >/dev/null 2>&1 \
            && ok "Teams notified." || warn "Teams notification failed (ignored)."
    fi
fi

ok "Post-deploy tasks complete."
exit 0
