#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOGS="$ROOT/logs"
PIDS="$ROOT/pids"

# Java 21 우선 사용 — Gradle 빌드도 동일 JDK를 쓰도록 JAVA_HOME 통일
JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17 2>/dev/null)"
export JAVA_HOME
JAVA_CMD="$JAVA_HOME/bin/java"

# OTel Java Agent
OTEL_AGENT_VERSION="2.8.0"
OTEL_AGENT="$ROOT/infra/otel/opentelemetry-javaagent.jar"
OTEL_AGENT_URL="https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar"

mkdir -p "$LOGS" "$PIDS" "$ROOT/infra/otel"

# ── 색상 ────────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*"; }

# ── 포트 대기 ────────────────────────────────────────────────────────────────
wait_port() {
  local name=$1 port=$2 timeout=${3:-60}
  info "Waiting for $name on :$port ..."
  for ((i=0; i<timeout; i++)); do
    if nc -z localhost "$port" 2>/dev/null; then
      success "$name is up"
      return 0
    fi
    sleep 1
  done
  error "$name failed to start within ${timeout}s"
  return 1
}

# ── OTel Agent 다운로드 ──────────────────────────────────────────────────────
download_otel_agent() {
  if [[ -f "$OTEL_AGENT" ]]; then
    return 0
  fi
  info "Downloading OTel Java Agent v${OTEL_AGENT_VERSION}..."
  curl -L --fail --progress-bar -o "$OTEL_AGENT" "$OTEL_AGENT_URL" \
    || { error "OTel Agent download failed. Traces will be disabled."; return 0; }
  success "OTel Agent downloaded"
}

# ── Spring Boot 기동 ─────────────────────────────────────────────────────────
start_spring() {
  local name=$1 jar=$2 port=$3

  if nc -z localhost "$port" 2>/dev/null; then
    warn "$name already running on :$port — skipping"
    return 0
  fi

  local OTEL_OPTS=()
  if [[ -f "$OTEL_AGENT" ]]; then
    OTEL_OPTS=(
      "-javaagent:${OTEL_AGENT}"
      "-Dotel.service.name=${name}"
      "-Dotel.exporter.otlp.endpoint=http://localhost:4317"
      "-Dotel.exporter.otlp.protocol=grpc"
      "-Dotel.traces.exporter=otlp"
      "-Dotel.metrics.exporter=none"
      "-Dotel.logs.exporter=otlp"
      "-Dotel.propagators=tracecontext,baggage"
    )
  fi

  info "Starting $name ..."
  "$JAVA_CMD" "${OTEL_OPTS[@]}" -jar "$jar" > "$LOGS/$name.log" 2>&1 &
  echo $! > "$PIDS/$name.pid"
  wait_port "$name" "$port"
}

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# 1. 인프라 (Docker)
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COMPOSE_FILE="$ROOT/infra/compose.yml"
if [[ -f "$COMPOSE_FILE" ]]; then
  info "Starting infrastructure (Docker Compose)..."
  docker compose -f "$COMPOSE_FILE" up -d
  wait_port "MySQL"  23306 60
  wait_port "Kafka"  9092  60
  wait_port "Redis"  6379  30
  success "Infrastructure ready"
else
  warn "infra/compose.yml not found — skipping infrastructure startup."
  warn "MySQL/Kafka/Redis must already be running, or services will fail to boot."
fi

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# 2. 빌드
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
download_otel_agent

if [[ "${SKIP_BUILD:-}" != "true" ]]; then
  info "Building all modules..."
  cd "$ROOT"
  ./gradlew :eureka-server:bootJar \
            :api-gateway:bootJar \
            :user-service:bootJar \
            :connection-service:bootJar \
            :message-service:bootJar \
            :fanout-delivery-service:bootJar \
            -x test --parallel -q
  success "Build complete"
fi

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# 3. Spring Boot 서비스 기동
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
EUREKA_JAR=$(find "$ROOT/services/eureka-server/build/libs"           -name "*.jar" ! -name "*plain*" | head -1)
GW_JAR=$(find "$ROOT/services/api-gateway/build/libs"                 -name "*.jar" ! -name "*plain*" | head -1)
USER_JAR=$(find "$ROOT/services/user-service/build/libs"              -name "*.jar" ! -name "*plain*" | head -1)
CONN_JAR=$(find "$ROOT/services/connection-service/build/libs"        -name "*.jar" ! -name "*plain*" | head -1)
MSG_JAR=$(find "$ROOT/services/message-service/build/libs"            -name "*.jar" ! -name "*plain*" | head -1)
FANOUT_JAR=$(find "$ROOT/services/fanout-delivery-service/build/libs" -name "*.jar" ! -name "*plain*" | head -1)

start_spring "eureka-server"           "$EUREKA_JAR"  8761
start_spring "api-gateway"             "$GW_JAR"      8080
start_spring "user-service"            "$USER_JAR"    8081
start_spring "connection-service"      "$CONN_JAR"    8082
start_spring "message-service"         "$MSG_JAR"     8083
start_spring "fanout-delivery-service" "$FANOUT_JAR"  8084

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Debezium 커넥터 등록
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
if [[ -f "$ROOT/infra/register-debezium.sh" ]]; then
  info "Registering Debezium connector..."
  bash "$ROOT/infra/register-debezium.sh"
else
  warn "infra/register-debezium.sh not found — skipping Debezium connector registration."
fi

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# 4. 프론트엔드
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
if [[ ! -d "$ROOT/frontend" ]]; then
  warn "frontend/ not found — skipping frontend startup."
elif nc -z localhost 3000 2>/dev/null; then
  warn "Frontend already running on :3000 — skipping"
else
  if [[ ! -f "$ROOT/frontend/.env.local" ]]; then
    warn "frontend/.env.local not found — copying from .env.local.example"
    cp "$ROOT/frontend/.env.local.example" "$ROOT/frontend/.env.local"
  fi
  info "Starting frontend..."
  cd "$ROOT/frontend"
  nohup npm run dev > "$LOGS/frontend.log" 2>&1 &
  echo $! > "$PIDS/frontend.pid"
  wait_port "frontend" 3000 90
fi

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo ""
success "All services started!"
echo ""
echo -e "  ${CYAN}Eureka Dashboard${NC}   http://localhost:8761"
echo -e "  ${CYAN}API Gateway${NC}        http://localhost:8080"
echo -e "  ${CYAN}Frontend${NC}           http://localhost:3000"
echo -e "  ${CYAN}Kafka UI${NC}           http://localhost:9090"
echo -e "  ${CYAN}RedisInsight${NC}       http://localhost:5540"
echo ""
echo -e "  Logs → ${LOGS}/"
echo -e "  Use ${YELLOW}./scripts/status.sh${NC} to check service health"
