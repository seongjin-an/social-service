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

## 테스트 데이터 · 데모 스크립트

| 스크립트 | 용도 |
|---|---|
| `scripts/seed.mjs` | 합성 프로필 시더 (F0-4). 추천 피드/부하테스트 모수를 만든다 |
| `scripts/seed-clean.sh` | 시드 데이터 정리 (`@seed.local` 계정 기준) |
| `scripts/demo-bots.mjs` | 시드 계정을 실제 사용자처럼 움직이게 하는 데모 봇 |

옵션과 사용 예시는 **각 파일 상단 주석**에 있고 `--help` 로도 볼 수 있다.
설명을 여기와 파일 두 곳에 적으면 반드시 한쪽이 낡으므로 파일 쪽만 관리한다.

```bash
node scripts/seed.mjs --help
node scripts/demo-bots.mjs --help
./scripts/seed-clean.sh --help
```

자주 쓰는 흐름만 적어둔다.

```bash
# 모수 깔기 (강남역 반경 3km 에 1000명) + 피드로 확인
node scripts/seed.mjs --count 1000 --concurrency 24 --verify

# 데모: 봇을 띄우고 브라우저에서 seed0@seed.local / qwer1234 로 로그인
node scripts/demo-bots.mjs --demo-email seed0@seed.local --bots 8

# 정리 (지울 대상 먼저 확인)
./scripts/seed-clean.sh --dry-run
./scripts/seed-clean.sh --yes
```

### 알아둘 것 두 개

**반경은 좁게.** `prefDistanceKm` 에 `@Max(10)` 이 걸려 있어서 넓게 뿌리면 서로 후보로
안 잡히고 피드가 빈다. 기본 3km 를 크게 벗어나지 않는 게 좋다.

**첫 시딩만 느리다.** 태그가 없는 상태에서는 요청마다 태그를 새로 만드느라 별도 트랜잭션이
붙어 커넥션을 두 배로 쓴다(그래서 profile-service 풀을 30 으로 올려뒀다 · `DB_POOL_SIZE`).
어휘가 깔린 뒤에는 `--concurrency 24` 로 신규 200명이 실패 없이 들어간다.
