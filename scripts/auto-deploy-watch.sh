#!/usr/bin/env bash
# Auto-push watcher for My-EMP-Portal
#
# Watches this repo; whenever files change it auto-commits and pushes to
# the remote "main" branch, which triggers the GitHub Actions deploy
# workflow (push to main -> deploy to EC2 -> live site updates).
#
# Usage:
#   bash scripts/auto-deploy-watch.sh [repo-dir]
#   ...or just double-click scripts/auto-deploy-watch.cmd
#
# Safety: any NEW file whose name looks sensitive (secrets/keys/env)
# blocks the auto-commit so credentials are never pushed.

set -u

REPO_DIR="${1:-$(pwd)}"
POLL_SECS="${POLL_SECS:-8}"
DEBOUNCE_SECS="${DEBOUNCE_SECS:-4}"
REMOTE="${REMOTE:-my-emp}"
BRANCH="${BRANCH:-main}"

# Untracked files matching this are NEVER auto-committed.
SENSITIVE_PATTERN='(\.env([.\-].*)?$|\.pem$|\.key$|\.p12$|\.jks$|id_rsa|id_ed25519|credentials|secret|password)'

cd "$REPO_DIR" || { echo "Cannot cd into $REPO_DIR"; exit 1; }

log() { echo "[$(date '+%H:%M:%S')] $*"; }

log "Watching: $REPO_DIR"
log "On change -> auto commit + push to $REMOTE/$BRANCH -> GitHub Actions auto-deploys to EC2"
log "Poll: ${POLL_SECS}s | Debounce: ${DEBOUNCE_SECS}s | Ctrl+C to stop"

while true; do
  if [ -n "$(git status --porcelain)" ]; then
    sleep "$DEBOUNCE_SECS"   # let the edit settle before committing

    # Safety guard: never commit newly-created sensitive files
    BLOCKED=$(git status --porcelain | grep '^??' | awk '{print $2}' | grep -iE "$SENSITIVE_PATTERN" || true)
    if [ -n "$BLOCKED" ]; then
      log "!! AUTO-COMMIT BLOCKED - sensitive file found: $BLOCKED"
      log "   Move it out of the repo or add it to .gitignore, then I will resume automatically."
      sleep "$POLL_SECS"
      continue
    fi

    git add -A
    if git diff --cached --quiet; then
      continue
    fi
    git commit -q -m "auto: sync $(date '+%Y-%m-%d %H:%M:%S')" || true
    if git push -q "$REMOTE" "$BRANCH" 2>/tmp/auto-push-err; then
      log "Pushed $(git rev-parse --short HEAD) -> auto-deploy started"
    else
      log "!! PUSH FAILED: $(tail -1 /tmp/auto-push-err)"
    fi
  fi
  sleep "$POLL_SECS"
done
