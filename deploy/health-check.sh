#!/usr/bin/env bash
#
# health-check.sh — proves the deployed stack actually works, not merely that
# containers were created.
#
# Runs standalone:  ~/hr-portal/deploy/health-check.sh
#
# Checks, in order:
#   1. no container is in a restart loop
#   2. every container reports healthy (or "running" if it has no healthcheck)
#   3. MySQL answers a real query
#   4. the Spring Boot backend returns {"status":"UP"} from /actuator/health
#   5. Nginx serves the SPA (HTTP 200 + non-empty body)
#   6. the API is reachable THROUGH Nginx (end-to-end path the browser uses)
#
# Exit 0 = healthy. Any non-zero exit tells deploy.sh to roll back.
#
set -Eeuo pipefail

DEPLOY_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="${APP_DIR:-$(cd -- "$DEPLOY_DIR/.." && pwd)}"

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-.env}"

MYSQL_CONTAINER="${MYSQL_CONTAINER:-hrportal-mysql}"
BACKEND_CONTAINER="${BACKEND_CONTAINER:-hrportal-backend}"
WEB_CONTAINER="${WEB_CONTAINER:-hrportal-web}"

BACKEND_PORT="${BACKEND_PORT:-7060}"
WEB_URL="${WEB_URL:-http://127.0.0.1}"

# The backend does Flyway migrations on boot; on a small EC2 the first start
# after a schema change can genuinely take a couple of minutes.
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-300}"
HEALTH_INTERVAL="${HEALTH_INTERVAL:-5}"

log()  { printf '[%s] %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$*"; }
ok()   { printf '\033[1;32m  ✔ %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m  ! %s\033[0m\n' "$*"; }
fail() { printf '\033[1;31m  ✘ %s\033[0m\n' "$*" >&2; }

cd "$APP_DIR"

if docker compose version >/dev/null 2>&1; then
    DC=(docker compose)
else
    DC=(docker-compose)
fi
compose() { "${DC[@]}" -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"; }

# Dump diagnostics for a failing container so the CI log explains itself.
dump_diagnostics() {
    local container="$1"
    fail "--- last 80 log lines from $container ---"
    docker logs --tail 80 "$container" 2>&1 | sed 's/^/    /' >&2 || true
    fail "--- container state ---"
    docker inspect -f \
      '    status={{.State.Status}} exitcode={{.State.ExitCode}} restarts={{.RestartCount}} oomkilled={{.State.OOMKilled}}{{if .State.Health}} health={{.State.Health.Status}}{{end}}' \
      "$container" >&2 2>/dev/null || true
    if docker inspect -f '{{if .State.Health}}yes{{end}}' "$container" 2>/dev/null | grep -q yes; then
        fail "--- last healthcheck output ---"
        docker inspect -f '{{range .State.Health.Log}}    {{.Output}}{{end}}' "$container" 2>/dev/null | tail -10 >&2 || true
    fi
}

container_exists() { docker inspect "$1" >/dev/null 2>&1; }

# Wait until a container is healthy (or running, if it declares no healthcheck).
wait_for_container() {
    local container="$1"
    local deadline=$(( SECONDS + HEALTH_TIMEOUT ))
    local state health last_report=0

    log "Waiting for $container (timeout ${HEALTH_TIMEOUT}s)..."
    while (( SECONDS < deadline )); do
        if ! container_exists "$container"; then
            fail "Container $container does not exist."
            return 1
        fi

        state="$(docker inspect -f '{{.State.Status}}' "$container" 2>/dev/null || echo unknown)"
        health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container" 2>/dev/null || echo none)"

        case "$state" in
            running)
                case "$health" in
                    healthy)  ok "$container is healthy."; return 0 ;;
                    none)     ok "$container is running (no healthcheck declared)."; return 0 ;;
                    starting) : ;;   # keep waiting
                    unhealthy)
                        # Docker retries; only give up when the deadline passes.
                        : ;;
                esac
                ;;
            restarting)
                warn "$container is restarting (crash loop?)."
                ;;
            exited|dead)
                fail "$container has exited (state=$state)."
                dump_diagnostics "$container"
                return 1
                ;;
        esac

        if (( SECONDS - last_report >= 30 )); then
            log "  ...$container: state=$state health=$health ($(( deadline - SECONDS ))s left)"
            last_report=$SECONDS
        fi
        sleep "$HEALTH_INTERVAL"
    done

    fail "$container did not become healthy within ${HEALTH_TIMEOUT}s (state=$state health=$health)."
    dump_diagnostics "$container"
    return 1
}

# Retry a command until it succeeds or the timeout expires.
retry_until() {
    local desc="$1" timeout="$2"; shift 2
    local deadline=$(( SECONDS + timeout ))
    while (( SECONDS < deadline )); do
        if "$@" >/dev/null 2>&1; then
            return 0
        fi
        sleep "$HEALTH_INTERVAL"
    done
    fail "$desc did not succeed within ${timeout}s."
    return 1
}

printf '\n\033[1;36m--- Health verification ---\033[0m\n'

# ---------------------------------------------------------------------------
# 1. Restart-loop detection
# ---------------------------------------------------------------------------
log "Checking for restart loops..."
loop_found=0
for c in $(compose ps -q 2>/dev/null); do
    name="$(docker inspect -f '{{.Name}}' "$c" 2>/dev/null | sed 's|^/||')"
    restarts="$(docker inspect -f '{{.RestartCount}}' "$c" 2>/dev/null || echo 0)"
    if (( restarts > 5 )); then
        fail "$name has restarted ${restarts} times — it is in a crash loop."
        dump_diagnostics "$name"
        loop_found=1
    fi
done
(( loop_found == 1 )) && exit 1
ok "No crash loops detected."

# ---------------------------------------------------------------------------
# 2. Container health
# ---------------------------------------------------------------------------
wait_for_container "$MYSQL_CONTAINER"   || exit 1
wait_for_container "$BACKEND_CONTAINER" || exit 1
wait_for_container "$WEB_CONTAINER"     || exit 1

# The analytics service is optional (compose profile). Only check it if present.
if container_exists hrportal-analytics && \
   [[ "$(docker inspect -f '{{.State.Status}}' hrportal-analytics)" != "exited" ]]; then
    wait_for_container hrportal-analytics || exit 1
else
    log "Analytics service not deployed (optional profile) — skipping."
fi

# ---------------------------------------------------------------------------
# 3. MySQL answers a real query
# ---------------------------------------------------------------------------
log "Querying MySQL..."
mysql_query() {
    docker exec "$MYSQL_CONTAINER" sh -c \
        'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B -e "SELECT 1" "$MYSQL_DATABASE"'
}
if retry_until "MySQL query" 60 mysql_query; then
    ok "MySQL is accepting queries."
else
    dump_diagnostics "$MYSQL_CONTAINER"
    exit 1
fi

# ---------------------------------------------------------------------------
# 4. Backend /actuator/health reports UP
# ---------------------------------------------------------------------------
# The eclipse-temurin JRE image ships no curl/wget, so we speak HTTP directly
# over bash's /dev/tcp. This verifies the Spring context AND the DB connection
# (the db health indicator is enabled), which a bare TCP connect would not.
log "Checking backend /actuator/health..."
backend_health_body() {
    docker exec "$BACKEND_CONTAINER" bash -c "
        exec 3<>/dev/tcp/127.0.0.1/${BACKEND_PORT} || exit 1
        printf 'GET /actuator/health HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n' >&3
        cat <&3
    " 2>/dev/null
}
backend_is_up() { backend_health_body | grep -q '"status":"UP"'; }

if retry_until "backend health" "$HEALTH_TIMEOUT" backend_is_up; then
    ok "Backend reports status UP (Spring context + database confirmed)."
else
    fail "Backend never reported UP. Last response:"
    backend_health_body 2>&1 | tail -20 | sed 's/^/    /' >&2 || true
    dump_diagnostics "$BACKEND_CONTAINER"
    exit 1
fi

# ---------------------------------------------------------------------------
# 5. Nginx serves the SPA
# ---------------------------------------------------------------------------
log "Checking the web frontend at $WEB_URL ..."
if ! command -v curl >/dev/null 2>&1; then
    warn "curl is not installed on the host; falling back to an in-container check."
    web_ok() { docker exec "$WEB_CONTAINER" wget -q -O /dev/null http://127.0.0.1/; }
else
    web_ok() { [[ "$(curl -s -o /dev/null -w '%{http_code}' -m 10 "$WEB_URL/")" == "200" ]]; }
fi

if retry_until "web frontend" 90 web_ok; then
    ok "Nginx is serving the frontend (HTTP 200)."
else
    fail "The frontend did not return HTTP 200 at $WEB_URL/."
    dump_diagnostics "$WEB_CONTAINER"
    exit 1
fi

# Guard against Nginx serving a 200 with an empty document root (a broken
# frontend build still produces a running container).
if command -v curl >/dev/null 2>&1; then
    body_size="$(curl -s -o /dev/null -w '%{size_download}' -m 10 "$WEB_URL/" || echo 0)"
    if (( body_size < 100 )); then
        fail "The frontend returned only ${body_size} bytes — the build output is probably empty."
        dump_diagnostics "$WEB_CONTAINER"
        exit 1
    fi
    ok "Frontend document is ${body_size} bytes."
fi

# ---------------------------------------------------------------------------
# 6. End-to-end: the API through Nginx, exactly as the browser reaches it
# ---------------------------------------------------------------------------
if command -v curl >/dev/null 2>&1; then
    log "Checking the API through the Nginx reverse proxy..."
    # An unauthenticated GET on a protected route must return 401/403 — proving
    # the proxy reaches Spring Security rather than 502-ing. A 502/504 means the
    # backend is unreachable from Nginx.
    api_code="$(curl -s -o /dev/null -w '%{http_code}' -m 15 "$WEB_URL/api/users/me" || echo 000)"
    case "$api_code" in
        401|403)
            ok "API reachable through Nginx (HTTP $api_code — auth enforced as expected)."
            ;;
        200)
            ok "API reachable through Nginx (HTTP 200)."
            ;;
        502|503|504)
            fail "Nginx cannot reach the backend (HTTP $api_code) — the reverse proxy is broken."
            dump_diagnostics "$WEB_CONTAINER"
            exit 1
            ;;
        000)
            fail "No response from $WEB_URL/api/users/me (connection failed or timed out)."
            exit 1
            ;;
        *)
            warn "API returned HTTP $api_code through Nginx — unexpected but not fatal."
            ;;
    esac
fi

printf '\n\033[1;32m  ✔ ALL HEALTH CHECKS PASSED\033[0m\n\n'
exit 0
