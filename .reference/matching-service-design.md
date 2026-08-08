# Matching Service (P1) — 설계 문서

> 개정 2026-07-29 · 짝: [`features.md`](./features.md)(F1-*) · [`schema.sql`](./schema.sql)(likes/matches/outbox 메모 A~D)
>
> **범위**: P1 매칭 코어. 이건 단일 서비스가 아니라 **matching-service(신규) + message-service(Saga 확장) + fanout(알림 확장) + connection(수신 타입 추가)** 4곳이 물리는 작업이다.

---

## 0. 확정 전제 (이미 검증됨)

- **식별자 = `userId`(UUID)**. 채널·연결·fanout 전부 userId 기반 → matching도 userId. (프로필 단위 매칭 아님)
- **이벤트 백본 = Kafka + Outbox + Debezium CDC** (실증 완료). reference는 "Poller"라 적혀 있으나 **실제 자산은 Debezium** — matching도 **Debezium 재사용**한다. (Poller 안 씀)
- **공용 트랜잭션 아웃박스**: `social.outbox` 한 테이블을 여러 서비스가 공유하고, **Debezium EventRouter가 `destination_topic` 컬럼으로 라우팅**한다(이미 동작 중). → matching이 여기에 행만 넣으면 **커넥터 설정 변경 없이** 원하는 토픽으로 나간다.
  - 트레이드오프: "테이블=서비스 소유" 순수성은 약간 깨짐. 대신 검증된 단일 커넥터를 그대로 씀. (엄격 분리를 원하면 `matching_outbox` 별도 테이블 + connector `table.include.list`에 추가하는 대안 — 지금은 공용 권장)
- DB는 전부 `social` 스키마 공유, 소유는 테이블로 구분. matching 소유 = `likes`, `matches`.

---

## 1. 데이터 모델

### matching 소유 (신규)
```sql
likes   (from_user_id BIN16, to_user_id BIN16, type ENUM(LIKE|PASS|SUPER), created_at,
         PRIMARY KEY(from_user_id, to_user_id))          -- 복합PK = 중복 좋아요 멱등 흡수
        INDEX idx_likes_to_user(to_user_id, type)         -- "나를 좋아한 사람"

matches (match_id BIN16 PK = UUID v7,                     -- 시간 정렬 UUID · saga 상관키 · 파티션 키
         user_lo_id BIN16, user_hi_id BIN16,              -- 항상 min/max 정렬 저장
         channel_id BIGINT NULL,                          -- Saga로 백필(성사 직후 NULL)
         status ENUM(ACTIVE|UNMATCHED), created_at, updated_at,
         UNIQUE KEY uk_matches_pair(user_lo_id, user_hi_id))  -- ★ 중복 매칭 원천 차단
        INDEX idx_matches_lo(user_lo_id, status), idx_matches_hi(user_hi_id, status)
```

### 공용 outbox (기존 `social.outbox` 그대로 매핑)
matching 전용 `OutboxEventEntity`로 같은 테이블 매핑. 컬럼 동일(event_id, saga_id, aggregate_*, event_type, payload, **destination_topic**, **partition_key**, status, created_at ...).

### message-service 확장 (channel ALTER — ddl-auto가 컬럼 추가)
```
channel:         + type   ENUM(DIRECT|OPEN)            REST 생성분은 OPEN, 매칭 생성분은 DIRECT
                 + match_id BINARY(16) NULL UNIQUE     ← DIRECT 채널의 Saga 멱등 키 (matchId = UUID)
                 + status ENUM(ACTIVE|CLOSED)          언매치 시 CLOSED → 전송 거부
                 (+ category/owner_id/max_members/member_count : OPEN 전용, 아직 미도입)
channel_members: (role 은 OPEN 도입 시 추가 — DIRECT 에는 필요 없어 보류)
```
> `match_id UNIQUE`가 핵심: MATCH_CREATED 재수신 시 "이 매칭의 채널 이미 있나?"를 이걸로 판정(멱등).

---

## 2. Kafka 토픽

| 토픽 | 생산 | 소비 | 파티션 키 | 발행 방식 |
|---|---|---|---|---|
| `like-relay` | matching REST(POST /likes) | matching 판정 컨슈머 | **pair(a,b)=min:max** | KafkaTemplate 직접 |
| `match-created` | matching(outbox) | message-service | matchId | outbox→Debezium |
| `channel-created` | message-service(outbox) | matching | matchId | outbox→Debezium |
| `match-fanout` | matching(outbox) | fanout-service | matchId | outbox→Debezium |
| `match-unmatched` | matching(outbox) | message-service | matchId | outbox→Debezium |

- **like-relay만 outbox 미경유**(직접 발행) — 판정 전 단계라 트랜잭션 원자성 대상이 아님. 나머지 saga 이벤트는 전부 outbox.
- `create-topics.sh`에 5개 추가 (like-relay는 pair 병렬성 위해 partitions≥3, 나머지도 3/RF1). ✅
- Debezium 커넥터: **변경 없음**(destination_topic으로 match-created/channel-created/match-fanout 자동 라우팅). ✅

### 페이로드 봉투(envelope) 규칙 — 실제 구현
| 방향 | 형태 | 이유 |
|---|---|---|
| like-relay, match-created, channel-created, match-unmatched | **raw JSON** | 토픽당 타입이 하나 → 봉투의 `type` 으로 라우팅할 게 없음 |
| **match-fanout** | `{type, payload}` **봉투** | fanout-service 는 한 서비스가 여러 fanout(message/read/match)을 처리 → 디스패처가 `type` 으로 핸들러를 찾음 |
| connection-instance-{id} | `{type, payload}` 봉투 | connection 도 동일 디스패처 구조 (`MATCH_NOTIFICATION`) |

> `JsonUtil` 은 **모르는 필드를 무시**한다(FAIL_ON_UNKNOWN_PROPERTIES off) — 생산자가 페이로드에 필드를
> 추가해도 소비자가 먼저 깨지지 않는다(전진 호환). 이게 없으면 필드 추가가 곧 컨슈머 장애다.

---

## 3. 흐름 ① — 좋아요 접수 & 매칭 판정

```
POST /api/likes {toUserId, type}   (X-User-Id = fromUserId)
  → 검증(자기자신 금지 등) → like-relay 발행 (key = pair(from,to)) → 202 Accepted (즉시)

[like-relay 컨슈머]  ★단일 작성자 per pair★
  파티션 키 pair(a,b) → A→B, B→A 가 항상 같은 파티션 → 같은 스레드 → 직렬 처리(락 불필요)
  1) likes UPSERT (PK 멱등)
  2) type ∈ {LIKE,SUPER} 면 역방향 조회:
       SELECT FROM likes WHERE from_user_id=상대 AND to_user_id=나 AND type IN (LIKE,SUPER)
  3) 역방향 존재 → ⟨한 트랜잭션⟩
       - matches INSERT (user_lo/hi 정렬, channel_id=NULL, status=ACTIVE)  ← UNIQUE(lo,hi) 중복차단
       - outbox   INSERT (event_type=MATCH_CREATED, saga 상관키=matchId,
                          destination_topic='match-created', partition_key=matchId,
                          payload={matchId, userLo, userHi, recipients:[A,B]})
     PASS 는 저장만, 매칭 판정 제외.
```
**정합성**: (파티션 직렬화 = 엇갈림 방지) + (UNIQUE = 중복 방지) 이중 안전. matches+outbox 한 트랜잭션 = dual-write 어긋남 방지.

---

## 4. 흐름 ② — Saga (매칭 → DIRECT 채널 → 알림)

```
[1] matching        : matches INSERT + outbox(MATCH_CREATED, dest=match-created)   ⟨tx⟩
        │  Debezium → match-created
        ▼
[2] message-service : MATCH_CREATED 소비
        - 멱등: channel.findByMatchId(matchId) 있으면 no-op(재수신 대비)
        - 없으면 channel(type=DIRECT, match_id=matchId) 생성 + channel_members(A,B)
        - outbox(CHANNEL_CREATED, dest=channel-created, payload={matchId, channelId, recipients})  ⟨tx⟩
        │  Debezium → channel-created
        ▼
[3] matching        : CHANNEL_CREATED 소비
        - matches.channel_id 백필 (matchId로, 이미 차 있으면 no-op 멱등)
        - outbox(MATCH_FANOUT, dest=match-fanout, payload={matchId, channelId, recipientIds:[A,B]})  ⟨tx⟩
        │  Debezium → match-fanout
        ▼
[4] fanout-service  : MATCH_FANOUT 소비
        - recipient 각각 ws:user 조회 → 붙은 connection-instance-{id} 토픽으로 push
        - payload type = MATCH_NOTIFICATION {matchId, channelId, partnerProfile...}
        ▼
[5] connection      : MATCH_NOTIFICATION 핸들러 → 해당 유저 WS 세션에 push
        "매칭 성사! + channelId" → 클라가 채팅방 오픈
```
**포인트**: 사용자 알림(match-fanout)은 **channelId가 채워진 [3] 이후**에만 발사 → 알림에 channelId 포함(채팅방 바로 열림). at-least-once라 [2][3][5] 모두 **멱등**(matchId/matchId/중복 push 무시)이어야 함.

---

## 5. 서비스별 작업 목록

> 상태: **A~E 전부 구현·검증 완료** (2026-08-06). 아래는 실제 산출물 기준.

### A. matching-service (신규) ✅
- 엔티티: `LikeEntity`(복합PK), `MatchEntity`(**UUID v7** PK, UNIQUE lo/hi), `OutboxEventEntity`(social.outbox 매핑)
- `POST /api/likes` → 검증 → like-relay 발행(key=pair) → 202
- like-relay 컨슈머(판정 `LikeJudgeService`): UPSERT → 역방향 조회 → matches+outbox ⟨tx⟩
- channel-created 컨슈머(`MatchSagaService`): 백필 + match-fanout outbox ⟨tx⟩
- `GET /api/matches`(`MatchQueryService`) — ACTIVE 목록. 상대 카드는 **Redis `profile:card:{userId}` MGET**로 조립(+channelId). 쿼리 2번(DB 1 + MGET 1)으로 N+1 없음
- `DELETE /api/matches/{matchId}`(`UnmatchService`) — status=UNMATCHED + **outbox(MATCH_UNMATCHED, dest=match-unmatched, key=matchId)** ⟨tx⟩ → message가 채널 CLOSED
- `GET /api/likes/received`(`ReceivedLikeQueryService`) — 매칭 이력 있는 상대 제외
- `GlobalExceptionHandler` — 400/401/403/404 매핑(기능 정의서의 에러 표 그대로)
- **Snowflake 불필요**: matchId 는 UUID v7(로컬 생성이라도 시간+랜덤으로 인스턴스 조율 없이 유일)
- build.gradle: web + spring-kafka + data-jpa + data-redis + eureka-client + validation + actuator

### B. message-service (확장) ✅
- `match-created` / `match-unmatched` 전용 @KafkaListener(**raw 파싱** — envelope 디스패처 미경유) + `MatchSagaService`
  - `onMatchCreated`: DIRECT 채널 + 멤버 생성 → CHANNEL_CREATED outbox ⟨tx⟩. 멱등 = 선조회 + `uk_channel_match_id` UNIQUE 2중
  - `onMatchUnmatched`: 해당 matchId 채널 **`status=CLOSED`**(멱등)
- **CLOSED 채널 전송 가드**: `MessageRelayHandler` 진입부에서 상태 확인 후 저장/발행 거부
- `ChannelEntity` + `type`/`matchId`/`status`, 팩토리 `direct(matchId)` / `of(title)`(=OPEN)
- 컨슈머 재시도 정책: **못 읽는 레코드는 ack 후 스킵**(재시도해도 성공 불가 · 파티션 점유 방지), 처리 실패는 재배달

### B-2. profile-service (확장 — 카드 캐시) ✅
- **Redis `profile:card:{userId}`** 갱신 (`ProfileCardCacheService` + `ProfileCardRedisRepository`)
  - 스키마는 공용 모듈 `com.social.common.ProfileCard` — 생산자(profile)/소비자(matching)가 같은 레코드를 공유해 드리프트 차단
  - 갱신 경로: 프로필 생성/수정 · 이미지 업로드/삭제/대표변경/순서변경 · 프로필 삭제(남은 프로필로 재구성, 없으면 DEL)
  - 조립은 트랜잭션 안 · Redis 쓰기만 afterCommit(기존 tags/geo 패턴)

### C. fanout-delivery-service (확장) ✅
- `KafkaMessageType.MATCH_FANOUT` + `match-fanout` @KafkaListener + `MatchFanoutHandler`
- `ConnectionRouteResolver` 로 ws:user → instanceId 해석 로직을 **한 곳으로 추출**(message/read/match 3곳 중복 제거)
  - `ConnectionRoute(online, instanceIds)` — "오프라인"과 "stale 키만 남음"을 구분해야 미읽음 카운터를 잘못 올리지 않음
- connection-instance 토픽으로 `MATCH_NOTIFICATION` 발행
- 오프라인은 **스킵**(알림 유실 ≠ 매칭 유실 — F-M4 목록에 남아 있음)

### D. connection-service (확장) ✅
- `KafkaMessageType.MATCH_NOTIFICATION` + `MatchNotificationHandler` → 세션 push
- 클라 수신 형태: `{"type":"MATCH_NOTIFICATION","payload":{userId, matchId, channelId}}`

### E. infra ✅
- `create-topics.sh`에 5개 추가, Debezium 커넥터 무변경
- (버그 픽스) 커넥터에 `include.schema.changes=false` — auto-create 가 꺼진 환경에서 Debezium 이
  스키마 변경 이벤트를 `social`(=topic.prefix) 토픽에 쓰려다 프로듀서가 막혀 **모든 outbox 발행이 멈추던** 문제

---

## 6. API

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/likes` | `{toUserId, type}` → 202 (비동기 판정) |
| GET | `/api/matches` | 내 ACTIVE 매칭 목록(상대 카드 + channelId) |
| DELETE | `/api/matches/{matchId}` | 언매치(status=UNMATCHED) + 채널 비활성 |
| GET | `/api/likes/received` | 나를 좋아한 사람(선택) |

게이트웨이 라우트 추가: `Path=/api/likes/**,/api/matches/**` → `lb://matching-service`.

---

## 7. 정합성 요약 (데모/면접 스크립트)
1. `like-relay` **파티션 직렬화**(pair 키) → 쌍별 단일 작성자 → 락 없이 엇갈림 불가
2. `matches` + `outbox` **한 트랜잭션** → dual-write 어긋남 방지
3. `UNIQUE(lo,hi)` → 동시 스와이프 폭주에도 중복 매칭 0
4. outbox→Debezium **반드시 배달** + 소비자 **멱등** → 유실 0
5. Saga(MATCH_CREATED↔CHANNEL_CREATED)로 채널 백필 → 매칭과 채팅방이 따로 놀지 않음

## 8. 완료 기준 (P1 데모 시나리오)
- A↔B 서로 좋아요 → 정확히 **채팅방 1개** 생성, 양쪽 실시간 알림(+channelId), 채팅 시작
- 같은 ms 동시 스와이프 반복 → `matches` 중복 0, 유실 0
- matching 컨슈머/서버를 중간에 죽였다 살려도 결국 매칭·알림 도착 (멱등·재처리)

## 9. 검증 결과 (2026-08-06 실측)
- [x] `POST /api/likes` A→B → 202, 매칭 없음 / B→A → 202 → **matches 1행 + DIRECT 채널 1개 + 멤버 2명**
- [x] outbox MATCH_CREATED → `match-created` → CHANNEL_CREATED → `channel-created` → **channel_id 백필** → MATCH_FANOUT → `match-fanout` (전 구간 Debezium)
- [x] **WS 실수신**: 두 유저 동시 접속 상태에서 양쪽 모두
      `{"type":"MATCH_NOTIFICATION","payload":{userId,matchId,channelId:3}}` 수신 확인
- [x] **동시 스와이프 30건**(양방향 15쌍 병렬) → `matches` **1건**, DIRECT 채널 **1개** (중복 0)
- [x] 오프라인 수신자 → 알림 스킵 로그 + `GET /api/matches` 에는 정상 노출
- [x] 언매치 → matches UNMATCHED · 목록에서 제외 · **channel status=CLOSED** · 재요청 멱등(200)
- [x] **CLOSED 채널 전송 거부**: CLOSED 채널로 message-relay 투입 → 저장 안 됨 / ACTIVE 채널은 저장됨
- [x] 에러 매핑: 자기자신 400 · 헤더누락 401 · 남의 매칭 403 · 없는 matchId 404
- [x] 카드 캐시: 프로필 생성 즉시 `profile:card:{userId}` 채워지고 매칭 목록의 `partner` 에 반영(미스는 userId만)
- [ ] k6 대규모 부하(P4)

## 10. 결정 사항 (구현으로 확정)
- [x] **공용 outbox 재사용** — `social.outbox` 한 테이블 + EventRouter 라우팅. 커넥터 무변경으로 3개 토픽 추가 확인.
- [x] **saga 상관키 = `matchId`(UUID v7)** 단독. 모든 saga 이벤트의 `partition_key` 로도 사용 → 한 매칭의 이벤트는 순서 보장. `saga_id` 컬럼은 미사용.
- [x] **like-relay partitions=3 / concurrency=3** — 쌍별 직렬성 보장.
- [x] **Snowflake 불필요** — matchId 는 UUID v7. (message-service 의 Snowflake 는 messageId 용으로 그대로 둠)
- [x] **언매치 시 채널 → CLOSED** (match-unmatched → message-service 가 닫음 + 전송 가드) ✅
- [x] **F-M4 상대 카드 → Redis `profile:card:{userId}`** (profile-service 가 채움) ✅
- [x] **언매치 후 재매칭 skip** — 판정이 `findByUserLoIdAndUserHiId` 존재만 보고 끊으므로 UNMATCHED 쌍은 재매칭되지 않는다. 같은 이유로 F-M6 도 매칭 이력이 있는 상대를 상태 무관하게 제외한다.
- [x] **컨슈머 실패 정책** — 파싱 실패(재시도 무의미) = ack 스킵 / 처리 실패 = 재배달.

### 남은 선택 항목 (기능에 지장 없음)
- [ ] F-M4 커서 페이지네이션 (매칭 수가 커지면)
- [ ] `toUserId` 실존 유저 검증 (데모는 skip — 유령 like 는 매칭이 안 되므로 무해)
- [ ] OPEN 채널(카테고리/정원/방장) 도입 시 `channel` 잔여 컬럼 + `channel_members.role`
</content>
