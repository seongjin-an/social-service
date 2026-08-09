"use client";

import type { ReactNode } from "react";
import { SocketProvider, useSocketEvent } from "@/lib/socket";
import { ToastProvider, useToast } from "@/lib/toast";
import type { MatchNotificationPayload } from "@/lib/types";

/**
 * 매칭 알림을 앱 어디에 있든 받는다 — 피드에서 스와이프하는 도중에 떠야 의미가 있다.
 *
 * <p>서버가 channelId 를 함께 보내주므로(Saga 가 채널을 만든 뒤에야 알림을 쏜다)
 * 토스트를 누르면 곧바로 채팅방으로 들어갈 수 있다.
 */
function MatchNotificationListener() {
  const { push } = useToast();

  useSocketEvent<MatchNotificationPayload>("MATCH_NOTIFICATION", (payload) => {
    push({
      tone: "match",
      title: "💘 매칭 성사!",
      body: "상대도 당신을 좋아했어요.",
      href: payload.channelId ? `/chat/${payload.channelId}` : "/matches",
    });
  });

  return null;
}

export function Providers({ children }: { children: ReactNode }) {
  return (
    <ToastProvider>
      <SocketProvider>
        <MatchNotificationListener />
        {children}
      </SocketProvider>
    </ToastProvider>
  );
}
