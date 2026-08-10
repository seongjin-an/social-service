"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { AppShell } from "@/components/AppShell";
import { api, ApiError } from "@/lib/api";
import { useToast } from "@/lib/toast";
import {
  ROOM_CATEGORIES,
  categoryEmoji,
  categoryLabel,
  type CreateRoomRequest,
  type OpenRoom,
  type OpenRoomPage,
  type RoomCategory,
} from "@/lib/types";

type Tab = RoomCategory | "ALL";

/**
 * 오픈채팅 게시판 (F3-2). 카테고리 탭 + 제목 검색 + "더 보기" 커서 페이지네이션.
 *
 * <p>커서는 마지막 방의 channelId 다. 추천 피드처럼 스냅샷/버전을 들 필요가 없는 이유는
 * 정렬 키가 점수가 아니라 단조 증가하는 PK 라서, 보는 중에 방이 새로 생겨도 이미 넘긴
 * 페이지의 순서가 흔들리지 않기 때문이다(새 방은 1페이지 위쪽에만 끼어든다).
 */
export default function RoomsPage() {
  const router = useRouter();
  const { push } = useToast();

  const [tab, setTab] = useState<Tab>("ALL");
  const [query, setQuery] = useState("");
  const [applied, setApplied] = useState("");   // 실제로 서버에 보낸 검색어

  const [rooms, setRooms] = useState<OpenRoom[] | null>(null);
  const [nextCursor, setNextCursor] = useState<number | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [composing, setComposing] = useState(false);

  const buildPath = useCallback(
    (cursor: number | null) => {
      const params = new URLSearchParams({ size: "20" });
      if (tab !== "ALL") params.set("category", tab);
      if (applied) params.set("q", applied);
      if (cursor !== null) params.set("cursor", String(cursor));
      return `/api/rooms?${params.toString()}`;
    },
    [tab, applied],
  );

  // 탭/검색어가 바뀌면 목록을 처음부터 다시 받는다(커서도 리셋).
  useEffect(() => {
    let cancelled = false;
    setRooms(null);
    setError(null);

    api<OpenRoomPage>(buildPath(null))
      .then((page) => {
        if (cancelled) return;
        setRooms(page.items);
        setNextCursor(page.nextCursor);
      })
      .catch((caught) => {
        if (cancelled) return;
        setError(caught instanceof ApiError ? caught.message : "방 목록을 불러오지 못했습니다");
      });

    return () => {
      cancelled = true;
    };
  }, [buildPath]);

  const loadMore = async () => {
    if (nextCursor === null || loadingMore) return;
    setLoadingMore(true);
    try {
      const page = await api<OpenRoomPage>(buildPath(nextCursor));
      setRooms((prev) => [...(prev ?? []), ...page.items]);
      setNextCursor(page.nextCursor);
    } catch (caught) {
      push({
        tone: "error",
        title: "더 불러오지 못했습니다",
        body: caught instanceof ApiError ? caught.message : "잠시 후 다시 시도해 주세요",
      });
    } finally {
      setLoadingMore(false);
    }
  };

  const createRoom = async (request: CreateRoomRequest) => {
    const room = await api<OpenRoom>("/api/rooms", { method: "POST", body: request });
    setComposing(false);
    push({ title: "방을 열었어요", body: room.title });
    router.push(`/rooms/${room.channelId}`);
  };

  return (
    <AppShell>
      <div className="mb-4 flex items-center gap-3">
        <h1 className="text-xl font-bold">오픈채팅</h1>
        <button
          onClick={() => setComposing((v) => !v)}
          className="ml-auto rounded-lg bg-rose-500 px-3 py-1.5 text-sm font-medium text-white hover:bg-rose-600"
        >
          {composing ? "닫기" : "+ 방 만들기"}
        </button>
      </div>

      {composing && <RoomComposer onSubmit={createRoom} />}

      {/* 카테고리 탭 */}
      <div className="mb-3 flex flex-wrap gap-1.5">
        <TabButton active={tab === "ALL"} onClick={() => setTab("ALL")}>
          전체
        </TabButton>
        {ROOM_CATEGORIES.map((category) => (
          <TabButton
            key={category.value}
            active={tab === category.value}
            onClick={() => setTab(category.value)}
          >
            {category.emoji} {category.label}
          </TabButton>
        ))}
      </div>

      {/* 제목 검색 */}
      <form
        onSubmit={(event) => {
          event.preventDefault();
          setApplied(query.trim());
        }}
        className="mb-4 flex gap-2"
      >
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="방 제목으로 검색"
          className="flex-1 rounded-xl border border-black/10 bg-white px-3 py-2 text-sm outline-none focus:border-rose-400 dark:border-white/15 dark:bg-neutral-800"
        />
        <button
          type="submit"
          className="rounded-xl border border-black/10 px-4 text-sm hover:bg-black/5 dark:border-white/15 dark:hover:bg-white/10"
        >
          검색
        </button>
        {applied && (
          <button
            type="button"
            onClick={() => {
              setQuery("");
              setApplied("");
            }}
            className="rounded-xl px-3 text-sm opacity-60 hover:opacity-100"
          >
            초기화
          </button>
        )}
      </form>

      {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}

      {!rooms ? (
        <p className="text-sm opacity-60">불러오는 중…</p>
      ) : rooms.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-black/15 p-10 text-center dark:border-white/15">
          <p className="font-medium">
            {applied ? `"${applied}" 에 맞는 방이 없어요` : "아직 열린 방이 없어요"}
          </p>
          <p className="mt-1 text-sm opacity-60">첫 번째 방을 열어보세요.</p>
        </div>
      ) : (
        <>
          <ul className="space-y-2">
            {rooms.map((room) => (
              <RoomRow key={room.channelId} room={room} />
            ))}
          </ul>

          {nextCursor !== null && (
            <button
              onClick={() => void loadMore()}
              disabled={loadingMore}
              className="mt-3 w-full rounded-xl border border-black/10 py-2.5 text-sm hover:bg-black/5 disabled:opacity-50 dark:border-white/15 dark:hover:bg-white/10"
            >
              {loadingMore ? "불러오는 중…" : "더 보기"}
            </button>
          )}
        </>
      )}
    </AppShell>
  );
}

function RoomRow({ room }: { room: OpenRoom }) {
  const full = room.memberCount >= room.maxMembers;

  return (
    <li>
      <Link
        href={`/rooms/${room.channelId}`}
        className="flex items-center gap-3 rounded-2xl border border-black/10 bg-white p-3 transition hover:border-rose-300 dark:border-white/10 dark:bg-neutral-900 dark:hover:border-rose-700"
      >
        <span className="text-2xl">{categoryEmoji(room.category)}</span>

        <div className="min-w-0 flex-1">
          <p className="flex items-center gap-2 font-semibold">
            <span className="truncate">{room.title}</span>
            {room.joined && (
              <span className="shrink-0 rounded-full bg-emerald-500/15 px-2 py-0.5 text-xs font-medium text-emerald-700 dark:text-emerald-400">
                참여중
              </span>
            )}
          </p>
          <p className="mt-0.5 flex items-center gap-2 text-xs opacity-60">
            <span>{categoryLabel(room.category)}</span>
            <span>·</span>
            <span className={full ? "text-amber-600 dark:text-amber-400" : ""}>
              {room.memberCount}/{room.maxMembers}
            </span>
          </p>
        </div>

        {/* 지금 붙어 있는 사람 수 — 방이 살아 있는지 한눈에 보인다 */}
        <span className="shrink-0 text-xs opacity-70">
          <span
            className={[
              "mr-1 inline-block h-2 w-2 rounded-full align-middle",
              room.onlineCount > 0 ? "bg-emerald-500" : "bg-neutral-400",
            ].join(" ")}
          />
          {room.onlineCount}명
        </span>
      </Link>
    </li>
  );
}

function TabButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      className={[
        "rounded-full px-3 py-1.5 text-sm transition",
        active
          ? "bg-rose-500 text-white"
          : "border border-black/10 hover:bg-black/5 dark:border-white/15 dark:hover:bg-white/10",
      ].join(" ")}
    >
      {children}
    </button>
  );
}

function RoomComposer({ onSubmit }: { onSubmit: (request: CreateRoomRequest) => Promise<void> }) {
  const [title, setTitle] = useState("");
  const [category, setCategory] = useState<RoomCategory>("HOBBY");
  const [maxMembers, setMaxMembers] = useState(30);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!title.trim()) return;
    setSubmitting(true);
    setError(null);
    try {
      await onSubmit({ title: title.trim(), category, maxMembers });
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "방을 만들지 못했습니다");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form
      onSubmit={submit}
      className="mb-4 space-y-3 rounded-2xl border border-black/10 bg-white p-4 dark:border-white/10 dark:bg-neutral-900"
    >
      <input
        value={title}
        onChange={(event) => setTitle(event.target.value)}
        maxLength={100}
        placeholder="방 제목 (예: 한강 러닝 같이 해요)"
        className="w-full rounded-xl border border-black/10 bg-white px-3 py-2 text-sm outline-none focus:border-rose-400 dark:border-white/15 dark:bg-neutral-800"
      />

      <div className="flex flex-wrap gap-1.5">
        {ROOM_CATEGORIES.map((option) => (
          <TabButton
            key={option.value}
            active={category === option.value}
            onClick={() => setCategory(option.value)}
          >
            {option.emoji} {option.label}
          </TabButton>
        ))}
      </div>

      <label className="block text-sm">
        <span className="opacity-70">정원 {maxMembers}명</span>
        {/* 서버 검증: 2~500 */}
        <input
          type="range"
          min={2}
          max={500}
          step={1}
          value={maxMembers}
          onChange={(event) => setMaxMembers(Number(event.target.value))}
          className="mt-1 w-full accent-rose-500"
        />
      </label>

      {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}

      <button
        type="submit"
        disabled={submitting || !title.trim()}
        className="w-full rounded-xl bg-rose-500 py-2.5 text-sm font-semibold text-white hover:bg-rose-600 disabled:opacity-40"
      >
        {submitting ? "만드는 중…" : "방 열기"}
      </button>
    </form>
  );
}
