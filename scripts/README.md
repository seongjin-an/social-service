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