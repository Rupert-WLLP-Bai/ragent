#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG_DIR="${LOG_DIR:-$REPO_ROOT/logs/local-startup}"
mkdir -p "$LOG_DIR"

DB_USERNAME="${DB_USERNAME:-root}"
DB_PASSWORD="${DB_PASSWORD:-root}"
REDIS_PASSWORD="${REDIS_PASSWORD:-123456}"
RUSTFS_ACCESS_KEY_ID="${RUSTFS_ACCESS_KEY_ID:-rustfsadmin}"
RUSTFS_SECRET_ACCESS_KEY="${RUSTFS_SECRET_ACCESS_KEY:-rustfsadmin}"
RUSTFS_HEALTH_BUCKET="${RUSTFS_HEALTH_BUCKET:-rag-health}"
SERVER_PORT="${SERVER_PORT:-9090}"

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    printf '[ERROR] missing command: %s\n' "$cmd" >&2
    exit 1
  fi
}

warn_if_port_missing() {
  local port="$1"
  if ! ss -ltn | awk '{print $4}' | grep -Eq "(^|:)${port}$"; then
    printf '[WARN] dependency port %s is not listening\n' "$port"
  fi
}

start_if_missing() {
  local name="$1"
  local port="$2"
  local log_file="$3"
  shift 3

  if ss -ltn | awk '{print $4}' | grep -Eq "(^|:)${port}$"; then
    printf '[SKIP] %s already listening on %s\n' "$name" "$port"
    return 0
  fi

  printf '[START] %s\n' "$name"
  nohup "$@" >"$log_file" 2>&1 &
  printf '%s\n' "$!" >"$LOG_DIR/${name}.pid"
}

require_cmd mvn
require_cmd npm
require_cmd curl
require_cmd ss

wait_for_http() {
  local name="$1"
  local url="$2"
  local needle="$3"
  local attempts="${4:-60}"
  local i

  for ((i=1; i<=attempts; i++)); do
    local response
    response="$(curl -fsS "$url" 2>/dev/null || true)"
    if [[ "$response" == *"$needle"* ]]; then
      printf '[READY] %s\n' "$name"
      return 0
    fi
    sleep 1
  done

  printf '[ERROR] %s did not become ready: %s\n' "$name" "$url" >&2
  return 1
}

wait_for_mcp() {
  local attempts="${1:-90}"
  local i

  for ((i=1; i<=attempts; i++)); do
    local response
    response="$(curl -fsS -X POST "http://127.0.0.1:9099/mcp" -H 'Content-Type: application/json' -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' 2>/dev/null || true)"
    if [[ "$response" == *'"tools"'* ]]; then
      printf '[READY] mcp-server\n'
      return 0
    fi
    sleep 1
  done

  printf '[ERROR] mcp-server did not become ready on http://127.0.0.1:9099/mcp\n' >&2
  return 1
}

printf '== Checking dependency ports ==\n'
for port in 3306 6379 19530 9000; do
  warn_if_port_missing "$port"
done

printf '\n== Starting local services ==\n'
start_if_missing "mcp-server" 9099 "$LOG_DIR/mcp-server.log" \
  mvn -f "$REPO_ROOT/pom.xml" -pl mcp-server spring-boot:run
wait_for_mcp 90

start_if_missing "backend" "$SERVER_PORT" "$LOG_DIR/backend.log" \
  env \
    DB_USERNAME="$DB_USERNAME" \
    DB_PASSWORD="$DB_PASSWORD" \
    REDIS_PASSWORD="$REDIS_PASSWORD" \
    RUSTFS_ACCESS_KEY_ID="$RUSTFS_ACCESS_KEY_ID" \
    RUSTFS_SECRET_ACCESS_KEY="$RUSTFS_SECRET_ACCESS_KEY" \
    RUSTFS_HEALTH_BUCKET="$RUSTFS_HEALTH_BUCKET" \
    SERVER_PORT="$SERVER_PORT" \
    mvn -f "$REPO_ROOT/pom.xml" -pl bootstrap spring-boot:run -Dspring-boot.run.main-class=com.nageoffer.ai.ragent.RagentApplication

start_if_missing "frontend" 5173 "$LOG_DIR/frontend.log" \
  npm --prefix "$REPO_ROOT/frontend" run dev -- --host 0.0.0.0

printf '\n== Local URLs ==\n'
printf 'Frontend:  http://127.0.0.1:5173\n'
printf 'Backend:   http://127.0.0.1:%s/api/ragent\n' "$SERVER_PORT"
printf 'Health:    http://127.0.0.1:%s/api/ragent/actuator/health\n' "$SERVER_PORT"
printf 'Readiness: http://127.0.0.1:%s/api/ragent/actuator/health/readiness\n' "$SERVER_PORT"
printf 'MCP:       http://127.0.0.1:9099/mcp\n'
printf '%s\n' '' 'Notes:'
printf '%s\n' '- This script starts MCP, backend, and frontend only.'
printf '%s\n' '- It assumes dependency services on 3306/6379/19530/9000 are already running.'
printf '%s\n' '- If readiness is not UP on a first run, prepare rag-health and rag_default_store as documented in README.md.'
printf '\nLogs: %s\n' "$LOG_DIR"
printf 'Next: bash scripts/check-local.sh\n'
