#!/usr/bin/env bash
#
# Clear the stale backend container and finish the deploy.
#
#   scp -i $KEY fix-deploy-conflict.sh ubuntu@16.192.105.61:~
#   ssh -i $KEY ubuntu@16.192.105.61 "chmod +x fix-deploy-conflict.sh; ./fix-deploy-conflict.sh"
#
# ---------------------------------------------------------------------------
# WHAT HAPPENED
#
#   Conflict. The container name "/d134611c44f3_hrportal-backend" is already
#   in use by container "a7d4467aa5..."
#
# Compose recreates a container by renaming the old one out of the way first
# (hrportal-backend -> <hash>_hrportal-backend), starting the new one, then
# deleting the renamed one. An earlier run died between the rename and the
# delete, so the renamed container is still sitting there holding the name.
#
# Nothing is wrong with the image or the code — every image built cleanly, V97
# included. This only clears the leftover and starts the container.
#
# The database is untouched throughout: only the backend container is replaced,
# and it holds no data.
# ---------------------------------------------------------------------------

set -euo pipefail

COMPOSE_FILE="docker-compose.prod.yml"

say() { printf '\n\033[1;36m==> %s\033[0m\n' "$1"; }
die() { printf '\n\033[1;31mSTOP: %s\033[0m\n\n' "$1" >&2; exit 1; }

# ---- 1. Find the deployment ------------------------------------------------
for dir in "$HOME/hr-port" "$HOME/hr-portal" "/opt/hr-port" "$(pwd)"; do
  if [ -f "$dir/$COMPOSE_FILE" ]; then APP_DIR="$dir"; break; fi
done
[ -n "${APP_DIR:-}" ] || die "Could not find $COMPOSE_FILE. cd to the deployment directory first."
cd "$APP_DIR"
say "Deployment: $APP_DIR"

# ---- 2. Remove anything holding the backend name ---------------------------
say "Clearing leftover backend containers"
# -a because the blocking container is stopped, and a stopped container still
# owns its name. Matched on the suffix so the hash-prefixed rename is caught
# along with the plain name.
stale="$(sudo docker ps -a --format '{{.ID}} {{.Names}}' | awk '/hrportal-backend$/ {print $1}')"

if [ -z "$stale" ]; then
  echo "    nothing to clear"
else
  for id in $stale; do
    name="$(sudo docker inspect --format '{{.Name}}' "$id" | sed 's|^/||')"
    echo "    removing $name ($id)"
    sudo docker rm -f "$id" >/dev/null
  done
fi

# ---- 3. Start it -----------------------------------------------------------
say "Starting the backend"
# --no-deps so MySQL and the others are left alone; they are already running and
# restarting the database to fix a container-name clash would be absurd.
sudo docker compose -f "$COMPOSE_FILE" up -d --no-deps backend

# ---- 4. Wait for it --------------------------------------------------------
say "Waiting for it to answer"
for i in $(seq 1 60); do
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://localhost/api/my-modules || true)"
  # 401 is the right answer: it is up and asking for a token.
  if [ "$code" = "401" ]; then echo "    backend up  ✓"; break; fi
  if [ "$i" = "60" ]; then
    die "The backend did not come up within three minutes.

  sudo docker compose -f $COMPOSE_FILE logs --tail=100 backend

A Flyway failure would show there. V97 is written to do nothing when it matches
nobody, so it should not be the cause — but the log will say."
  fi
  sleep 3
done

# ---- 5. Did V97 actually do anything? --------------------------------------
say "Checking whether Elamaran is now an administrator"
# The point of the whole deploy. A migration that matched nobody runs perfectly,
# deploys cleanly, and leaves him exactly as he was — so this asks the database
# rather than assuming.
if [ -f .env ]; then
  # shellcheck disable=SC1091
  set -a; . ./.env; set +a
fi

QUERY="SELECT u.employee_code, u.name, r.code AS role
       FROM users u
       JOIN user_roles ur ON ur.user_id = u.id
       JOIN roles r ON r.id = ur.role_id
       WHERE u.employee_code = 'PIX-E100'
          OR u.name = 'Elamaran Subramaniyan';"

if command -v mysql >/dev/null 2>&1 && [ -n "${DB_HOST:-}" ]; then
  result="$(mysql -h "$DB_HOST" -P "${DB_PORT:-3306}" -u "$DB_USER" -p"$DB_PASSWORD" \
            "$DB_NAME" -e "$QUERY" 2>/dev/null || true)"
  if [ -n "$result" ]; then
    echo "$result" | sed 's/^/    /'
  else
    printf '\033[1;33m    No rows. PIX-E100 does not exist and no employee is named\n'
    printf '    "Elamaran Subramaniyan" — so V97 changed nothing.\n'
    printf '    Find his real employee code on the Employees screen and say so.\033[0m\n'
  fi
else
  echo "    No mysql client here. Run this against the database yourself:"
  echo "$QUERY" | sed 's/^/      /'
fi

cat <<EOF

────────────────────────────────────────────────────────────────────────
Backend is running again.

Expect one row above, with role COMPANY_ADMIN.

Still outstanding, both separate from this:
  * HTTPS — nothing listens on 443, so the site and the app both fail.
  * CORS  — run ./fix-cors.sh, or sign-in answers 403.
────────────────────────────────────────────────────────────────────────
EOF
