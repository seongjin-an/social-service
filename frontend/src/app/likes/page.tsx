"use client";

import { useEffect, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { Avatar } from "@/app/matches/page";
import { api, ApiError } from "@/lib/api";
import { useToast } from "@/lib/toast";
import type { ReceivedLikeView } from "@/lib/types";

/** 나를 좋아한 사람들 (F2-6). 이미 매칭된 상대는 서버가 빼고 준다. */
export default function ReceivedLikesPage() {
  const { push } = useToast();
  const [likes, setLikes] = useState<ReceivedLikeView[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () =>
    api<ReceivedLikeView[]>("/api/likes/received")
      .then(setLikes)
      .catch((caught) =>
        setError(caught instanceof ApiError ? caught.message : "불러오지 못했습니다"),
      );

  useEffect(() => {
    void load();
  }, []);

  // 여기서 좋아요를 누르면 상대가 이미 나를 좋아한 상태이므로 곧바로 매칭이 성사된다.
  const likeBack = async (like: ReceivedLikeView) => {
    setLikes((prev) => prev?.filter((l) => l.partner.userId !== like.partner.userId) ?? null);
    try {
      await api<void>("/api/likes", {
        method: "POST",
        body: { toUserId: like.partner.userId, likeType: "LIKE" },
      });
      push({ title: "좋아요를 보냈어요", body: "매칭되면 알림이 옵니다." });
    } catch (caught) {
      push({
        tone: "error",
        title: "전송 실패",
        body: caught instanceof ApiError ? caught.message : "잠시 후 다시 시도해 주세요",
      });
      void load();
    }
  };

  return (
    <AppShell>
      <h1 className="mb-4 text-xl font-bold">받은 좋아요</h1>

      {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}

      {!likes ? (
        <p className="text-sm opacity-60">불러오는 중…</p>
      ) : likes.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-black/15 p-10 text-center dark:border-white/15">
          <p className="font-medium">아직 받은 좋아요가 없어요</p>
        </div>
      ) : (
        <ul className="space-y-2">
          {likes.map((like) => (
            <li
              key={like.partner.userId}
              className="flex items-center gap-3 rounded-2xl border border-black/10 bg-white p-3 dark:border-white/10 dark:bg-neutral-900"
            >
              <Avatar url={like.partner.imageUrl} />

              <div className="min-w-0 flex-1">
                <p className="font-semibold">
                  {like.partner.age ?? "?"}세
                  {like.type === "SUPER" && (
                    <span className="ml-2 rounded-full bg-sky-500 px-2 py-0.5 text-xs font-medium text-white">
                      슈퍼
                    </span>
                  )}
                </p>
                <p className="truncate text-sm opacity-60">
                  {like.partner.bio ?? "소개가 없습니다"}
                </p>
              </div>

              <button
                onClick={() => void likeBack(like)}
                className="shrink-0 rounded-lg bg-rose-500 px-3 py-1.5 text-sm font-medium text-white hover:bg-rose-600"
              >
                나도 좋아요
              </button>
            </li>
          ))}
        </ul>
      )}
    </AppShell>
  );
}
