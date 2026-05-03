"use client";

import { useEffect, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { WifiOff, X } from "lucide-react";
import { useI18n } from "@/lib/i18n/i18n-context";

/**
 * Shows a dismissible banner when the browser loses network connectivity.
 * Automatically hides when the connection is restored.
 */
export function OfflineBanner() {
  const { t } = useI18n();
  const [isOffline, setIsOffline] = useState(() =>
    typeof navigator !== "undefined" ? !navigator.onLine : false
  );
  const [dismissed, setDismissed] = useState(false);

  useEffect(() => {
    const handleOffline = () => {
      setIsOffline(true);
      setDismissed(false); // re-show if dismissed earlier
    };
    const handleOnline = () => {
      setIsOffline(false);
      setDismissed(false);
    };

    window.addEventListener("offline", handleOffline);
    window.addEventListener("online", handleOnline);

    return () => {
      window.removeEventListener("offline", handleOffline);
      window.removeEventListener("online", handleOnline);
    };
  }, []);

  const visible = isOffline && !dismissed;

  return (
    <AnimatePresence>
      {visible && (
        <motion.div
          initial={{ y: -48, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          exit={{ y: -48, opacity: 0 }}
          transition={{ type: "spring", stiffness: 400, damping: 30 }}
          className="fixed left-0 right-0 top-0 z-[200] flex items-center justify-between gap-3 border-b border-amber-500/30 bg-amber-500/10 px-4 py-2.5 backdrop-blur-md"
        >
          <div className="flex items-center gap-2 text-amber-300">
            <WifiOff className="h-4 w-4 shrink-0" />
            <p className="text-sm font-medium">
              {t.layout.offlineMessage}
            </p>
          </div>
          <button
            onClick={() => setDismissed(true)}
            className="rounded-md p-1 text-amber-300/60 transition-colors hover:text-amber-300"
            aria-label={t.common.close}
          >
            <X className="h-4 w-4" />
          </button>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
