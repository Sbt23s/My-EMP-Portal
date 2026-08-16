#!/usr/bin/env bash
#
# A TURN server, so voice and video calls actually connect.
#
# Run this ON THE SERVER (16.192.105.61):
#
#   scp setup-turn.sh ubuntu@16.192.105.61:~
#   ssh -i <key> ubuntu@16.192.105.61
#   chmod +x setup-turn.sh; ./setup-turn.sh
#
# ---------------------------------------------------------------------------
# WHY THIS IS NEEDED
#
# WebRTC sends audio and video directly between two devices. To do that each
# side has to learn its own public address, which is what a STUN server tells
# it. The portal is configured with STUN only:
#
#   iceServers: [ stun:stun.l.google.com:19302, stun:stun1.l.google.com:19302 ]
#
# STUN is enough when at least one side is behind a friendly NAT — two laptops
# on the same office wifi, for instance. It is not enough when both sides are on
# mobile data, because carrier NAT is symmetric: the address STUN reports is
# valid only for the STUN server, and the other phone cannot use it.
#
# TURN is the fallback. When a direct path cannot be found, both sides send
# their media to the TURN server and it relays. That is the difference between
# "calls work at the office" and "calls work".
#
# This is not a mobile problem. The web portal has the same limitation today —
# two people on mobile data cannot call each other in the browser either.
# ---------------------------------------------------------------------------

set -euo pipefail

DOMAIN="pixoushrportal.pixous.info"
REALM="$DOMAIN"

say() { printf '\n\033[1;36m==> %s\033[0m\n' "$1"; }
die() { printf '\n\033[1;31mSTOP: %s\033[0m\n\n' "$1" >&2; exit 1; }

# ---- 1. Install ------------------------------------------------------------
say "Installing coturn"
sudo apt-get update -qq
sudo apt-get install -y -qq coturn

# ---- 2. A shared secret ----------------------------------------------------
# Time-limited credentials rather than a fixed username and password.
#
# A static password shipped inside an APK is a password published to everybody
# who installs it, and rotating it means shipping a new build. With a shared
# secret the backend mints a username that expires in a few hours, and the APK
# carries nothing worth stealing.
say "Generating the shared secret"
SECRET_FILE="/etc/turn-secret"
if sudo test -f "$SECRET_FILE"; then
  TURN_SECRET="$(sudo cat "$SECRET_FILE")"
  echo "    reusing the existing secret"
else
  TURN_SECRET="$(openssl rand -hex 32)"
  echo "$TURN_SECRET" | sudo tee "$SECRET_FILE" >/dev/null
  sudo chmod 600 "$SECRET_FILE"
  echo "    new secret written to $SECRET_FILE"
fi

# ---- 3. Configure ----------------------------------------------------------
say "Writing /etc/turnserver.conf"

# The address the world reaches this machine on, and the one the machine calls
# itself. On EC2 they differ, and coturn needs both: it advertises the public
# one while binding the private one.
PRIVATE_IP="$(hostname -I | awk '{print $1}')"
PUBLIC_IP="$(curl -s --max-time 10 https://api.ipify.org || echo '16.192.105.61')"
echo "    private $PRIVATE_IP   public $PUBLIC_IP"

sudo tee /etc/turnserver.conf >/dev/null <<CONF
# Managed by setup-turn.sh — edits here are overwritten if it is run again.

listening-port=3478
tls-listening-port=5349

listening-ip=${PRIVATE_IP}
external-ip=${PUBLIC_IP}/${PRIVATE_IP}

realm=${REALM}
server-name=${REALM}

# Time-limited credentials, minted by the backend from the shared secret.
use-auth-secret
static-auth-secret=${TURN_SECRET}

# Reuse the portal's certificate rather than issuing a second one for the same
# name. turns:// on 5349 is what gets through a network that blocks UDP.
cert=/etc/letsencrypt/live/${DOMAIN}/fullchain.pem
pkey=/etc/letsencrypt/live/${DOMAIN}/privkey.pem

fingerprint
no-cli
no-tlsv1
no-tlsv1_1

# A relay is an open pipe if it is not fenced in. These stop it being used to
# reach the machine's own services or anything else private.
no-multicast-peers
denied-peer-ip=0.0.0.0-0.255.255.255
denied-peer-ip=10.0.0.0-10.255.255.255
denied-peer-ip=127.0.0.0-127.255.255.255
denied-peer-ip=169.254.0.0-169.254.255.255
denied-peer-ip=172.16.0.0-172.31.255.255
denied-peer-ip=192.168.0.0-192.168.255.255

user-quota=12
total-quota=1200
CONF

# coturn ships disabled and starts silently doing nothing without this.
sudo sed -i 's/^#*TURNSERVER_ENABLED=.*/TURNSERVER_ENABLED=1/' /etc/default/coturn 2>/dev/null || \
  echo "TURNSERVER_ENABLED=1" | sudo tee -a /etc/default/coturn >/dev/null

# The certificate is renewed by certbot every ninety days, and coturn holds the
# old one until it is restarted — so calls would fail three months from now for
# a reason nobody would connect to a certificate.
sudo tee /etc/letsencrypt/renewal-hooks/deploy/restart-coturn.sh >/dev/null <<'HOOK'
#!/bin/sh
systemctl restart coturn
HOOK
sudo chmod +x /etc/letsencrypt/renewal-hooks/deploy/restart-coturn.sh

say "Starting coturn"
sudo systemctl enable coturn >/dev/null 2>&1 || true
sudo systemctl restart coturn
sleep 2
sudo systemctl is-active --quiet coturn || die "coturn did not start.

  sudo journalctl -u coturn -n 50 --no-pager

The usual cause is the certificate path: this expects
/etc/letsencrypt/live/${DOMAIN}/ , which enable-https.sh creates."

echo "    coturn running  ✓"

# ---- 4. What still has to happen -------------------------------------------
cat <<EOF

────────────────────────────────────────────────────────────────────────
coturn is up. Two things remain, and calls will not connect without them.

1. OPEN THE PORTS in the EC2 security group — inbound, from 0.0.0.0/0:

     3478   TCP and UDP     (TURN)
     5349   TCP and UDP     (TURN over TLS)
     49152-65535  UDP       (the relay range)

   The relay range is the one people forget. Without it a call negotiates,
   reports success, and carries no audio.

2. GIVE THE BACKEND THE SECRET, so it can mint credentials:

     cd ~/hr-port
     cp .env .env.backup-\$(date +%Y%m%d-%H%M%S)
     echo "APP_TURN_URL=turn:${DOMAIN}:3478" >> .env
     echo "APP_TURN_SECRET=${TURN_SECRET}" >> .env
     docker compose -f docker-compose.prod.yml up -d --no-deps backend

   The backend then needs an endpoint that returns short-lived ICE
   credentials, and both clients need to ask for them instead of using the
   hard-coded STUN list. That part is code, and it is not written yet —
   tell Claude once this script has run and the ports are open.

TEST IT from your laptop, after opening the ports:

     npm install -g turn-tester 2>/dev/null || true
     # or paste the TURN url into https://icetest.info and check for a
     # "relay" candidate. No relay candidate means the ports are still shut.
────────────────────────────────────────────────────────────────────────

The secret is in ${SECRET_FILE} and above. Treat it as a password —
anyone holding it can mint credentials and relay traffic through this box.
EOF
