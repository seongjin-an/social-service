"use client";

import { use, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { AppShell } from "@/components/AppShell";
import { ChatRoom } from "@/components/ChatRoom";
import { api, ApiError } from "@/lib/api";
import { useToast } from "@/lib/toast";
import {
  categoryEmoji,
  categoryLabel,
  type OpenRoom,
  type RoomPresence,
} from "@/lib/types";

/** 접속자 수는 서버가 밀어주지 않으므로(별도 이벤트가 없다) 이 주기로 다시 물어본다. */
const PRESENCE_POLL_MS = 15_000;

/**
 * 오픈채팅방. 입장하지 않았으면 방 정보와 "입장하기"만 보이고,
 * 입장 후에 대화창이 열린다 — 서버도 비멤버의 전송을 거부하므로(MessageRelayHandler 멤버십 가드)
 * 화면과 서버의 판정이 같다.
 */
export default function RoomPage({
  params,
}: {
  params: Promise<{ channelId: string }>;
}) {
  const { channelId: channelIdParam } = use(params);
  const channelId = Number(channelIdParam);

  const router = useRouter();
  const { push } = useToast();

  const [room, setRoom] = useState<OpenRoom | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api<OpenRoom>(`/api/rooms/${channelId}`)
      .then(setRoom)
      .catch((caught) =>
        setError(caught instanceof ApiError ? caught.message : "방 정보를 불러오지 못했습니다"),
      );
  }, [channelId]);

  // 접속자 수만 주기적으로 갱신한다(방 정보 전체를 다시 받을 필요는 없다).
  // deps 에 room 을 넣으면 폴링 결과가 room 을 바꿔 매 주기마다 타이머가 재생성되므로 channelId 만 본다.
  useEffect(() => {
    const timer = setInterval(() => {
      api<RoomPresence>(`/api/rooms/${channelId}/presence`)
        .then((presence) =>
          setRoom((prev) =>
            prev
              ? { ...prev, memberCount: presence.memberCount, onlineCount: presence.onlineCount }
              : prev,
          ),
        )
        .catch(() => {
          /* presence 실패는 화면을 막을 이유가 없다 — 다음 주기에 다시 시도한다 */
        });
    }, PRESENCE_POLL_MS);
    return () => clearInterval(timer);
  }, [channelId]);

  const join = async () => {
    setBusy(true);
    try {
      setRoom(await api<OpenRoom>(`/api/rooms/${channelId}/join`, { method: "POST" }));
    } catch (caught) {
      push({
        tone: "error",
        title: "입장하지 못했습니다",
        body: caught instanceof ApiError ? caught.message : "잠시 후 다시 시도해 주세요",
      });
    } finally {
      setBusy(false);
    }
  };

  const leave = async () => {
    if (!confirm("이 방에서 나갈까요?")) return;
    setBusy(true);
    try {
      await api<void>(`/api/rooms/${channelId}/leave`, { method: "DELETE" });
      router.push("/rooms");
    } catch (caught) {
      push({
        tone: "error",
        title: "나가지 못했습니다",
        body: caught instanceof ApiError ? caught.message : "잠시 후 다시 시도해 주세요",
      });
      setBusy(false);
    }
  };

  if (error) {
    return (
      <AppShell>
        <BackLink />
        <p className="mt-4 text-sm text-red-600 dark:text-red-400">{error}</p>
      </AppShell>
    );
  }

  if (!room) {
    return (
      <AppShell>
        <BackLink />
        <p className="mt-4 text-sm opacity-60">불러오는 중…</p>
      </AppShell>
    );
  }

  const full = room.memberCount >= room.maxMembers;

  return (
    <AppShell>
      <BackLink />

      <div className="mb-3 mt-2 flex items-start gap-3">
        <span className="text-3xl">{categoryEmoji(room.category)}</span>

        <div className="min-w-0 flex-1">
          <h1 className="truncate text-lg font-bold">{room.title}</h1>
          <p className="mt-0.5 flex flex-wrap items-center gap-2 text-xs opacity-60">
            <span>{categoryLabel(room.category)}</span>
            <span>·</span>
            <span>
              {room.memberCount}/{room.maxMembers}명 참여
            </span>
            <span>·</span>
            <span className="flex items-center gap-1">
              <span
                className={[
                  "inline-block h-2 w-2 rounded-full",
                  room.onlineCount > 0 ? "bg-emerald-500" : "bg-neutral-400",
                ].join(" ")}
              />
              {room.onlineCount}명 접속
            </span>
            {room.owner && (
              <>
                <span>·</span>
                <span className="text-rose-600 dark:text-rose-400">내가 방장</span>
              </>
            )}
          </p>
        </div>

        {room.joined && (
          <button
            onClick={() => void leave()}
            disabled={busy}
            className="shrink-0 rounded-lg px-2 py-1.5 text-xs opacity-50 hover:bg-black/5 hover:opacity-100 disabled:opacity-30 dark:hover:bg-white/10"
          >
            나가기
          </button>
        )}
      </div>

      {room.joined ? (
        <ChatRoom channelId={channelId} heightClass="h-[calc(100vh-18rem)]" showSenderName />
      ) : (
        <div className="rounded-2xl border border-dashed border-black/15 p-10 text-center dark:border-white/15">
          <p className="font-medium">{full ? "정원이 가득 찼어요" : "아직 입장하지 않았어요"}</p>
          <p className="mt-1 text-sm opacity-60">
            {full ? "누군가 나가면 들어갈 수 있어요." : "입장하면 대화가 열립니다."}
          </p>
          <button
            onClick={() => void join()}
            disabled={busy || full}
            className="mt-4 rounded-lg bg-rose-500 px-4 py-2 text-sm font-medium text-white hover:bg-rose-600 disabled:opacity-40"
          >
            {busy ? "입장 중…" : "입장하기"}
          </button>
        </div>
      )}
    </AppShell>
  );
}

function BackLink() {
  return (
    <Link href="/rooms" className="text-sm opacity-60 hover:opacity-100">
      ← 오픈채팅
    </Link>
  );
}
