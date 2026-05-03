"use client";

import { Languages, LogOut, Menu, MoonStar, SunMedium, Timer } from "lucide-react";
import { usePathname, useRouter } from "next/navigation";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/lib/auth/auth-context";
import { useI18n } from "@/lib/i18n/i18n-context";
import { useSettings } from "@/lib/settings/settings-context";

const pageMap: Record<string, keyof ReturnType<typeof useI18n>["t"]["nav"]> = {
  "/dashboard": "dashboard",
  "/campaigns": "campaigns",
  "/templates": "templates",
  "/employees": "employees",
  "/settings": "settings",
  "/access": "access"
};

export function Topbar({ onMenuClick }: { onMenuClick: () => void }) {
  const pathname = usePathname();
  const router = useRouter();
  const { settings, setSettings } = useSettings();
  const { t } = useI18n();
  const { auth, sessionWarning, logout } = useAuth();

  const pageTitle = t.nav[pageMap[pathname] ?? "dashboard"];

  const toggleTheme = () => {
    const next = settings.theme === "dark" ? "light" : "dark";
    setSettings({ theme: next });
  };

  const toggleLanguage = () => {
    setSettings({ language: settings.language === "tr" ? "en" : "tr" });
  };

  const handleLogout = () => {
    logout();
    router.push("/access");
  };

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-border bg-surface/80 px-4 backdrop-blur lg:px-6">
      <div className="flex items-center gap-3">
        <button
          onClick={onMenuClick}
          className="rounded-lg border border-border bg-panel p-2 text-muted hover:text-text lg:hidden"
          aria-label="Open menu"
        >
          <Menu className="h-4 w-4" />
        </button>
        <div>
          <p className="text-xs uppercase tracking-[0.14em] text-muted">Workspace</p>
          <p className="text-sm font-semibold text-text">{pageTitle}</p>
        </div>
      </div>

      <div className="flex items-center gap-2">
        {/* Session warning badge */}
        {sessionWarning ? (
          <Badge tone="warning" className="gap-1.5">
            <Timer className="h-3 w-3" />
            {t.auth.sessionExpired}
          </Badge>
        ) : null}

        {/* Company badge */}
        {auth.isAuthenticated && auth.companyName ? (
          <Badge tone="neutral" className="hidden md:inline-flex">
            {auth.companyName}
          </Badge>
        ) : null}

        <Button variant="ghost" onClick={toggleLanguage} className="gap-2">
          <Languages className="h-4 w-4" />
          <span className="font-mono text-xs uppercase">{settings.language}</span>
        </Button>
        <Button variant="ghost" onClick={toggleTheme} className="gap-2">
          {settings.theme === "dark" ? <SunMedium className="h-4 w-4" /> : <MoonStar className="h-4 w-4" />}
          <span className="text-xs">{settings.theme}</span>
        </Button>

        {/* Logout (visible on desktop) */}
        {auth.isAuthenticated ? (
          <Button variant="ghost" onClick={handleLogout} className="hidden gap-2 lg:inline-flex">
            <LogOut className="h-4 w-4" />
          </Button>
        ) : null}
      </div>
    </header>
  );
}
