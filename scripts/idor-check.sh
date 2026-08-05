#!/usr/bin/env bash
# IDOR / cross-tenant leakage check (PRODUCTION-CHECKLIST.md: "Write explicit automated tests
# that assert 403 (not 200) when user A requests user B's / tenant B's objects by ID").
#
# Not a JUnit suite - this repo has no test infrastructure (no Testcontainers/H2 profile) and
# stands up a full second Postgres just for tests wasn't worth it this pass (see DECISIONS.md).
# Instead this is a repeatable, checked-in live-verification script matching the same
# curl-based pattern used throughout this project's own build history, run against a real
# running arena-api + seed data. Run it after every change that touches ownership checks.
#
# Usage: BASE_URL=http://localhost:8081/api/v1 ./scripts/idor-check.sh
set -uo pipefail

BASE="${BASE_URL:-http://localhost:8081/api/v1}"
PY="${PYTHON_BIN:-python3}"
command -v "$PY" >/dev/null 2>&1 || PY="/c/Python314/python"

PASS=0
FAIL=0

signin() {
  curl -s -X POST "$BASE/auth/signin" -H "Content-Type: application/json" \
    -d "{\"email\":\"$1\",\"password\":\"$2\"}" | "$PY" -c "import sys,json; print(json.load(sys.stdin)['data']['token'])"
}

# assert_status URL METHOD TOKEN EXPECTED_STATUS DESCRIPTION
assert_status() {
  local url="$1" method="$2" token="$3" expected="$4" desc="$5"
  local status
  status=$(curl -s -o /dev/null -w "%{http_code}" -X "$method" "$url" -H "Authorization: Bearer $token")
  if [ "$status" = "$expected" ]; then
    echo "PASS  [$status] $desc"
    PASS=$((PASS + 1))
  else
    echo "FAIL  [$status, expected $expected] $desc"
    FAIL=$((FAIL + 1))
  fi
}

json_field() {
  "$PY" -c "import sys,json; d=json.load(sys.stdin)
try:
    for k in sys.argv[1].split('.'):
        d = d[int(k)] if k.isdigit() else d[k]
    print(d)
except Exception:
    print('')" "$1"
}

echo "== Signing in as two distinct tenants + platform_admin =="
TENANT_A_TOKEN=$(signin "demo.enterprise@vikisol.dev" "Demo@12345")
TENANT_B_TOKEN=$(signin "hr@razorpay.example.com" "Demo@12345")
PA_TOKEN=$(signin "admin@vikisol.dev" "Demo@12345")

if [ -z "$TENANT_A_TOKEN" ] || [ -z "$TENANT_B_TOKEN" ]; then
  echo "Could not sign in as both tenants - aborting (check demo seed data / server is running)."
  exit 2
fi

echo "== Discovering a real posting + applicant owned by Tenant A =="
POSTING_ID=""
APPLICANT_ID=""
for pid in $(curl -s "$BASE/enterprise/postings?page=0&size=20" -H "Authorization: Bearer $TENANT_A_TOKEN" | "$PY" -c "
import sys,json
d = json.load(sys.stdin)
print(' '.join(p['id'] for p in d['data']['content']))"); do
  APPLICANT_ID=$(curl -s "$BASE/enterprise/postings/$pid/applicants?page=0&size=1" -H "Authorization: Bearer $TENANT_A_TOKEN" | json_field "data.content.0.id")
  if [ -n "$APPLICANT_ID" ]; then POSTING_ID="$pid"; break; fi
done
echo "  posting=$POSTING_ID applicant=$APPLICANT_ID"

echo "== Discovering an applicant that already has an interview (not every application has one) =="
INTERVIEW_APPLICANT_ID=""
for pid in $(curl -s "$BASE/enterprise/postings?page=0&size=20" -H "Authorization: Bearer $TENANT_A_TOKEN" | "$PY" -c "
import sys,json
d = json.load(sys.stdin)
print(' '.join(p['id'] for p in d['data']['content']))"); do
  for aid in $(curl -s "$BASE/enterprise/postings/$pid/applicants?page=0&size=20" -H "Authorization: Bearer $TENANT_A_TOKEN" | "$PY" -c "
import sys,json
d = json.load(sys.stdin)
print(' '.join(a['id'] for a in d['data']['content']))"); do
    HAS_INTERVIEW=$(curl -s "$BASE/interviews/by-application/$aid" -H "Authorization: Bearer $TENANT_A_TOKEN" | "$PY" -c "
import sys,json
d = json.load(sys.stdin)
print('yes' if d.get('data') else '')" 2>/dev/null)
    if [ -n "$HAS_INTERVIEW" ]; then INTERVIEW_APPLICANT_ID="$aid"; break 2; fi
  done
done
echo "  applicant_with_interview=$INTERVIEW_APPLICANT_ID"

echo ""
echo "== Cross-tenant reads (Tenant B token against Tenant A's objects) - expect 403 =="
if [ -n "$POSTING_ID" ]; then
  assert_status "$BASE/enterprise/postings/$POSTING_ID" GET "$TENANT_B_TOKEN" 403 "GET posting owned by another tenant"
  assert_status "$BASE/enterprise/postings/$POSTING_ID/applicants" GET "$TENANT_B_TOKEN" 403 "GET applicant list of another tenant's posting"
fi
if [ -n "$APPLICANT_ID" ]; then
  assert_status "$BASE/enterprise/applicants/$APPLICANT_ID" GET "$TENANT_B_TOKEN" 403 "GET applicant owned by another tenant"
fi
if [ -n "$INTERVIEW_APPLICANT_ID" ]; then
  # Tenant B has no relationship at all to Tenant A's application (not the candidate, not the
  # hiring tenant) - a clean "genuinely uninvolved" party, unlike guessing at a talent account.
  # Only meaningful against an application that actually has an interview - GET .../by-application
  # returns 200+null (nothing to protect yet) when none exists, which isn't a 403 case at all.
  assert_status "$BASE/interviews/by-application/$INTERVIEW_APPLICANT_ID" GET "$TENANT_B_TOKEN" 403 "GET interview by application - uninvolved tenant"
else
  echo "SKIP  no applicant with an existing interview found - can't test this check"
fi

echo ""
echo "== Same-tenant reads (Tenant A token against its own objects) - expect 200 =="
if [ -n "$POSTING_ID" ]; then
  assert_status "$BASE/enterprise/postings/$POSTING_ID" GET "$TENANT_A_TOKEN" 200 "GET own posting"
  assert_status "$BASE/enterprise/postings/$POSTING_ID/applicants" GET "$TENANT_A_TOKEN" 200 "GET own applicant list"
fi
if [ -n "$APPLICANT_ID" ]; then
  assert_status "$BASE/enterprise/applicants/$APPLICANT_ID" GET "$TENANT_A_TOKEN" 200 "GET own applicant"
fi

echo ""
echo "== Role-gated platform_admin console - non-platform_admin gets 403 =="
assert_status "$BASE/admin/tenants" GET "$TENANT_A_TOKEN" 403 "company_admin cannot reach /admin/tenants"
assert_status "$BASE/admin/tenants" GET "$PA_TOKEN" 200 "platform_admin can reach /admin/tenants"

echo ""
echo "== File serving requires a valid signature =="
UPLOAD_URL_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/files/candidate-cv/00000000-0000-0000-0000-000000000000/cv/nonexistent.pdf")
if [ "$UPLOAD_URL_STATUS" = "403" ]; then
  echo "PASS  [403] Unsigned file URL rejected"
  PASS=$((PASS + 1))
else
  echo "FAIL  [$UPLOAD_URL_STATUS, expected 403] Unsigned file URL rejected"
  FAIL=$((FAIL + 1))
fi

echo ""
echo "===================================="
echo "  $PASS passed, $FAIL failed"
echo "===================================="
[ "$FAIL" -eq 0 ]
