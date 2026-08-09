"use client";

import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";

export interface Toast {
  id: number;
  title: string;
  body?: string;
  /** 누르면 이동할 경로 (매칭 알림 → 채팅방) */
  href?: string;
  tone?: "match" | "info" | "error";
}

interface ToastContextValue {
  push: (toast: Omit<Toast, "id">) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

let nextId = 1;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const push = useCallback((toast: Omit<Toast, "id">) => {
    const id = nextId++;
    setToasts((prev) => [...prev, { ...toast, id }]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 6000);
  }, []);

  const dismiss = (id: number) =>
    setToasts((prev) => prev.filter((t) => t.id !== id));

  const value = useMemo(() => ({ push }), [push]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="pointer-events-none fixed inset-x-0 top-4 z-50 flex flex-col items-center gap-2 px-4">
        {toasts.map((toast) => (
          <div
            key={toast.id}
            onClick={() => {
              if (toast.href) window.location.href = toast.href;
              dismiss(toast.id);
            }}
            className={[
              "pointer-events-auto w-full max-w-sm rounded-xl px-4 py-3 shadow-lg ring-1 backdrop-blur",
              toast.href ? "cursor-pointer" : "",
              toast.tone === "match"
                ? "bg-rose-500/95 text-white ring-rose-300"
                : toast.tone === "error"
                  ? "bg-red-600/95 text-white ring-red-300"
                  : "bg-neutral-900/95 text-white ring-neutral-700",
            ].join(" ")}
          >
            <p className="text-sm font-semibold">{toast.title}</p>
            {toast.body && (
              <p className="mt-0.5 text-xs opacity-90">{toast.body}</p>
            )}
            {toast.href && (
              <p className="mt-1 text-xs font-medium underline opacity-90">
                눌러서 채팅방 열기
              </p>
            )}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) throw new Error("useToast 는 ToastProvider 안에서만 쓸 수 있다");
  return context;
}
