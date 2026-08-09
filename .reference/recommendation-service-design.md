# Recommendation Service (P2) — 설계 문서

> 개정 2026-08-09 · 짝: [`recommendation-service-spec.md`](./recommendation-service-spec.md)(WHAT) · [`features.md`](./features.md)(F2-*)
>
> **범위**: P2 추천 피드. **recommendation-service(신규) + profile-service(캐시 확장)** 2곳이 물리는 작업이다.

---

## 0. 확정 전제

- **소유 테이블이 없는 서비스다.** 입력은 Redis 캐시(다른 서비스가 채운 것)와 Kafka 뿐이다.
  → `spring-boot-starter-data-jpa` / mysql-connector 를 넣지 않는다(넣으면 DataSource 없다고 기동 실패).
  → 이벤트를 소비만 하므로 `KafkaTemplate` 빈도 두지 않는다.
- **`scanBasePackages = "com.social"` 필수** — 공용 빈(JsonUtil 등)이 `com.social.common` 에 있다.
  기본 스캔 범위로는 닿지 않아 기동이 실패한다(컴파일로는 안 잡히는 종류의 실수).
- **식별자 = userId(UUID)**. 아래 §1 참조.

---

## 1. 식별자 축 통일 (선행 작업)

작업 전 상태는 축이 갈려 있었다.

| 자산 | 멤버/키 | 소유 |
|---|---|---|
| `geo:users` | ~~profileId~~ → **userId** | profile |
| `tags:{profileId}` | profileId | profile |
| `profile:card:{userId}` | userId | profile |
| `likes` / `matches` | userId | matching |

추천 파이프라인은 GEOSEARCH 로 시작해 카드·좋아요로 끝나므로, geo 만 profileId 면
**추천에서만 profileId → userId 매핑 조회가 영구히 따라붙는다.**

→ **`geo:users` 멤버를 userId 로 전환**했다. 위치는 사람 단위 속성이고(활성 프로필 개념도 아직 없다)
나머지가 전부 userId 축이기 때문이다.

- 백업 테이블 `profile_location` 은 프로필당 1:1 이라 PK 는 profileId 그대로다.
  즉 **DB 는 프로필 단위 / 인덱스는 사람 단위**이고, 축 변환은 `GeoCacheService` 한 곳에 모았다.
- 프로필 삭제 시 무조건 ZREM 하면 멀티프로필 유저가 프로필 하나 지웠다고 반경검색에서 통째로 사라진다
  → **남은 프로필 좌표로 재구성**하고, 남은 게 없거나 좌표가 없을 때만 제거한다(카드 캐시와 같은 규칙).
- 기존 인덱스에 남은 profileId 멤버는 카드 조회에서 걸러지므로 무해하다. 깔끔히 지우려면
  `DEL geo:users` 후 각자 위치를 한 번 갱신하면 재구성된다(권위는 `profile_location`).

`tags:{profileId}` 는 그대로 뒀다 — 아래 §3 에서 쓰지 않기로 했기 때문이다.

---

## 2. Redis 키 지도

| 키 | 타입 | 생산자 | 소비자 | 용도 |
|---|---|---|---|---|
| `geo:users` | GEO | profile | recommendation | 반경검색(멤버=userId) |
| `profile:card:{userId}` | STRING(JSON) | profile | matching, recommendation | 카드 + **필터/랭킹 입력** |
| `profile:pref:{userId}` | STRING(JSON) | profile | recommendation | 내 선호(반경·성별·나이) |
| `seen:{userId}` | SET | **recommendation** | recommendation | 이미 판단한 상대 |
| `feed:{userId}` | LIST | **recommendation** | recommendation | 후보 스냅샷(랭킹 순) |
| `feed:ver:{userId}` | STRING | **recommendation** | recommendation | 스냅샷 버전(커서 판정) |

키 접두사는 전부 공용 `KeyPrefix` 상수다 — 생산자/소비자가 다른 서비스라 문자열이 어긋나면
조용히 미스가 된다.

**선호값을 카드와 분리한 이유**: 카드는 *남에게 보여주는* 정보라 MGET 으로 통째로 뿌려진다.
선호는 *본인만 쓰는* 필터 입력이라 같은 값에 섞으면 남의 선호가 함께 노출된다.

**`ProfileCard` / `ProfilePreference` 는 `libs/common` 에 둔다** — 생산자(profile)와 소비자
(matching/recommendation)가 같은 레코드를 공유해 필드 드리프트를 컴파일 타임에 막는다.

---

## 3. 흐름 — 피드 조회

```
GET /api/feed?cursor=&size=   (X-User-Id)
  │
  ├─ cursor 없음 / 버전 불일치 → 스냅샷 재계산
  │     1) GET  profile:pref:{me}                        선호(없으면 제약 없음)
  │     2) GEOSEARCH geo:users FROMMEMBER {me}
  │              BYRADIUS {반경} km ASC COUNT {상한} WITHDIST
  │     3) 본인 제외
  │     4) SMISMEMBER seen:{me} ...                      이미 본 사람 제외  ★카드 조회 전에★
  │     5) MGET profile:card:{후보...}                   카드 일괄 조회
  │     6) 성별/나이 필터 + 점수 계산 + 정렬
  │     7) DEL+RPUSH feed:{me} / SET feed:ver:{me}       스냅샷 교체
  │
  └─ LRANGE feed:{me} offset..offset+size-1
        → 서빙 시점 seen 재필터 + 카드 재조회 → items + nextCursor
```

**Redis 왕복은 후보 수와 무관하게 고정 6회다.** 후보 1명이든 500명이든 같다.

### 왜 SINTERCARD 를 안 쓰나
`features.md` 는 랭킹의 태그 겹침을 `SINTERCARD tags:{me} tags:{cand}` 로 제안했지만 쓰지 않았다.
카드를 이미 MGET 했고 카드 안에 `tags` 가 들어 있으므로 겹침은 **메모리에서** 계산된다.
SINTERCARD 는 후보 수만큼 왕복이 늘어난다(500명이면 +500회).

### 왜 DB 를 안 보나
필터 입력(나이·성별)과 랭킹 입력(태그)이 모두 카드 캐시에 있다. 덕분에 조회 경로에
**서비스 간 동기 의존이 없다**(profile-service 가 죽어도 캐시가 살아 있으면 피드는 나간다).

### 순서가 중요한 지점
`seen` 제외를 **카드 MGET 앞에** 둔다. 이미 스와이프한 사람의 카드를 가져올 이유가 없고,
오래 쓴 유저일수록 그 비중이 크다.

### 랭킹식
```
score = 0.6 × (1 − 거리/반경) + 0.4 × min(겹친태그수 / 3, 1)
```
가중치·포화값은 전부 설정(`feed.ranking.*`)이다. 절대값은 의미 없고 상대 순서만 쓴다.
동점이면 가까운 순, 그래도 같으면 userId 순(결정적 정렬).

---

## 4. 커서 설계

**문제**: 매 요청 재계산하면 그 사이 누가 위치를 옮기거나 카드를 고쳐 점수가 흔들린다.
그러면 2페이지에서 같은 사람이 또 나오거나 누군가 건너뛰어진다.

**해법**: 한 번 계산한 순서를 `feed:{userId}` LIST 에 고정하고 그 안을 offset 으로 훑는다.
(= F2-2 의 "미리 계산해 둔 feed 큐"와 같은 자산 — 콜드 계산 회피 효과도 같이 얻는다.)

- **커서** = `base64url("{스냅샷버전}:{offset}")` — 불투명하게 만들어 클라이언트가 offset 을
  직접 만들어 쓰지 못하게 한다(형식은 서버 구현 사항이다).
- **버전이 필요한 이유**: 스냅샷이 TTL 로 사라진 뒤 재계산되면 같은 offset 이 전혀 다른 사람을
  가리킨다. 버전이 어긋난 커서는 무효로 보고 처음부터 다시 준다.
- **깨진 커서도 400 이 아니라 첫 페이지**로 처리한다.
- **카드는 스냅샷에 담지 않는다.** 스냅샷은 "누구를 어떤 순서로"만 고정하고, 보여줄 내용은 서빙
  시점에 다시 읽는다(사진을 바꾼 사람이 옛 사진으로 노출되면 안 된다).
- **거리는 스냅샷에 담는다** (`{userId}:{km}`). 응답의 "3.4km" 때문에 GEODIST 를 후보 수만큼
  다시 때리지 않기 위함이다. UUID 에는 `:` 가 없어 파싱이 안전하다.
- **빈 페이지 방어**: 서빙 시점 필터(그 사이 좋아요를 눌렀거나 프로필이 삭제된 경우)로 페이지가
  통째로 비면 다음 구간까지 훑어 채운다(최대 5회). 빈 배열 + 커서를 주면 클라이언트가 "끝"으로 읽는다.

---

## 5. seen — 왜 이벤트인가

matching 이 이미 `like-relay` 를 발행하고 거기에 필요한 정보가 다 있다. 선택지는 셋이었다.

| 안 | 문제 |
|---|---|
| 클라이언트가 별도 API 호출 | 좋아요 때마다 두 번 호출 — 빠뜨리면 조용히 깨진다 |
| matching 이 `seen:` 키에 직접 SADD | 남의 서비스 소유 키를 직접 쓴다 |
| **like-relay 구독** ✅ | 컨슈머 그룹만 다르면 됨. 클라이언트 변경 없음 |

- LIKE/PASS/SUPER 를 구분하지 않으므로 `type` 을 String 으로 받는다(타입이 추가돼도 안 깨진다).
- 조회는 `SMISMEMBER` 한 번 — `SMEMBERS` 로 전량을 끌어오면 오래 쓴 유저의 seen(수천 건)이 그대로 온다.
- SADD 라 멱등 → 재배달·중복 요청에 안전. 활동 시 TTL 을 갱신해 쓰는 유저 기록이 만료되지 않게 했다.
- **부수 효과**: 매칭은 서로 좋아요를 눌러야 성립하므로 매칭된 상대는 반드시 내 seen 에 있다
  → "이미 매칭된 상대 제외"에 별도 조회가 필요 없다.

---

## 6. 서비스별 작업 목록

### A. recommendation-service (신규) ✅
- 포트 8087 · 게이트웨이 라우트 `/api/feed/**` · 실행 스크립트 6종 등록
- `GET /api/feed` (`FeedQueryService` + `FeedSnapshotService`) · `POST /api/feed/seen`
- `like-relay` 컨슈머(`KafkaConsumer` → `SeenService`)
- 읽기 전용 리포지토리 4종(geo / card / pref / seen) + 스냅샷 리포지토리
- `GlobalExceptionHandler` — matching 과 같은 매핑(헤더 누락 401)
- build.gradle: web + data-redis + spring-kafka + eureka-client + validation + actuator (**JPA 없음**)

### B. profile-service (확장) ✅
- `geo:users` 멤버 축 전환 + `GeoCacheService` 로 afterCommit/삭제 규칙 집약
- `profile:pref:{userId}` 신설(`ProfilePreferenceCacheService` + 리포지토리)
  - 갱신 시점은 프로필 생성/수정/삭제뿐 — 이미지 변경은 선호값과 무관
- 공용 `ProfilePreference` 레코드 + `KeyPrefix` 상수(pref/seen/feed)

### C. 곁가지 수정 ✅
- matching-service 에 `spring.data.redis` 누락 — 카드 MGET 을 하는데 설정이 없어 `REDIS_HOST` 가 무시되고 있었다
- Debezium 커넥터가 비캡처 테이블(`social.channel`)의 ALTER 파싱에서 죽어 **outbox 발행이 전부 멈춰 있던** 문제
  → `skip.unparseable.ddl=true`(유실 없이 위치 유지). 증상 차단이므로 캡처 대상을 늘릴 때 재검토 필요.

---

## 7. API

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/feed?cursor=&size=` | 스와이프 후보 + 다음 커서 |
| POST | `/api/feed/seen` | `{userIds:[...]}` 노출 기록 |

---

## 8. 검증 결과 (2026-08-09 실측)

프로필 5명(A=나, B~E)을 서울시청 기준 1~3km 에 배치해 실제 기동 검증.

- [x] `geo:users` 멤버가 전부 **userId** — `GEODIST A→B 3.0031km · A→C 1.0011km`
- [x] 프로필 생성 즉시 `profile:card:{userId}` / `profile:pref:{userId}` 채워짐
- [x] **랭킹 역전 확인**: B(3.0km·태그 2개 겹침, 0.687) > C(1.0km·0개, 0.540)
      → 거리만으로 정렬되지 않는다는 걸 실측으로 확인
- [x] 필터: D(MALE) 성별 제외 · E(16세) 나이 제외
- [x] `sharedTags: ["러닝","여행"]` 응답 포함
- [x] 커서: `size=1` → 1p B / 2p C / `nextCursor: null`
- [x] 위조 커서(`deadbeef:1`)·깨진 커서 → 400 아니라 첫 페이지 재발급(로그 `커서 무효(스냅샷 교체됨)`)
- [x] **seen 전 구간**: `POST /api/likes` A→B(202) → like-relay → `seen:A` 적재(TTL 90일)
      → 피드 재조회 시 B 사라짐. 파이프라인 로그 `검색=4, seen제외=4, 최종=2` → `seen제외=3, 최종=1` → `최종=0`
- [x] 노출 기록 API 로 C 추가 → 빈 피드
- [x] 엣지: 위치 없는 유저 빈 피드(200) · 헤더 누락 401 · `userIds:[]` 400
- [x] (곁가지) Debezium 복구 후 새 매칭 1건이 match-created → channel-created → match-fanout 전 구간 발행,
      DIRECT 채널 자동 생성 확인
- [ ] k6 대규모 부하(P4) — 인기 지역 hot key, 반경 넓은 유저의 계산량

---

## 9. 성능 메모

- 왕복 6회 고정. 후보 상한(`feed.candidate-limit`, 기본 500)이 MGET 크기를 묶는다.
- 반경 상한(200km)이 필요한 이유: **반경이 곧 후보 수이고 후보 수가 곧 계산량**이다.
- 스냅샷 TTL 10분 — 짧으면 커서가 자주 무효화되고, 길면 새로 들어온 후보가 늦게 보인다.
- 지금은 요청 시 계산이다. 부하가 커지면 위치/선호 변경 이벤트로 **사전 계산**하는 게 다음 수순
  (스냅샷 자산이 이미 있으므로 트리거만 붙이면 된다).
