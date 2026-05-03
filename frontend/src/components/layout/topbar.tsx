"use client";

import { Languages, Menu, MoonStar, SunMedium } from "lucide-react";
import { usePathname } from "next/navigation";

import { Button } from "@/components/ui/button";
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
  const { settings, setSettings } = useSettings();
  const { t } = useI18n();

  const pageTitle = t.nav[pageMap[pathname] ?? "dashboard"];

  const toggleTheme = () => {
    const next = settings.theme === "dark" ? "light" : "dark";
    setSettings({ theme: next });
  };

  const toggleLanguage = () => {
    setSettings({ language: settings.language === "tr" ? "en" : "tr" });
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
        <Button variant="ghost" onClick={toggleLanguage} className="gap-2">
          <Languages className="h-4 w-4" />
          <span className="font-mono text-xs uppercase">{settings.language}</span>
        </Button>
        <Button variant="ghost" onClick={toggleTheme} className="gap-2">
          {settings.theme === "dark" ? <SunMedium className="h-4 w-4" /> : <MoonStar className="h-4 w-4" />}
          <span className="text-xs">{settings.theme}</span>
        </Button>
      </div>
    </header>
  );
}
