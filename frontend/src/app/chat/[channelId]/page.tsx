"use client";

import { use, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { AppShell } from "@/components/AppShell";
import { api, ApiError } from "@/lib/api";
import { getMyUserId } from "@/lib/auth";
import { useSocket, useSocketEvent } from "@/lib/socket";
import type {
  ChatMessage,
  ContentMessagePayload,
  HistoryMessage,
  MessageCursorResult,
} from "@/lib/types";

export default function ChatPage({
  params,
}: {
  params: Promise<{ channelId: string }>;
}) {
  const { channelId: channelIdParam } = use(params);
  const channelId = Number(channelIdParam);

  const { send, connected } = useSocket();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const myUserId = useMemo(() => getMyUserId(), []);
  const bottomRef = useRef<HTMLDivElement>(null);

  // 히스토리 — 필드명이 WS 와 달라서(userId/userName) 여기서 정규화한다.
  useEffect(() => {
    api<MessageCursorResult>(`/api/channels/${channelId}/messages?size=50`)
      .then((result) => {
        const history: ChatMessage[] = (result.messages ?? []).map(toChatMessage);
        history.sort((a, b) => a.createdAt - b.createdAt);
        setMessages(history);
      })
      .catch((caught) =>
        setError(caught instanceof ApiError ? caught.message : "대화를 불러오지 못했습니다"),
      )
      .finally(() => setLoading(false));
  }, [channelId]);

  // 실시간 수신 — 내가 보낸 것도 서버를 한 바퀴 돌아 여기로 돌아온다.
  useSocketEvent<ContentMessagePayload>("CONTENT_MESSAGE", (payload) => {
    if (Number(payload.channelId) !== channelId) return;

    setMessages((prev) => {
      // 낙관적으로 그려둔 내 메시지를 서버가 확정한 것으로 교체한다(중복 방지).
      const optimisticIndex = payload.clientMessageId
        ? prev.findIndex((m) => m.clientMessageId === payload.clientMessageId)
        : -1;

      const confirmed: ChatMessage = {
        messageId: payload.messageId,
        channelId: Number(payload.channelId),
        senderId: payload.senderId,
        senderName: payload.senderName,
        content: payload.content,
        createdAt: Number(payload.createdAt),
        clientMessageId: payload.clientMessageId,
      };

      if (optimisticIndex >= 0) {
        const next = [...prev];
        next[optimisticIndex] = confirmed;
        return next;
      }
      // 재전송/중복 배달 방어
      if (prev.some((m) => m.messageId === confirmed.messageId)) return prev;
      return [...prev, confirmed];
    });
  });

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    const content = draft.trim();
    if (!content) return;

    // clientMessageId — 서버의 멱등 키이자, 돌아온 메시지와 낙관적 렌더링을 잇는 끈.
    const clientMessageId = crypto.randomUUID();
    const ok = send("SEND_MESSAGE", { channelId, content, clientMessageId });
    if (!ok) {
      setError("연결이 끊겨 전송하지 못했습니다. 잠시 후 다시 시도해 주세요.");
      return;
    }

    setMessages((prev) => [
      ...prev,
      {
        messageId: `pending:${clientMessageId}`,
        channelId,
        senderId: myUserId ?? "me",
        senderName: null,
        content,
        createdAt: Date.now(),
        clientMessageId,
        pending: true,
      },
    ]);
    setDraft("");
    setError(null);
  };

  return (
    <AppShell>
      <div className="mb-3 flex items-center gap-2">
        <Link href="/matches" className="text-sm opacity-60 hover:opacity-100">
          ← 매칭
        </Link>
        <h1 className="text-lg font-bold">채팅방 #{channelId}</h1>
      </div>

      <div className="flex h-[calc(100vh-14rem)] flex-col rounded-2xl border border-black/10 bg-white dark:border-white/10 dark:bg-neutral-900">
        <div className="flex-1 space-y-2 overflow-y-auto p-4">
          {loading ? (
            <p className="text-sm opacity-60">불러오는 중…</p>
          ) : messages.length === 0 ? (
            <p className="py-10 text-center text-sm opacity-50">
              아직 대화가 없습니다. 먼저 인사를 건네보세요.
            </p>
          ) : (
            messages.map((message) => (
              <Bubble
                key={message.messageId}
                message={message}
                mine={message.senderId === myUserId}
              />
            ))
          )}
          <div ref={bottomRef} />
        </div>

        {error && (
          <p className="border-t border-black/10 px-4 py-2 text-xs text-red-600 dark:border-white/10 dark:text-red-400">
            {error}
          </p>
        )}

        <form
          onSubmit={submit}
          className="flex gap-2 border-t border-black/10 p-3 dark:border-white/10"
        >
          <input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder={connected ? "메시지를 입력하세요" : "연결 중…"}
            disabled={!connected}
            className="flex-1 rounded-xl border border-black/10 bg-white px-3 py-2 text-sm outline-none focus:border-rose-400 disabled:opacity-50 dark:border-white/15 dark:bg-neutral-800"
          />
          <button
            type="submit"
            disabled={!connected || !draft.trim()}
            className="rounded-xl bg-rose-500 px-4 text-sm font-semibold text-white transition hover:bg-rose-600 disabled:opacity-40"
          >
            전송
          </button>
        </form>
      </div>
    </AppShell>
  );
}

function Bubble({ message, mine }: { message: ChatMessage; mine: boolean }) {
  return (
    <div className={mine ? "flex justify-end" : "flex justify-start"}>
      <div
        className={[
          "max-w-[75%] rounded-2xl px-3 py-2 text-sm",
          mine
            ? "bg-rose-500 text-white"
            : "bg-black/5 dark:bg-white/10",
          message.pending ? "opacity-50" : "",
        ].join(" ")}
      >
        {!mine && message.senderName && (
          <p className="mb-0.5 text-xs opacity-60">{message.senderName}</p>
        )}
        <p className="whitespace-pre-wrap break-words">{message.content}</p>
        <p className="mt-0.5 text-right text-[10px] opacity-60">
          {message.pending
            ? "전송 중…"
            : new Date(message.createdAt).toLocaleTimeString("ko-KR", {
                hour: "2-digit",
                minute: "2-digit",
              })}
        </p>
      </div>
    </div>
  );
}

function toChatMessage(message: HistoryMessage): ChatMessage {
  return {
    messageId: message.messageId,
    channelId: Number(message.channelId),
    senderId: message.userId,
    senderName: message.userName,
    content: message.content,
    createdAt: Number(message.createdAt),
  };
}
