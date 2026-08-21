#!/usr/bin/env node
/**
 * 데모용 봇 — 시더로 만든 계정들을 실제 사용자처럼 움직이게 한다.
 *
 * 브라우저로 데모 계정에 로그인해 있으면 좋아요가 쌓이고, 스와이프하면 매칭이 성사되고,
 * 상대가 먼저 말을 걸고 내 답장에 반응한다. 시연할 때 화면이 살아 있게 만드는 용도다.
 *
 * ── 전제 ────────────────────────────────────────────────────────────────────
 *   1. 인프라 + 서비스 기동 (api-gateway 8080 · connection 8082 · message 8083 ·
 *      fanout 8084 · matching 8086 이 살아 있어야 매칭·채팅이 흐른다)
 *   2. 시드 모수 존재 — scripts/.seed/*.json (없으면 seed.mjs 를 먼저 돌린다)
 *   3. 데모 계정도 프로필 + 위치가 있어야 피드에 후보가 뜬다
 *
 * ── CLI 옵션 ────────────────────────────────────────────────────────────────
 *   --demo-email <이메일>     봇들이 좋아요를 보낼 데모 계정. userId 는 DB 에서 찾는다
 *   --demo-user-id <uuid>     이메일 대신 userId 직접 지정 (DB 조회 안 함)
 *   --bots <n>                투입할 봇 수 (기본 10)
 *   --room <channelId>        봇 전원이 그 오픈방에 들어가 주기적으로 잡담한다
 *   --like-back-rate <0~1>    나를 좋아한 사람에게 되돌려줄 확률 (기본 0.85)
 *   --poll-ms <ms>            받은 좋아요 확인 주기 (기본 5000)
 *   --seed-dir <경로>         모수 파일 폴더 (기본 scripts/.seed)
 *   --gateway <url>           기본 http://localhost:8080
 *   --ws <url>                기본 ws://localhost:8080
 *   --help                    이 설명
 *
 * ── 봇이 알아서 하는 것 (auto on 일 때) ─────────────────────────────────────
 *   접속 직후 데모 계정에 좋아요 하나 (0.5~8초 랜덤)
 *   5초마다 받은 좋아요 확인 → 85% 확률로 되돌려줌 (데모가 먼저 눌러도 몇 초 뒤 매칭)
 *   매칭 알림 받으면 3~9초 뒤 먼저 인사
 *   25~45초 동안 답이 없으면 한 번 더 말 걸기
 *   상대 메시지에 2~7초 뒤 답장 (문장에 ? 나 "까"/"나요" 가 있으면 질문으로 보고 답변형으로)
 *   모든 행동에 랜덤 지연을 넣는다 — 즉답하면 사람처럼 안 보인다
 *
 * ── 터미널에서 조종 (띄운 뒤 명령을 타이핑) ─────────────────────────────────
 *   like [n]            봇 n명이 데모 계정에게 좋아요 (기본 1)
 *   say <말>            매칭된 봇이 그 말을 보낸다
 *   say <봇이름> <말>   특정 봇이 말한다 · 예) say seed5 밥 먹었어요?
 *   chat [n]            봇 n명이 각자 매칭된 채널에 잡담 (기본 1)
 *   add [n]             봇 n명 추가 투입 (기본 1)
 *   room <channelId>    봇 전원 오픈방 입장
 *   roomsay <말>        오픈방에 말하기
 *   auto on|off         자동 행동 켜기/끄기 — off 면 봇은 시키는 것만 한다
 *   who                 봇 목록과 상태
 *   help                명령 목록
 *   quit                종료
 *
 * ── 사용 예시 ───────────────────────────────────────────────────────────────
 *   # 기본 — 봇 10명이 알아서 움직인다
 *   node scripts/demo-bots.mjs --demo-email seed0@seed.local
 *
 *   # 시연용 — 봇 8명 + 오픈방 5번까지 살려두기
 *   node scripts/demo-bots.mjs --demo-email seed0@seed.local --bots 8 --room 5
 *
 *   # 대사를 내가 직접 넣는 시연 흐름
 *   node scripts/demo-bots.mjs --demo-email seed0@seed.local --bots 8
 *     auto off                       ← 봇을 조용하게
 *     like 3                         ← 받은 좋아요 3개가 눈앞에서 늘어난다
 *     (브라우저에서 오른쪽 스와이프)  ← 몇 초 뒤 매칭 알림
 *     say 안녕하세요! 매칭됐네요      ← 채팅방에 도착
 *     say seed5 저도 반가워요 ㅎㅎ    ← 특정 봇 지목
 *     roomsay 여기 사람 많네요        ← 오픈방에 한마디
 *
 * ── 봇이 쓰는 통신 (프론트와 동일 · 뒷문 없음) ──────────────────────────────
 *   로그인      POST /api/auth/login
 *   좋아요      POST /api/likes            { toUserId, likeType }
 *   받은 좋아요  GET  /api/likes/received   (상대 정보는 partner 아래에 있다)
 *   방 입장     POST /api/rooms/{id}/join
 *   실시간      ws://.../ws?token=JWT      (브라우저가 WS 핸드셰이크에 헤더를 못 붙여서 쿼리 토큰)
 *   메시지 전송  WS   {"type":"SEND_MESSAGE","payload":{channelId, content, clientMessageId}}
 *   하트비트    WS   {"type":"HEARTBEAT","payload":{}}   (프론트도 30초마다 보낸다)
 *   받는 것     CONTENT_MESSAGE · MATCH_NOTIFICATION
 *   메시지 전송은 REST 가 아예 없다 — MessageController 는 조회만 있고 전송은 WS 전용이다.
 *
 * Ctrl+C 또는 quit 으로 종료한다.
 */

import { readdir, readFile } from 'node:fs/promises';
import { readFileSync } from 'node:fs';
import { execFile } from 'node:child_process';
import { createInterface } from 'node:readline';
import { promisify } from 'node:util';

const run = promisify(execFile);

const DEFAULTS = {
  gateway: 'http://localhost:8080',
  ws: 'ws://localhost:8080',
  bots: 10,
  'seed-dir': 'scripts/.seed',
  'demo-email': '',
  'demo-user-id': '',
  room: '',              // 오픈방 channelId — 주면 봇들이 들어가서 잡담한다
  'like-back-rate': 0.85, // 나를 좋아한 사람에게 되돌려줄 확률
  'poll-ms': 5000,        // 받은 좋아요 확인 주기
};

// 봇이 쓰는 말. 첫 인사 / 이어가는 말 / 상대 말에 반응하는 말로 나눠둔다.
const GREETINGS = [
  '안녕하세요! 매칭됐네요 ㅎㅎ',
  '오 반가워요! 프로필 잘 봤어요',
  '안녕하세요~ 태그 겹치는 거 보고 눌렀어요',
  '매칭 감사합니다 :) 오늘 뭐 하셨어요?',
];
const SMALL_TALK = [
  '요즘 뭐 하고 지내세요?',
  '주말에 보통 뭐 하세요?',
  '저는 요즘 러닝 시작했어요',
  '사진 보니까 취향이 비슷할 것 같아요',
  '근처 카페 좋은 데 아세요?',
];
const REPLIES = [
  '오 진짜요?',
  'ㅎㅎ 저도 그래요',
  '좋네요! 저도 해보고 싶었어요',
  '아 그러셨구나',
  '맞아요 맞아요',
  '나중에 같이 가요~',
];
const QUESTION_REPLIES = [
  '저는 주로 집에서 쉬어요 ㅎㅎ',
  '음 생각해본 적 없는데 좋은 질문이네요',
  '요즘은 운동 다니고 있어요',
  '넷플릭스 보는 게 취미예요',
];
const ROOM_CHATTER = [
  '안녕하세요~',
  '오늘 날씨 좋네요',
  '여기 사람 많네요 ㅎㅎ',
  '다들 어디 사세요?',
  '저 방금 들어왔어요',
  '주말에 뭐 하실 거예요?',
];

const opt = { ...DEFAULTS, ...parseArgs(process.argv.slice(2)) };
const bots = [];
let shuttingDown = false;
// 자동 행동(좋아요 되돌려주기 · 매칭 후 먼저 인사 · 자동 답장) 켜짐/꺼짐.
// 시연할 때 대사를 내가 직접 넣고 싶으면 `auto off` 로 끄고 say 로 조종한다.
let autoMode = true;
let lastRoom = opt.room ? Number(opt.room) : 0;   // roomsay 대상

//-------------------------------------------------------------------------------------------------
// 봇 하나
//-------------------------------------------------------------------------------------------------

class Bot {
  constructor(account, demoUserId) {
    this.account = account;
    this.demoUserId = demoUserId;
    this.token = null;
    this.socket = null;
    this.timers = [];
    this.likedBack = new Set();   // 이미 되돌려준 상대
    this.channels = new Set();    // 내가 들어가 있는 DIRECT 채널
    this.name = account.email.split('@')[0];
  }

  async start() {
    const login = await api('POST', '/api/auth/login', {
      email: this.account.email, password: this.account.password,
    });
    this.token = login?.data?.accessToken;
    if (!this.token) throw new Error('로그인 실패');
    this.userId = this.account.userId ?? jwtSubject(this.token);

    this.connect();

    // 데모 계정에게 먼저 좋아요를 하나 보낸다.
    // 이러면 데모 화면의 '받은 좋아요'가 채워지고, 피드에서 오른쪽으로 넘기는 순간 바로 매칭된다.
    if (autoMode && this.demoUserId && this.demoUserId !== this.userId) {
      this.after(rand(500, 8000), () => { if (autoMode) this.like(this.demoUserId); });
    }

    // 나를 좋아한 사람 확인 → 되돌려주기. 데모가 먼저 눌러도 몇 초 뒤 매칭이 성사된다.
    this.every(Number(opt['poll-ms']) + rand(0, 1500), () => this.likeBackNewOnes());

    if (opt.room) {
      this.after(rand(1000, 6000), () => this.joinRoom(Number(opt.room)));
    }
  }

  connect() {
    const socket = new WebSocket(`${opt.ws}/ws?token=${encodeURIComponent(this.token)}`);
    this.socket = socket;

    socket.addEventListener('open', () => {
      log(`${this.name} 접속`);
      this.every(30_000, () => this.send('HEARTBEAT', {}));
    });

    socket.addEventListener('message', (event) => {
      let envelope;
      try { envelope = JSON.parse(event.data); } catch { return; }
      if (envelope.type === 'MATCH_NOTIFICATION') this.onMatched(envelope.payload);
      if (envelope.type === 'CONTENT_MESSAGE') this.onMessage(envelope.payload);
    });

    socket.addEventListener('close', () => {
      if (shuttingDown) return;
      log(`${this.name} 연결 끊김 — 3초 후 재연결`);
      this.after(3000, () => this.connect());
    });

    socket.addEventListener('error', () => {});
  }

  /** 매칭됐다는 알림. 사람처럼 잠깐 뜸을 들이고 먼저 말을 건다. */
  onMatched(payload) {
    const channelId = payload?.channelId;
    if (!channelId || this.channels.has(channelId)) return;
    this.channels.add(channelId);
    this.lastChannel = channelId;
    log(`${this.name} 매칭! channelId=${channelId}`);

    if (!autoMode) return;   // 수동 모드 — 대사는 사람이 넣는다

    this.after(rand(3000, 9000), () => {
      if (autoMode) this.sendMessage(channelId, pick(GREETINGS));
    });
    // 상대가 아무 말도 안 하면 한 번 더 툭 던진다. 진짜 사람도 그러니까.
    this.after(rand(25_000, 45_000), () => {
      if (autoMode && !this.gotReplyIn?.[channelId]) this.sendMessage(channelId, pick(SMALL_TALK));
    });
  }

  /** 상대 메시지에 반응. 타이핑하는 시간처럼 몇 초 쉬고 답한다. */
  onMessage(payload) {
    const channelId = payload?.channelId;
    const senderId = payload?.senderId;
    const content = String(payload?.content ?? '');
    if (!channelId || !senderId || senderId === this.userId) return;

    this.gotReplyIn = this.gotReplyIn ?? {};
    this.gotReplyIn[channelId] = true;
    if (!autoMode) return;

    const isQuestion = content.includes('?') || content.includes('까') || content.includes('나요');
    const reply = isQuestion ? pick(QUESTION_REPLIES) : pick(REPLIES);
    this.after(rand(2000, 7000), () => {
      if (autoMode) this.sendMessage(channelId, reply);
    });
  }

  async likeBackNewOnes() {
    if (!autoMode) return;
    const received = await api('GET', '/api/likes/received', null, this.token);
    const list = received?.data ?? [];
    for (const item of list) {
      const from = item.partner?.userId;   // ReceivedLikeView.partner 가 상대 프로필 카드다
      if (!from || this.likedBack.has(from)) continue;
      this.likedBack.add(from);
      if (Math.random() > Number(opt['like-back-rate'])) continue;   // 일부는 그냥 넘긴다
      this.after(rand(1000, 6000), () => this.like(from));
    }
  }

  async like(toUserId) {
    const res = await api('POST', '/api/likes', { toUserId, likeType: 'LIKE' }, this.token);
    if (res !== null) log(`${this.name} → 좋아요`);
  }

  async joinRoom(channelId) {
    await api('POST', `/api/rooms/${channelId}/join`, {}, this.token);
    log(`${this.name} 오픈방 ${channelId} 입장`);
    this.every(rand(20_000, 60_000), () => this.sendMessage(channelId, pick(ROOM_CHATTER)));
  }

  sendMessage(channelId, content) {
    const ok = this.send('SEND_MESSAGE', {
      channelId, content, clientMessageId: crypto.randomUUID(),
    });
    if (ok) log(`${this.name}: ${content}`);
  }

  send(type, payload) {
    if (this.socket?.readyState !== WebSocket.OPEN) return false;
    this.socket.send(JSON.stringify({ type, payload }));
    return true;
  }

  after(ms, fn) { this.timers.push(setTimeout(() => { if (!shuttingDown) fn(); }, ms)); }
  every(ms, fn) { this.timers.push(setInterval(() => { if (!shuttingDown) fn(); }, ms)); }

  stop() {
    for (const timer of this.timers) { clearTimeout(timer); clearInterval(timer); }
    try { this.socket?.close(); } catch {}
  }
}

//-------------------------------------------------------------------------------------------------
// 준비
//-------------------------------------------------------------------------------------------------

/** 시더가 남긴 모수 파일들에서 계정을 모은다. */
async function loadAccounts() {
  let files = [];
  try {
    files = (await readdir(opt['seed-dir'])).filter(name => name.endsWith('.json'));
  } catch {
    throw new Error(`${opt['seed-dir']} 가 없다. 먼저 시더를 돌려라: node scripts/seed.mjs --count 200`);
  }
  if (files.length === 0) throw new Error(`${opt['seed-dir']} 에 모수 파일이 없다. 먼저 시더를 돌려라.`);

  const accounts = [];
  for (const file of files) {
    const parsed = JSON.parse(await readFile(`${opt['seed-dir']}/${file}`, 'utf8'));
    accounts.push(...parsed);
  }
  return accounts;
}

/** 데모 계정의 userId. 이메일만 준 경우 DB 에서 찾는다(개발용 스크립트라 docker exec 로 충분). */
async function resolveDemoUserId() {
  if (opt['demo-user-id']) return opt['demo-user-id'];
  if (!opt['demo-email']) return '';

  const sql = `select lower(concat(substr(hex(id),1,8),'-',substr(hex(id),9,4),'-',substr(hex(id),13,4),'-',substr(hex(id),17,4),'-',substr(hex(id),21,12))) from users where email = '${opt['demo-email']}'`;
  const { stdout } = await run('docker', [
    'exec', 'social-mysql', 'mysql', '-udev_user', '-pdev_password', 'social', '-N', '-B', '-e', sql,
  ]);
  const found = stdout.split('\n').map(line => line.trim()).filter(line => /^[0-9a-f-]{36}$/.test(line))[0];
  if (!found) throw new Error(`${opt['demo-email']} 계정을 못 찾았다`);
  return found;
}

//-------------------------------------------------------------------------------------------------
// 조종 콘솔
//-------------------------------------------------------------------------------------------------

function startConsole() {
  const readline = createInterface({ input: process.stdin, terminal: false });

  readline.on('line', async (raw) => {
    const line = raw.trim();
    if (!line) return;
    try {
      await handleCommand(line);
    } catch (e) {
      log(`명령 실패: ${e.message}`);
    }
  });
  readline.on('close', () => {});   // 파이프로 명령을 넣는 경우 여기서 끝나도 봇은 계속 돈다
}

async function handleCommand(line) {
  const [command, ...rest] = line.split(/\s+/);
  const arg = rest.join(' ');

  switch (command) {
    case 'help': printUsage(); return;

    case 'who': {
      for (const bot of bots) {
        const state = bot.socket?.readyState === WebSocket.OPEN ? '접속' : '끊김';
        log(`${bot.name} · ${state} · 매칭 채널 ${[...bot.channels].join(',') || '없음'}`);
      }
      log(`총 ${bots.length}명 · 자동 행동 ${autoMode ? 'on' : 'off'}`);
      return;
    }

    case 'auto': {
      autoMode = arg !== 'off';
      log(`자동 행동 ${autoMode ? 'on' : 'off'}`);
      return;
    }

    case 'like': {
      const count = Number(rest[0]) || 1;
      if (!demoUserId) { log('데모 계정이 지정 안 됐다 (--demo-email)'); return; }
      for (const bot of pickBots(count)) await bot.like(demoUserId);
      return;
    }

    case 'say': {
      // 첫 낱말이 봇 이름이면 그 봇이 말한다.
      const named = bots.find(bot => bot.name === rest[0]);
      // 무작위로 한 명 뽑고 매칭 여부를 보면 반쯤은 헛손질이 된다. 매칭된 봇 중에서 고른다.
      const matched = bots.filter(bot => bot.lastChannel);
      const bot = named ?? pickFrom(matched);
      const text = named ? rest.slice(1).join(' ') : arg;
      if (!bot) { log('매칭된 봇이 없다 — 먼저 매칭을 만들어라'); return; }
      if (!bot.lastChannel) { log(`${bot.name} 은 매칭된 채널이 없다`); return; }
      if (!text) { log('무슨 말을 할지 안 적었다'); return; }
      bot.sendMessage(bot.lastChannel, text);
      return;
    }

    case 'chat': {
      const count = Number(rest[0]) || 1;
      const talkers = shuffle(bots.filter(bot => bot.lastChannel)).slice(0, count);
      if (talkers.length === 0) { log('매칭된 봇이 없다'); return; }
      for (const bot of talkers) bot.sendMessage(bot.lastChannel, pick(SMALL_TALK));
      return;
    }

    case 'add': {
      const count = Number(rest[0]) || 1;
      const used = new Set(bots.map(bot => bot.account.email));
      const fresh = accounts.filter(a => !used.has(a.email) && a.userId !== demoUserId).slice(0, count);
      if (fresh.length === 0) { log('투입할 계정이 없다 — 시더를 더 돌려라'); return; }
      for (const account of fresh) {
        const bot = new Bot(account, demoUserId);
        bots.push(bot);
        try { await bot.start(); } catch (e) { log(`${account.email} 시작 실패: ${e.message}`); }
        await sleep(200);
      }
      log(`${fresh.length}명 추가 (총 ${bots.length}명)`);
      return;
    }

    case 'room': {
      const channelId = Number(rest[0]);
      if (!channelId) { log('channelId 를 적어라 · 예) room 42'); return; }
      for (const bot of bots) await bot.joinRoom(channelId);
      lastRoom = channelId;
      return;
    }

    case 'roomsay': {
      if (!lastRoom) { log('먼저 room <channelId> 로 들어가라'); return; }
      if (!arg) { log('무슨 말을 할지 안 적었다'); return; }
      pickFrom(bots)?.sendMessage(lastRoom, arg);
      return;
    }

    case 'quit': case 'exit': {
      shuttingDown = true;
      for (const bot of bots) bot.stop();
      log('종료');
      setTimeout(() => process.exit(0), 200);
      return;
    }

    default: log(`모르는 명령: ${command} (help 참고)`);
  }
}

/** 무작위로 n명 고른다 — 매번 같은 봇이 말하면 티가 난다. */
function pickBots(count) {
  return shuffle(bots).slice(0, Math.max(1, count));
}

function shuffle(list) {
  return [...list].sort(() => Math.random() - 0.5);
}

function pickFrom(list) {
  return list.length === 0 ? undefined : shuffle(list)[0];
}

//-------------------------------------------------------------------------------------------------
// 유틸
//-------------------------------------------------------------------------------------------------

async function api(method, path, body, token) {
  const headers = {};
  if (body != null) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;
  try {
    const res = await fetch(`${opt.gateway}${path}`, {
      method, headers, body: body == null ? undefined : JSON.stringify(body),
    });
    const text = await res.text();
    if (!res.ok) return null;
    return text ? JSON.parse(text) : {};
  } catch {
    return null;
  }
}

function jwtSubject(token) {
  return JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8')).sub;
}

const pick = (list) => list[Math.floor(Math.random() * list.length)];
const rand = (min, max) => min + Math.floor(Math.random() * (max - min));
const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));
const stamp = () => new Date().toLocaleTimeString('ko-KR', { hour12: false });
const log = (message) => console.log(`[${stamp()}] ${message}`);

/** 파일 상단 주석을 그대로 출력한다. 설명을 두 군데 적으면 반드시 한쪽이 낡는다. */
function printUsage() {
  const source = readFileSync(new URL(import.meta.url), 'utf8');
  const header = source.slice(source.indexOf('/**') + 3, source.indexOf(' */'));
  console.log(header.split('\n').map(line => line.replace(/^\s*\* ?/, '')).join('\n'));
}

function parseArgs(argv) {
  const out = {};
  for (let i = 0; i < argv.length; i++) {
    if (!argv[i].startsWith('--')) continue;
    const key = argv[i].slice(2);
    const next = argv[i + 1];
    if (next === undefined || next.startsWith('--')) out[key] = true;
    else { out[key] = next; i++; }
  }
  return out;
}

//-------------------------------------------------------------------------------------------------
// main
//-------------------------------------------------------------------------------------------------

if (opt.help) {
  printUsage();
  process.exit(0);
}

const accounts = await loadAccounts();
const demoUserId = await resolveDemoUserId();
const chosen = accounts.filter(a => a.userId !== demoUserId).slice(0, Number(opt.bots));

console.log(`봇 ${chosen.length}명 · 게이트웨이 ${opt.gateway}`);
console.log(demoUserId
  ? `데모 계정 ${opt['demo-email'] || demoUserId} 에게 좋아요를 보내고, 받은 좋아요는 되돌려준다.`
  : '데모 계정 지정 안 됨 — 봇끼리만 상호작용한다 (--demo-email 로 지정)');
if (opt.room) console.log(`오픈방 ${opt.room} 에 들어가서 잡담한다.`);
console.log('Ctrl+C 로 종료\n');

for (const account of chosen) {
  const bot = new Bot(account, demoUserId);
  bots.push(bot);
  try {
    await bot.start();
  } catch (e) {
    log(`${account.email} 시작 실패: ${e.message}`);
  }
  await sleep(rand(200, 600));   // 한꺼번에 붙지 않게 살짝 흩어준다
}

log(`봇 ${bots.length}명 가동 중`);
console.log('명령을 타이핑해 조종할 수 있다. help 를 쳐 보라.\n');

startConsole();

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    shuttingDown = true;
    log('종료 중...');
    for (const bot of bots) bot.stop();
    setTimeout(() => process.exit(0), 300);
  });
}
