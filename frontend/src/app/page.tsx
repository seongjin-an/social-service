"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { getAccessToken } from "@/lib/auth";

/** 토큰 유무로 로그인/피드 중 하나로 보낸다. */
export default function Home() {
  const router = useRouter();

  useEffect(() => {
    router.replace(getAccessToken() ? "/feed" : "/login");
  }, [router]);

  return (
    <div className="flex min-h-screen items-center justify-center text-sm opacity-60">
      불러오는 중…
    </div>
  );
}
