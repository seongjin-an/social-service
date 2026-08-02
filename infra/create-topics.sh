#!/usr/bin/env bash
# Kafka 토픽 명시 생성 (멱등). broker auto-create 를 끈 상태이므로 서비스 기동 전 필수.
# 앱 설정 의도(partitions=3)를 보장한다 — auto-create 는 broker 기본값(파티션 1)로 만들어 병렬성이 죽는다.
# start.sh 가 인프라 기동 직후 호출한다.
set -euo pipefail

CONTAINER="${KAFKA_CONTAINER:-social-kafka}"
BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:9092}"

# kafka-topics 실행 경로 (cp-kafka: PATH · apache/kafka: /opt/kafka/bin/*.sh)
if docker exec "$CONTAINER" bash -lc 'command -v kafka-topics' >/dev/null 2>&1; then
  KT="kafka-topics"
else
  KT="/opt/kafka/bin/kafka-topics.sh"
fi

echo "[create-topics] 브로커 대기: $CONTAINER / $BOOTSTRAP ..."
until docker exec "$CONTAINER" $KT --bootstrap-server "$BOOTSTRAP" --list >/dev/null 2>&1; do
  sleep 2
done

create() {
  local name=$1 parts=$2 rf=$3
  docker exec "$CONTAINER" $KT --bootstrap-server "$BOOTSTRAP" \
    --create --if-not-exists --topic "$name" --partitions "$parts" --replication-factor "$rf" >/dev/null
  echo "[create-topics] ✔ $name (partitions=$parts, rf=$rf)"
}

# 채팅 파이프라인 토픽 (전부 partitions=3, RF=1)
#  - message-relay / read-relay : connection → message-service
#  - message-fanout / read-fanout: message-service(outbox/Debezium) → fanout-service
create message-relay   3 1
create read-relay      3 1
create message-fanout  3 1
create read-fanout     3 1

# 매칭 파이프라인 토픽 (P1)
#  - like-relay      : matching REST → matching 판정 컨슈머 (pair 키 직렬화)
#  - match-created   : matching(outbox) → message-service (DIRECT 채널 생성)
#  - channel-created : message-service(outbox) → matching (channel_id 백필)
#  - match-fanout    : matching(outbox) → fanout-service (매칭 알림)
#  - match-unmatched : matching(outbox) → message-service (채널 CLOSED)
create like-relay      3 1
create match-created   3 1
create channel-created 3 1
create match-fanout    3 1
create match-unmatched 3 1

# 참고(여기서 안 만듦): connection-instance-{id} 는 connection-service 가 인스턴스별로
#   AdminClient 로 동적 생성(retention 120s), Debezium/Connect 내부 토픽은 Connect 가 생성.

echo "[create-topics] 완료. 현재 파이프라인 토픽:"
docker exec "$CONTAINER" $KT --bootstrap-server "$BOOTSTRAP" --list 2>/dev/null \
  | grep -E "relay|fanout|match" | sed 's/^/  /'
