#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PIDS="$ROOT/pids"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }

kill_service() {
  local name=$1
  local pid_file="$PIDS/$name.pid"

  if [[ ! -f "$pid_file" ]]; then
    warn "$name — PID file not found, skipping"
    return 0
  fi

  local pid
  pid=$(cat "$pid_file")

  if kill -0 "$pid" 2>/dev/null; then
    info "Stopping $name (PID $pid)..."
    kill "$pid"
    # 최대 10초 대기
    for ((i=0; i<10; i++)); do
      kill -0 "$pid" 2>/dev/null || break
      sleep 1
    done
    # 아직 살아있으면 강제 종료
    if kill -0 "$pid" 2>/dev/null; then
      warn "$name did not stop gracefully — forcing..."
      kill -9 "$pid" 2>/dev/null || true
    fi
    success "$name stopped"
  else
    warn "$name (PID $pid) was not running"
  fi
  rm -f "$pid_file"
}

# ── Spring Boot 서비스 종료 (역순) ───────────────────────────────────────────
kill_service "frontend"
kill_service "fanout-delivery-service"
kill_service "message-service"
kill_service "connection-service"
kill_service "user-service"
kill_service "api-gateway"
kill_service "eureka-server"

# ── Docker 인프라 종료 ────────────────────────────────────────────────────────
if [[ "${STOP_INFRA:-}" == "true" ]]; then
  info "Stopping infrastructure (Docker Compose)..."
  docker compose -f "$ROOT/infra/compose.yml" down
  success "Infrastructure stopped"
else
  echo ""
  warn "Docker infra is still running. Use STOP_INFRA=true ./scripts/stop.sh to stop it too."
fi

echo ""
success "Done"
