"use client";

import { useCallback, useMemo, useState } from "react";
import { useRouter } from "next/navigation";

import { ErrorBoundary } from "@/components/layout/error-boundary";
import { KeyboardShortcutsHelp } from "@/components/layout/keyboard-shortcuts-help";
import { OfflineBanner } from "@/components/layout/offline-banner";
import { Sidebar } from "@/components/layout/sidebar";
import { Topbar } from "@/components/layout/topbar";
import { useKeyboardShortcuts } from "@/lib/hooks/use-keyboard-shortcuts";

export function WorkspaceShell({ children }: { children: React.ReactNode }) {
  const [mobileOpen, setMobileOpen] = useState(false);
  const [shortcutsOpen, setShortcutsOpen] = useState(false);
  const router = useRouter();

  const shortcuts = useMemo(
    () => [
      { keys: "g d", handler: () => router.push("/dashboard"), description: "Dashboard" },
      { keys: "g c", handler: () => router.push("/campaigns"), description: "Kampanyalar" },
      { keys: "g t", handler: () => router.push("/templates"), description: "Template Studio" },
      { keys: "g e", handler: () => router.push("/employees"), description: "Çalışanlar" },
      { keys: "g s", handler: () => router.push("/settings"), description: "Ayarlar" },
      { keys: "?", handler: () => setShortcutsOpen((v) => !v), description: "Kısayolları göster" }
    ],
    [router]
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
            <ErrorBoundary>
              {children}
            </ErrorBoundary>
          </main>
        </div>
      </div>

      <KeyboardShortcutsHelp open={shortcutsOpen} onClose={closeShortcuts} />
    </div>
  );
}
