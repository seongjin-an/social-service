"use client";

import type { TokenPair } from "./types";

/**
 * 토큰 보관 — 데모라 localStorage 를 쓴다.
 * (운영이라면 XSS 로 토큰이 새는 걸 막기 위해 httpOnly 쿠키 + CSRF 대책이 맞다.)
 */
const ACCESS_KEY = "sd.accessToken";
const REFRESH_KEY = "sd.refreshToken";

export function saveTokens(tokens: TokenPair) {
  localStorage.setItem(ACCESS_KEY, tokens.accessToken);
  localStorage.setItem(REFRESH_KEY, tokens.refreshToken);
}

export function getAccessToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(ACCESS_KEY);
}

export function getRefreshToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(REFRESH_KEY);
}

export function clearTokens() {
  localStorage.removeItem(ACCESS_KEY);
  localStorage.removeItem(REFRESH_KEY);
}

/**
 * 토큰에서 userId(sub)를 꺼낸다. **검증 목적이 아니다** — 내 메시지와 남의 메시지를
 * 구분해 정렬/정렬하는 화면 용도일 뿐이고, 진짜 인증은 게이트웨이가 서명을 검증한다.
 */
export function getMyUserId(): string | null {
  const token = getAccessToken();
  if (!token) return null;
  try {
    const payload = token.split(".")[1];
    const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
    return (JSON.parse(json) as { sub?: string }).sub ?? null;
  } catch {
    return null;
  }
}

export function getMyName(): string | null {
  const token = getAccessToken();
  if (!token) return null;
  try {
    const payload = token.split(".")[1];
    const json = decodeURIComponent(
      atob(payload.replace(/-/g, "+").replace(/_/g, "/"))
        .split("")
        .map((c) => "%" + ("00" + c.charCodeAt(0).toString(16)).slice(-2))
        .join(""),
    );
    return (JSON.parse(json) as { name?: string }).name ?? null;
  } catch {
    return null;
  }
}
