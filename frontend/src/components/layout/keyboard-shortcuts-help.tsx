"use client";

import { AnimatePresence, motion } from "framer-motion";
import { Keyboard, X } from "lucide-react";
import { useI18n } from "@/lib/i18n/i18n-context";

type ShortcutItem = {
  keys: string[];
  description: string;
};

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
  const { t } = useI18n();

  const shortcutGroups: { group: string; items: ShortcutItem[] }[] = [
    {
      group: t.layout.shortcutsNavigationGroup,
      items: [
        { keys: ["g", "d"], description: t.nav.dashboard },
        { keys: ["g", "c"], description: t.nav.campaigns },
        { keys: ["g", "t"], description: t.nav.templates },
        { keys: ["g", "e"], description: t.nav.employees },
        { keys: ["g", "s"], description: t.nav.settings }
      ]
    },
    {
      group: t.layout.shortcutsGeneralGroup,
      items: [
        { keys: ["?"], description: t.layout.shortcutsToggleHelp },
        { keys: ["Esc"], description: t.layout.shortcutsCloseModal }
      ]
    }
  ];

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
                <p className="text-sm font-medium text-text">{t.layout.shortcutsTitle}</p>
              </div>
              <button
                onClick={onClose}
                className="rounded-md p-1 text-muted transition-colors hover:text-text"
                aria-label={t.common.close}
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div className="space-y-5">
              {shortcutGroups.map((group) => (
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
                                <span className="text-[10px] text-muted">{t.layout.shortcutsThen}</span>
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
