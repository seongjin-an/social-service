/** 백엔드 API 계약. 서버 쪽 record 와 1:1 로 맞춰 둔다. */

// ── 인증 (user-service) ──────────────────────────────────────────────────────
export interface TokenPair {
  accessToken: string;
  refreshToken: string;
}

export interface UserResponse {
  id: string;
  email: string;
  name: string;
  phone: string | null;
  role: string;
}

// ── 프로필 (profile-service) ─────────────────────────────────────────────────
export type Gender = "MALE" | "FEMALE";

export interface ProfileImage {
  imageId: string;
  imageUrl: string;
  primaryImage: boolean;
  sortOrder: number | null;
}

export interface ProfileResponse {
  profileId: string;
  gender: Gender | null;
  age: number | null;
  bio: string | null;
  prefGender: Gender | null;
  prefAgeMin: number | null;
  prefAgeMax: number | null;
  prefDistanceKm: number | null;
  tags: string[];
  images: ProfileImage[];
}

/** 서버 검증: bio 는 필수, 선호 4개 전부 필수, prefDistanceKm 은 1~10. */
export interface ProfileWriteRequest {
  gender: Gender;
  birthday: string; // yyyyMMdd
  bio: string;
  tags: string[];
  prefGender: Gender;
  prefAgeMin: number;
  prefAgeMax: number;
  prefDistanceKm: number;
}

// ── 카드 (libs/common ProfileCard 와 동일) ───────────────────────────────────
export interface ProfileCard {
  userId: string;
  profileId: string | null;
  age: number | null;
  gender: Gender | null;
  bio: string | null;
  tags: string[];
  imageUrl: string | null;
}

// ── 추천 피드 (recommendation-service) ───────────────────────────────────────
export interface FeedItem {
  profile: ProfileCard;
  distanceKm: number;
  sharedTags: string[];
}

export interface FeedView {
  items: FeedItem[];
  /** null 이면 더 없음. 항상 이 값으로 갈아끼운다(옛 커서를 재사용하면 1페이지만 돈다). */
  nextCursor: string | null;
}

// ── 좋아요 / 매칭 (matching-service) ─────────────────────────────────────────
export type LikeType = "LIKE" | "PASS" | "SUPER";

export interface MatchView {
  matchId: string;
  /** Saga 백필 전이면 null — "채팅방 준비 중"으로 표시한다. */
  channelId: number | null;
  partner: ProfileCard;
  matchedAt: string;
}

export interface ReceivedLikeView {
  partner: ProfileCard;
  type: LikeType;
  likedAt: string;
}

// ── 오픈채팅 게시판 (message-service · P3) ───────────────────────────────────
export type RoomCategory = "HOBBY" | "SPORTS" | "FOOD" | "MUSIC" | "TRAVEL" | "ETC";

/** 게시판 탭. 서버 enum(RoomCategory)과 값이 1:1 이어야 한다. */
export const ROOM_CATEGORIES: { value: RoomCategory; label: string; emoji: string }[] = [
  { value: "HOBBY", label: "취미", emoji: "🎨" },
  { value: "SPORTS", label: "운동", emoji: "🏃" },
  { value: "FOOD", label: "맛집", emoji: "🍜" },
  { value: "MUSIC", label: "음악", emoji: "🎸" },
  { value: "TRAVEL", label: "여행", emoji: "✈️" },
  { value: "ETC", label: "기타", emoji: "💬" },
];

export function categoryLabel(category: RoomCategory | null): string {
  return ROOM_CATEGORIES.find((c) => c.value === category)?.label ?? "기타";
}

export function categoryEmoji(category: RoomCategory | null): string {
  return ROOM_CATEGORIES.find((c) => c.value === category)?.emoji ?? "💬";
}

export interface OpenRoom {
  /** 오픈방도 channel 이다 — 이 값이 그대로 채팅 채널 id 다. */
  channelId: number;
  title: string;
  category: RoomCategory | null;
  ownerId: string | null;
  maxMembers: number;
  /** 가입 인원. 정원 판정의 기준. */
  memberCount: number;
  /** 지금 WS 로 붙어 있는 인원. 근사값(연결 TTL 만큼 늦게 줄어든다). */
  onlineCount: number;
  joined: boolean;
  owner: boolean;
  createdAt: string;
}

export interface OpenRoomPage {
  items: OpenRoom[];
  /** channelId 키셋 커서. null 이면 끝. 피드 커서와 달리 버전이 없다. */
  nextCursor: number | null;
}

export interface RoomPresence {
  channelId: number;
  memberCount: number;
  onlineCount: number;
}

export interface CreateRoomRequest {
  title: string;
  category: RoomCategory;
  maxMembers: number;
}

// ── 채팅 (message-service) ───────────────────────────────────────────────────
export interface Channel {
  channelId: number;
  title: string | null;
  type: "DIRECT" | "OPEN" | null;
  status: "ACTIVE" | "CLOSED" | null;
  matchId: string | null;
}

/**
 * 히스토리 API 의 메시지. **WS 로 오는 것과 필드명이 다르다** —
 * 여기는 {@code userId/userName}, WS 는 {@code senderId/senderName} 이다.
 * 화면에서는 {@link ChatMessage} 로 정규화해서 쓴다.
 */
export interface HistoryMessage {
  channelId: number;
  messageId: string;
  userId: string;
  userName: string | null;
  content: string;
  createdAt: number;
}

export interface MessageCursorResult {
  nextKey: number;
  messages: HistoryMessage[];
}

/** 화면에서 쓰는 통일된 메시지 모델. */
export interface ChatMessage {
  messageId: string;
  channelId: number;
  senderId: string;
  senderName: string | null;
  content: string;
  createdAt: number;
  clientMessageId?: string | null;
  /** 서버 확인 전 낙관적 렌더링 상태 */
  pending?: boolean;
}

// ── WebSocket ────────────────────────────────────────────────────────────────
/** 서버 → 클라 봉투. connection-service 의 WebSocketOutboundEnvelope 와 동일. */
export interface WsInbound<T = unknown> {
  type: string;
  payload: T;
}

export interface MatchNotificationPayload {
  userId: string;
  matchId: string;
  channelId: number;
}

export interface ContentMessagePayload {
  messageId: string;
  channelId: number;
  senderId: string;
  senderName: string | null;
  content: string;
  createdAt: number;
  clientMessageId: string | null;
}

export interface ReadEventPayload {
  channelId: number;
  readerId: string;
  lastReadMessageId: number;
}
