#!/usr/bin/env bash
# Debezium outbox 커넥터 등록 (멱등). scripts/start.sh 가 인프라·서비스 기동 후 호출한다.
# 전제: Kafka Connect(localhost:28083) 기동 + social.outbox 테이블 존재(message-service ddl-auto).
set -euo pipefail

CONNECT_URL="${CONNECT_URL:-http://localhost:28083}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONNECTOR_JSON="$HERE/debezium/outbox-connector.json"
NAME="outbox-connector"

echo "[register-debezium] Kafka Connect 대기: $CONNECT_URL ..."
until curl -sf "$CONNECT_URL/connectors" >/dev/null 2>&1; do
  sleep 3
done

if command -v jq >/dev/null 2>&1; then
  # jq 있으면 config 만 뽑아 PUT (있으면 갱신, 없으면 생성 — 완전 멱등)
  echo "[register-debezium] PUT $NAME/config (jq) ..."
  CONFIG="$(jq -c '.config' "$CONNECTOR_JSON")"
  HTTP_CODE="$(curl -s -o /tmp/dbz_reg_resp.json -w '%{http_code}' \
    -X PUT -H 'Content-Type: application/json' \
    --data "$CONFIG" "$CONNECT_URL/connectors/$NAME/config")"
else
  # jq 없으면 전체 POST, 이미 있으면(409) 정상으로 간주
  echo "[register-debezium] POST /connectors (no jq) ..."
  HTTP_CODE="$(curl -s -o /tmp/dbz_reg_resp.json -w '%{http_code}' \
    -X POST -H 'Content-Type: application/json' \
    --data "@$CONNECTOR_JSON" "$CONNECT_URL/connectors")"
  [[ "$HTTP_CODE" == "409" ]] && { echo "[register-debezium] 이미 등록됨(409)"; HTTP_CODE=200; }
fi

if [[ "$HTTP_CODE" =~ ^2 ]]; then
  echo "[register-debezium] OK ($HTTP_CODE)"
  curl -sf "$CONNECT_URL/connectors/$NAME/status" || true
  echo
else
  echo "[register-debezium] 실패 ($HTTP_CODE):"; cat /tmp/dbz_reg_resp.json; echo
  exit 1
fi
