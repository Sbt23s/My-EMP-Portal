#!/usr/bin/env bash
# Watchdog for the auto-push watcher.
# Runs hidden every 5 minutes (see auto-deploy-watchdog.vbs + scheduled task).
# If the watcher's heartbeat file is stale (>2 min), restarts the watcher.

ALIVE="$HOME/.hr-watch-alive"
if [ -f "$ALIVE" ] && [ -n "$(find "$ALIVE" -mmin -2 2>/dev/null)" ]; then
  exit 0   # watcher is alive
fi

REPO="$HOME/Documents/product level/GitHub/hr-port"
if [ ! -f "$REPO/scripts/auto-deploy-watch.sh" ]; then
  echo "$(date) watchdog: repo not found at $REPO" >> "$HOME/hr-watch.log"
  exit 1
fi

nohup bash "$REPO/scripts/auto-deploy-watch.sh" "$REPO" >> "$HOME/hr-watch.log" 2>&1 &
echo "$(date) watchdog: watcher restarted (pid $!)" >> "$HOME/hr-watch.log"
exit 0
