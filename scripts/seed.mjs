#!/usr/bin/env node
/**
 * 합성 프로필 시더 (features.md F0-4) — 추천 피드와 부하테스트가 돌아갈 모수를 만든다.
 *
 * ── 왜 API 경로로 넣는가 ────────────────────────────────────────────────────
 *   추천은 DB 를 안 본다. FeedSnapshotService 는 geo:users · profile:card:{userId} ·
 *   profile:pref:{userId} 만 읽고, FeedQueryService 는 카드 캐시가 없는 후보를 그냥 버린다.
 *   그래서 SQL 벌크 인서트로 넣으면 DB 엔 1000명이 있는데 피드는 텅 빈 상태가 된다.
 *   실제 쓰기 경로를 타야 저장 코드가 캐시까지 같이 채워준다.
 *
 * ── 왜 프로필/위치는 게이트웨이를 우회하는가 ────────────────────────────────
 *   SaveProfileController / UpdateLocationController 가 X-User-Id 헤더를 직접 받는다.
 *   그래서 회원가입만 user-service(8081)로 보내고, 프로필과 위치는 profile-service(8085)에
 *   그 헤더를 붙여 바로 넣는다. 유저마다 로그인해서 토큰을 들고 다닐 필요가 없다.
 *
 * ── 전제 ────────────────────────────────────────────────────────────────────
 *   인프라 컨테이너 + user-service(8081) + profile-service(8085) 기동.
 *   --verify 를 쓰려면 api-gateway(8080) 와 recommendation-service(8087)도 필요하다.
 *
 * ── 옵션 ────────────────────────────────────────────────────────────────────
 *   --count <n>          생성 인원 (기본 200)
 *   --prefix <문자열>    이메일/이름 접두사 겸 집단 구분 키 (기본 seed)
 *   --lat --lng          클러스터 중심 (기본 강남역 37.4979 / 127.0276)
 *   --radius-km <km>     분포 반경 (기본 3). prefDistanceKm 상한이 10km 라서 좁게 뭉쳐야
 *                        서로 후보로 잡힌다. 넓게 뿌리면 피드가 빈다
 *   --concurrency <n>    동시 요청 (기본 6). 어휘가 깔린 뒤엔 24 까지 올려도 된다.
 *                        빈 DB 첫 시딩은 태그를 만드느라 커넥션을 두 배로 쓴다
 *   --password <비번>    전원 동일 (기본 qwer1234)
 *   --seed <숫자>        PRNG 시드 (기본 20260820). 시드+prefix 가 같으면 같은 모수가 재현된다
 *   --out <경로>         결과 모수 파일 (기본 scripts/.seed/{prefix}-users.json)
 *   --verify             시딩 후 시드 유저 1명으로 /api/feed 를 호출해 후보 수를 확인
 *   --user-base          기본 http://localhost:8081
 *   --profile-base       기본 http://localhost:8085
 *   --gateway            기본 http://localhost:8080 (--verify 용)
 *   --help               이 설명
 *
 * ── 채워지는 것 ─────────────────────────────────────────────────────────────
 *   MySQL  users · profile · profile_location · tag · profile_tag
 *   Redis  geo:users · profile:card:{userId} · profile:pref:{userId} · tags:{profileId}
 *   결과   --out 파일에 {email, password, userId, profileId, gender, age, tags, lat, lng}
 *          k6 나 demo-bots.mjs 가 이 파일을 모수로 읽는다
 *
 * ── 멱등 ────────────────────────────────────────────────────────────────────
 *   이미 있는 이메일은 로그인으로 userId 를 회수하고, 프로필이 있으면 재사용해 위치만 갱신한다.
 *   중간에 끊겨도 같은 명령을 다시 돌리면 빈 곳만 채워진다.
 *
 * ── 사용 예시 ───────────────────────────────────────────────────────────────
 *   # 기본 — 강남역 반경 3km 에 200명
 *   node scripts/seed.mjs
 *
 *   # 1000명 + 시딩 직후 피드 확인
 *   node scripts/seed.mjs --count 1000 --concurrency 24 --verify
 *
 *   # 다른 지역에 별도 집단 (prefix 가 다르면 좌표 분포도 달라진다)
 *   node scripts/seed.mjs --count 300 --prefix hongdae --lat 37.5563 --lng 126.9236 \
 *     --out scripts/.seed/hongdae.json
 *
 *   # 좁게 뭉쳐서 매칭 데모용 소수 집단
 *   node scripts/seed.mjs --count 30 --prefix demo --radius-km 1
 *
 * 정리는 scripts/seed-clean.sh 로 한다.
 */

import { readFileSync } from 'node:fs';

const DEFAULTS = {
  count: 200,
  // 강남역. prefDistanceKm 상한이 10km 라서 시드는 좁게 뭉쳐야 서로 후보로 잡힌다.
  lat: 37.4979,
  lng: 127.0276,
  'radius-km': 3,
  concurrency: 6,
  prefix: 'seed',
  password: 'qwer1234',
  'user-base': 'http://localhost:8081',
  'profile-base': 'http://localhost:8085',
  gateway: 'http://localhost:8080',
  out: '',   // 안 주면 scripts/.seed/{prefix}-users.json
  seed: 20260820,
  verify: false,
};

const TAGS = [
  '러닝', '넷플릭스', '카페투어', '등산', '요가', '보드게임', '전시', '캠핑',
  '와인', '헬스', '베이킹', '사진', '여행', '드라이브', '독서', '재즈',
  '테니스', '클라이밍', '고양이', '강아지',
];

const args = parseArgs(process.argv.slice(2));
const opt = { ...DEFAULTS, ...args };

if (opt.help) {
  printUsage();
  process.exit(0);
}

// prefix 를 바꿔 돌렸는데 out 이 고정이면 앞 집단 파일을 덮어쓴다. 집단마다 파일을 따로 둔다.
if (!opt.out || opt.out === true) {
  opt.out = `scripts/.seed/${opt.prefix}-users.json`;
}
// prefix 를 시드에 섞는다. 안 섞으면 prefix 만 바꿔 돌린 집단이 이전 집단과 좌표·나이가
// 그대로 겹쳐서(같은 난수열) 거리 0.00km 짜리 후보가 잔뜩 생긴다.
const rand = mulberry32(Number(opt.seed) + hashCode(String(opt.prefix)));

const stats = { created: 0, reused: 0, located: 0, failed: 0 };
const failures = [];
const seeded = [];

/** 시드 1명분의 결정적 스펙 — 실패해서 재실행해도 같은 사람이 나온다. */
function specFor(i) {
  const gender = i % 2 === 0 ? 'MALE' : 'FEMALE';
  const age = 20 + Math.floor(rand() * 20);          // 20~39
  const now = new Date(2026, 7, 20);                 // 고정 기준일(재현성)
  const birthYear = now.getFullYear() - age;
  // 1~7월로 제한 — 카드의 age 는 서버가 '오늘 - birthday' 로 계산하므로,
  // 생일이 이미 지난 달로 두면 bio 에 적은 나이와 카드 나이가 어긋나지 않는다.
  const month = 1 + Math.floor(rand() * 7);
  const day = 1 + Math.floor(rand() * 28);
  const point = randomPointWithin(Number(opt.lat), Number(opt.lng), Number(opt['radius-km']), rand);
  const tagCount = 2 + Math.floor(rand() * 3);       // 2~4개
  const tags = pickDistinct(TAGS, tagCount, rand).sort((a, b) => a.localeCompare(b, 'ko'));
  // 대부분 이성 선호 + 10% 는 동성 선호 → 필터가 실제로 걸러지는지 보이게 섞는다.
  const prefGender = rand() < 0.9 ? (gender === 'MALE' ? 'FEMALE' : 'MALE') : gender;

  return {
    email: `${opt.prefix}${i}@seed.local`,
    password: opt.password,
    name: `${opt.prefix}${i}`,
    phone: `010-${String(4000 + Math.floor(i / 10000)).padStart(4, '0')}-${String(i % 10000).padStart(4, '0')}`,
    gender,
    age,
    birthday: `${birthYear}${String(month).padStart(2, '0')}${String(day).padStart(2, '0')}`,
    bio: `${tags[0]} 좋아하는 ${age}살. 시드 데이터 #${i}`,
    tags,
    prefGender,
    prefAgeMin: 20,
    prefAgeMax: 39,
    prefDistanceKm: 10,   // @Max(10) — 상한
    lat: point.lat,
    lng: point.lng,
  };
}

async function seedOne(i) {
  const spec = specFor(i);
  try {
    // 5xx/일시 오류는 재시도로 흡수한다 — 모수에 구멍이 나면 부하테스트 결과가 흔들린다.
    const userId = await retry(() => ensureUser(spec));
    const profileId = await retry(() => ensureProfile(userId, spec));
    await retry(() => putLocation(userId, profileId, spec));
    stats.located++;
    seeded.push({
      email: spec.email, password: spec.password, userId, profileId,
      gender: spec.gender, age: spec.age, tags: spec.tags,
      lat: +spec.lat.toFixed(6), lng: +spec.lng.toFixed(6),
    });
  } catch (e) {
    stats.failed++;
    if (failures.length < 10) failures.push(`${spec.email}: ${e.message}`);
  }
}

/** 회원가입 → userId. 이미 있으면 로그인해서 JWT sub 로 회수(멱등). */
async function ensureUser(spec) {
  const signup = await req('POST', `${opt['user-base']}/api/auth/signup`, {
    email: spec.email, password: spec.password, name: spec.name, phone: spec.phone, role: 'USER',
  });
  if (signup.ok) {
    stats.created++;
    return signup.body?.data?.id ?? fail('signup 응답에 id 없음', signup);
  }

  const login = await req('POST', `${opt['user-base']}/api/auth/login`, {
    email: spec.email, password: spec.password,
  });
  if (!login.ok) fail(`signup ${signup.status} 후 login 도 실패 ${login.status}`, login);
  stats.reused++;
  return jwtSubject(login.body?.data?.accessToken ?? fail('login 응답에 accessToken 없음', login));
}

/** 기존 프로필이 있으면 재사용, 없으면 생성. */
async function ensureProfile(userId, spec) {
  const mine = await req('GET', `${opt['profile-base']}/api/profiles/me`, null, userId);
  if (mine.ok && Array.isArray(mine.body?.data) && mine.body.data.length > 0) {
    return mine.body.data[0].profileId;
  }

  const created = await req('POST', `${opt['profile-base']}/api/profiles`, {
    gender: spec.gender, birthday: spec.birthday, bio: spec.bio, tags: spec.tags,
    prefGender: spec.prefGender, prefAgeMin: spec.prefAgeMin,
    prefAgeMax: spec.prefAgeMax, prefDistanceKm: spec.prefDistanceKm,
  }, userId);
  if (!created.ok) fail(`프로필 생성 실패 ${created.status}`, created);
  return created.body?.data ?? fail('프로필 생성 응답에 profileId 없음', created);
}

/** profile_location UPSERT + GEOADD geo:users — 이 한 방이 반경검색의 입력이다. */
async function putLocation(userId, profileId, spec) {
  const res = await req('PUT', `${opt['profile-base']}/api/profiles/${profileId}/location`,
    { lat: +spec.lat.toFixed(6), lng: +spec.lng.toFixed(6) }, userId);
  if (!res.ok) fail(`위치 갱신 실패 ${res.status}`, res);
}

/** 시드 유저 한 명으로 실제 피드를 호출해 모수가 살아 있는지 확인한다. */
async function verifyFeed() {
  const first = seeded[0];
  if (!first) return console.log('\n검증 스킵: 시드된 유저가 없다.');

  const login = await req('POST', `${opt.gateway}/api/auth/login`,
    { email: first.email, password: first.password });
  if (!login.ok) return console.log(`\n검증 실패: 게이트웨이 로그인 ${login.status}`);

  const token = login.body?.data?.accessToken;
  const feed = await fetch(`${opt.gateway}/api/feed?size=10`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const body = await feed.json().catch(() => null);
  const items = body?.data?.items ?? body?.items ?? [];
  console.log(`\n검증 — ${first.email} 기준 /api/feed`);
  console.log(`  HTTP ${feed.status} · 후보 ${items.length}건 · nextCursor=${body?.data?.nextCursor ?? body?.nextCursor ?? 'null'}`);
  for (const item of items.slice(0, 3)) {
    const card = item.profile ?? item;
    console.log(`  · ${card.gender}/${card.age}세 · ${Number(item.distanceKm ?? 0).toFixed(2)}km · 겹치는태그=${(item.sharedTags ?? []).join(',') || '-'}`);
  }
  if (items.length === 0) {
    console.log('  후보 0건이면: 선호 성별/나이 필터, 반경(--radius-km), seen 셋을 먼저 의심.');
  }
}

//-------------------------------------------------------------------------------------------------
// 하부 유틸
//-------------------------------------------------------------------------------------------------

async function req(method, url, body, userId) {
  const headers = {};
  if (body != null) headers['Content-Type'] = 'application/json';
  if (userId) headers['X-User-Id'] = userId;

  let last;
  for (let attempt = 0; attempt < 2; attempt++) {   // 네트워크 흔들림만 1회 재시도
    try {
      const res = await fetch(url, { method, headers, body: body == null ? undefined : JSON.stringify(body) });
      const text = await res.text();
      let parsed = null;
      try { parsed = text ? JSON.parse(text) : null; } catch { parsed = { raw: text }; }
      return { ok: res.ok, status: res.status, body: parsed };
    } catch (e) {
      last = e;
    }
  }
  throw new Error(`${method} ${url} — ${last?.message ?? '알 수 없는 네트워크 오류'}`);
}

/** 멱등 단계 재시도 — 지수 백오프. 마지막 실패는 그대로 던져 통계에 남긴다. */
async function retry(step, attempts = 3) {
  let last;
  for (let attempt = 1; attempt <= attempts; attempt++) {
    try {
      return await step();
    } catch (e) {
      last = e;
      if (attempt < attempts) await sleep(120 * attempt * attempt);
    }
  }
  throw last;
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function fail(message, res) {
  const detail = res?.body?.message ?? res?.body?.raw ?? '';
  throw new Error(detail ? `${message} (${String(detail).slice(0, 120)})` : message);
}

function jwtSubject(token) {
  const payload = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8'));
  return payload.sub ?? fail('JWT 에 sub 없음');
}

/** 원 안 균등분포(sqrt 보정 없으면 중심에 몰린다). */
function randomPointWithin(lat, lng, radiusKm, rng) {
  const distance = radiusKm * Math.sqrt(rng());
  const bearing = rng() * 2 * Math.PI;
  const dLat = (distance / 111.32) * Math.cos(bearing);
  const dLng = (distance / (111.32 * Math.cos((lat * Math.PI) / 180))) * Math.sin(bearing);
  return { lat: lat + dLat, lng: lng + dLng };
}

function pickDistinct(source, n, rng) {
  const pool = [...source];
  const out = [];
  for (let i = 0; i < n && pool.length > 0; i++) {
    out.push(pool.splice(Math.floor(rng() * pool.length), 1)[0]);
  }
  return out;
}

/** 문자열 → 정수 (prefix 를 시드에 섞기 위한 것). */
function hashCode(value) {
  let hash = 0;
  for (let i = 0; i < value.length; i++) {
    hash = (Math.imul(31, hash) + value.charCodeAt(i)) | 0;
  }
  return hash;
}

/** 시드 고정 PRNG — 같은 --seed 면 같은 모수가 나온다. */
function mulberry32(a) {
  return function () {
    a |= 0; a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** 파일 상단 주석을 그대로 출력한다. 설명을 두 군데 적으면 반드시 한쪽이 낡는다. */
function printUsage() {
  const source = readFileSync(new URL(import.meta.url), 'utf8');
  const header = source.slice(source.indexOf('/**') + 3, source.indexOf(' */'));
  console.log(header.split('\n').map(line => line.replace(/^\s*\* ?/, '')).join('\n'));
}

function parseArgs(argv) {
  const out = {};
  for (let i = 0; i < argv.length; i++) {
    const token = argv[i];
    if (!token.startsWith('--')) continue;
    const key = token.slice(2);
    const next = argv[i + 1];
    if (next === undefined || next.startsWith('--')) { out[key] = true; }
    else { out[key] = next; i++; }
  }
  return out;
}

async function pool(total, concurrency, worker) {
  let next = 0;
  const runners = Array.from({ length: Math.min(concurrency, total) }, async () => {
    while (true) {
      const i = next++;
      if (i >= total) return;
      await worker(i);
      const done = stats.located + stats.failed;
      if (done % 50 === 0) process.stdout.write(`  ...${done}/${total}\n`);
    }
  });
  await Promise.all(runners);
}

//-------------------------------------------------------------------------------------------------
// main
//-------------------------------------------------------------------------------------------------

const count = Number(opt.count);
// profile-service 는 프로필 저장 트랜잭션 안에서 태그 get-or-create 를 REQUIRES_NEW 로 돌린다
// → 저장 1건이 커넥션을 최대 2개 잡는다. Hikari 기본 풀이 10 이므로 동시성을 올리면
//   커넥션 고갈로 30초 타임아웃 → 500 이 쏟아진다(DB 데드락이 아니라 풀 굶주림).
if (Number(opt.concurrency) > 8) {
  console.log(`경고 — 동시성 ${opt.concurrency}: profile-service 커넥션 풀(기본 10) 고갈 위험. 신규 프로필 생성이 많으면 8 이하 권장.`);
}
console.log(`시더 — ${count}명 · 중심 (${opt.lat}, ${opt.lng}) 반경 ${opt['radius-km']}km · 동시 ${opt.concurrency}`);
console.log(`  user-service=${opt['user-base']} · profile-service=${opt['profile-base']}`);

const startedAt = process.hrtime.bigint();
await pool(count, Number(opt.concurrency), seedOne);
const elapsedSec = Number(process.hrtime.bigint() - startedAt) / 1e9;

const { writeFile, mkdir } = await import('node:fs/promises');
const { dirname } = await import('node:path');
await mkdir(dirname(opt.out), { recursive: true });
await writeFile(opt.out, JSON.stringify(seeded, null, 2));

console.log(`\n완료 ${elapsedSec.toFixed(1)}초 — 신규 ${stats.created} · 기존재사용 ${stats.reused} · 위치반영 ${stats.located} · 실패 ${stats.failed}`);
console.log(`  ${opt.out} (${seeded.length}명) — k6/테스트가 이 파일을 모수로 쓰면 된다.`);
if (failures.length > 0) {
  console.log('  실패 샘플:');
  for (const f of failures) console.log(`   - ${f}`);
}

if (opt.verify) await verifyFeed();
