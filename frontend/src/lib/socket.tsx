"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { getAccessToken } from "./auth";
import type { WsInbound } from "./types";

const WS_BASE = process.env.NEXT_PUBLIC_WS_BASE ?? "ws://localhost:8080";

type Handler = (payload: unknown) => void;

interface SocketContextValue {
  connected: boolean;
  /** 특정 타입의 서버 메시지를 구독한다. 반환값을 호출하면 해제. */
  subscribe: (type: string, handler: Handler) => () => void;
  send: (type: string, payload: unknown) => boolean;
}

const SocketContext = createContext<SocketContextValue | null>(null);

/**
 * 앱 전역에 WebSocket 연결 하나를 유지한다.
 *
 * <p><b>브라우저는 WS 핸드셰이크에 커스텀 헤더를 못 붙인다</b> — Authorization 헤더를 쓸 수 없다.
 * 그래서 게이트웨이가 지원하는 {@code ?token=} 쿼리 폴백으로 붙는다. 게이트웨이가 JWT 를 검증하고
 * X-User-Id 를 주입해 connection-service 로 넘긴다.
 *
 * <p>화면마다 연결을 새로 열면 매칭 알림이 화면 이동 중에 유실되므로 provider 한 곳에서만 연다.
 */
export function SocketProvider({ children }: { children: ReactNode }) {
  const [connected, setConnected] = useState(false);
  const socketRef = useRef<WebSocket | null>(null);
  const handlersRef = useRef<Map<string, Set<Handler>>>(new Map());
  const retryRef = useRef(0);
  const closedByUsRef = useRef(false);

  const subscribe = useCallback((type: string, handler: Handler) => {
    const map = handlersRef.current;
    if (!map.has(type)) map.set(type, new Set());
    map.get(type)!.add(handler);
    return () => {
      map.get(type)?.delete(handler);
    };
  }, []);

  const send = useCallback((type: string, payload: unknown) => {
    const socket = socketRef.current;
    if (!socket || socket.readyState !== WebSocket.OPEN) return false;
    socket.send(JSON.stringify({ type, payload }));
    return true;
  }, []);

  useEffect(() => {
    closedByUsRef.current = false;
    let reconnectTimer: ReturnType<typeof setTimeout> | undefined;
    let heartbeatTimer: ReturnType<typeof setInterval> | undefined;

    const connect = () => {
      const token = getAccessToken();
      if (!token) return; // 로그인 전 — 로그인 후 이 컴포넌트가 다시 마운트된다

      const socket = new WebSocket(
        `${WS_BASE}/ws?token=${encodeURIComponent(token)}`,
      );
      socketRef.current = socket;

      socket.onopen = () => {
        retryRef.current = 0;
        setConnected(true);
        // 연결 유지 — 유휴 커넥션이 중간 프록시에서 끊기는 걸 막는다.
        heartbeatTimer = setInterval(() => {
          if (socket.readyState === WebSocket.OPEN) {
            socket.send(JSON.stringify({ type: "HEARTBEAT", payload: {} }));
          }
        }, 30_000);
      };

      socket.onmessage = (event) => {
        let envelope: WsInbound;
        try {
          envelope = JSON.parse(event.data as string) as WsInbound;
        } catch {
          return; // 못 읽는 프레임은 버린다
        }
        handlersRef.current.get(envelope.type)?.forEach((handler) => {
          try {
            handler(envelope.payload);
          } catch (error) {
            console.error(`[ws] ${envelope.type} 핸들러 오류`, error);
          }
        });
      };

      socket.onclose = () => {
        setConnected(false);
        if (heartbeatTimer) clearInterval(heartbeatTimer);
        if (closedByUsRef.current) return;

        // 지수 백오프 재연결 — 서버 재기동 중에도 알아서 붙는다.
        retryRef.current = Math.min(retryRef.current + 1, 5);
        const delay = 500 * 2 ** (retryRef.current - 1);
        reconnectTimer = setTimeout(connect, delay);
      };

      socket.onerror = () => socket.close();
    };

    connect();

    return () => {
      closedByUsRef.current = true;
      if (reconnectTimer) clearTimeout(reconnectTimer);
      if (heartbeatTimer) clearInterval(heartbeatTimer);
      socketRef.current?.close();
      socketRef.current = null;
    };
  }, []);

  const value = useMemo(
    () => ({ connected, subscribe, send }),
    [connected, subscribe, send],
  );

  return (
    <SocketContext.Provider value={value}>{children}</SocketContext.Provider>
  );
}

export function useSocket() {
  const context = useContext(SocketContext);
  if (!context) throw new Error("useSocket 은 SocketProvider 안에서만 쓸 수 있다");
  return context;
}

/** 특정 타입의 서버 메시지를 구독하는 훅. handler 는 최신 것이 항상 쓰인다. */
export function useSocketEvent<T>(type: string, handler: (payload: T) => void) {
  const { subscribe } = useSocket();
  const handlerRef = useRef(handler);
  handlerRef.current = handler;

  useEffect(
    () => subscribe(type, (payload) => handlerRef.current(payload as T)),
    [type, subscribe],
  );
}
