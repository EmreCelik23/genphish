"use client";

import { useCallback, useMemo, useState } from "react";
import { useRouter } from "next/navigation";

import { ErrorBoundary } from "@/components/layout/error-boundary";
import { KeyboardShortcutsHelp } from "@/components/layout/keyboard-shortcuts-help";
import { OfflineBanner } from "@/components/layout/offline-banner";
import { Sidebar } from "@/components/layout/sidebar";
import { Topbar } from "@/components/layout/topbar";
import { useKeyboardShortcuts } from "@/lib/hooks/use-keyboard-shortcuts";
import { useI18n } from "@/lib/i18n/i18n-context";

export function WorkspaceShell({ children }: { children: React.ReactNode }) {
  const { t } = useI18n();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [shortcutsOpen, setShortcutsOpen] = useState(false);
  const router = useRouter();

  const shortcuts = useMemo(
    () => [
      { keys: "g d", handler: () => router.push("/dashboard"), description: t.nav.dashboard },
      { keys: "g c", handler: () => router.push("/campaigns"), description: t.nav.campaigns },
      { keys: "g t", handler: () => router.push("/templates"), description: t.nav.templates },
      { keys: "g e", handler: () => router.push("/employees"), description: t.nav.employees },
      { keys: "g s", handler: () => router.push("/settings"), description: t.nav.settings },
      { keys: "?", handler: () => setShortcutsOpen((v) => !v), description: t.layout.shortcutsToggleHelp }
    ],
    [router, t.layout.shortcutsToggleHelp, t.nav.campaigns, t.nav.dashboard, t.nav.employees, t.nav.settings, t.nav.templates]
  );

  useKeyboardShortcuts(shortcuts);

  const closeShortcuts = useCallback(() => setShortcutsOpen(false), []);

  return (
    <div className="min-h-screen bg-surface text-text">
      <OfflineBanner />

      <div className="relative mx-auto flex w-full max-w-[1600px]">
        <Sidebar mobileOpen={mobileOpen} onClose={() => setMobileOpen(false)} />
        <div className="min-h-screen min-w-0 flex-1">
          <Topbar onMenuClick={() => setMobileOpen(true)} />
          <main className="p-4 lg:p-6">
            <ErrorBoundary
              title={t.layout.errorTitle}
              retryLabel={t.layout.retryAction}
              unknownErrorLabel={t.common.unknownError}
            >
              {children}
            </ErrorBoundary>
          </main>
        </div>
      </div>

      <KeyboardShortcutsHelp open={shortcutsOpen} onClose={closeShortcuts} />
    </div>
  );
}
