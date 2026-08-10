"use client";

import { use } from "react";
import Link from "next/link";
import { AppShell } from "@/components/AppShell";
import { ChatRoom } from "@/components/ChatRoom";

/** 매칭으로 열린 1:1 채팅방(DIRECT). 대화창 자체는 오픈방과 공용이다. */
export default function ChatPage({
  params,
}: {
  params: Promise<{ channelId: string }>;
}) {
  const { channelId: channelIdParam } = use(params);
  const channelId = Number(channelIdParam);

  return (
    <AppShell>
      <div className="mb-3 flex items-center gap-2">
        <Link href="/matches" className="text-sm opacity-60 hover:opacity-100">
          ← 매칭
        </Link>
        <h1 className="text-lg font-bold">채팅방 #{channelId}</h1>
      </div>

      <ChatRoom channelId={channelId} heightClass="h-[calc(100vh-14rem)]" />
    </AppShell>
  );
}
