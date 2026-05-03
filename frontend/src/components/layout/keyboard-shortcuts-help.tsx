"use client";

import { AnimatePresence, motion } from "framer-motion";
import { Keyboard, X } from "lucide-react";

type ShortcutItem = {
  keys: string[];
  description: string;
};

const SHORTCUT_GROUPS: { group: string; items: ShortcutItem[] }[] = [
  {
    group: "Navigasyon",
    items: [
      { keys: ["g", "d"], description: "Dashboard" },
      { keys: ["g", "c"], description: "Kampanyalar" },
      { keys: ["g", "t"], description: "Template Studio" },
      { keys: ["g", "e"], description: "Çalışanlar" },
      { keys: ["g", "s"], description: "Ayarlar" }
    ]
  },
  {
    group: "Genel",
    items: [
      { keys: ["?"], description: "Bu pencereyi aç/kapat" },
      { keys: ["Esc"], description: "Modal kapat" }
    ]
  }
];

type Props = {
  open: boolean;
  onClose: () => void;
};

function Kbd({ children }: { children: React.ReactNode }) {
  return (
    <kbd className="inline-flex h-5 items-center rounded border border-border bg-surface px-1.5 font-mono text-[10px] font-medium text-muted">
      {children}
    </kbd>
  );
}

export function KeyboardShortcutsHelp({ open, onClose }: Props) {
  return (
    <AnimatePresence>
      {open && (
        <>
          {/* Backdrop */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-[150] bg-black/60 backdrop-blur-sm"
            onClick={onClose}
          />

          {/* Panel */}
          <motion.div
            initial={{ opacity: 0, scale: 0.95, y: 16 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.95, y: 16 }}
            transition={{ type: "spring", stiffness: 500, damping: 35 }}
            className="fixed left-1/2 top-1/2 z-[160] w-full max-w-md -translate-x-1/2 -translate-y-1/2 rounded-2xl border border-border bg-panel p-6 shadow-2xl"
          >
            <div className="mb-5 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Keyboard className="h-4 w-4 text-accent" />
                <p className="text-sm font-medium text-text">Klavye Kısayolları</p>
              </div>
              <button
                onClick={onClose}
                className="rounded-md p-1 text-muted transition-colors hover:text-text"
                aria-label="Kapat"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div className="space-y-5">
              {SHORTCUT_GROUPS.map((group) => (
                <div key={group.group}>
                  <p className="mb-2 text-[10px] uppercase tracking-[0.14em] text-muted">
                    {group.group}
                  </p>
                  <div className="space-y-2">
                    {group.items.map((item) => (
                      <div key={item.description} className="flex items-center justify-between">
                        <span className="text-sm text-text">{item.description}</span>
                        <div className="flex items-center gap-1">
                          {item.keys.map((k, i) => (
                            <span key={i} className="flex items-center gap-1">
                              <Kbd>{k}</Kbd>
                              {i < item.keys.length - 1 && (
                                <span className="text-[10px] text-muted">then</span>
                              )}
                            </span>
                          ))}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
