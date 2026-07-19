-- ══════════════════════════════════════════════════════════════════════════
--  Social Discovery — 테이블 설계 (MySQL 8 / schema: chatting)
--  개정 2026-07-19
--
--  ⚙️  전제
--   - 모든 서비스가 단일 스키마 `chatting` 을 공유한다 (user/message/matching...).
--     → 서비스 경계는 "테이블 소유권"으로만 나눈다. 크로스 서비스 FK 는 걸지 않는다.
--   - 실제 스키마는 JPA `ddl-auto: update` 가 엔티티에서 생성한다.
--     이 파일은 (1) 설계 확정용, (2) JPA 가 안 만들어주는 인덱스/유니크/코멘트 확인용.
--   - user_id 는 UUID → Hibernate 기본 매핑이 BINARY(16). 기존 users/channel_members 와
--     동일하게 맞춘다 (CHAR(36) 아님 주의 — JOIN/비교 깨짐).
--   - 시간 컬럼은 BaseEntity 패턴(created_at DATETIME, updated_at DATETIME) 을 따른다.
-- ══════════════════════════════════════════════════════════════════════════


-- ╭──────────────────────────────────────────────────────────────────────────╮
-- │ user-service ─ users / user_locations   (♻️ users · 🆕 user_locations)      │
-- │  ★ 계정(인증)은 user-service, 프로필(소개)은 profile-service 로 분리 (메모 G) │
-- ╰──────────────────────────────────────────────────────────────────────────╯
-- users(기존) 는 인증/신원만: id(BINARY16 PK), email, password_hash, name, phone, role, created_at, updated_at
--   → name 은 users 에 유지(JWT 클레임·게이트웨이 X-User-Name 의 원천). users 엔 컬럼 추가 없음.
--   → 프로필 테이블(profile/tag/...)은 별도 profile-service 소유. 아래 참조.

-- 위치: 초고빈도 쓰기라 계정/프로필과 격리. 실시간 반경검색 권위는 Redis geo:users,
--       이 테이블은 source of truth/마지막 위치 백업. users 와 1:1(PK 공유).
CREATE TABLE user_locations (
    user_id     BINARY(16)   NOT NULL COMMENT 'PK 겸 FK · users.id 와 1:1',
    lat         DECIMAL(9,6) NULL COMMENT '위도 · 위치 갱신 시 Redis GEOADD 와 함께 반영',
    lng         DECIMAL(9,6) NULL COMMENT '경도',
    geohash     VARCHAR(12)  NULL COMMENT 'geohash prefix · geohash 방식 탐색 쓸 때만(선택)',
    created_at  DATETIME     NULL COMMENT '위치 생성 시각',
    updated_at  DATETIME     NULL COMMENT '마지막 위치 갱신 시각',
    PRIMARY KEY (user_id)
);
CREATE INDEX idx_user_locations_geohash ON user_locations (geohash);  -- geohash 방식 쓸 때만


-- ╭──────────────────────────────────────────────────────────────────────────╮
-- │ profile-service ─ profile / profile_image / tag / profile_tag   (🆕)        │
-- │  ★ 멀티프로필: 한 user_id 가 profile 을 여러 개 가질 수 있다 (1:N) (메모 G)   │
-- │  ★ PK 는 모두 UUID v7(앱에서 UuidV7Generator 로 생성) + Persistable(merge회피) │
-- ╰──────────────────────────────────────────────────────────────────────────╯

-- 프로필: 소개·선호. user 와 1:N (user_id 유니크 아님 — 멀티프로필). 자체 profile_id(UUID) PK.
CREATE TABLE profile (
    profile_id       BINARY(16)   NOT NULL COMMENT 'PK · UUID v7',
    user_id          BINARY(16)   NOT NULL COMMENT 'FK 개념 · users.id (유니크 아님, 유저당 여러 프로필)',
    gender           VARCHAR(10)  NULL COMMENT '내 성별 · MALE|FEMALE|OTHER (enum STRING)',
    birthday         DATE         NULL COMMENT '생년월일(일까지) · 정확한 나이/만18세 판정',
    bio              VARCHAR(500) NULL COMMENT '자기소개 문구 · 프로필 카드 노출',
    pref_gender      VARCHAR(10)  NULL COMMENT '선호 성별(추천 필터) · MALE|FEMALE|ALL',
    pref_age_min     INT          NULL COMMENT '선호 최소 나이(추천 필터)',
    pref_age_max     INT          NULL COMMENT '선호 최대 나이(추천 필터)',
    pref_distance_km INT          NULL COMMENT '선호 반경 km(추천 필터) · GEOSEARCH 반경 입력',
    created_at       DATETIME     NULL COMMENT '생성 시각(@CreatedDate)',
    updated_at       DATETIME     NULL COMMENT '수정 시각(@LastModifiedDate)',
    PRIMARY KEY (profile_id)
);
CREATE INDEX idx_profile_user ON profile (user_id);   -- "이 유저의 프로필들" 조회

-- 프로필 사진: 오브젝트 스토리지(S3/MinIO)에 업로드하고 메타/URL 만 저장 (profile 1:N).
--   앞서 정리한 "이미지는 스토리지, 바디엔 URL" 패턴. 업로드 API 는 별도(도메인 바디와 분리).
CREATE TABLE profile_image (
    profile_image_id   BINARY(16)   NOT NULL COMMENT 'PK · UUID v7',
    profile_id         BINARY(16)   NOT NULL COMMENT 'FK · profile.profile_id',
    original_file_name VARCHAR(255) NOT NULL COMMENT '원본 파일명',
    stored_file_name   VARCHAR(255) NOT NULL COMMENT '저장 파일명(UUID 등) · UNIQUE',
    object_key         VARCHAR(500) NOT NULL COMMENT 'S3/MinIO 오브젝트 키 · UNIQUE',
    image_url          VARCHAR(1000) NOT NULL COMMENT '접근 URL(CDN)',
    content_type       VARCHAR(100) NOT NULL COMMENT 'MIME 타입',
    file_size          BIGINT       NOT NULL COMMENT '파일 크기(byte)',
    primary_image      BOOLEAN      NOT NULL COMMENT '대표 사진 여부',
    created_at         DATETIME     NULL,
    updated_at         DATETIME     NULL,
    PRIMARY KEY (profile_image_id),
    UNIQUE KEY uk_profile_image_stored (stored_file_name),
    UNIQUE KEY uk_profile_image_object (object_key)
);
CREATE INDEX idx_profile_image_profile ON profile_image (profile_id);

-- 태그 마스터: 고정 어휘. 서빙 겹침계산은 Redis SET(profile 별)으로 SINTERCARD (메모 H).
CREATE TABLE tag (
    tag_id          BINARY(16)  NOT NULL COMMENT 'PK · UUID v7',
    name            VARCHAR(50) NOT NULL COMMENT '표시 이름 · 예: Java, 러닝',
    normalized_name VARCHAR(50) NOT NULL COMMENT 'trim+lower 정규화 · 어휘 통제/조회 키',
    usage_count     BIGINT      NOT NULL DEFAULT 0 COMMENT '부착 횟수 · 인기 태그 정렬',
    created_at      DATETIME    NULL,
    updated_at      DATETIME    NULL,
    PRIMARY KEY (tag_id),
    -- ⚠️ 현재 코드는 UNIQUE 가 name 에 걸려 있으나, "Java"="java" 통제를 위해
    --    normalized_name 으로 옮기고 조회도 findByNormalizedName 로 하는 것을 권장 (재검증 #3)
    UNIQUE KEY uk_tag_normalized_name (normalized_name)
);

-- 프로필↔태그 (M:N). ★ 유저가 아니라 '프로필'에 태그가 붙는다 (멀티프로필이라 profile 기준).
CREATE TABLE profile_tag (
    profile_tag_id BINARY(16) NOT NULL COMMENT 'PK · UUID v7',
    profile_id     BINARY(16) NOT NULL COMMENT 'FK · profile.profile_id',
    tag_id         BINARY(16) NOT NULL COMMENT 'FK · tag.tag_id',
    created_at     DATETIME   NULL,
    updated_at     DATETIME   NULL,
    PRIMARY KEY (profile_tag_id),
    UNIQUE KEY uk_profile_tag (profile_id, tag_id)   -- 같은 태그 중복 부착 차단(멱등)
);
CREATE INDEX idx_profile_tag_tag ON profile_tag (tag_id);   -- "이 태그 가진 프로필" · 인기태그 통계


-- ╭──────────────────────────────────────────────────────────────────────────╮
-- │ matching-service ─ likes / matches / outbox   (🆕 신규 · 프로젝트의 심장)   │
-- ╰──────────────────────────────────────────────────────────────────────────╯

-- 좋아요/패스. 복합 PK 로 "중복 좋아요"를 멱등 흡수한다.
CREATE TABLE likes (
    from_user_id  BINARY(16)  NOT NULL COMMENT '누른 사람(주체) · users.id',
    to_user_id    BINARY(16)  NOT NULL COMMENT '대상 사람(객체) · users.id',
    type          VARCHAR(10) NOT NULL COMMENT '행위 · LIKE(좋아요)|PASS(패스)|SUPER(슈퍼좋아요) · 매칭 판정은 LIKE/SUPER 만',
    created_at    DATETIME    NOT NULL COMMENT '누른 시각',
    PRIMARY KEY (from_user_id, to_user_id)   -- ★ 같은 상대에게 또 눌러도 PK 로 흡수(멱등)
);
-- 역방향 존재 확인 SELECT(from=상대, to=나) 은 PK 프리픽스로 커버됨 → 별도 인덱스 불필요.
-- "나를 좋아한 사람" 목록이 필요하면:
CREATE INDEX idx_likes_to_user ON likes (to_user_id, type);

-- 매칭(성사). 항상 (lo < hi) 로 정렬 저장 → UNIQUE 로 "정확히 한 번" 을 보장.
CREATE TABLE matches (
    match_id     BIGINT      NOT NULL COMMENT '매칭 PK · Snowflake ID · match-fanout 파티션 키로도 사용',
    user_lo_id   BINARY(16)  NOT NULL COMMENT '두 유저 중 작은 쪽 id = min(a,b) · 정렬 저장이 UNIQUE 의 전제',
    user_hi_id   BINARY(16)  NOT NULL COMMENT '두 유저 중 큰 쪽 id = max(a,b)',
    channel_id   BIGINT      NULL     COMMENT '이 매칭의 DIRECT 채팅방 id · Saga 로 백필되어 성사 직후엔 NULL (메모 C)',
    status       VARCHAR(10) NOT NULL COMMENT '매칭 상태 · ACTIVE(유효)|UNMATCHED(해제됨)',
    created_at   DATETIME    NOT NULL COMMENT '매칭 성사 시각',
    updated_at   DATETIME    NULL     COMMENT '마지막 변경 시각 · channel_id 백필/언매치 시 갱신',
    PRIMARY KEY (match_id),
    UNIQUE KEY uk_matches_pair (user_lo_id, user_hi_id)   -- ★★ 중복 매칭 원천 차단 ★★
);
CREATE INDEX idx_matches_lo ON matches (user_lo_id, status);   -- "내 매칭 목록"
CREATE INDEX idx_matches_hi ON matches (user_hi_id, status);

-- Outbox — message-service 의 outbox 구조를 그대로 재사용(복붙). Poller 가 PENDING 을 집어 발행.
--  ※ design.html 은 "Debezium CDC" 로 서술돼 있으나 실제 자산은 Poller 방식.
--    matching 도 동일 Poller 패턴 재사용 권장(일관성). ⇒ 아래 설계메모 D 참고.
CREATE TABLE outbox (
    event_id          BINARY(16)   NOT NULL COMMENT '이벤트 PK · UUID · 소비자 멱등 판별 키로도 활용',
    saga_id           BINARY(16)   NULL COMMENT 'Saga 상관관계 id · 한 매칭의 [1]→[2]→[3] 스텝을 하나로 묶음 (메모 C)',
    saga_type         VARCHAR(100) NULL COMMENT 'Saga 종류 · 예: MATCH',
    aggregate_type    VARCHAR(100) NULL COMMENT '이벤트를 낳은 집합체 종류 · 예: MATCH',
    aggregate_id      VARCHAR(100) NULL COMMENT '그 집합체의 id · 예: match_id 값',
    event_type        VARCHAR(100) NULL COMMENT '이벤트 이름 · 예: MATCH_CREATED, CHANNEL_CREATED',
    payload           TEXT         NULL COMMENT '이벤트 본문(JSON) · 예: {matchId, channelId, recipients:[A,B]}',
    destination_topic VARCHAR(255) NOT NULL COMMENT '발행 대상 Kafka 토픽 · 예: match-fanout',
    partition_key     VARCHAR(255) NOT NULL COMMENT 'Kafka 파티션 키 · 예: matchId (순서보장 단위)',
    status            VARCHAR(20)  NOT NULL COMMENT '발행 상태 · PENDING(대기)|IN_PROGRESS(집었음)|PROCESSED(발행완료)|FAILED(실패)',
    retry_count       INT          NOT NULL DEFAULT 0 COMMENT '발행 재시도 횟수',
    last_error        VARCHAR(1000) NULL COMMENT '마지막 실패 사유 메시지(디버깅용)',
    trace_id          VARCHAR(64)  NULL COMMENT '분산추적 trace id · 발행 시점 MDC 에서 복사',
    span_id           VARCHAR(64)  NULL COMMENT '분산추적 span id',
    created_at        DATETIME(6)  NOT NULL COMMENT '이벤트 적재 시각(마이크로초) · 폴러 스캔 정렬 기준',
    processed_at      DATETIME(6)  NULL COMMENT '발행 완료 시각 · PROCESSED 로 바뀔 때 기록',
    version           BIGINT       NULL COMMENT '낙관적 락(@Version) · 폴러 동시 집기 충돌 방지',
    PRIMARY KEY (event_id)
);
CREATE INDEX idx_outbox_poll ON outbox (status, created_at);  -- 폴러가 PENDING 을 오래된 순으로 스캔


-- ╭──────────────────────────────────────────────────────────────────────────╮
-- │ message-service ─ `channel` / `channel_members` 확장  (♻️)                  │
-- │  ★ rooms/room_members 를 두지 않는다 — channel 이 곧 "방"이다 (메모 F)        │
-- ╰──────────────────────────────────────────────────────────────────────────╯
-- 기존 channel: channel_id(BIGINT IDENTITY PK), title, created_at, updated_at
-- DIRECT(1:1 매칭방)도 OPEN(오픈채팅방)도 같은 channel/message/channel_members 파이프라인.
-- 오픈방 전용 메타는 아래 컬럼으로 붙인다 (DIRECT 채널에선 전부 NULL).
ALTER TABLE channel
    ADD COLUMN type         VARCHAR(10)  NOT NULL DEFAULT 'DIRECT' COMMENT 'DIRECT | OPEN',
    ADD COLUMN category     VARCHAR(50)  NULL COMMENT 'OPEN 전용 · 탐색 카테고리',
    ADD COLUMN owner_id     BINARY(16)   NULL COMMENT 'OPEN 전용 · 방장',
    ADD COLUMN max_members  INT          NULL COMMENT 'OPEN 전용 · 정원',
    ADD COLUMN member_count INT          NULL DEFAULT 0 COMMENT 'OPEN 전용 · 인원 캐시(실시간 접속수는 Redis presence)',
    ADD COLUMN status       VARCHAR(10)  NULL COMMENT 'OPEN 전용 · ACTIVE|CLOSED';

-- 멤버십은 channel_members 로 통일(room_members 없음). role 한 컬럼만 추가.
ALTER TABLE channel_members
    ADD COLUMN role VARCHAR(10) NULL COMMENT 'OPEN 방에서만 OWNER|MEMBER, DIRECT 는 NULL';

-- 오픈방 탐색/검색용 인덱스 (openchat-service 가 이 쿼리를 서빙)
CREATE INDEX idx_channel_open_browse ON channel (type, category, status);
CREATE FULLTEXT INDEX ft_channel_title ON channel (title);   -- 방 제목 검색(선택)


-- ╭──────────────────────────────────────────────────────────────────────────╮
-- │ openchat-service   (🆕 신규 · 소유 테이블 없음 · 탐색/입장 API 만 제공)       │
-- ╰──────────────────────────────────────────────────────────────────────────╯
-- 방 메타는 channel(type=OPEN), 멤버십은 channel_members, 대화는 message,
-- 대량 전송은 fanout-delivery-service 를 그대로 재활용한다.
-- openchat 은 "방을 어떻게 찾고/만들고/들어오는가"(탐색·검색·생성·입장) 오케스트레이션만 책임진다.
--   - 조회: channel WHERE type='OPEN' (+category/title 검색), channel_members 로 인원
--   - 방 생성/입장: message-service 채널·멤버 생성 경로 재사용 (자체 테이블 없음)
--   ⇒ 소유 테이블이 없으므로, 정말 별도 서비스로 뺄지 vs message-service 안의 모듈로 둘지 재검토 여지(메모 F)


-- ╭──────────────────────────────────────────────────────────────────────────╮
-- │ recommendation-service   (🆕 신규 · 상태는 대부분 Redis, RDB 테이블 거의 X) │
-- ╰──────────────────────────────────────────────────────────────────────────╯
-- 추천은 users(user-service) + 아래 Redis 자원을 조합해 서빙한다.
--   geo:users        (GEO)        GEOADD userId lng lat        → 반경 검색
--   feed:{userId}    (List)       미리 계산해 채워둔 추천 후보 큐
--   seen:{userId}    (Bloom/Set)  이미 본 사람 제외
-- (선택) 노출/AB 분석이 필요할 때만 로그 테이블. 없어도 데모는 돈다.
-- CREATE TABLE reco_log (
--     id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '로그 PK(자동증가)',
--     user_id       BINARY(16) NOT NULL COMMENT '추천을 받은 사람(뷰어)',
--     shown_user_id BINARY(16) NOT NULL COMMENT '노출된 상대',
--     shown_at      DATETIME   NOT NULL COMMENT '노출 시각',
--     INDEX idx_reco_log_user (user_id, shown_at)
-- );


-- ══════════════════════════════════════════════════════════════════════════
--  📝 설계 메모 (구현하다 헷갈릴 만한 지점)
--
--  A) pair 직렬화 키 vs 저장 정렬
--     - like-relay 파티션 키 = pair(a,b) = hash(min(a,b), max(a,b))  → A→B, B→A 가 같은 파티션.
--     - matches 저장은 user_lo/hi 로 정렬 → UNIQUE(lo,hi) 가 중복매칭 차단.
--     - 이 둘은 별개 장치(직렬화 + 유니크)의 이중 안전. 하나만으론 부족.
--
--  B) 매칭 판정 로직 (컨슈머, 단일 스레드)
--     1) likes UPSERT (INSERT ... ON DUPLICATE KEY / PK 멱등)
--     2) 역방향 존재?  SELECT FROM likes WHERE from_user_id=상대 AND to_user_id=나 AND type IN ('LIKE','SUPER')
--     3) 있으면 → matches INSERT (UNIQUE 로 중복 차단) + outbox INSERT  ⟨한 트랜잭션⟩
--
--  C) matches.channel_id 백필 → ✅ Saga 방식으로 확정
--     성사 직후 channel_id 는 NULL. 채널 생성을 별도 스텝으로 분리해 백필한다.
--       [1] matching: matches INSERT(channel_id=NULL) + outbox(MATCH_CREATED, saga_id=새 UUID,
--                     saga_type='MATCH', recipients=[A,B])   ⟨한 트랜잭션⟩
--       [2] message-service: MATCH_CREATED 소비 → DIRECT channel 생성(type=DIRECT)
--                     + channel_members(A,B) → CHANNEL_CREATED(saga_id 그대로, channelId) 발행
--       [3] matching: CHANNEL_CREATED 소비 → 해당 match(saga_id)로 matches.channel_id 백필
--                     → fanout 으로 "매칭+채팅방 준비 완료" 최종 푸시
--     - outbox 의 saga_id/saga_type 컬럼이 바로 이 상관관계(correlation)용.
--     - 멱등: [2] 재수신 시 채널 중복 생성 금지 → saga_id(or matchId) 로 채널 조회 후 없을 때만 생성.
--       matches.channel_id 가 이미 차 있으면 [3] 은 no-op.
--
--  D) Outbox 발행: Poller vs Debezium → 🕓 미확정 (일단 Poller 로 시작)
--     실제 message-service 는 Poller(주기적으로 PENDING 스캔 후 Kafka 발행)다. matching 도 우선
--     동일 Poller 패턴을 복붙해 시작하고, 부하테스트(Phase 4)에서 CDC 전환 여부를 저울질한다.
--     어느 쪽이든 소비자 멱등성(matches UNIQUE + 채널 멱등 생성)이 안전망.
--     → 최종 선택 확정 시 design.html 의 "CDC" 서술을 그에 맞춰 조정할 것.
--
--  E) UUID 저장형  ★함정 주의★
--     기존 user_id 가 BINARY(16). 새 테이블도 반드시 BINARY(16) 로. CHAR(36) 섞으면 조인/비교가
--     조용히 깨진다. JPA 는 UUID 필드면 알아서 BINARY(16). 수동 DDL 쓸 때만 주의.
--
--  F) 왜 rooms 를 안 만드나 → ✅ channel 이 곧 방
--     channel 에 title/type/멤버십(channel_members)이 이미 있어서 rooms/room_members 는
--     title·joined_at·멤버십이 통째로 중복이었다. 특히 room_members 는 channel_members 의
--     판박이라 입장마다 이중 쓰기 + 읽음처리(last_read_message_id) 분리 문제만 생긴다.
--     → 오픈방 전용 메타(category/owner/max_members/member_count/status)만 channel 에 nullable
--       컬럼으로 얹고, DIRECT 채널에선 NULL. "1:1 방도 오픈방도 같은 channel" 통일 철학과 일치.
--     주의: openchat-service 가 소유 테이블이 없어져 사실상 탐색 API 파사드가 된다.
--           → 별도 서비스 유지 vs message-service 모듈로 흡수는 구현하며 재판단(과설계 경계).
--
--  G) 계정/프로필/위치 분리 + 프로필은 별도 서비스 · 멀티프로필(1:N)   [갱신 2026-07-19]
--     - user-service: users(계정 email/password/name/role) + user_locations(위치).
--     - profile-service(신규): profile / profile_image / tag / profile_tag 소유.
--     - ★ 멀티프로필: 유저 1명이 profile 을 여러 개 가진다(1:N). profile.user_id 에 유니크 없음,
--       profile 자체 UUID PK. → 매칭/추천이 "어느 프로필을 쓰나"(대표/active 개념)는 결정 필요.
--     - profile 은 birthday(DATE, 일까지)로 정확한 나이/만18세 판정. (초안의 birth_year 대체)
--     - 사진은 profiles 의 JSON 컬럼이 아니라 profile_image 테이블 + 오브젝트 스토리지(S3/MinIO).
--     - 위치 갱신 API 는 user_locations UPSERT + Redis GEOADD 를 함께(멱등). 위치는 추천 서빙 때만 Redis 조회.
--     - 참고: profile-service 가 users 를 FK 참조하면 크로스 서비스 FK(상단 원칙 위배). 공유 DB라
--       물리적으론 되지만, 논리적으론 user_id 를 값으로만 들고 FK 는 생략하는 게 원칙에 맞음.
--
--  H) 관심사 태그 → ✅ tag(마스터) + profile_tag(M:N) 정규화, 서빙은 Redis SET   [갱신 2026-07-19]
--     - 왜 마스터: 매칭 랭킹이 "관심사 겹침 수" 기반이라 자유입력이면 표기흔들림으로 겹침이 깨진다.
--       고정 어휘를 피커로 고르게 → tag.normalized_name UNIQUE 로 통제(현재 코드는 name 에 걸림, 이관 권장).
--     - ★ 부착 단위가 유저가 아니라 '프로필' → profile_tag(profile_id, tag_id). 멀티프로필이라 자연스러움.
--     - 왜 조인: profile_tag 로 무결성 + "이 태그 가진 프로필"/인기태그(usage_count) 통계가 SQL 로 열림.
--     - 서빙 분리: 추천 때 매 후보마다 SQL 조인은 무겁다 → 프로필별 Redis SET tags:{profileId} 에
--       tag_id 를 넣어두고 SINTERCARD 로 겹침 수를 O(작음)에 계산. (위치=DB+Redis 와 같은 결)
--     - 동기화: 프로필 태그 수정 시 profile_tag UPSERT/삭제 + Redis SET 갱신 + tag.usage_count 증감을 함께.
--       ⚠️ 태그 get-or-create 는 uk 위반 레이스 → save 실패 시 findByNormalizedName 재조회로 흡수.
--       ⚠️ 요청 태그는 distinct 로 중복 제거(안 하면 uk_profile_tag 위반).
-- ══════════════════════════════════════════════════════════════════════════
