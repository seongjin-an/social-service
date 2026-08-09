"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { AppShell } from "@/components/AppShell";
import { api, ApiError } from "@/lib/api";
import { useSocketEvent } from "@/lib/socket";
import { useToast } from "@/lib/toast";
import type { MatchNotificationPayload, MatchView } from "@/lib/types";

export default function MatchesPage() {
  const { push } = useToast();
  const [matches, setMatches] = useState<MatchView[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () =>
    api<MatchView[]>("/api/matches")
      .then(setMatches)
      .catch((caught) =>
        setError(caught instanceof ApiError ? caught.message : "불러오지 못했습니다"),
      );

  useEffect(() => {
    void load();
  }, []);

  // 이 화면을 보고 있는 동안 매칭이 되면 목록도 바로 갱신한다.
  useSocketEvent<MatchNotificationPayload>("MATCH_NOTIFICATION", () => {
    void load();
  });

  const unmatch = async (match: MatchView) => {
    if (!confirm("이 매칭을 해제할까요? 채팅방도 닫힙니다.")) return;

    setMatches((prev) => prev?.filter((m) => m.matchId !== match.matchId) ?? null);
    try {
      await api<void>(`/api/matches/${match.matchId}`, { method: "DELETE" });
    } catch (caught) {
      push({
        tone: "error",
        title: "언매치 실패",
        body: caught instanceof ApiError ? caught.message : "잠시 후 다시 시도해 주세요",
      });
      void load();
    }
  };

  return (
    <AppShell>
      <h1 className="mb-4 text-xl font-bold">매칭</h1>

      {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}

      {!matches ? (
        <p className="text-sm opacity-60">불러오는 중…</p>
      ) : matches.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-black/15 p-10 text-center dark:border-white/15">
          <p className="font-medium">아직 매칭이 없어요</p>
          <p className="mt-1 text-sm opacity-60">
            탐색에서 마음에 드는 상대에게 좋아요를 눌러보세요.
          </p>
          <Link
            href="/feed"
            className="mt-4 inline-block rounded-lg bg-rose-500 px-4 py-2 text-sm font-medium text-white hover:bg-rose-600"
          >
            탐색하러 가기
          </Link>
        </div>
      ) : (
        <ul className="space-y-2">
          {matches.map((match) => (
            <li
              key={match.matchId}
              className="flex items-center gap-3 rounded-2xl border border-black/10 bg-white p-3 dark:border-white/10 dark:bg-neutral-900"
            >
              <Avatar url={match.partner.imageUrl} />

              <div className="min-w-0 flex-1">
                <p className="font-semibold">
                  {match.partner.age ?? "?"}세
                  <span className="ml-2 text-xs font-normal opacity-50">
                    {new Date(match.matchedAt).toLocaleDateString("ko-KR")}
                  </span>
                </p>
                <p className="truncate text-sm opacity-60">
                  {match.partner.bio ?? "소개가 없습니다"}
                </p>
              </div>

              {match.channelId ? (
                <Link
                  href={`/chat/${match.channelId}`}
                  className="shrink-0 rounded-lg bg-rose-500 px-3 py-1.5 text-sm font-medium text-white hover:bg-rose-600"
                >
                  채팅
                </Link>
              ) : (
                // Saga 가 채널을 만들기 전 짧은 순간 — channelId 가 null 로 온다
                <span className="shrink-0 rounded-lg bg-black/5 px-3 py-1.5 text-xs opacity-60 dark:bg-white/10">
                  채팅방 준비 중
                </span>
              )}

              <button
                onClick={() => void unmatch(match)}
                className="shrink-0 rounded-lg px-2 py-1.5 text-xs opacity-40 hover:bg-black/5 hover:opacity-100 dark:hover:bg-white/10"
              >
                해제
              </button>
            </li>
          ))}
        </ul>
      )}
    </AppShell>
  );
}

export function Avatar({ url }: { url: string | null }) {
  return (
    <div className="h-12 w-12 shrink-0 overflow-hidden rounded-full bg-gradient-to-br from-rose-100 to-sky-100 dark:from-rose-950 dark:to-sky-950">
      {url ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={url} alt="" className="h-full w-full object-cover" />
      ) : (
        <div className="flex h-full w-full items-center justify-center text-lg opacity-40">
          👤
        </div>
      )}
    </div>
  );
}
