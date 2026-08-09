"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { AppShell } from "@/components/AppShell";
import { api, ApiError } from "@/lib/api";
import { useToast } from "@/lib/toast";
import type { FeedItem, FeedView, LikeType, ProfileResponse } from "@/lib/types";

export default function FeedPage() {
  const router = useRouter();
  const { push } = useToast();

  const [queue, setQueue] = useState<FeedItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [exhausted, setExhausted] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /**
   * 서버가 준 커서를 그대로 들고 다닌다. 절대 직접 만들지 않는다 —
   * 스냅샷 버전이 실려 있어서 위조하면 서버가 무효로 보고 첫 페이지를 준다.
   */
  const cursorRef = useRef<string | null>(null);
  const loadingRef = useRef(false);

  const loadMore = useCallback(
    async (reset = false) => {
      if (loadingRef.current) return;
      loadingRef.current = true;
      setError(null);

      try {
        const cursor = reset ? null : cursorRef.current;
        const query = cursor ? `?size=10&cursor=${encodeURIComponent(cursor)}` : "?size=10";
        const view = await api<FeedView>(`/api/feed${query}`);

        // 응답의 커서로 항상 갈아끼운다. 옛 커서를 재사용하면 1페이지만 계속 돈다.
        cursorRef.current = view.nextCursor;

        setQueue((prev) => (reset ? view.items : [...prev, ...view.items]));
        // nextCursor 가 null 이면 스냅샷을 다 소비한 것 = 더 없음
        if (!view.nextCursor) setExhausted(true);
      } catch (caught) {
        setError(caught instanceof ApiError ? caught.message : "피드를 불러오지 못했습니다");
      } finally {
        loadingRef.current = false;
        setLoading(false);
      }
    },
    [],
  );

  // 프로필이 없으면 피드가 항상 비므로 온보딩으로 먼저 보낸다.
  useEffect(() => {
    let cancelled = false;
    api<ProfileResponse[]>("/api/profiles/me")
      .then((profiles) => {
        if (cancelled) return;
        if (profiles.length === 0) {
          router.replace("/onboarding");
          return;
        }
        void loadMore(true);
      })
      .catch(() => {
        if (!cancelled) void loadMore(true);
      });
    return () => {
      cancelled = true;
    };
  }, [router, loadMore]);

  const act = async (item: FeedItem, likeType: LikeType) => {
    // 낙관적으로 카드를 먼저 치운다 — 판정은 어차피 비동기(202)라 기다릴 이유가 없다.
    setQueue((prev) => prev.filter((entry) => entry.profile.userId !== item.profile.userId));

    try {
      await api<void>("/api/likes", {
        method: "POST",
        body: { toUserId: item.profile.userId, likeType },
      });
    } catch (caught) {
      push({
        tone: "error",
        title: "전송 실패",
        body: caught instanceof ApiError ? caught.message : "잠시 후 다시 시도해 주세요",
      });
    }

    // 카드가 얼마 안 남았으면 미리 다음 페이지를 당겨온다.
    if (queue.length <= 3 && !exhausted) void loadMore();
  };

  const current = queue[0];

  return (
    <AppShell>
      {loading ? (
        <Placeholder>불러오는 중…</Placeholder>
      ) : error ? (
        <Placeholder>
          <p className="text-red-600 dark:text-red-400">{error}</p>
          <RetryButton onClick={() => void loadMore(true)} />
        </Placeholder>
      ) : !current ? (
        <Placeholder>
          <p className="text-lg font-semibold">주변에 더 볼 사람이 없어요</p>
          <p className="mt-1 text-sm opacity-60">
            반경 안의 후보를 모두 확인했습니다. 잠시 후 다시 시도하거나 반경을 넓혀 보세요.
          </p>
          <RetryButton onClick={() => void loadMore(true)} label="새로고침" />
        </Placeholder>
      ) : (
        <div className="mx-auto max-w-sm">
          <FeedCard item={current} />

          <div className="mt-5 flex items-center justify-center gap-4">
            <ActionButton
              onClick={() => void act(current, "PASS")}
              className="bg-white text-neutral-500 ring-1 ring-black/10 hover:bg-neutral-100 dark:bg-neutral-800 dark:ring-white/15"
            >
              ✕
            </ActionButton>
            <ActionButton
              onClick={() => void act(current, "SUPER")}
              className="h-12 w-12 bg-sky-500 text-white hover:bg-sky-600"
            >
              ★
            </ActionButton>
            <ActionButton
              onClick={() => void act(current, "LIKE")}
              className="bg-rose-500 text-white hover:bg-rose-600"
            >
              ♥
            </ActionButton>
          </div>

          <p className="mt-4 text-center text-xs opacity-40">
            남은 카드 {queue.length}장{exhausted ? " · 마지막 묶음" : ""}
          </p>
        </div>
      )}
    </AppShell>
  );
}

function FeedCard({ item }: { item: FeedItem }) {
  const { profile, distanceKm, sharedTags } = item;

  return (
    <article className="overflow-hidden rounded-3xl border border-black/10 bg-white shadow-sm dark:border-white/10 dark:bg-neutral-900">
      <div className="relative flex aspect-[4/5] items-center justify-center bg-gradient-to-br from-rose-100 to-sky-100 dark:from-rose-950 dark:to-sky-950">
        {profile.imageUrl ? (
          // 프로필 이미지는 profile-service 가 직접 서빙한다(다른 오리진).
          // <img> 는 CORS 없이도 그려지므로 next/image 최적화 없이 그대로 쓴다.
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={profile.imageUrl}
            alt=""
            className="h-full w-full object-cover"
          />
        ) : (
          <span className="text-6xl opacity-30">👤</span>
        )}

        <span className="absolute right-3 top-3 rounded-full bg-black/60 px-2.5 py-1 text-xs font-medium text-white">
          {distanceKm.toFixed(1)}km
        </span>
      </div>

      <div className="p-4">
        <div className="flex items-baseline gap-2">
          <h2 className="text-xl font-bold">
            {profile.age ?? "?"}세
          </h2>
          <span className="text-sm opacity-50">
            {profile.gender === "FEMALE" ? "여성" : profile.gender === "MALE" ? "남성" : ""}
          </span>
        </div>

        {profile.bio && <p className="mt-2 text-sm opacity-80">{profile.bio}</p>}

        {profile.tags.length > 0 && (
          <div className="mt-3 flex flex-wrap gap-1.5">
            {profile.tags.map((tag) => {
              const shared = sharedTags.includes(tag);
              return (
                <span
                  key={tag}
                  className={[
                    "rounded-full px-2.5 py-1 text-xs",
                    shared
                      ? "bg-rose-500 font-medium text-white"
                      : "bg-black/5 opacity-70 dark:bg-white/10",
                  ].join(" ")}
                >
                  {tag}
                </span>
              );
            })}
          </div>
        )}

        {/* 추천 이유 — 서버가 sharedTags 를 내려주므로 계산 없이 그대로 보여준다 */}
        {sharedTags.length > 0 && (
          <p className="mt-3 text-xs font-medium text-rose-600 dark:text-rose-400">
            관심사 {sharedTags.length}개 일치 · {sharedTags.join(", ")}
          </p>
        )}
      </div>
    </article>
  );
}

function ActionButton({
  children,
  onClick,
  className,
}: {
  children: React.ReactNode;
  onClick: () => void;
  className: string;
}) {
  return (
    <button
      onClick={onClick}
      className={`flex h-14 w-14 items-center justify-center rounded-full text-xl shadow-sm transition active:scale-95 ${className}`}
    >
      {children}
    </button>
  );
}

function Placeholder({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center text-center">
      {children}
    </div>
  );
}

function RetryButton({ onClick, label = "다시 시도" }: { onClick: () => void; label?: string }) {
  return (
    <button
      onClick={onClick}
      className="mt-4 rounded-lg bg-rose-500 px-4 py-2 text-sm font-medium text-white hover:bg-rose-600"
    >
      {label}
    </button>
  );
}
