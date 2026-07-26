#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PIDS="$ROOT/pids"

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; GRAY='\033[0;90m'; NC='\033[0m'

check() {
  local name=$1 port=$2 url=${3:-}
  local pid_file="$PIDS/$name.pid"
  local pid="-"
  local pid_status=""

  # PID 파일 확인
  if [[ -f "$pid_file" ]]; then
    pid=$(cat "$pid_file")
    if ! kill -0 "$pid" 2>/dev/null; then
      pid_status=" ${RED}(dead)${NC}"
    fi
  fi

  # 포트 확인
  if nc -z localhost "$port" 2>/dev/null; then
    local status="${GREEN}●  UP${NC}"
    local link=""
    [[ -n "$url" ]] && link="${GRAY} → $url${NC}"
    printf "  %-22s %b  :%-5s  PID %-7s%b%b\n" "$name" "$status" "$port" "$pid" "$pid_status" "$link"
  else
    printf "  %-22s %b  :%-5s  PID %-7s%b\n" "$name" "${RED}○  DOWN${NC}" "$port" "$pid" "$pid_status"
  fi
}

check_docker() {
  local name=$1 container=$2 port=$3
  local running
  running=$(docker inspect -f '{{.State.Running}}' "$container" 2>/dev/null || echo "false")
  if [[ "$running" == "true" ]]; then
    printf "  %-22s %b  :%-5s\n" "$name" "${GREEN}●  UP${NC}" "$port"
  else
    printf "  %-22s %b  :%-5s\n" "$name" "${YELLOW}○  STOPPED${NC}" "$port"
  fi
}

echo ""
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${CYAN}  Social Discovery — Service Status${NC}"
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo -e "  ${GRAY}[ Infrastructure ]${NC}"
check_docker "MySQL"          "social-mysql"        23306
check_docker "Kafka"          "social-kafka"        9092
check_docker "Kafka Connect"  "kafka-connect"       28083
check_docker "Kafka UI"       "social-kafka-ui"     9090
check_docker "Redis"          "social-redis"        6379
check_docker "RedisInsight"   "social-redisinsight" 5540
echo ""
echo -e "  ${GRAY}[ Spring Boot ]${NC}"
check "eureka-server"           8761 "http://localhost:8761"
check "api-gateway"             8080 "http://localhost:8080"
check "user-service"            8081
check "connection-service"      8082
check "message-service"         8083
check "fanout-delivery-service" 8084
check "profile-service"         8085
echo ""
echo -e "  ${GRAY}[ Frontend ]${NC}"
check "frontend"           3000 "http://localhost:3000"
echo ""
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
