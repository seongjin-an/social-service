"use client";

import { clearTokens, getAccessToken } from "./auth";

export const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080";

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
  }
}

/**
 * 백엔드 응답 래퍼가 **두 종류**다.
 *   ApiResponse : { success: boolean, message, data, traceId }   user/matching/recommendation/message
 *   Response    : { status: number,  message, data }             profile
 * 화면 코드가 이걸 알 필요는 없으므로 여기서 흡수하고 항상 data 만 돌려준다.
 */
interface ApiResponseShape<T> {
  success?: boolean;
  status?: number;
  message?: string | null;
  data?: T;
}

function unwrap<T>(body: ApiResponseShape<T>, httpStatus: number): T {
  const ok =
    typeof body.success === "boolean"
      ? body.success
      : typeof body.status === "number"
        ? body.status >= 200 && body.status < 300
        : httpStatus >= 200 && httpStatus < 300;

  if (!ok) {
    throw new ApiError(body.message ?? "요청이 실패했습니다", httpStatus);
  }
  return body.data as T;
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  /** 인증이 필요 없는 요청(로그인/회원가입) */
  anonymous?: boolean;
}

export async function api<T>(
  path: string,
  { method = "GET", body, anonymous = false }: RequestOptions = {},
): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";

  if (!anonymous) {
    const token = getAccessToken();
    if (!token) {
      redirectToLogin();
      throw new ApiError("로그인이 필요합니다", 401);
    }
    headers["Authorization"] = `Bearer ${token}`;
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    // 네트워크 자체가 안 되는 경우 — 서버가 안 떠 있거나 CORS 가 막힌 경우다.
    throw new ApiError(
      "서버에 연결할 수 없습니다. 백엔드가 떠 있는지 확인해 주세요.",
      0,
    );
  }

  if (response.status === 401 && !anonymous) {
    // 토큰 만료/블랙리스트 — 조용히 로그인으로 되돌린다.
    clearTokens();
    redirectToLogin();
    throw new ApiError("로그인이 만료되었습니다", 401);
  }

  // 204 등 본문 없는 응답
  const text = await response.text();
  if (!text) {
    if (!response.ok) {
      throw new ApiError(`요청 실패 (${response.status})`, response.status);
    }
    return undefined as T;
  }

  let parsed: ApiResponseShape<T>;
  try {
    parsed = JSON.parse(text) as ApiResponseShape<T>;
  } catch {
    throw new ApiError(`응답을 해석할 수 없습니다 (${response.status})`, response.status);
  }

  return unwrap<T>(parsed, response.status);
}

function redirectToLogin() {
  if (typeof window === "undefined") return;
  if (window.location.pathname !== "/login") {
    window.location.href = "/login";
  }
}
