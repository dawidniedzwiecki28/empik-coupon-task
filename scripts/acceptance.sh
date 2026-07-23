#!/usr/bin/env bash
#
# Black-box acceptance + load check for a RUNNING coupon service.
#
# It walks every create/redeem outcome, proves the concurrency guarantee
# (500 users race for 50 slots -> exactly 50 succeed), fires a 2000-request
# load burst, and prints a pass/fail summary (non-zero exit on any failure).
#
# Prerequisites:
#   - the service is running (see the README "Running it" section) and was
#     started with COUPON_REST_TRUST_CLIENT_IP=true, so this script can set the
#     caller's country via the X-Forwarded-For header;
#   - bash, curl, python3, uuidgen, seq and xargs (with -P) on the PATH.
#
# Usage:
#   ./scripts/acceptance.sh                     # against http://localhost:8080
#   BASE=http://some-host:8080 ./scripts/acceptance.sh
#
set -uo pipefail

BASE=${BASE:-http://localhost:8080}
RUN="$(date +%s)$RANDOM"   # unique code suffix (time + randomness) so parallel runs don't collide
US='8.8.8.8'               # geolocates to US in the bundled DB
pass=0; fail=0

hr()  { printf '%s\n' "----------------------------------------------------------------"; }
now() { python3 -c 'import time; print(time.time())'; }

# create <code> <maxUses> <country> -> prints HTTP status
create() {
  curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/coupons" \
    -H 'Content-Type: application/json' \
    -d "{\"code\":\"$1\",\"maxUses\":$2,\"country\":\"$3\"}"
}
# redeem <code> <userId> [ip]  (omit ip -> loopback, no header) -> prints HTTP status
redeem() {
  if [ $# -ge 3 ]; then
    curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/coupons/redemptions" \
      -H 'Content-Type: application/json' -H "X-Forwarded-For: $3" \
      -d "{\"code\":\"$1\",\"userId\":\"$2\"}"
  else
    curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/coupons/redemptions" \
      -H 'Content-Type: application/json' \
      -d "{\"code\":\"$1\",\"userId\":\"$2\"}"
  fi
}
expect() { # <label> <expected> <actual>
  if [ "$2" = "$3" ]; then printf '  \033[32mPASS\033[0m  %-42s %s\n' "$1" "$3"; pass=$((pass+1))
  else printf '  \033[31mFAIL\033[0m  %-42s expected %s, got %s\n' "$1" "$2" "$3"; fail=$((fail+1)); fi
}

echo; echo "Coupon service acceptance test  ->  $BASE   (run id $RUN)"; hr

# ── 0. health ───────────────────────────────────────────────────────────
health=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/actuator/health")
if [ "$health" != "200" ]; then echo "  app not healthy (/actuator/health = $health) — is it running?"; exit 1; fi
echo "  app is UP"; hr

# ── 1. create: validation & duplicates ─────────────────────────────────
echo "CREATE"
expect "create a coupon"                   201 "$(create "SUMMER$RUN" 5 US)"
expect "duplicate code (case-insensitive)" 409 "$(create "summer$RUN" 1 US)"
expect "blank code"                        400 "$(create "" 1 US)"
expect "non-positive maxUses"              400 "$(create "BADMAX$RUN" 0 US)"
expect "malformed country"                 400 "$(create "BADCTY$RUN" 1 XYZ)"
hr

# ── 1b. read a coupon back via its Location header ──────────────────────
echo "READ"
loc=$(curl -s -D - -o /dev/null -X POST "$BASE/api/coupons" -H 'Content-Type: application/json' \
  -d "{\"code\":\"READ$RUN\",\"maxUses\":3,\"country\":\"US\"}" \
  | awk 'tolower($1)=="location:"{print $2}' | tr -d '\r')
expect "GET a created coupon"              200 "$(curl -s -o /dev/null -w '%{http_code}' "$BASE$loc")"
expect "GET an unknown coupon"             404 "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/coupons/$(uuidgen)")"
hr

# ── 2. redeem: every outcome ────────────────────────────────────────────
echo "REDEEM"
expect "success"                           200 "$(redeem "SUMMER$RUN" "$(uuidgen)" "$US")"
expect "case-insensitive code"             200 "$(redeem "summer$RUN" "$(uuidgen)" "$US")"
U=$(uuidgen)
expect "first redemption by a user"        200 "$(redeem "SUMMER$RUN" "$U" "$US")"
expect "same user again"                   409 "$(redeem "SUMMER$RUN" "$U" "$US")"
create "ONCE$RUN" 1 US >/dev/null
expect "fills the last slot"               200 "$(redeem "ONCE$RUN" "$(uuidgen)" "$US")"
expect "limit reached"                     409 "$(redeem "ONCE$RUN" "$(uuidgen)" "$US")"
expect "unknown coupon"                    404 "$(redeem "NOPE$RUN" "$(uuidgen)" "$US")"
expect "malformed userId"                  400 "$(redeem "SUMMER$RUN" "not-a-uuid" "$US")"
expect "geo unavailable (loopback IP)"     422 "$(redeem "SUMMER$RUN" "$(uuidgen)")"
create "POLAND$RUN" 5 PL >/dev/null
expect "wrong country"                     403 "$(redeem "POLAND$RUN" "$(uuidgen)" "$US")"
body=$(curl -s -X POST "$BASE/api/coupons/redemptions" -H 'Content-Type: application/json' \
  -H "X-Forwarded-For: $US" -d "{\"code\":\"POLAND$RUN\",\"userId\":\"$(uuidgen)\"}")
countries=$(printf '%s' "$body" | python3 -c \
  'import json,sys; b=json.load(sys.stdin); print(b.get("requiredCountry"), b.get("callerCountry"), sep="/")' \
  2>/dev/null || echo "parse-error")
expect "403 problem+json carries both countries" "PL/US" "$countries"
hr

# ── 3. concurrency correctness: exactly maxUses win the race ────────────
echo "CONCURRENCY  (500 distinct users race for 50 slots)"
export BASE CODE="RUSH$RUN"
create "$CODE" 50 US >/dev/null
statuses=$(seq 1 500 | xargs -P 50 -I{} sh -c \
  'curl -s -o /dev/null -w "%{http_code}\n" -X POST "$BASE/api/coupons/redemptions" \
     -H "Content-Type: application/json" -H "X-Forwarded-For: 8.8.8.8" \
     -d "{\"code\":\"$CODE\",\"userId\":\"$(uuidgen)\"}"')
ok=$(echo "$statuses"    | grep -c '^200$'          || true)
full=$(echo "$statuses"  | grep -c '^409$'          || true)
other=$(echo "$statuses" | grep -vcE '^(200|409)$'  || true)
expect "exactly 50 redemptions succeed"    50  "$ok"
expect "the other 450 rejected as full"    450 "$full"
expect "no unexpected statuses"            0   "$other"
hr

# ── 4. load burst: throughput + latency ─────────────────────────────────
echo "LOAD BURST  (2000 redemptions, concurrency 64, large coupon)"
export CODE="LOAD$RUN"
create "$CODE" 100000 US >/dev/null
t0=$(now)
lstat=$(seq 1 2000 | xargs -P 64 -I{} sh -c \
  'curl -s -o /dev/null -w "%{http_code}\n" -X POST "$BASE/api/coupons/redemptions" \
     -H "Content-Type: application/json" -H "X-Forwarded-For: 8.8.8.8" \
     -d "{\"code\":\"$CODE\",\"userId\":\"$(uuidgen)\"}"')
t1=$(now)
n2=$(echo "$lstat"   | grep -c '^200$'  || true)
nerr=$(echo "$lstat" | grep -vc '^200$' || true)
python3 - "$t0" "$t1" "$n2" "$nerr" <<'PY'
import sys
t0,t1,ok,err=float(sys.argv[1]),float(sys.argv[2]),int(sys.argv[3]),int(sys.argv[4])
dt=t1-t0; total=ok+err
print(f"  {total} requests in {dt:.2f}s  ->  {total/dt:,.0f} req/s   ({ok} x 200, {err} non-200)")
print("  note: bounded by curl/shell spawn on localhost, not the app's ceiling")
PY
expect "all load-burst requests returned 200" 0 "$nerr"
echo "  single-request latency samples:"
for _ in 1 2 3; do
  curl -s -o /dev/null -w "    %{time_total}s (HTTP %{http_code})\n" -X POST "$BASE/api/coupons/redemptions" \
    -H 'Content-Type: application/json' -H "X-Forwarded-For: $US" \
    -d "{\"code\":\"$CODE\",\"userId\":\"$(uuidgen)\"}"
done
hr

# ── summary ─────────────────────────────────────────────────────────────
echo "SUMMARY: $pass passed, $fail failed"
[ "$fail" -eq 0 ] && echo "  ALL GOOD" || echo "  SOME CHECKS FAILED"
hr
exit $((fail > 0 ? 1 : 0))
