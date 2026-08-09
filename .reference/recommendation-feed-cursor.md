# 추천 피드 — 커서·버전·스냅샷 동작 설명

> 작성 2026-08-09 · 짝: [`recommendation-service-design.md`](./recommendation-service-design.md)(설계 전반) · [`recommendation-service-spec.md`](./recommendation-service-spec.md)(기능 규칙)
>
> 이 문서는 **커서 페이지네이션이 실제로 어떻게 도는지**를 실측 값과 캐시 키로 따라가며 설명한다.
> 아래 값은 전부 2026-08-09 로컬 기동에서 실제로 찍은 것이다(꾸며낸 예시 아님).

---

## 0. 세 단어의 역할

| 이름 | 정체 | 한 줄 |
|---|---|---|
| **스냅샷** | `feed:{userId}` LIST | 계산한 후보 순서를 고정해 둔 것 |
| **커서** | `base64url("{버전}:{offset}")` | "여기까지 봤다"는 책갈피 |
| **버전** | `feed:ver:{userId}` STRING | 그 책갈피가 아직 유효한지 판별하는 표식 |

**목적은 스냅샷을 보관하는 것 자체가 아니라, 페이지를 넘길 때 같은 사람이 또 나오거나
누군가 건너뛰어지지 않게 하는 것이다.** 스냅샷은 그 수단이다.

### 스냅샷이 없으면 깨지는 이유

매 요청 다시 계산하면 이렇게 된다. A 가 `size=1` 로 넘긴다고 하자.

```
1페이지 요청 → 계산: [B(0.687), C(0.540)] → B 서빙, "1개 봤음" 기록
   ↓ 이 사이에 C 가 A 쪽으로 1km 더 이동 (거리 점수 상승)
2페이지 요청 → 재계산: [C(0.610), B(0.587)] → "1개 봤으니 2번째" → B 서빙
```

**B 를 두 번 보고 C 는 못 봤다.** 점수가 흔들려 목록 순서가 바뀌었는데 위치("2번째")는 그대로였기
때문이다. **위치는 목록이 고정돼 있을 때만 의미가 있다.**

---

## 1. 등장하는 키 5개

```
profile-service 가 쓰고 → recommendation 은 읽기만
  geo:users                  ZSET/GEO   멤버 = userId, 좌표
  profile:card:{userId}      STRING     카드 (나이·성별·태그·사진)
  profile:pref:{userId}      STRING     내 필터 조건 (반경·성별·나이)

recommendation 소유 (쓰고 읽음)
  seen:{userId}              SET        이미 판단한 상대
  feed:{userId}              LIST       후보 스냅샷 (랭킹 순)
  feed:ver:{userId}          STRING     그 스냅샷의 "판"
```

---

## 2. 실측 트레이스

등장인물 — 서울시청 기준으로 배치한 테스트 계정 5명.

| | userId | 성별/나이 | 태그 | A 로부터 거리 |
|---|---|---|---|---|
| **A** | `aaaaaaaa…` | MALE / 32 | 러닝, 여행 | 기준점 (나) |
| B | `bbbbbbbb…` | FEMALE / 28 | 러닝, 여행, 커피 | 3.0km |
| C | `cccccccc…` | FEMALE / 30 | 게임 | 1.0km |
| D | `dddddddd…` | MALE | 러닝 | 2.0km |
| E | `eeeeeeee…` | FEMALE / 16 | 러닝, 여행 | 2.5km |

A 의 선호: `FEMALE`, 20~40세, 10km.

### STEP 0 — 계산 전, 입력만 있는 상태

```
ZRANGE geo:users 0 -1
  aaaaaaaa…  cccccccc…  dddddddd…  eeeeeeee…  bbbbbbbb…      ← 멤버가 전부 userId

GET profile:pref:aaaaaaaa…
  {"userId":"aaaaaaaa…","prefGender":"FEMALE","prefAgeMin":20,"prefAgeMax":40,"prefDistanceKm":10}

GET profile:card:bbbbbbbb…
  {"userId":"bbbbbbbb…","age":28,"gender":"FEMALE","tags":["러닝","여행","커피"],"imageUrl":null}

EXISTS seen:aaaaaaaa…      → 0
EXISTS feed:aaaaaaaa…      → 0        아직 스냅샷 없음
EXISTS feed:ver:aaaaaaaa…  → 0
```

### STEP 1 — 첫 요청 `GET /api/feed?size=1` (커서 없음 → 계산)

서버 로그가 깔때기를 그대로 보여준다.

```
[Feed] 스냅샷 재계산: 반경=10.0km, 검색=4, seen제외=4, 최종=2, ver=f2eb337c
                             ↑         ↑           ↑
                   GEOSEARCH 4명   seen 통과 4명   성별·나이 필터 후 2명 (D=성별, E=나이 탈락)
```

계산이 끝나면 **키 두 개가 새로 생긴다.**

```
LRANGE feed:aaaaaaaa… 0 -1
  [0] bbbbbbbb-0000-4000-8000-000000000002:3.00     ← 랭킹 1위 (0.687)
  [1] cccccccc-0000-4000-8000-000000000003:1.00     ← 랭킹 2위 (0.540)

GET feed:ver:aaaaaaaa…  →  f2eb337c
TTL feed:aaaaaaaa…      →  599초
```

원소가 `{userId}:{거리km}` 인 이유: 응답의 `distanceKm` 를 위해 GEODIST 를 후보 수만큼
다시 때리지 않으려고 거리를 함께 담는다. UUID 에는 `:` 가 없어 파싱이 안전하다.

> **랭킹이 거리순이 아니다.** B 는 C 보다 3배 멀지만 관심사가 2개 겹쳐서 위에 온다.
> `0.6 × (1 − 3.0/10) + 0.4 × min(2/3, 1) = 0.687` vs `0.6 × (1 − 1.0/10) + 0 = 0.540`

`size=1` 이므로 `[0]` 만 서빙하고 커서를 발급한다.

```
응답: items=[bbbbbbbb… · 3.0km · sharedTags=["러닝","여행"]]
      nextCursor = "ZjJlYjMzN2M6MQ"
         ↓ base64url 해독
      "f2eb337c:1"
          ▲        ▲
          │        └─ offset = 1     지금까지 서빙한 인원수
          └─ version = f2eb337c      feed:ver 값과 동일
```

### STEP 2 — 커서로 2페이지 (버전 일치 → 이어 읽기)

```
커서의 version  : f2eb337c
서버의 feed:ver : f2eb337c        → 일치
```

일치하므로 **재계산 없이** `LRANGE feed:{A} 1 1` 만 한다.

```
응답: items=[cccccccc… · 1.0km]
      nextCursor = null            스냅샷 2명을 다 소비 → 끝
요청 후 feed:ver = f2eb337c        그대로 (건드리지 않음)
```

여기가 성능 이득 지점이다. GEOSEARCH·MGET·필터·정렬을 전부 건너뛰고 `LRANGE` 한 번이다.

### STEP 3 — 스냅샷이 만료되면 (핵심)

TTL 10분을 기다리는 대신 `DEL feed:ver:{A}` 로 같은 상황을 만들었다.

```
EXISTS feed:ver:aaaaaaaa…  →  0        판이 사라짐
```

이 상태에서 **STEP 1 에서 받았던 옛 커서 `f2eb337c:1` 을 그대로** 다시 낸다.

```
응답: items=[bbbbbbbb…]        ← offset=1 이었는데 0번째 B 가 나왔다 = 처음부터
      nextCursor = "NzI1ZWU4Yjg6MQ" → "725ee8b8:1"      version 새로 발급

[Feed] 커서 무효(스냅샷 교체됨) — 처음부터 다시: userId=aaaaaaaa…
[Feed] 스냅샷 재계산: … ver=725ee8b8
```

**버전이 없었다면** 서버는 `offset=1` 만 보고 새 목록의 1번을 줬을 것이다. 새 목록의 1번이
누구인지는 아무도 보장 못 한다 — 그 사이 누가 이사 왔으면 전혀 다른 사람이 나가고 B 는 영영 안 보인다.

버전은 **"이 책갈피가 가리키던 책이 아직 그 책인가"** 를 묻는 한 줄이다.
책갈피에 "47쪽"만 적으면 책이 새로 조판됐을 때 무의미하다. 그래서 **"3판 47쪽"** 처럼 판을 함께 적는다.

### STEP 4 — seen 이 끼어들면

```
SMEMBERS seen:aaaaaaaa…                                (비어있음)
   ↓ POST /api/feed/seen {"userIds":["cccccccc…"]}
SMEMBERS seen:aaaaaaaa…  →  cccccccc-0000-4000-8000-000000000003
```

새로고침하면 재계산 단계에서 C 가 **아예 후보에서 빠진다.**

```
[Feed] 스냅샷 재계산: 검색=4, seen제외=3, 최종=1, ver=1a58355d
                             ↑ 4명 중 C 가 빠져 3명
LRANGE feed:aaaaaaaa… 0 -1
  [0] bbbbbbbb-0000-4000-8000-000000000002:3.00        C 가 사라진 스냅샷
```

### 전체 그림

```
                 ┌─ GET  profile:pref:{A}         내 조건
                 ├─ GEOSEARCH geo:users …         반경 안 후보
커서 없음 ──계산──┼─ SMISMEMBER seen:{A} …         이미 본 사람 제거  ★카드 조회 전에★
                 ├─ MGET profile:card:{후보들}    카드 일괄 (필터·랭킹 입력)
                 └─ RPUSH feed:{A} / SET feed:ver:{A}       순서 고정 + 판 발급
                                    │
커서 있음 ──버전 비교──┬─ 같다 ──▶ LRANGE feed:{A} offset..    (계산 건너뜀)
                       └─ 다르다 ─▶ 위 계산을 처음부터
```

**카드 내용은 스냅샷에 없다.** `feed:{A}` 에는 "누구를 어떤 순서로"(+거리)만 있고, 이름·사진·나이는
서빙할 때 `profile:card` 에서 새로 읽는다. 순서는 고정하되 내용은 항상 최신이어야 하기 때문이다
(사진을 바꾼 사람이 옛 사진으로 나가면 안 된다).

---

## 3. 커서가 만료된 뒤 클라이언트가 그 커서를 다시 보내면?

**스냅샷을 다시 만들고 새 커서를 발급한다.** (커서를 다시 계산하는 게 아니라, 커서는 계산 결과의 부산물이다.)

```
1. 커서 해독          → version=f2eb337c, offset=1
2. GET feed:ver:{A}   → nil          (TTL 만료)
3. 버전 불일치 판정    → offset=1 은 버린다
4. 스냅샷 재계산       → feed:{A} 새로 쓰고 feed:ver:{A} = 725ee8b8 발급
5. offset=0 부터 서빙  → 1페이지 내용
6. 새 커서 발급        → "725ee8b8:1"
```

**순서가 핵심이다.** 버전 비교가 리스트를 읽기 *전에* 일어나므로 만료된 스냅샷에서 엉뚱한 데이터를
읽는 경우는 생기지 않는다. **"이어 읽기 vs 처음부터"** 둘 중 하나만 나온다.

### 클라이언트가 할 일

응답 형태가 1페이지와 완전히 같다 — 에러도 아니고 특별한 플래그도 없다. 클라이언트는

- 받은 `items` 를 화면에 쌓고
- 갖고 있던 커서를 응답의 새 `nextCursor` 로 **교체**하면 된다

옛 커서를 계속 재사용하면 매번 1페이지만 돌게 되므로, "응답의 커서로 항상 갈아끼운다"만 지키면 된다.
깨진 커서·위조된 버전도 400 이 아니라 첫 페이지로 처리한다 — 커서 만료는 사용자 잘못이 아니라
서버 캐시가 만료된 것이므로, 에러 대신 최신 후보를 처음부터 주는 쪽이 맞다.

### 대신 생기는 일과 그걸 막는 장치

`offset` 이 0 으로 돌아가니 아까 본 카드가 다시 보일 수 있다.

| 상황 | 다시 뜨나 | 이유 |
|---|---|---|
| 좋아요/패스를 누른 카드 | **안 뜬다** | `like-relay` → `seen:{A}` 에 있어 재계산 때 빠짐 |
| 보고 그냥 넘긴 카드 | 뜰 수 있다 | 아무 기록이 없어 여전히 후보 |
| 클라이언트가 노출 기록을 보낸 카드 | **안 뜬다** | `POST /api/feed/seen` 으로 `seen` 에 들어감 |

**`POST /api/feed/seen` 이 있는 이유가 정확히 이 구멍 때문이다.**

> **역할 분담**: 커서는 *한 스냅샷 안에서의 위치*만 책임지고,
> 스냅샷을 넘나드는 중복 방지는 `seen` 이 책임진다.

### TTL 10분

`feed:{A}` 와 `feed:ver:{A}` 는 같은 순간 같은 TTL 로 설정되므로 함께 만료된다.

- **짧으면**: 커서가 자주 무효화돼 사용자가 처음으로 자주 되돌아간다
- **길면**: 새로 근처에 온 사람이 늦게 보인다

`feed.snapshot.ttl` 설정으로 빼 뒀으니 코드 수정 없이 조절할 수 있다. 한 세션에 몇 분간 스와이프하고
나가는 사용 패턴이면 10분으로 대개 한 세션이 한 스냅샷에 덮인다.

---

## 4. 코드 위치 — `rebuild()` vs `replace()`

"재계산"은 **`FeedSnapshotService.rebuild()`**, `replace()` 는 그 **마지막 쓰기 단계**다.

```
FeedQueryService.resolvePosition()              커서 판정
   │  커서 없음(:107) 또는 버전 불일치(:113)
   ▼
FeedSnapshotService.rebuild()            (:88)  ← 진짜 "재계산"
   │  pref GET → GEOSEARCH → 본인 제외 → seen 제외 → 카드 MGET → 필터 → 점수 정렬
   ▼
FeedSnapshotRedisRepository.replace()    (:48)  ← Redis 쓰기만
      DEL feed:{A} → RPUSH … → EXPIRE → SET feed:ver:{A} = 새 버전 → return 버전
```

경계를 이렇게 그은 이유: **리포지토리는 Redis 명령만 알고, 도메인 규칙은 서비스가 갖는다.**
`replace()` 는 "누구를 어떤 순서로 넣을지" 를 전혀 모른다 — 이미 정렬된 리스트를 받아 담을 뿐이다.

### 버전 발급 지점

```java
public String replace(UUID userId, List<FeedEntry> ranked) {
    String version = UUID.randomUUID().toString().substring(0, 8);   // ← 새 판 발급
    redisTemplate.delete(key);                                        // 옛 목록 제거
    if (!ranked.isEmpty()) {
        redisTemplate.opsForList().rightPushAll(key, values);
        redisTemplate.expire(key, ttl);
    }
    redisTemplate.opsForValue().set(versionKey, version, ttl);        // 목록과 같은 TTL
    return version;                                                   // 호출자가 커서에 실어 보낸다
}
```

**리스트 교체와 버전 발급이 같은 메서드 안에 있는 게 중요하다.** 목록만 바꾸고 버전을 그대로 두면
옛 커서가 유효한 것처럼 통과해 버린다. 둘은 반드시 함께 움직여야 해서 한 곳에 묶었다.

### `replace()` 가 4번 호출되는 이유

`FeedSnapshotService` 에서 4곳이 호출하는데 앞의 3개는 **빈 리스트**다.

| 줄 | 상황 | 넘기는 값 |
|---|---|---|
| 92 | 위치를 한 번도 안 올린 유저 | `List.of()` |
| 106 | 반경 안에 아무도 없음 | `List.of()` |
| 117 | 후보 전원이 seen | `List.of()` |
| 137 | 정상 — 필터·정렬 통과 | `ranked` |

후보가 0명이어도 굳이 `replace()` 를 불러 버전을 발급하는 이유: `feed:ver` 가 **없는 상태(아직 계산 전)**와
**있는데 목록이 빈 상태(계산했더니 없었다)**를 구분해야 한다. 구분하지 않으면 후보가 없는 유저는
요청할 때마다 매번 GEOSEARCH 를 다시 돌게 된다.

---

## 5. 한 줄 요약

> 순서를 고정해두지 않으면 "몇 번째"라는 말이 의미가 없어서 **스냅샷**을 만들고 —
> 그 스냅샷이 교체됐는지 알아야 옛 책갈피를 버릴 수 있어서 **버전**을 붙였다.
> 스냅샷을 넘나드는 중복은 커서가 아니라 **seen** 이 막는다.
