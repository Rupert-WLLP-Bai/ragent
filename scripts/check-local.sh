#!/usr/bin/env bash
set -euo pipefail

SERVER_PORT="${SERVER_PORT:-9090}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
MCP_PORT="${MCP_PORT:-9099}"
BACKEND_HEALTH_URL="${BACKEND_HEALTH_URL:-http://127.0.0.1:${SERVER_PORT}/api/ragent/actuator/health}"
BACKEND_READINESS_URL="${BACKEND_READINESS_URL:-http://127.0.0.1:${SERVER_PORT}/api/ragent/actuator/health/readiness}"
FRONTEND_URL="${FRONTEND_URL:-http://127.0.0.1:${FRONTEND_PORT}}"
MCP_URL="${MCP_URL:-http://127.0.0.1:${MCP_PORT}/mcp}"
EXPECTED_PORTS=(3306 6379 19530 9000 "$MCP_PORT" "$SERVER_PORT" "$FRONTEND_PORT")

pass_count=0
fail_count=0

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    printf '[FAIL] missing command: %s\n' "$cmd"
    exit 1
  fi
}

pass() {
  printf '[PASS] %s\n' "$1"
  pass_count=$((pass_count + 1))
}

fail() {
  printf '[FAIL] %s\n' "$1"
  fail_count=$((fail_count + 1))
}

check_port() {
  local port="$1"
  if ss -ltn | awk '{print $4}' | grep -Eq "(^|:)${port}$"; then
    pass "port ${port} is listening"
  else
    fail "port ${port} is not listening"
  fi
}

check_http_contains() {
  local name="$1"
  local url="$2"
  local needle="$3"
  local response
  if response=$(curl -fsS "$url" 2>/dev/null) && [[ "$response" == *"$needle"* ]]; then
    pass "$name responded as expected"
  else
    fail "$name did not return expected content"
  fi
}

check_mcp() {
  local response
  if response=$(curl -fsS -X POST "$MCP_URL" -H 'Content-Type: application/json' -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' 2>/dev/null) \
    && [[ "$response" == *'"tools"'* ]]; then
    pass "mcp tools/list responded"
  else
    fail "mcp tools/list failed"
  fi
}

require_cmd curl
require_cmd ss

printf '== Port checks ==\n'
for port in "${EXPECTED_PORTS[@]}"; do
  check_port "$port"
done

printf '\n== HTTP checks ==\n'
check_http_contains "frontend" "$FRONTEND_URL" '<!doctype html>'
check_http_contains "backend health" "$BACKEND_HEALTH_URL" '"status":"UP"'
check_http_contains "backend readiness" "$BACKEND_READINESS_URL" '"status":"UP"'

readiness_response="$(curl -fsS "$BACKEND_READINESS_URL" 2>/dev/null || true)"
if [[ "$readiness_response" == *'"collection":"rag_default_store"'* ]]; then
  pass "milvus default collection is reported in readiness"
fi
if [[ "$readiness_response" == *'"bucket":"rag-health"'* ]]; then
  pass "rustfs health bucket is reported in readiness"
fi
check_mcp

printf '\n== Summary ==\n'
printf 'Passed: %s\n' "$pass_count"
printf 'Failed: %s\n' "$fail_count"

if [[ "$fail_count" -gt 0 ]]; then
  printf '[HINT] On first run, verify rag-health bucket and rag_default_store collection are prepared.\n'
  exit 1
fi
