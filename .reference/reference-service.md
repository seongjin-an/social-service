```text
추천 서비스는 사실 "새 데이터를 안 만드는 읽기 전용 조립기" 예요. 자기 테이블이 없고, 다른 서비스가 이미 써둔 걸 모아서 정렬해 내려줄 뿐입니다. 그렇게 보면 훨씬 단순해져요.

재료 (전부 이미 채워지고 있음)

┌──────────────────────────────┬────────────────────┬──────────────────────────────┐
│             재료             │        출처        │             용도             │
├──────────────────────────────┼────────────────────┼──────────────────────────────┤
│ geo:users (Redis GEO)        │ 위치 업데이트      │ 반경 내 후보 찾기            │
├──────────────────────────────┼────────────────────┼──────────────────────────────┤
│ tags:{profileId} (Redis SET) │ 프로필 저장        │ 관심사 겹침 점수             │
├──────────────────────────────┼────────────────────┼──────────────────────────────┤
│ profile 테이블               │ profile-service DB │ 성별·나이·선호 필터          │
├──────────────────────────────┼────────────────────┼──────────────────────────────┤
│ likes/matches                │ matching-service   │ 이미 좋아요/매칭한 사람 제외 │
└──────────────────────────────┴────────────────────┴──────────────────────────────┘

파이프라인 (GET /api/feed 한 번에 일어나는 일)

1) 반경검색:  GEOSEARCH geo:users FROMMEMBER {나} BYRADIUS {내prefDistanceKm} km ASC WITHDIST
              → 반경 내 profileId 목록(가까운 순 + 거리)

2) 필터:      후보들의 profile 배치 조회(DB) →
              - 본인 제외
              - 성별: 후보.gender == 내 prefGender
              - 나이: 내 prefAgeMin ≤ 후보 나이 ≤ prefAgeMax
              - 이미 매칭/좋아요/차단한 사람 제외

3) 본 사람 제외: SMISMEMBER seen:{나} {후보들}  → seen 에 있는 건 컷

4) 랭킹:      각 후보 점수 =
                w1·(가까울수록↑, step1의 거리)
              + w2·(SINTERCARD tags:{나} tags:{후보}  = 겹치는 태그 수)
              + w3·(활동성 등)
              → 점수 desc 정렬

5) 상위 N개 응답 + 내려준 N개를 seen:{나} 에 추가(SADD)

💡 "커서 페이지네이션" 때문에 헷갈렸을 텐데 — 스와이프 앱은 커서가 거의 필요 없어요

핵심 아하 포인트: seen 집합이 곧 페이지네이션입니다.
- 매 요청 = "내가 아직 안 본 최고 후보 N명 줘"
- 응답한 N명을 seen:{나}에 넣음 → 다음 요청엔 자동으로 그다음 N명
- 스와이프(좋아요/패스)해도 seen에 들어가니 두 번 안 뜸

Tinder식이 딱 이거예요. 랭킹된 집합 위에서 offset/cursor를 관리할 필요 없이, "안 본 것 중 top N" 만 반복하면 됩니다. (성능 최적화가 필요해지면 그때 feed:{나} 스냅샷 캐시(F2-2)를 얹으면 됨 — MVP엔 불필요.)

최소 스켈레톤

recommendation-service (소유 테이블 없음)
 ├─ GET /api/feed?size=20   → FeedController
 ├─ FeedService.getFeed(userId, size)
 │    1 GEOSEARCH (RedisGeoRepository)
 │    2 profileRepository.findByIdsIn(candidateIds)  // 필터용
 │    3 SMISMEMBER seen:{me}                          // 제외
 │    4 SINTERCARD tags:{me} tags:{cand} 로 점수      // 랭킹
 │    5 SADD seen:{me} {returned}                     // 본 사람 기록
 └─ 의존: spring-data-redis + (profile 조회) + eureka(게이트웨이 라우팅 위해 필요)

헷갈릴 만한 실무 포인트 2개

1. 후보 profile 조회를 어디서? — 반경검색은 profileId만 주니, 필터·카드용 데이터(성별/나이/이미지)는 DB 배치 조회하거나 profile을 Redis에 카드 캐시(profile:card:{id})로 미리 넣어두면 빠릅니다. MVP는 DB 배치조회로 충분.
2. 데이터가 있어야 의미 있음 — 그래서 시더(P0-F)로 수만 명 + geo 적재가 선행돼야 반경검색·랭킹이 실제로 검증됩니다. (유저 몇 명으론 "반경에 아무도 없음" 만 나옴)

---
정리하면 recommendation은 "geo로 좁히고 → 필터·seen 컷 → 태그겹침·거리로 정렬 → top N, 본 건 기록" 이 전부예요. matching보다 개념은 쉬운데 시더가 있어야 돌아간다는 게 포인트입니다.

그래서 순서는 여전히 matching 먼저 → 시더 → recommendation을 추천해요. matching부터 설계 들어갈까요?
```