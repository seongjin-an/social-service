# Social Discovery — 기능 정의서

> 개정 2026-07-18 · 짝: [`design.html`](./design.html) (아키텍처) · [`schema.sql`](./schema.sql) (테이블)
>
> 전제: 인증은 기존 user-service JWT 재사용 → 모든 `/api/**` 는 토큰에서 `currentUserId(UUID)` 를 꺼내 쓴다.
> 우선순위: **Phase 1(매칭 코어)이 데모의 심장** → 여기부터. Phase 0 은 그 토대.

---

## 🅿 우선순위 한눈에

| Phase | 서비스 | 한 줄 | 상태 |
|---|---|---|---|
| P0 | user-service | 프로필/위치/선호 확장 + 합성 시더 | 토대 |
| **P1 🔥** | matching · message · fanout | 좋아요→직렬 판정→매칭→Saga 채널→실시간 푸시 | **심장** |
| P2 | recommendation | geo 추천 피드 서빙 | |
| P3 | openchat(파사드) | 오픈방 탐색/생성/입장/presence | |
| P4 | (테스트) | k6 부하 + Grafana 정합성 증명 | |
| 옵션 | moderation | 차단/신고/필터 | |

---

## Phase 0 — 기반 · 시드  (user-service ♻️ · profile-service 🆕)

> 관심사 분리: user-service = `users`(계정) + `user_locations`(위치) · profile-service = `profile`/`tag`/`profile_tag`/`profile_image`(멀티프로필 1:N). (schema.sql 메모 G)

### F0-1. 프로필 확장 조회/수정
> ⚙️ profile-service(신규) 소유: `profile` / `profile_image` / `tag` / `profile_tag`. **멀티프로필(1:N)** — 한 유저가 프로필 여러 개.
- `POST /api/profiles` — 프로필 생성 `{ gender, birthday(yyyyMMdd), bio, tags[], prefGender, prefAgeMin, prefAgeMax, prefDistanceKm }` (X-User-Id 헤더)
- `GET  /api/profiles/me` — 내 프로필 목록(멀티) · `GET /api/profiles/{profileId}` — 상세
- 핵심: `birthday`는 DATE(일까지) → 정확한 나이/만18세. 사진은 `profile_image`(스토리지+URL), 관심사는 `tag`+`profile_tag`(F0-5).

### F0-5. 관심사 태그
- `GET  /api/tags?q={keyword}` — 피커용 태그 목록(고정 어휘)
- 저장: 프로필 저장 시 `profile_tag` 동기화 **+ Redis SET `tags:{profileId}` 갱신 + `tag.usage_count` 증감**
- 핵심: 자유입력 지양 → 정규화(`normalized_name`)로 "Java"="java" 통제해야 겹침 계산 유지. 서빙 랭킹은 Redis `SINTERCARD`.
- ⚠️ 구현 주의(재검증): 요청 태그 `distinct` 로 중복 제거 · 태그 get-or-create 레이스는 `save` 실패 시 `findByNormalizedName` 재조회로 흡수.

### F0-2. 위치 업데이트
- `PUT  /api/users/me/location` — `{ lat, lng }`
- 핵심: **`user_locations` UPSERT(영속 백업) + Redis `GEOADD geo:users {userId} {lng} {lat}`(실시간 권위)** 를 함께(멱등).
  → 위치는 전용 테이블이라 계정/프로필 row 를 안 건드린다. 이 한 방이 P2 추천 반경검색의 입력.

### F0-3. 선호(preference) 설정
- `PUT  /api/users/me/preferences` — `{ prefGender, prefAgeMin, prefAgeMax, prefDistanceKm }` → `profiles` UPSERT
- 핵심: `profiles` 의 pref_* 컬럼. P2 추천 필터의 기준값.

### F0-4. 합성 프로필 시더  ★테스트 토대★
- 스크립트/부트스트랩으로 **수만 명** 생성: 랜덤 위치(특정 도시 반경 분포)·나이·성별·관심사.
- 생성 시 `geo:users` 에도 함께 적재.
- 목적: P2 추천·P4 부하테스트가 돌아갈 모수 확보.

---

## Phase 1 — 매칭 코어  🔥  (matching 🆕 · message ♻️ · fanout ♻️)

> 데모의 클라이맥스. "동시에 눌러도 / 죽어도 / Kafka 흔들려도 매칭·알림 안 어긋난다"를 코드로 증명.

### F1-1. 좋아요/패스 접수  (matching-service)
- `POST /api/likes` — `{ toUserId, type: LIKE|PASS|SUPER }` → **202 Accepted** (즉시 응답)
- 로직: 검증 후 **`like-relay` 발행**하고 끝. 파티션 키 = `pair(a,b) = min(a,b)+":"+max(a,b)` 해시.
  → A→B, B→A 가 항상 같은 파티션 → 같은 컨슈머 스레드.
- 정합성 포인트: 판정은 여기서 안 한다(비동기 디커플링). 사용자는 안 기다림.

### F1-2. 매칭 판정 컨슈머  ★심장★  (matching-service, `like-relay` 구독, 단일 스레드 직렬)
1. `likes` UPSERT — PK(from,to) 로 **중복 좋아요 멱등 흡수**
2. `type in (LIKE,SUPER)` 일 때만 역방향 조회:
   `SELECT ... FROM likes WHERE from_user_id=상대 AND to_user_id=나 AND type IN ('LIKE','SUPER')`
3. 역방향 있으면 → **한 트랜잭션**:
   - `matches` INSERT (`user_lo/hi` 정렬, `channel_id=NULL`, `status=ACTIVE`) — `UNIQUE(lo,hi)` 로 중복매칭 차단
   - `outbox` INSERT (`event=MATCH_CREATED`, `saga_id=새 UUID`, `saga_type=MATCH`, payload=`{matchId, recipients:[A,B]}`)
- 정합성 포인트: **직렬화(엇갈림 방지) + UNIQUE(중복 방지)** 이중 안전. PASS 는 저장만 하고 매칭 판정 제외.

### F1-3. 매칭 이벤트 발행  (matching-service, outbox)
- **Poller** 가 `outbox` PENDING 스캔 → `match-fanout`(key=matchId) 발행 → PROCESSED 마킹.
  (message-service 의 OutboxEventPoller 패턴 그대로 복붙. CDC 전환은 P4에서 재검토)

### F1-4. Saga — DIRECT 채널 생성/백필  ★가장 헷갈리는 부분★
- **[2] message-service** (`MATCH_CREATED` 구독):
  - `channel`(type=DIRECT) 생성 + `channel_members`(A,B) 생성
  - **멱등**: saga_id(or matchId)로 기존 채널 조회 → 없을 때만 생성 (재수신 대비)
  - `CHANNEL_CREATED`(saga_id 유지, `channelId`) 발행 (message 쪽 outbox 재사용)
- **[3] matching-service** (`CHANNEL_CREATED` 구독):
  - saga_id 로 `matches.channel_id` 백필. 이미 차 있으면 no-op(멱등).

### F1-5. 실시간 매칭 알림  (fanout-delivery-service, `match-fanout` 구독)
- Redis `ws:user` 조회 → 양쪽 접속 인스턴스(`connection-instance-{id}`)로 푸시
- 내용: "매칭 성사!" + `channelId`(백필된) → 클라가 채팅방 오픈
- 정합성 포인트: at-least-once → 클라/서버 모두 같은 matchId 중복 푸시 무시 가능해야.

### F1-6. 매칭 목록 / 언매치  (matching-service)
- `GET    /api/matches` — 내 ACTIVE 매칭 목록 (상대 프로필 카드 + channel_id)
- `DELETE /api/matches/{matchId}` — 언매치(`status=UNMATCHED`) + 채널 비활성 처리
- `GET    /api/likes/received` — 나를 좋아한 사람 (선택 기능, `idx_likes_to_user` 활용)

### ✅ P1 완료 기준 (데모 시나리오)
- A↔B 서로 좋아요 → 정확히 **채팅방 1개** 생성, 양쪽에 알림, 채팅 시작.
- 같은 ms 동시 스와이프 반복해도 `matches` 중복 0, 유실 0.
- matching 컨슈머/서버를 중간에 죽였다 살려도 결국 매칭·알림 도착.

---

## Phase 2 — 추천 피드  (recommendation-service 🆕)

### F2-1. 추천 후보 서빙
- `GET /api/feed?cursor={cursor}&size={n}` — 스와이프 후보 목록(프로필 카드 배열 + 다음 커서)
- 파이프라인:
  1. **반경검색**: `GEOSEARCH geo:users FROMMEMBER {me} BYRADIUS {prefDistanceKm}` → 후보 userId
  2. **필터**: 성별(prefGender)·나이(prefAge min/max)·본인 제외·이미 매칭/차단 제외
  3. **seen 제외**: `seen:{me}`(Bloom/Set) 에 있는 사람 컷
  4. **랭킹**: 거리·**관심사 겹침 수(Redis `SINTERCARD tags:{me} tags:{cand}`)**·활동성 등 가중 스코어 정렬
  5. **커서 페이지네이션**으로 잘라 응답

### F2-2. feed 큐 캐시 (선택, 성능)
- 후보를 미리 계산해 `feed:{userId}`(List) 에 적재 → 요청 시 pop 서빙(콜드 계산 회피).
- 갱신: 위치/선호 변경 시 or 큐 소진 임박 시 재계산.

### F2-3. seen 기록
- 카드 노출 or 좋아요/패스 시 `seen:{me}` 에 상대 추가 → 다음 피드에서 제외.

### ✅ P2 완료 기준
- 반경/필터/본 사람 제외가 반영된 피드가 커서로 끊김 없이 내려온다.
- 좋아요 누른 사람은 다시 안 뜬다.

---

## Phase 3 — 오픈채팅  (openchat-service 🆕 · 소유 테이블 없음 · 파사드)

> `channel`(type=OPEN)·`channel_members`·`message`·fanout 재활용. openchat 은 탐색/오케스트레이션만.
> ⚠️ 소유 테이블이 없어 "별도 서비스 vs message-service 흡수"는 구현하며 재판단(schema.sql 메모 F).

### F3-1. 방 생성
- `POST /api/rooms` — `{ title, category, maxMembers }`
- 로직: `channel`(type=OPEN, owner_id=me, member_count=1, status=ACTIVE) 생성 + `channel_members`(me, role=OWNER).

### F3-2. 방 탐색/검색
- `GET /api/rooms?category={c}&q={keyword}&cursor={cursor}` — 방 목록
- 로직: `channel WHERE type='OPEN' AND status='ACTIVE'` (+category, +title fulltext) → `idx_channel_open_browse` 활용.

### F3-3. 입장 / 퇴장
- `POST /api/rooms/{channelId}/join` — `channel_members`(role=MEMBER) 멱등 추가 + `member_count` 증가(+정원 체크)
- `POST /api/rooms/{channelId}/leave` — 멤버 삭제 + `member_count` 감소
- 정합성 포인트: `member_count` 는 캐시 → 경합 시 틀어질 수 있음. **실시간 정확한 수는 Redis presence**로.

### F3-4. presence (접속자 수)
- `GET /api/rooms/{channelId}/presence`
- 로직: WS 연결/해제 시 Redis presence(SET/HLL) 갱신 → 현재 접속자 수 조회.

### F3-5. 대규모 방 fanout
- 오픈방 메시지도 기존 message-relay/fanout 파이프라인 재사용.
- 인기 방(수천 명) fanout 시 hot key/백프레셔가 P4 부하 스토리의 한 축.

### ✅ P3 완료 기준
- 방 만들고 → 목록/카테고리/검색으로 찾고 → 들어가서 → 여럿이 실시간 대화 + 접속자 수 표시.

---

## Phase 4 — 부하 · 정합성 증명  (테스트/관측)

### F4-1. 동시 스와이프 폭주 → 중복 매칭 0
- k6: 대량 유저가 서로를 같은 순간 좋아요 → `matches` 중복/유실 0 그래프.

### F4-2. 인기 유저 hot key
- 한 인기 유저에게 좋아요 집중 → 파티션 편중/백프레셔를 Kafka가 흡수하는지 관측.

### F4-3. 추천 서빙 부하
- `/api/feed` 대량 호출 → geo/랭킹/캐시 지연·처리량.

### F4-4. 대규모 오픈방 fanout
- 수천 명 방에 메시지 폭주 → fanout 지연·드롭 여부.

### F4-5. Grafana 대시보드
- 매칭 성사율·중복 0·컨슈머 랙·outbox PENDING 적체·fanout 지연 시각화.

---

## 옵션 — moderation
- 차단(block): 차단 시 추천/매칭/방에서 상호 제외.
- 신고(report): 사유 저장 → 관리 큐.
- 필터링: 금칙어·이미지(placeholder 단계선 skip 가능).

---

## 🔗 정합성 요약 (면접/데모 스크립트)
1. `like-relay` **파티션 직렬화** → 락 없이 쌍별 단일 작성자 → 엇갈림 불가 (정합성)
2. `matches`+`outbox` **한 트랜잭션** → dual-write 어긋남 방지 (원자성)
3. `outbox`→`match-fanout` **반드시 배달**(Poller/CDC) + 소비자 멱등 → 유실 방지 (신뢰성)
4. Saga(MATCH_CREATED↔CHANNEL_CREATED)로 채널 백필 → 매칭과 채팅방이 따로 놀지 않음
