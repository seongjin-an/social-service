# 오픈채팅 게시판 (P3) — 기능 명세

> 담당: **message-service** (별도 openchat-service 를 두지 않았다 — 이유는 [배치 결정](#배치-결정) 참고)
> 경로 접두사: `/api/rooms`
> 게이트웨이 라우트: `message-service` 의 predicate 에 `/api/rooms/**` 추가, `JwtAuthentication` 적용

---

## 한 줄 요약

방 목록을 게시판처럼 보여주고(카테고리 탭 · 제목 검색 · 커서 페이지네이션), 들어가면
**1:1 채팅과 완전히 동일한 파이프라인**으로 여럿이 실시간 대화한다.

---

## 왜 새 테이블이 없나

오픈방은 `channel` 의 한 종류다(`type='OPEN'`). 멤버십은 `channel_members`, 대화는 `message`.
그래서 실시간 경로에 **손댈 것이 없다**:

```
WS SEND_MESSAGE
  → connection-service ──(message-relay)──▶ message-service
      MessageRelayHandler: CLOSED 가드 → 멤버십 가드 → 멱등 검사 → message INSERT + outbox
  → Debezium(outbox) ──(message-fanout)──▶ fanout-delivery-service
      recipientIds 를 순회하며 ws:user:{userId} 로 인스턴스를 찾아 배달
  → connection-instance-{id} ──▶ connection-service ──▶ WS CONTENT_MESSAGE
```

`recipientIds` 의 출처는 `OutboxEventWriter` → `ChannelMemberCacheService.getRecipientIds()`
→ Redis `channel:members:{channelId}` SET (miss 면 DB 재구성). **오픈방 멤버가 이 SET 에
들어가는 순간 단체 대화가 된다.** connection-service 와 fanout-delivery-service 는 P3 때문에
한 줄도 바뀌지 않았다.

### 추가된 컬럼 (ddl-auto=update 로 자동 반영)

`channel` — DIRECT 채널에서는 전부 NULL

| 컬럼 | 타입 | 용도 |
|---|---|---|
| `category` | VARCHAR(50) | 게시판 탭. `RoomCategory` enum 이름 |
| `owner_id` | BINARY(16) | 방장. 나가면 양도 또는 폐쇄 |
| `max_members` | INT | 정원 (2~500) |
| `member_count` | INT | 인원 비정규화 캐시. **원자적 UPDATE 로만 갱신** |

`channel_members`

| 컬럼 | 타입 | 용도 |
|---|---|---|
| `role` | VARCHAR(10) | `OWNER`\|`MEMBER`. DIRECT 는 NULL |

인덱스 — `schema.sql` 초안의 `(type, category, status)` 하나를 **둘로 나눴다**:

```sql
idx_channel_open_browse    (type, status, channel_id)             -- "전체" 탭
idx_channel_open_category  (type, status, category, channel_id)   -- 카테고리 탭
```

초안대로 하나만 두면 "전체" 탭이 `category` 를 건너뛰어야 해서 `channel_id` 범위를 쓸 수 없다
(커서 페이지네이션이 인덱스를 못 탄다).

---

## API

전부 `X-User-Id`(게이트웨이 JWT 필터 주입) 필요. 누락 시 401.

### F3-1. 방 생성 · `POST /api/rooms` → 201

```json
{ "title": "한강 러닝 같이 해요", "category": "SPORTS", "maxMembers": 30 }
```

- `category`: `HOBBY` | `SPORTS` | `FOOD` | `MUSIC` | `TRAVEL` | `ETC`
- `title` 최대 100자, `maxMembers` 2~500
- 채널 INSERT + 방장 멤버 INSERT 가 **한 트랜잭션** → 반쪽 방이 생기지 않는다
- 만든 사람이 방장이자 첫 멤버라 `memberCount=1` 로 시작

### F3-2. 방 탐색 · `GET /api/rooms`

| 파라미터 | 기본 | 설명 |
|---|---|---|
| `category` | 없음(=전체) | 탭 |
| `q` | 없음 | 제목 부분 일치 |
| `cursor` | 없음(=1페이지) | 직전 응답의 `nextCursor` |
| `size` | 20 | 최대 50 |

```json
{
  "items": [
    { "channelId": 7, "title": "한강 러닝 같이 해요", "category": "SPORTS",
      "ownerId": "29c1…", "maxMembers": 30, "memberCount": 3,
      "onlineCount": 2, "joined": false, "owner": false, "createdAt": "…" }
  ],
  "nextCursor": 7
}
```

**커서에 버전이 없다** — 추천 피드(`/api/feed`)와 다른 점이다. 피드의 커서는
`base64url("{snapshotVersion}:{offset}")` 로 스냅샷을 함께 가리켜야 했다. 정렬 키가
거리·태그 점수라서 사용자가 움직이거나 태그가 바뀌면 순서가 재배열되고, 그러면 넘긴
페이지의 카드가 다시 나오거나 건너뛰어진다.

게시판의 정렬 키는 **단조 증가하는 PK(`channel_id`)** 다. 새 방은 언제나 1페이지 위쪽에만
끼어들고 이미 넘긴 구간의 순서는 절대 변하지 않는다. 그래서 커서가 그냥 "마지막으로 본
channelId" 하나로 충분하고, 스냅샷도 TTL 도 필요 없다.

> `q` 는 지금 `LIKE '%q%'` 다 → 방이 많아지면 풀스캔. `schema.sql` 의
> `ft_channel_title` FULLTEXT 로 갈아탈 지점이다(네이티브 쿼리 필요).

### 내 방 · `GET /api/rooms/me`
같은 응답 형태. `channel_members` JOIN + ACTIVE 만.

### 방 상세 · `GET /api/rooms/{channelId}`
`joined` / `owner` 가 **호출한 사람 기준**으로 채워진다.

### F3-4. presence · `GET /api/rooms/{channelId}/presence`

```json
{ "channelId": 7, "memberCount": 3, "onlineCount": 2 }
```

`memberCount`(가입)와 `onlineCount`(지금 접속)는 **다른 값이고 다른 뜻**이다.
전용 presence 키를 새로 만들지 않고 connection-service 가 이미 관리하는
`ws:user:{userId}` 의 존재 여부로 센다 — 접속 상태의 진실을 두 곳에 쓰면 어긋나는
순간부터 어느 쪽도 못 믿는다.

따라서 `onlineCount` 는 **근사값**이다. 프로세스가 그냥 죽으면 연결 키 TTL 이 지나기
전까지 접속 중으로 잡힌다. 게시판의 "● 2명"에는 충분하지만 정원 판정에는 쓰지 않는다
(정원은 `member_count` 가 지킨다).

성능: 게시판 한 장(20개 방, 멤버 수백 명)에서 방마다 물어보면 쿼리 20번 + Redis 왕복
수백 번이다. `findByChannelIdIn` **한 번** + `executePipelined` EXISTS **한 번**으로 끝낸다.

### F3-3. 입장 · `POST /api/rooms/{channelId}/join` → 200

멱등하다. 이미 멤버면 아무것도 하지 않고 현재 방 정보를 돌려준다.

**순서가 중요하다 — 자리를 먼저 예약하고 멤버를 INSERT 한다.**

```sql
UPDATE channel SET member_count = member_count + 1
WHERE channel_id = ? AND status = 'ACTIVE' AND member_count < max_members
```

정원 조건이 UPDATE 의 WHERE 에 있으므로, 남은 1자리에 100명이 동시에 몰려도
**정확히 1건만** 1을 반환한다. 엔티티를 읽어 +1 하고 저장하는 방식이면 lost update 로
정원이 샌다. 0이 반환되면 409.

반대 순서(멤버 INSERT → 카운트 증가)로 하면 "멤버는 들어갔는데 카운트는 못 올린"
상태가 남는다.

에러: 없는 방/DIRECT 채널 404 · 정원 초과 409 · 닫힌 방 409

### F3-3. 퇴장 · `DELETE /api/rooms/{channelId}/leave` → 204

멱등(멤버가 아니어도 204).

**방장이 나가면 방을 닫지 않고 최초 입장자에게 넘긴다** — 남은 사람들의 대화를 방장
사정으로 끊지 않기 위해서다. 아무도 안 남으면 그때 닫는다(게시판에서 사라지고,
`MessageRelayHandler` 의 CLOSED 가드가 잔여 전송을 막는다).

---

## 대화

오픈방 전용 대화 API는 **없다**.

| | 경로 |
|---|---|
| 전송 | WS `{"type":"SEND_MESSAGE","payload":{"channelId":7,"content":"…","clientMessageId":"<uuid>"}}` |
| 수신 | WS `CONTENT_MESSAGE` (방 멤버 전원) |
| 히스토리 | `GET /api/channels/{channelId}/messages?size=50` |

프론트도 1:1 과 오픈방이 같은 `<ChatRoom>` 컴포넌트를 쓴다. 서버에서 같은 파이프라인인데
클라이언트에서 나눌 이유가 없다.

---

## 함께 막은 보안 구멍 — 비멤버 전송

`MessageRelayHandler` 에 **발신자 멤버십 가드**를 추가했다.

오픈채팅이 생기면서 방 목록이 공개돼 누구나 `channelId` 를 알 수 있게 됐다. 가드가
없으면 입장하지 않은 사람이 WS 로 `channelId` 만 바꿔 아무 방에나 글을 쓸 수 있다.
(사실 DIRECT 채널에도 같은 구멍이 있었다 — 남의 1:1 대화방에 끼어들기.)

비용은 0이다: 바로 다음 줄의 `OutboxEventWriter` 가 읽을 `channel:members:{channelId}`
캐시를 여기서 먼저 읽을 뿐이다.

---

## 캐시 무효화를 커밋 후로 옮긴 이유

`ChannelMemberCacheService.invalidateAfterCommit()` 을 새로 만들어 입장/퇴장/방 생성에 쓴다.

트랜잭션 **안에서** 지우면 이런 일이 생긴다:

1. 입장 트랜잭션이 `channel:members:{7}` 을 지움
2. 커밋 전에 그 방으로 메시지가 도착
3. 팬아웃이 캐시 miss → **아직 커밋 안 된** DB 를 읽어 재구성 → 새 멤버가 빠진 목록
4. 그 목록이 TTL **30분** 동안 굳는다 → 새 멤버는 방에 있는데 30분간 메시지를 못 받는다

커밋 후로 옮기면 "커밋되지 않은 상태를 30분 캐싱"이 원천적으로 불가능해진다.

---

## 배치 결정

`schema.sql` 메모 F 가 "소유 테이블이 없으니 별도 서비스로 뺄지 재검토"로 남겨둔 지점 —
**message-service 안의 모듈로 확정했다.**

소유 테이블이 하나도 없어서 서비스를 나누면 방 생성(채널 INSERT + 방장 멤버 INSERT)과
입장(자리 예약 + 멤버 INSERT)이 전부 두 서비스에 걸친 분산 트랜잭션이 된다. 얻는 것은
서비스 개수 하나이고, 잃는 것은 로컬 트랜잭션이 공짜로 주던 정합성이다.

```
services/message-service/src/main/java/com/social/message/
├── controller/
│   ├── OpenRoomController.java            # /api/rooms
│   ├── GlobalExceptionHandler.java        # ★ 새로 추가 (아래 참고)
│   └── request/CreateRoomRequest.java
├── domain/
│   ├── RoomCategory.java                  # 게시판 탭 enum
│   └── ChannelMemberRole.java             # OWNER | MEMBER
└── service/openchat/
    ├── OpenRoomService.java               # 생성/탐색/입장/퇴장
    ├── RoomPresenceService.java           # ws:user 파이프라인 조회
    ├── OpenRoom.java · OpenRoomPage.java · RoomPresence.java · CreateRoom.java
```

> `GlobalExceptionHandler` 가 message-service 에는 **없었다** — `BusinessException` 이
> 전부 500 으로 나가고 있었다. 오픈채팅은 "정원 초과 409 · 없는 방 404 · 미인증 401"을
> 클라이언트가 구분해야 해서 matching/recommendation 과 같은 매핑으로 추가했다.

---

## 프론트

| 경로 | 화면 |
|---|---|
| `/rooms` | 게시판 — 카테고리 탭, 제목 검색, 방 만들기, "더 보기" |
| `/rooms/{channelId}` | 방 — 제목·정원·접속자 헤더 + (입장 후) 대화창 |

- 입장 전에는 방 정보와 "입장하기"만 — 서버도 비멤버 전송을 거부하므로 화면과 서버의 판정이 같다
- `onlineCount` 는 서버가 밀어주는 이벤트가 없어서 15초 주기로 `presence` 를 다시 물어본다
- `<ChatRoom>` 을 1:1(`/chat/{id}`)과 공용으로 쓴다. 오픈방은 `showSenderName` 만 켠다

---

## 남은 것

- `q` 검색을 FULLTEXT 로 (방이 많아지면)
- 수천 명 방의 fanout 지연 — **P4 부하 시나리오 F4-4** 의 한 축
- 강퇴/신고 등 moderation (features.md 의 옵션 항목)
