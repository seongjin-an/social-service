"use client";

import { useState } from "react";
import { api, ApiError } from "@/lib/api";
import { saveTokens } from "@/lib/auth";
import type { TokenPair, UserResponse } from "@/lib/types";

type Mode = "login" | "signup";

export default function LoginPage() {
  const [mode, setMode] = useState<Mode>("login");
  const [email, setEmail] = useState("demo1@test.com");
  const [password, setPassword] = useState("password123");
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      if (mode === "signup") {
        await api<UserResponse>("/api/auth/signup", {
          method: "POST",
          anonymous: true,
          body: { email, password, name, phone: null, role: "USER" },
        });
      }

      const tokens = await api<TokenPair>("/api/auth/login", {
        method: "POST",
        anonymous: true,
        body: { email, password },
      });
      saveTokens(tokens);

      // 전체 새로고침으로 넘어간다 — WebSocket provider 가 토큰을 들고 새로 마운트돼야
      // 매칭 알림이 곧바로 붙는다(라우터 이동만으로는 provider 가 재실행되지 않는다).
      window.location.href = "/feed";
    } catch (caught) {
      setError(
        caught instanceof ApiError ? caught.message : "알 수 없는 오류가 발생했습니다",
      );
      setBusy(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <h1 className="text-center text-3xl font-bold tracking-tight">💕 Social</h1>
        <p className="mt-2 text-center text-sm opacity-60">
          추천 → 좋아요 → 매칭 → 실시간 채팅
        </p>

        <div className="mt-8 rounded-2xl border border-black/10 bg-white p-6 shadow-sm dark:border-white/10 dark:bg-neutral-900">
          <div className="mb-5 grid grid-cols-2 gap-1 rounded-lg bg-black/5 p-1 text-sm dark:bg-white/10">
            {(["login", "signup"] as Mode[]).map((value) => (
              <button
                key={value}
                type="button"
                onClick={() => {
                  setMode(value);
                  setError(null);
                }}
                className={[
                  "rounded-md py-1.5 transition",
                  mode === value ? "bg-white shadow-sm dark:bg-neutral-800" : "opacity-60",
                ].join(" ")}
              >
                {value === "login" ? "로그인" : "회원가입"}
              </button>
            ))}
          </div>

          <form onSubmit={submit} className="space-y-3">
            {mode === "signup" && (
              <Field label="이름">
                <input
                  required
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className={inputClass}
                  placeholder="홍길동"
                />
              </Field>
            )}

            <Field label="이메일">
              <input
                required
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className={inputClass}
              />
            </Field>

            <Field label="비밀번호">
              <input
                required
                type="password"
                minLength={8}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className={inputClass}
              />
            </Field>

            {error && (
              <p className="rounded-lg bg-red-50 px-3 py-2 text-xs text-red-700 dark:bg-red-950 dark:text-red-300">
                {error}
              </p>
            )}

            <button
              type="submit"
              disabled={busy}
              className="w-full rounded-xl bg-rose-500 py-2.5 text-sm font-semibold text-white transition hover:bg-rose-600 disabled:opacity-50"
            >
              {busy ? "처리 중…" : mode === "login" ? "로그인" : "가입하고 시작하기"}
            </button>
          </form>
        </div>

        <p className="mt-4 text-center text-xs opacity-50">
          데모 계정: demo1@test.com / demo2@test.com · 비밀번호 password123
        </p>
      </div>
    </div>
  );
}

const inputClass =
  "w-full rounded-lg border border-black/10 bg-white px-3 py-2 text-sm outline-none focus:border-rose-400 dark:border-white/15 dark:bg-neutral-800";

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1 block text-xs font-medium opacity-70">{label}</span>
      {children}
    </label>
  );
}
