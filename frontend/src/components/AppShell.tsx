"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { clearTokens, getMyName } from "@/lib/auth";
import { useSocket } from "@/lib/socket";

const NAV = [
  { href: "/feed", label: "탐색" },
  { href: "/matches", label: "매칭" },
  { href: "/likes", label: "받은 좋아요" },
  { href: "/rooms", label: "오픈채팅" },
];

/** 로그인 이후 화면들의 공통 껍데기 — 네비 + 연결 상태 표시. */
export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { connected } = useSocket();
  const [name, setName] = useState<string | null>(null);

  useEffect(() => setName(getMyName()), []);

  return (
    <div className="flex min-h-full flex-col">
      <header className="sticky top-0 z-40 border-b border-black/10 bg-white/80 backdrop-blur dark:border-white/10 dark:bg-neutral-950/80">
        <div className="mx-auto flex h-14 w-full max-w-3xl items-center gap-4 px-4">
          <Link href="/feed" className="text-lg font-bold tracking-tight">
            💕 Social
          </Link>

          <nav className="flex items-center gap-1 text-sm">
            {NAV.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className={[
                  "rounded-lg px-3 py-1.5 transition",
                  // 하위 경로(/rooms/12)에서도 탭이 켜져 있어야 한다
                  pathname === item.href || pathname.startsWith(`${item.href}/`)
                    ? "bg-rose-500 text-white"
                    : "hover:bg-black/5 dark:hover:bg-white/10",
                ].join(" ")}
              >
                {item.label}
              </Link>
            ))}
          </nav>

          <div className="ml-auto flex items-center gap-3 text-xs">
            {/* 실시간 알림이 살아 있는지 한눈에 — 데모에서 "왜 알림이 안 와요"를 바로 가른다 */}
            <span className="flex items-center gap-1.5">
              <span
                className={[
                  "inline-block h-2 w-2 rounded-full",
                  connected ? "bg-emerald-500" : "bg-neutral-400",
                ].join(" ")}
              />
              <span className="opacity-60">{connected ? "실시간 연결됨" : "연결 끊김"}</span>
            </span>
            {name && <span className="opacity-70">{name}</span>}
            <button
              onClick={() => {
                clearTokens();
                router.push("/login");
              }}
              className="rounded-lg px-2 py-1 opacity-60 hover:bg-black/5 hover:opacity-100 dark:hover:bg-white/10"
            >
              로그아웃
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-3xl flex-1 px-4 py-6">{children}</main>
    </div>
  );
}
