#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOGS="$ROOT/logs"
PIDS="$ROOT/pids"

# Java 21 우선 사용 — Gradle 빌드도 동일 JDK를 쓰도록 JAVA_HOME 통일
JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17 2>/dev/null)"
export JAVA_HOME
JAVA_CMD="$JAVA_HOME/bin/java"

CYAN='\033[0;36m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }

usage() {
  echo "Usage: $0 <service> [--skip-build]"
  echo ""
  echo "Services: eureka-server api-gateway user-service connection-service message-service fanout-delivery-service frontend"
  exit 1
}

[[ $# -lt 1 ]] && usage

SERVICE=$1
SKIP_BUILD=${2:-}

case "$SERVICE" in
  eureka-server)      PORT=8761 ;;
  api-gateway)        PORT=8080 ;;
  user-service)       PORT=8081 ;;
  connection-service) PORT=8082 ;;
  message-service)            PORT=8083 ;;
  fanout-delivery-service)    PORT=8084 ;;
  frontend)                   PORT=3000 ;;
  *) echo "Unknown service: $SERVICE"; usage ;;
esac

wait_port() {
  local port=$1 timeout=${2:-60}
  for ((i=0; i<timeout; i++)); do
    nc -z localhost "$port" 2>/dev/null && return 0
    sleep 1
  done
  return 1
}

# ── 종료 ────────────────────────────────────────────────────────────────────
pid_file="$PIDS/$SERVICE.pid"
if [[ -f "$pid_file" ]]; then
  pid=$(cat "$pid_file")
  if kill -0 "$pid" 2>/dev/null; then
    info "Stopping $SERVICE (PID $pid)..."
    kill "$pid"
    for ((i=0; i<10; i++)); do
      kill -0 "$pid" 2>/dev/null || break
      sleep 1
    done
    kill -0 "$pid" 2>/dev/null && kill -9 "$pid" 2>/dev/null || true
    success "$SERVICE stopped"
  fi
  rm -f "$pid_file"
fi

# ── 빌드 ────────────────────────────────────────────────────────────────────
if [[ "$SERVICE" != "frontend" && "$SKIP_BUILD" != "--skip-build" ]]; then
  info "Building $SERVICE..."
  cd "$ROOT"
  ./gradlew ":$SERVICE:bootJar" -x test -q
  success "Build complete"
fi

# ── 기동 ────────────────────────────────────────────────────────────────────
mkdir -p "$LOGS" "$PIDS"

if [[ "$SERVICE" == "frontend" ]]; then
  if [[ ! -f "$ROOT/frontend/.env.local" ]]; then
    warn "frontend/.env.local not found — copying from .env.local.example"
    cp "$ROOT/frontend/.env.local.example" "$ROOT/frontend/.env.local"
  fi
  info "Starting frontend..."
  cd "$ROOT/frontend"
  nohup npm run dev > "$LOGS/frontend.log" 2>&1 &
  echo $! > "$PIDS/frontend.pid"
else
  jar=$(find "$ROOT/services/$SERVICE/build/libs" -name "*.jar" ! -name "*plain*" | head -1)
  info "Starting $SERVICE..."
  "$JAVA_CMD" -jar "$jar" > "$LOGS/$SERVICE.log" 2>&1 &
  echo $! > "$PIDS/$SERVICE.pid"
fi

WAIT_TIMEOUT=60
[[ "$SERVICE" == "frontend" ]] && WAIT_TIMEOUT=90
info "Waiting for $SERVICE on :$PORT..."
if wait_port "$PORT" $WAIT_TIMEOUT; then
  success "$SERVICE restarted on :$PORT"
else
  echo "Failed to start $SERVICE. Check logs: ./scripts/logs.sh $SERVICE"
  exit 1
fi
