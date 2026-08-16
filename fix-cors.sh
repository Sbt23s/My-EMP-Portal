#!/usr/bin/env bash
#
# Let the new HTTPS domain talk to the API.
#
# Run this ON THE SERVER (16.192.105.61):
#
#   scp fix-cors.sh ubuntu@16.192.105.61:~
#   ssh ubuntu@16.192.105.61
#   chmod +x fix-cors.sh && ./fix-cors.sh
#
# ---------------------------------------------------------------------------
# THE PROBLEM
#
# Signing in at https://pixoushrportal.pixous.info answers 403. The same request
# without a browser's Origin header answers 401 — which is the server working
# correctly and rejecting a wrong password.
#
#   curl -X POST .../api/auth/login                      -> 401  (fine)
#   curl -X POST .../api/auth/login -H "Origin: https://..." -> 403  (blocked)
#   curl -X OPTIONS .../api/auth/login -H "Origin: ..."      -> 403  (blocked)
#
# So the API is healthy and the credentials path is healthy. What is refusing is
# CORS: Spring is configured with a list of origins it will answer, the new
# domain is not on it, and Spring Security turns that into a 403 rather than a
# message that mentions CORS. Nothing in the browser console says "origin", so
# it reads as a permissions problem with the account.
#
# Spring matches these EXACTLY — scheme, host and port all have to line up.
# "https://pixoushrportal.pixous.info" and "http://pixoushrportal.pixous.info"
# are two different origins, and neither covers the other.
# ---------------------------------------------------------------------------

set -euo pipefail

DOMAIN="pixoushrportal.pixous.info"
COMPOSE_FILE="docker-compose.prod.yml"

say() { printf '\n\033[1;36m==> %s\033[0m\n' "$1"; }
die() { printf '\n\033[1;31mSTOP: %s\033[0m\n\n' "$1" >&2; exit 1; }

# ---- 1. Find the deployment ------------------------------------------------
say "Finding the deployment"
for dir in "$HOME/hr-port" "$HOME/hr-portal" "/opt/hr-port" "$(pwd)"; do
  if [ -f "$dir/$COMPOSE_FILE" ]; then APP_DIR="$dir"; break; fi
done
[ -n "${APP_DIR:-}" ] || die "Could not find $COMPOSE_FILE.
cd into the deployment directory and run this again from there."
cd "$APP_DIR"
echo "    $APP_DIR  ✓"

[ -f .env ] || die "No .env here. This script edits it and will not create one
from scratch — the file also holds the database password and the JWT secret,
and writing a fresh one would wipe both."

# ---- 2. Back it up ---------------------------------------------------------
# An .env overwrite has destroyed APP_JWT_SECRET on this deployment before.
# Never edit it without a copy that is dated.
BACKUP=".env.backup-$(date +%Y%m%d-%H%M%S)"
cp .env "$BACKUP"
say "Backed up .env to $BACKUP"

# ---- 3. Build the origin list ----------------------------------------------
# Every address the portal is actually reached on. The IP stays: anyone with a
# bookmark to it must keep working, and dropping it would swap one 403 for
# another.
ORIGINS="https://${DOMAIN},http://${DOMAIN},http://16.192.105.61,http://localhost,http://localhost:5174"

say "Setting the allowed origins"
if grep -q '^APP_CORS_ALLOWED_ORIGINS=' .env; then
  # In place, so its position and the comments around it survive.
  sed -i "s|^APP_CORS_ALLOWED_ORIGINS=.*|APP_CORS_ALLOWED_ORIGINS=${ORIGINS}|" .env
  echo "    updated the existing line"
else
  printf '\nAPP_CORS_ALLOWED_ORIGINS=%s\n' "$ORIGINS" >> .env
  echo "    added the line"
fi
grep '^APP_CORS_ALLOWED_ORIGINS=' .env | sed 's/^/    /'

# ---- 4. Restart the backend only -------------------------------------------
say "Restarting the backend"
# Only the backend. `down` would stop MySQL as well, and there is no reason to
# take the database offline to change one environment variable.
docker compose -f "$COMPOSE_FILE" up -d --no-deps --force-recreate backend

say "Waiting for it to come back"
for i in $(seq 1 40); do
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://localhost/api/my-modules" || true)"
  # 401 means it is up and asking for a token, which is exactly right.
  if [ "$code" = "401" ]; then echo "    backend up  ✓"; break; fi
  [ "$i" = "40" ] && die "The backend did not come back within two minutes.

  docker compose -f $COMPOSE_FILE logs --tail=80 backend

Your old .env is at $BACKUP if you need to put it back."
  sleep 3
done

# ---- 5. Prove the fix ------------------------------------------------------
say "Checking that the browser origin is now accepted"

pre="$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 \
  -X OPTIONS "https://${DOMAIN}/api/auth/login" \
  -H "Origin: https://${DOMAIN}" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: content-type" || true)"

post="$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 \
  -X POST "https://${DOMAIN}/api/auth/login" \
  -H "Content-Type: application/json" \
  -H "Origin: https://${DOMAIN}" \
  -d '{"username":"__probe__","password":"__probe__"}' || true)"

echo "    preflight (OPTIONS): $pre   — want 200 or 204"
echo "    login    (POST)    : $post  — want 401 (wrong password, reached the server)"

if { [ "$pre" = "200" ] || [ "$pre" = "204" ]; } && [ "$post" = "401" ]; then
  cat <<EOF

────────────────────────────────────────────────────────────────────────
Fixed. Sign in at https://${DOMAIN} — a real password will now work.

401 on the probe is the correct answer: the request reached the login
endpoint and was refused because "__probe__" is not a password. Before
this it was 403, refused before the endpoint was ever reached.
────────────────────────────────────────────────────────────────────────
EOF
else
  die "Still blocked (preflight $pre, login $post).

Check what the backend actually loaded:

  docker compose -f $COMPOSE_FILE exec backend printenv | grep CORS

If it does not show the new value, the container did not pick up the .env —
try:  docker compose -f $COMPOSE_FILE up -d --force-recreate backend

Your old .env is at $BACKUP."
fi
