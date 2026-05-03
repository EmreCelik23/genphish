"use client";

import { createContext, useCallback, useContext, useMemo, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { AlertTriangle, CheckCircle2, Info, X, XCircle } from "lucide-react";

import { cn } from "@/lib/utils";

// ── Types ────────────────────────────────────────────────────────────

type ToastTone = "info" | "success" | "warning" | "error";

type Toast = {
  id: string;
  message: string;
  tone: ToastTone;
};

type ToastContextValue = {
  toast: (message: string, tone?: ToastTone) => void;
};

// ── Context ──────────────────────────────────────────────────────────

const ToastContext = createContext<ToastContextValue | null>(null);

const TOAST_DURATION = 4000;
let toastCounter = 0;

const toneConfig: Record<ToastTone, { icon: React.ComponentType<{ className?: string }>; border: string; bg: string }> = {
  info: { icon: Info, border: "border-sky-500/30", bg: "bg-sky-500/10" },
  success: { icon: CheckCircle2, border: "border-emerald-500/30", bg: "bg-emerald-500/10" },
  warning: { icon: AlertTriangle, border: "border-amber-500/30", bg: "bg-amber-500/10" },
  error: { icon: XCircle, border: "border-rose-500/30", bg: "bg-rose-500/10" }
};

// ── Provider ─────────────────────────────────────────────────────────

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const dismiss = useCallback((id: string) => {
    setToasts((prev) => prev.filter((toast) => toast.id !== id));
  }, []);

  const toast = useCallback(
    (message: string, tone: ToastTone = "info") => {
      const id = `toast-${++toastCounter}`;
      setToasts((prev) => [...prev, { id, message, tone }]);
      setTimeout(() => dismiss(id), TOAST_DURATION);
    },
    [dismiss]
  );

  const value = useMemo<ToastContextValue>(() => ({ toast }), [toast]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="fixed right-4 top-4 z-[100] flex flex-col gap-2">
        <AnimatePresence mode="popLayout">
          {toasts.map((item) => {
            const config = toneConfig[item.tone];
            const Icon = config.icon;
            return (
              <motion.div
                key={item.id}
                layout
                initial={{ opacity: 0, x: 80, scale: 0.95 }}
                animate={{ opacity: 1, x: 0, scale: 1 }}
                exit={{ opacity: 0, x: 80, scale: 0.95 }}
                transition={{ type: "spring", stiffness: 500, damping: 35 }}
                className={cn(
                  "flex items-center gap-3 rounded-xl border px-4 py-3 shadow-lg backdrop-blur-md",
                  config.border,
                  config.bg,
                  "bg-panel/95"
                )}
              >
                <Icon className="h-4 w-4 shrink-0 text-muted" />
                <p className="text-sm text-text">{item.message}</p>
                <button
                  onClick={() => dismiss(item.id)}
                  className="ml-2 shrink-0 rounded-md p-1 text-muted hover:text-text"
                >
                  <X className="h-3.5 w-3.5" />
                </button>
              </motion.div>
            );
          })}
        </AnimatePresence>
      </div>
    </ToastContext.Provider>
  );
}

// ── Hook ─────────────────────────────────────────────────────────────

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error("useToast must be used inside ToastProvider");
  }
  return context;
}
