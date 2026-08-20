```text
  스크립트 목록

  ┌──────────────────────┬────────────────────────────────────────────┐
  │       스크립트       │                    용도                    │
  ├──────────────────────┼────────────────────────────────────────────┤
  │ ./scripts/start.sh   │ 인프라 기동 → 빌드 → 전체 서비스 순차 기동 │
  ├──────────────────────┼────────────────────────────────────────────┤
  │ ./scripts/stop.sh    │ 모든 서비스 종료 (graceful → force)        │
  ├──────────────────────┼────────────────────────────────────────────┤
  │ ./scripts/status.sh  │ 서비스별 UP/DOWN 상태 + PID 한눈에 확인    │
  ├──────────────────────┼────────────────────────────────────────────┤
  │ ./scripts/logs.sh    │ 특정 서비스 로그 tail                      │
  ├──────────────────────┼────────────────────────────────────────────┤
  │ ./scripts/build.sh   │ 전체 또는 특정 모듈 빌드                   │
  ├──────────────────────┼────────────────────────────────────────────┤
  │ ./scripts/restart.sh │ 특정 서비스 단독 재기동                    │
  └──────────────────────┴────────────────────────────────────────────┘

  자주 쓸 패턴

  # 전체 시작
  ./scripts/start.sh

  # 빌드 스킵하고 시작 (인프라 + JAR 이미 있을 때)
  SKIP_BUILD=true ./scripts/start.sh

  # 상태 확인
  ./scripts/status.sh

  # 특정 서비스 로그
  ./scripts/logs.sh message-service
  ./scripts/logs.sh api-gateway 200   # 마지막 200줄

  # message-service만 재빌드 + 재기동
  ./scripts/restart.sh message-service

  # 재기동 시 빌드 스킵
  ./scripts/restart.sh message-service --skip-build

  # 서비스만 종료 (인프라 유지)
  ./scripts/stop.sh

  # 인프라까지 전부 종료
  STOP_INFRA=true ./scripts/stop.sh

  # 특정 모듈만 빌드
  ./scripts/build.sh message-service user-service

  추가로 message-service DB URL 포트를 3306 → 23306 으로 수정했어 (Docker MySQL 포트와 일치).
```

---

## 시더 — `scripts/seed.mjs` (F0-4 합성 프로필)

추천 피드/부하테스트의 모수를 만든다. **실제 API 경로로 쓴다** — 추천은 DB 가 아니라
`geo:users` · `profile:card:{userId}` · `profile:pref:{userId}` 를 읽고, 카드 캐시가 없는 후보는
`FeedQueryService` 가 그냥 버리기 때문에 SQL 벌크 인서트로는 피드가 영원히 빈다.

회원가입만 user-service(8081) 로 가고, 프로필/위치는 profile-service(8085) 에 `X-User-Id`
헤더로 직접 넣는다(컨트롤러가 그 헤더를 받으므로 유저별 토큰 관리가 필요 없다).

```bash
# 기본: 강남역 반경 3km 에 200명
node scripts/seed.mjs

# 1000명 + 시딩 후 실제 /api/feed 로 검증
node scripts/seed.mjs --count 1000 --verify

# 다른 도시/다른 모수 (prefix 가 다르면 별도 집단이 된다)
node scripts/seed.mjs --count 300 --prefix hongdae --lat 37.5563 --lng 126.9236 --out scripts/.seed/hongdae.json
```

| 옵션 | 기본값 | 설명 |
|---|---|---|
| `--count` | 200 | 생성 인원 |
| `--lat` `--lng` | 37.4979 / 127.0276 | 클러스터 중심(강남역) |
| `--radius-km` | 3 | 분포 반경. `prefDistanceKm` 상한이 **10km** 이므로 좁게 뭉쳐야 서로 후보로 잡힌다 |
| `--concurrency` | 6 | ⚠️ 8 초과 금지에 가깝다 — 아래 참고 |
| `--prefix` | seed | 이메일/이름 접두사. 집단 구분 키 |
| `--password` | qwer1234 | 전원 동일(로그인 테스트용) |
| `--seed` | 20260820 | PRNG 시드. **시드와 prefix 가 같으면** 같은 모수가 재현된다(prefix 를 시드에 섞어서, 집단마다 좌표가 겹치지 않는다) |
| `--out` | scripts/.seed/seed-users.json | 결과 모수 파일(k6/테스트가 읽는다) |
| `--verify` | off | 시드 유저 1명으로 게이트웨이 로그인 → `/api/feed` 호출해 후보 수 확인 |

**멱등**하다. 이미 있는 이메일은 로그인으로 userId 를 회수하고, 프로필이 있으면 재사용해 위치만
갱신한다. 중간에 죽어도 같은 명령을 다시 돌리면 빈 곳만 채워진다.

### ⚠️ 동시성 기본값이 6 인 이유
안전빵 기본값이다. 지금은 더 올려도 된다 — 동시 24 로 신규 200명을 넣어도 실패 0 이다.

태그가 <b>이미 있으면</b> 조회로 끝나서 커넥션 하나로 돌아간다. 없는 태그를 만들 때만
`TagCreator` 가 별도 트랜잭션(REQUIRES_NEW)을 열어 그 순간 커넥션을 두 개 쓴다.
빈 DB 에 처음 시딩할 때는 전 요청이 그 경로를 타므로 커넥션 소비가 두 배가 된다
(그래서 profile-service 풀을 30 으로 올려뒀다 · `DB_POOL_SIZE` 로 조정).

어휘가 이미 깔려 있는 상태라면 `--concurrency 24` 정도까지 편하게 올려도 된다.

