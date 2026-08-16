#!/usr/bin/env bash
#
# Give the portal a certificate, so the mobile app can stop talking in the clear.
#
# Run this ON THE SERVER (16.192.105.61), over SSH, as a user with sudo.
#
#   scp enable-https.sh ubuntu@16.192.105.61:~
#   ssh ubuntu@16.192.105.61
#   chmod +x enable-https.sh && ./enable-https.sh
#
# ---------------------------------------------------------------------------
# BEFORE RUNNING THIS, the DNS record must exist and must have propagated.
#
#   Type:  A
#   Name:  ems
#   Value: 16.192.105.61
#   TTL:   600
#
# Created wherever pixoustech.com is managed (it resolves to 184.168.115.185,
# which is GoDaddy, so most likely there).
#
# Why a name and not the IP: Let's Encrypt does not issue certificates for bare
# IP addresses. There is no way to get a trusted certificate for
# https://16.192.105.61 — this is the reason the app is still on http, and a
# subdomain is the whole fix.
#
# The script checks the record itself and refuses to continue without it, because
# certbot's failure when DNS is wrong is a wall of text that does not say "your
# DNS record is missing".
# ---------------------------------------------------------------------------

set -euo pipefail

DOMAIN="ems.pixoustech.com"
EXPECTED_IP="16.192.105.61"
EMAIL="sethubala.pixous@gmail.com"   # where expiry warnings go

say() { printf '\n\033[1;36m==> %s\033[0m\n' "$1"; }
die() { printf '\n\033[1;31mSTOP: %s\033[0m\n\n' "$1" >&2; exit 1; }

# ---- 1. Is the DNS record there? -------------------------------------------
say "Checking that $DOMAIN points here"

resolved="$(getent hosts "$DOMAIN" | awk '{print $1}' | head -1 || true)"

if [ -z "$resolved" ]; then
  die "$DOMAIN does not resolve yet.

Create this record at your domain registrar, then wait a few minutes:

    Type   A
    Name   ems
    Value  $EXPECTED_IP

Check progress with:  dig +short $DOMAIN"
fi

if [ "$resolved" != "$EXPECTED_IP" ]; then
  die "$DOMAIN resolves to $resolved, but this server is $EXPECTED_IP.

Either the record points somewhere else, or DNS has not propagated yet.
Certbot would fail here with a confusing error, so this stops first."
fi

echo "    $DOMAIN -> $resolved  ✓"

# ---- 2. Port 80 has to be reachable from outside ----------------------------
say "Checking that port 80 is open to the internet"
# Let's Encrypt proves you own the domain by fetching a file over port 80. If the
# EC2 security group does not allow it, the challenge fails.
if ! curl -fsS --max-time 10 "http://$DOMAIN/" -o /dev/null; then
  die "Cannot reach http://$DOMAIN/ from here.

Open port 80 (and 443) to 0.0.0.0/0 in the EC2 security group for this
instance. Let's Encrypt fetches a challenge file over port 80 — without it
no certificate can be issued."
fi
echo "    port 80 reachable  ✓"

# ---- 3. Certbot -------------------------------------------------------------
say "Installing certbot"
if ! command -v certbot >/dev/null 2>&1; then
  sudo apt-get update -qq
  sudo apt-get install -y -qq certbot python3-certbot-nginx
fi
echo "    certbot ready  ✓"

# ---- 4. Issue and install ----------------------------------------------------
say "Requesting the certificate and configuring nginx"
# --nginx edits the site config in place and adds the redirect. --redirect makes
# plain http permanently forward to https, so an old link still lands somewhere
# safe rather than serving the portal unencrypted.
sudo certbot --nginx \
  -d "$DOMAIN" \
  --non-interactive \
  --agree-tos \
  --email "$EMAIL" \
  --redirect

# ---- 5. Renewal --------------------------------------------------------------
say "Checking automatic renewal"
# The certificate lasts 90 days. The package installs a timer that renews it;
# this proves the renewal actually works now, rather than finding out in three
# months when the portal goes down.
sudo certbot renew --dry-run

# ---- 6. Prove it ------------------------------------------------------------
say "Verifying"
code="$(curl -s -o /dev/null -w '%{http_code}' "https://$DOMAIN/api/my-modules" || true)"
if [ "$code" = "401" ]; then
  echo "    https://$DOMAIN/api answers 401 (needs a login) — working  ✓"
elif [ "$code" = "000" ]; then
  die "https://$DOMAIN is still not answering. Check that port 443 is open in
the EC2 security group."
else
  echo "    https://$DOMAIN/api answered $code"
fi

cat <<EOF

────────────────────────────────────────────────────────────────────────
Done. The portal now has a certificate.

Tell Claude, and these two changes take a minute:

  1. flutter_app/lib/core/config/app_config.dart
       http://16.192.105.61/api  ->  https://$DOMAIN/api

  2. delete android/app/src/main/res/xml/network_security_config.xml
     and its line in AndroidManifest.xml

Then rebuild the APK. Until both are done the app is still sending
passwords in the clear, so it is worth doing straight away.
────────────────────────────────────────────────────────────────────────
EOF
