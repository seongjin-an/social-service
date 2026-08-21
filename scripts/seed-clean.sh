#!/usr/bin/env bash
# 시더로 만든 테스트 데이터 정리. 대상은 이메일이 @seed.local 로 끝나는 계정이다.
# 원래 쓰던 계정(tester@test.com 같은)은 패턴이 달라서 안 건드린다.
#
# ── 왜 API 를 안 쓰는가 ──────────────────────────────────────────────────────
#   DELETE /api/profiles/{id} 를 태우는 쪽이 "실제 경로" 지만, 수천 건을 지우는 데
#   HTTP 왕복을 쓸 이유가 없다. 개발용 정리 스크립트라 MySQL/Redis 를 직접 지운다.
#   대신 API 가 해주던 뒷정리(파생 캐시 · 태그 카운트 재계산)를 여기서 직접 챙긴다.
#
# ── 지우는 범위 ──────────────────────────────────────────────────────────────
#   MySQL  profile_tag · profile_image · profile_location · profile · users
#          likes · matches, 그리고 그 매칭이 만든 DIRECT 채널의 message ·
#          channel_members · channel
#   Redis  profile:card:{userId} · profile:pref:{userId} · seen:{userId} ·
#          feed:{userId} · feed:ver:{userId} · tags:{profileId} · geo:users 멤버
#   마무리  tag.usage_count 를 실제 profile_tag 부착 수로 재계산.
#          벌크로 지우면 카운트가 그만큼 부풀어 있기 때문이다.
#          태그 자체는 남긴다 — 어휘는 참조 데이터라 카운트 0 이 정상이다
#   파일   prefix 없이 전체를 지울 때만 scripts/.seed/*.json 도 삭제한다
#
# ── 옵션 ─────────────────────────────────────────────────────────────────────
#   --prefix <문자열>   그 접두사 집단만 (예: lock1 → lock1%@seed.local)
#   --dry-run           대상 수만 보여주고 아무것도 지우지 않는다
#   --yes, -y           확인 프롬프트 생략
#   --help, -h          이 설명
#
#   환경변수로 접속 정보를 바꿀 수 있다:
#     MYSQL_CONTAINER(social-mysql) · REDIS_CONTAINER(social-redis)
#     DB_USER(dev_user) · DB_PASSWORD(dev_password) · DB_NAME(social)
#
# ── 사용 예시 ────────────────────────────────────────────────────────────────
#   # 뭘 지울지 먼저 확인 (안전)
#   ./scripts/seed-clean.sh --dry-run
#
#   # 특정 집단만 정리
#   ./scripts/seed-clean.sh --prefix lock1 --dry-run
#   ./scripts/seed-clean.sh --prefix lock1 --yes
#
#   # 전부 정리 (확인 프롬프트 뜬다 · yes 입력)
#   ./scripts/seed-clean.sh
#
#   # 스크립트에서 쓸 때 — 프롬프트 없이
#   ./scripts/seed-clean.sh --yes
#
# ⚠️ 멱등하지만 원자적이지는 않다. 중간에 실패하면 일부만 지워진 상태로 남는데,
#    같은 명령을 다시 돌리면 남은 것부터 이어서 지운다.
#
# 데이터를 다시 채울 때는 scripts/seed.mjs 를 쓴다.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-social-mysql}"
REDIS_CONTAINER="${REDIS_CONTAINER:-social-redis}"
DB_USER="${DB_USER:-dev_user}"
DB_PASSWORD="${DB_PASSWORD:-dev_password}"
DB_NAME="${DB_NAME:-social}"

# 파일 상단 주석을 그대로 출력한다. 설명을 두 군데 적으면 반드시 한쪽이 낡는다.
print_usage() {
  sed -n '2,/^$/p' "$0" | sed 's/^# \{0,1\}//'
}

PREFIX=""
DRY_RUN=false
ASSUME_YES=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --prefix)  PREFIX="$2"; shift 2 ;;
    --dry-run) DRY_RUN=true; shift ;;
    --yes|-y)  ASSUME_YES=true; shift ;;
    --help|-h) print_usage; exit 0 ;;
    *) echo "알 수 없는 옵션: $1 (--help 참고)"; exit 1 ;;
  esac
done

EMAIL_PATTERN="%@seed.local"
[[ -n "$PREFIX" ]] && EMAIL_PATTERN="${PREFIX}%@seed.local"

CYAN='\033[0;36m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }

# 에러는 그대로 보여준다. 비밀번호 경고만 걸러낸다.
sql() {
  local out
  if ! out=$(docker exec -i "$MYSQL_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -N -B -e "$1" 2>&1); then
    echo "$out" | grep -v "Using a password" >&2 || true
    return 1
  fi
  # grep -v 는 걸러낼 게 없으면 exit 1 을 내는데, set -e 아래에서는 그게 곧 스크립트 중단이다.
  echo "$out" | grep -v "Using a password" || true
}
redis() { docker exec -i "$REDIS_CONTAINER" redis-cli "$@" >/dev/null; }

# ── 대상 파악 ─────────────────────────────────────────────────────────────
USER_COUNT=$(sql "select count(*) from users where email like '${EMAIL_PATTERN}'")
if [[ "$USER_COUNT" == "0" ]]; then
  info "지울 대상이 없다 (email like '${EMAIL_PATTERN}')"
  exit 0
fi

PROFILE_COUNT=$(sql "select count(*) from profile p join users u on u.id = p.user_id where u.email like '${EMAIL_PATTERN}'")
info "대상 — 계정 ${USER_COUNT}개 · 프로필 ${PROFILE_COUNT}개 (email like '${EMAIL_PATTERN}')"

if [[ "$DRY_RUN" == true ]]; then
  info "--dry-run 이므로 아무것도 지우지 않는다."
  exit 0
fi

if [[ "$ASSUME_YES" != true ]]; then
  read -r -p "정말 지울까? (yes 입력) " answer
  [[ "$answer" == "yes" ]] || { warn "취소했다."; exit 1; }
fi

# ── Redis 먼저 ────────────────────────────────────────────────────────────
# 파생 캐시라 DB 보다 먼저 지워도 되고, DB 를 먼저 지우면 어떤 키를 지울지 알 수 없어진다.
info "Redis 키 정리 중..."
USER_IDS=$(sql "select lower(concat(substr(hex(id),1,8),'-',substr(hex(id),9,4),'-',substr(hex(id),13,4),'-',substr(hex(id),17,4),'-',substr(hex(id),21,12))) from users where email like '${EMAIL_PATTERN}'")
PROFILE_IDS=$(sql "select lower(concat(substr(hex(p.profile_id),1,8),'-',substr(hex(p.profile_id),9,4),'-',substr(hex(p.profile_id),13,4),'-',substr(hex(p.profile_id),17,4),'-',substr(hex(p.profile_id),21,12))) from profile p join users u on u.id = p.user_id where u.email like '${EMAIL_PATTERN}'")

# 한 번에 몰아서 지운다 — 유저 수가 수천이면 왕복 비용이 그대로 시간이 된다.
while read -r uid; do
  [[ -z "$uid" ]] && continue
  echo "DEL profile:card:$uid profile:pref:$uid seen:$uid feed:$uid feed:ver:$uid"
  echo "ZREM geo:users $uid"
done <<< "$USER_IDS" | docker exec -i "$REDIS_CONTAINER" redis-cli > /dev/null

while read -r pid; do
  [[ -z "$pid" ]] && continue
  echo "DEL tags:$pid"
done <<< "$PROFILE_IDS" | docker exec -i "$REDIS_CONTAINER" redis-cli > /dev/null
success "Redis 정리 완료"

# ── MySQL ─────────────────────────────────────────────────────────────────
# 자식(profile_tag/image/location) → 부모(profile) → 계정(users) 순서.
# 매칭 쪽(likes/matches)과 그 매칭이 만든 DIRECT 채널도 같이 걷어낸다.
info "MySQL 행 삭제 중..."
# 임시 테이블(temporary)을 안 쓰는 이유: 한 쿼리에서 같은 임시 테이블을 두 번 참조하면
# MySQL 이 "Can't reopen table" (1137) 로 막는다. 아래 matches 조건이 딱 그 모양이라
# 그냥 일반 테이블로 만들고 끝에 지운다.
sql "
drop table if exists _seed_users;
drop table if exists _seed_profiles;
drop table if exists _seed_matches;
drop table if exists _seed_channels;

create table _seed_users as select id from users where email like '${EMAIL_PATTERN}';
alter table _seed_users add primary key (id);

create table _seed_profiles as
  select p.profile_id from profile p join _seed_users s on s.id = p.user_id;
alter table _seed_profiles add primary key (profile_id);

create table _seed_matches as
  select m.match_id from matches m join _seed_users s on s.id = m.user_lo_id
  union
  select m.match_id from matches m join _seed_users s on s.id = m.user_hi_id;
alter table _seed_matches add primary key (match_id);

create table _seed_channels as
  select c.channel_id from channel c join _seed_matches s on s.match_id = c.match_id
   where c.type = 'DIRECT';
alter table _seed_channels add primary key (channel_id);
"

sql "
delete pt from profile_tag pt join _seed_profiles s on s.profile_id = pt.profile_id;
delete pi from profile_image pi join _seed_profiles s on s.profile_id = pi.profile_id;
delete pl from profile_location pl join _seed_profiles s on s.profile_id = pl.profile_id;
delete p  from profile p  join _seed_profiles s on s.profile_id = p.profile_id;

delete l from likes l join _seed_users s on s.id = l.from_user_id;
delete l from likes l join _seed_users s on s.id = l.to_user_id;

delete msg from message msg join _seed_channels s on s.channel_id = msg.channel_id;
delete cm  from channel_members cm join _seed_channels s on s.channel_id = cm.channel_id;
delete c   from channel c join _seed_channels s on s.channel_id = c.channel_id;
delete m   from matches m join _seed_matches s on s.match_id = m.match_id;

delete cm from channel_members cm join _seed_users s on s.id = cm.user_id;
delete u  from users u join _seed_users s on s.id = u.id;

drop table _seed_channels;
drop table _seed_matches;
drop table _seed_profiles;
drop table _seed_users;
"
success "MySQL 정리 완료"

# ── 태그 카운트 재계산 ────────────────────────────────────────────────────
# profile_tag 를 벌크로 지웠으니 usage_count 가 그만큼 부풀어 있다. 실제 부착 수로 다시 맞춘다.
info "tag.usage_count 재계산 중..."
sql "update tag t set t.usage_count = (select count(*) from profile_tag pt where pt.tag_id = t.tag_id)"
DRIFT=$(sql "select count(*) from (select t.usage_count uc, (select count(*) from profile_tag pt where pt.tag_id=t.tag_id) rc from tag t) x where uc <> rc")
success "usage_count 재계산 완료 (틀어진 태그 ${DRIFT}개)"

# 아무도 안 쓰는 태그는 남겨둔다 — 어휘(vocabulary)는 참조 데이터라 카운트 0 이 정상이다.

# ── 결과 ──────────────────────────────────────────────────────────────────
REMAIN_USERS=$(sql "select count(*) from users where email like '%@seed.local'")
REMAIN_PROFILES=$(sql "select count(*) from profile")
GEO=$(docker exec -i "$REDIS_CONTAINER" redis-cli ZCARD geo:users | tr -d '\r')
echo
success "정리 끝 — 남은 시드 계정 ${REMAIN_USERS}개 · 전체 프로필 ${REMAIN_PROFILES}개 · geo:users ${GEO}개"

# 결과 파일도 같이 치운다(해당 prefix 만 지우는 경우는 남겨둔다 — 어떤 파일이 그 집단인지 알 수 없다).
if [[ -z "$PREFIX" && -d "$ROOT/scripts/.seed" ]]; then
  rm -f "$ROOT"/scripts/.seed/*.json
  info "scripts/.seed/*.json 도 삭제했다."
fi
