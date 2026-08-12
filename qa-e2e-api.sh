#!/usr/bin/env bash
# E2E API test #2 — uses the EXACT urls the web frontend calls.
BASE="http://localhost:7060"
PASS="Test1234@"
PASSES=0; FAILS=0

ok()   { PASSES=$((PASSES+1)); echo "  PASS  $1"; }
fail() { FAILS=$((FAILS+1)); echo "  *** FAIL ***  $1 — $(head -c 200 /tmp/probe-body.json)"; }

login() {
  curl -s -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
    -d "{\"username\":\"$1\",\"password\":\"$PASS\"}"
}
token_of() { echo "$1" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p'; }

hit() {
  local method="$1" path="$2" body="$3" code
  if [ -n "$body" ]; then
    code=$(curl -s -o /tmp/probe-body.json -w "%{http_code}" --max-time 20 -X "$method" \
      "$BASE$path" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "$body")
  else
    code=$(curl -s -o /tmp/probe-body.json -w "%{http_code}" --max-time 20 -X "$method" \
      "$BASE$path" -H "Authorization: Bearer $TOKEN")
  fi
  echo "$code"
}
check() {
  if [ "$2" = "$3" ]; then ok "$1 (got $3)"; else fail "$1 — expected $2, got $3"; fi
}

# ============ arun (IT Employee) ============
RESP=$(login arun); TOKEN=$(token_of "$RESP")
[ -n "$TOKEN" ] && ok "arun login" || { fail "arun login"; exit 1; }

note(){ echo ""; echo "### $1"; }

note "arun — dashboard (as Dashboard.tsx calls)"
check "GET /api/dashboard/me" 200 "$(hit GET /api/dashboard/me)"
check "POST /api/org/dropdowns [designation]" 200 "$(hit POST /api/org/dropdowns '["designation"]')"
check "GET /api/dashboard/celebrations" 200 "$(hit GET /api/dashboard/celebrations)"

note "arun — attendance flow (punch in → today → punch out)"
check "POST /api/attendance/punch-in" 200 "$(hit POST /api/attendance/punch-in '{}')"
sleep 1
check "GET /api/attendance/today" 200 "$(hit GET /api/attendance/today)"
check "GET /api/attendance/me?month=2026-08" 200 "$(hit GET '/api/attendance/me?month=2026-08')"
check "GET /api/attendance/me/summary?month=2026-08" 200 "$(hit GET '/api/attendance/me/summary?month=2026-08')"
check "POST /api/attendance/punch-out" 200 "$(hit POST /api/attendance/punch-out '{}')"

note "arun — leave (correct payload: fromDate/toDate)"
check "GET /api/leave/types" 200 "$(hit GET /api/leave/types)"
check "GET /api/leave/balances" 200 "$(hit GET /api/leave/balances)"
check "GET /api/leave/me" 200 "$(hit GET /api/leave/me)"
LTID=$(curl -s "$BASE/api/leave/types" -H "Authorization: Bearer $TOKEN" | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -1)
if [ -n "$LTID" ]; then
  check "POST /api/leave/apply (type $LTID)" 200 "$(hit POST /api/leave/apply "{\"leaveTypeId\":$LTID,\"fromDate\":\"2026-09-01\",\"toDate\":\"2026-09-02\",\"reason\":\"QA automated test\",\"halfDay\":false}")"
else
  fail "no leave type id found"
fi

note "arun — payroll"
check "GET /api/payroll/salary-months/me" 200 "$(hit GET /api/payroll/salary-months/me)"
check "GET /api/payroll/payslip/list" 200 "$(hit GET /api/payroll/payslip/list)"
MONTH=$(curl -s "$BASE/api/payroll/salary-months/me" -H "Authorization: Bearer $TOKEN" | sed -n 's/.*"month":\([0-9]*\).*/\1/p' | head -1)
if [ -n "$MONTH" ]; then
  YEAR=$(curl -s "$BASE/api/payroll/salary-months/me" -H "Authorization: Bearer $TOKEN" | sed -n 's/.*"year":\([0-9]*\).*/\1/p' | head -1)
  check "GET /api/payroll/payslips/month?month=$MONTH&year=$YEAR" 200 "$(hit GET "/api/payroll/payslips/month?month=$MONTH&year=$YEAR")"
else
  fail "no salary month found (payslips empty is fine — but we couldn't exercise the endpoint)"
fi

note "arun — tickets (correct payload: title)"
check "GET /api/tickets" 200 "$(hit GET /api/tickets)"
check "POST /api/tickets (title)" 200 "$(hit POST /api/tickets '{"title":"QA probe ticket","description":"created by automated QA probe","priority":"MEDIUM"}')"

note "arun — tasks / work-reports / claims (web-exact urls)"
check "GET /api/tasks/me" 200 "$(hit GET /api/tasks/me)"
check "GET /api/tasks/all" 200 "$(hit GET /api/tasks/all)"
check "GET /api/work-reports/me" 200 "$(hit GET /api/work-reports/me)"
check "GET /api/ta-expenses/me" 200 "$(hit GET /api/ta-expenses/me)"
check "GET /api/notifications" 200 "$(hit GET /api/notifications)"
check "GET /api/users/me" 200 "$(hit GET /api/users/me)"

# ============ karthik (IT Manager) ============
RESP=$(login karthik); TOKEN=$(token_of "$RESP")
[ -n "$TOKEN" ] && ok "karthik login" || fail "karthik login"

note "karthik — manager views (web-exact urls)"
check "GET /api/tasks/all" 200 "$(hit GET /api/tasks/all)"
check "GET /api/tasks/workload" 200 "$(hit GET /api/tasks/workload)"
check "GET /api/ta-expenses/team" 200 "$(hit GET /api/ta-expenses/team)"
check "GET /api/ta-expenses/all" 200 "$(hit GET /api/ta-expenses/all)"
check "GET /api/work-reports/team" 200 "$(hit GET /api/work-reports/team)"
check "GET /api/work-reports/all" 200 "$(hit GET /api/work-reports/all)"
check "GET /api/leave/pending" 200 "$(hit GET /api/leave/pending)"
check "GET /api/leave/requests-for-me" 200 "$(hit GET /api/leave/requests-for-me)"
check "GET /api/attendance/team" 200 "$(hit GET /api/attendance/team)"
check "GET /api/attendance/absent-today" 200 "$(hit GET /api/attendance/absent-today)"
check "GET /api/assets" 200 "$(hit GET /api/assets)"
check "GET /api/tickets/all" 200 "$(hit GET /api/tickets/all)"
check "GET /api/tickets/assigned-to-me" 200 "$(hit GET /api/tickets/assigned-to-me)"
check "GET /api/complaints" 200 "$(hit GET /api/complaints)"
check "GET /api/dashboard/org-insights" 200 "$(hit GET /api/dashboard/org-insights)"
check "GET /api/users (directory)" 200 "$(hit GET /api/users)"

# ============ priya (IT HR) ============
RESP=$(login priya); TOKEN=$(token_of "$RESP")
[ -n "$TOKEN" ] && ok "priya login" || fail "priya login"

note "priya — HR views (web-exact urls)"
check "GET /api/users (directory)" 200 "$(hit GET /api/users)"
check "GET /api/onboarding/employees" 200 "$(hit GET /api/onboarding/employees)"
UID1=$(curl -s "$BASE/api/users?size=1" -H "Authorization: Bearer $TOKEN" | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -1)
if [ -n "$UID1" ]; then
  check "GET /api/onboarding/$UID1" 200 "$(hit GET "/api/onboarding/$UID1")"
fi
check "GET /api/payroll/salary-months?month=8&year=2026" 200 "$(hit GET '/api/payroll/salary-months?month=8&year=2026')"
check "POST /api/org/dropdowns [department]" 200 "$(hit POST /api/org/dropdowns '["department","designation"]')"

# ============ admin ============
RESP=$(login admin); TOKEN=$(token_of "$RESP")
[ -n "$TOKEN" ] && ok "admin login" || fail "admin login"

note "admin — executive"
check "GET /api/dashboard/executive" 200 "$(hit GET /api/dashboard/executive)"
check "GET /api/settings" 200 "$(hit GET /api/settings)"
check "GET /api/payroll/salaries" 200 "$(hit GET /api/payroll/salaries)"

# ============ negative / edge ============
note "negative tests"
code=$(curl -s -o /tmp/probe-body.json -w "%{http_code}" --max-time 10 "$BASE/api/does-not-exist-xyz" -H "Authorization: Bearer $TOKEN")
check "unknown path should be 404 (currently 500 — BUG)" 404 "$code"
code=$(curl -s -o /tmp/probe-body.json -w "%{http_code}" --max-time 10 "$BASE/api/attendance/punch-in" -H "Authorization: Bearer $TOKEN")
check "GET on POST-only endpoint should be 405 (currently 500 — BUG)" 405 "$code"
code=$(curl -s -o /tmp/probe-body.json -w "%{http_code}" --max-time 10 "$BASE/api/dashboard/me")
check "no token → 401" 401 "$code"
code=$(curl -s -o /tmp/probe-body.json -w "%{http_code}" --max-time 10 -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" -d '{"username":"arun","password":"wrongpass"}')
check "wrong password → 401" 401 "$code"

echo ""
echo "=========================================="
echo "  RESULTS: $PASSES passed, $FAILS failed"
echo "=========================================="
