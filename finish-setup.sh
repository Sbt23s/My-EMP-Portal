#!/usr/bin/env bash
#
# Bring HTTPS back, fix CORS, and confirm Elamaran is an administrator.
#
#   scp -i $KEY finish-setup.sh ubuntu@16.192.105.61:~
#   ssh -i $KEY ubuntu@16.192.105.61 "chmod +x finish-setup.sh; ./finish-setup.sh"
#
# ---------------------------------------------------------------------------
# Runs the three remaining things in the only order that works, and refuses to
# start if the certificate is missing.
#
# That refusal is the important part. nginx will not start when a certificate
# named in its config is absent, and this deploy adds a 443 block that names
# one. Get that wrong and the web container fails to boot — taking port 80 with
# it, so the site is not merely without HTTPS, it is gone. Checking first costs
# a second; finding out afterwards costs the evening.
# ---------------------------------------------------------------------------

set -euo pipefail

DOMAIN="pixoushrportal.pixous.info"
COMPOSE_FILE="docker-compose.prod.yml"

say()  { printf '\n\033[1;36m==> %s\033[0m\n' "$1"; }
warn() { printf '\033[1;33m    %s\033[0m\n' "$1"; }
die()  { printf '\n\033[1;31mSTOP: %s\033[0m\n\n' "$1" >&2; exit 1; }

for dir in "$HOME/hr-portal" "$HOME/hr-port" "/opt/hr-port" "$(pwd)"; do
  if [ -f "$dir/$COMPOSE_FILE" ]; then APP_DIR="$dir"; break; fi
done
[ -n "${APP_DIR:-}" ] || die "Could not find $COMPOSE_FILE."
cd "$APP_DIR"
say "Deployment: $APP_DIR"

# ---- 1. Is there a certificate to serve? -----------------------------------
say "Checking the certificate"
CERT="/etc/letsencrypt/live/$DOMAIN/fullchain.pem"
KEY="/etc/letsencrypt/live/$DOMAIN/privkey.pem"

if ! sudo test -f "$CERT" || ! sudo test -f "$KEY"; then
  die "No certificate at /etc/letsencrypt/live/$DOMAIN/

Deploying now would give nginx a config naming a certificate that is not there,
it would refuse to start, and the site would go down completely — port 80 as
well. Nothing has been changed.

Issue one first:

  sudo docker compose -f $COMPOSE_FILE stop web
  sudo certbot certonly --standalone -d $DOMAIN
  sudo docker compose -f $COMPOSE_FILE start web

then run this again."
fi

expiry="$(sudo openssl x509 -enddate -noout -in "$CERT" | cut -d= -f2)"
echo "    certificate present, valid until $expiry  ✓"

# ---- 2. CORS ---------------------------------------------------------------
say "Allowing the HTTPS origin"
# Spring matches origins exactly and turns a mismatch into a 403 that says
# nothing about origins, so signing in fails in a way that reads as a bad
# password. The IP stays on the list: an old bookmark should keep working.
[ -f .env ] || die "No .env here — it holds the database password and the JWT secret,
and this script will not create one from scratch."

BACKUP=".env.backup-$(date +%Y%m%d-%H%M%S)"
cp .env "$BACKUP"
echo "    .env backed up to $BACKUP"

ORIGINS="https://${DOMAIN},http://${DOMAIN},http://16.192.105.61,http://localhost,http://localhost:5174"
if grep -q '^APP_CORS_ALLOWED_ORIGINS=' .env; then
  sed -i "s|^APP_CORS_ALLOWED_ORIGINS=.*|APP_CORS_ALLOWED_ORIGINS=${ORIGINS}|" .env
else
  printf '\nAPP_CORS_ALLOWED_ORIGINS=%s\n' "$ORIGINS" >> .env
fi
echo "    origins set  ✓"

# ---- 3. Pull and rebuild ---------------------------------------------------
say "Pulling the new nginx configuration"
git pull --ff-only

say "Rebuilding web and restarting backend"
# Both: web carries the new 443 config, backend picks up the new origins.
sudo docker compose -f "$COMPOSE_FILE" up -d --build --no-deps web backend

# ---- 4. Did nginx actually come up? ----------------------------------------
say "Waiting for HTTPS"
for i in $(seq 1 40); do
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 -k "https://localhost/" || true)"
  if [ "$code" != "000" ]; then echo "    443 is answering  ✓"; break; fi
  if [ "$i" = "40" ]; then
    warn "Nothing on 443 after two minutes. nginx probably refused to start:"
    warn "  sudo docker compose -f $COMPOSE_FILE logs --tail=50 web"
    die "HTTPS did not come up. Your previous .env is at $BACKUP."
  fi
  sleep 3
done

# ---- 5. Prove the whole path ------------------------------------------------
say "Checking from outside"
pre="$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 -X OPTIONS \
  "https://${DOMAIN}/api/auth/login" -H "Origin: https://${DOMAIN}" \
  -H "Access-Control-Request-Method: POST" || true)"
login="$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 -X POST \
  "https://${DOMAIN}/api/auth/login" -H "Content-Type: application/json" \
  -H "Origin: https://${DOMAIN}" -d '{"username":"__probe__","password":"__probe__"}' || true)"

echo "    preflight OPTIONS : $pre    want 200 or 204"
echo "    login POST        : $login   want 401 (reached the endpoint, wrong password)"

# ---- 6. Is Elamaran an administrator? --------------------------------------
say "Checking Elamaran's role"
# No mysql client on this host, so borrow one in a throwaway container on the
# same network. It is deleted the moment the query returns.
set -a; . ./.env; set +a
QUERY="SELECT u.employee_code, u.name, r.code AS role
       FROM users u JOIN user_roles ur ON ur.user_id=u.id
       JOIN roles r ON r.id=ur.role_id
       WHERE u.employee_code='PIX-E100' OR u.name='Elamaran Subramaniyan';"

result="$(sudo docker run --rm --network "$(basename "$APP_DIR")_hrnet" mysql:8 \
  mysql -h "${DB_HOST}" -P "${DB_PORT:-3306}" -u "${DB_USER}" -p"${DB_PASSWORD}" \
  "${DB_NAME}" -e "$QUERY" 2>/dev/null || true)"

if [ -n "$result" ]; then
  echo "$result" | sed 's/^/    /'
else
  warn "No rows — PIX-E100 does not exist and nobody is named"
  warn "\"Elamaran Subramaniyan\", so V97 changed nothing."
  warn "Find his real employee code on the Employees screen and say so."
fi

cat <<EOF

────────────────────────────────────────────────────────────────────────
Done.

  https://${DOMAIN}   should now load and sign in.

Expect one row above with role COMPANY_ADMIN. If it says "No rows", the
employee code is different and needs saying.

Still separate: ./setup-turn.sh, for calls to connect on mobile data.
────────────────────────────────────────────────────────────────────────
EOF
