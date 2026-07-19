#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOGS="$ROOT/logs"

SERVICES=(eureka-server api-gateway user-service connection-service message-service fanout-delivery-service frontend)

usage() {
  echo "Usage: $0 <service> [lines]"
  echo ""
  echo "Services:"
  for s in "${SERVICES[@]}"; do echo "  $s"; done
  echo ""
  echo "Examples:"
  echo "  $0 api-gateway          # tail -f"
  echo "  $0 message-service 200  # last 200 lines then follow"
  exit 1
}

[[ $# -lt 1 ]] && usage

SERVICE=$1
LINES=${2:-100}
LOG_FILE="$LOGS/$SERVICE.log"

if [[ ! -f "$LOG_FILE" ]]; then
  echo "Log file not found: $LOG_FILE"
  echo "Is $SERVICE running? Check with ./scripts/status.sh"
  exit 1
fi

echo "==> $LOG_FILE (last $LINES lines, then following...)"
echo ""
tail -n "$LINES" -f "$LOG_FILE"
