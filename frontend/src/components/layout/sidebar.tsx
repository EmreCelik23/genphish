"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { FileText, Home, Megaphone, Settings, Shield, Users, X } from "lucide-react";

import { useI18n } from "@/lib/i18n/i18n-context";
import { useSettings } from "@/lib/settings/settings-context";
import { cn } from "@/lib/utils";

const navItems = [
  { href: "/dashboard", key: "dashboard", icon: Home },
  { href: "/campaigns", key: "campaigns", icon: Megaphone },
  { href: "/templates", key: "templates", icon: FileText },
  { href: "/employees", key: "employees", icon: Users },
  { href: "/settings", key: "settings", icon: Settings }
] as const;

type SidebarProps = {
  mobileOpen: boolean;
  onClose: () => void;
};

function SidebarContent({ onNavigate }: { onNavigate: () => void }) {
  const pathname = usePathname();
  const { t } = useI18n();
  const { settings } = useSettings();

  return (
    <>
      <div className="flex items-center gap-3 px-6 py-6">
        <div className="rounded-lg border border-border bg-surface p-2">
          <Shield className="h-5 w-5 text-accent" />
        </div>
        <div>
          <p className="text-sm font-semibold text-text">{t.appName}</p>
          <p className="text-xs text-muted">SOC Workspace</p>
        </div>
      </div>

      <nav className="px-3">
        {navItems.map((item) => {
          const active = pathname === item.href;
          const Icon = item.icon;
          return (
            <Link
              key={item.href}
              href={item.href}
              onClick={onNavigate}
              className={cn(
                "mb-1 flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm transition",
                active
                  ? "border border-border bg-[linear-gradient(120deg,rgba(56,189,248,0.13),transparent)] text-text"
                  : "text-muted hover:bg-[var(--panel-hover)] hover:text-text"
              )}
            >
              <Icon className="h-4 w-4" />
              <span>{t.nav[item.key]}</span>
            </Link>
          );
        })}
      </nav>

      <div className="mt-auto space-y-3 px-6 py-6">
        <div className="rounded-xl border border-border bg-surface/50 p-3">
          <p className="text-[10px] uppercase tracking-[0.16em] text-muted">Company Scope</p>
          <p className="mt-1 truncate font-mono text-xs text-text">
            {settings.companyId ? settings.companyId : "not configured"}
          </p>
        </div>
        <Link href="/access" onClick={onNavigate} className="text-xs text-muted underline-offset-2 hover:text-text hover:underline">
          {t.nav.access}
        </Link>
      </div>
    </>
  );
}

export function Sidebar({ mobileOpen, onClose }: SidebarProps) {
  return (
    <>
      <div
        className={cn(
          "fixed inset-0 z-40 bg-black/45 transition-opacity duration-200 lg:hidden",
          mobileOpen ? "pointer-events-auto opacity-100" : "pointer-events-none opacity-0"
        )}
        onClick={onClose}
      />

      <aside
        className={cn(
          "fixed inset-y-0 left-0 z-50 flex w-[280px] flex-col border-r border-border bg-panel/95 backdrop-blur transition-transform duration-300 lg:hidden",
          mobileOpen ? "translate-x-0" : "-translate-x-full"
        )}
      >
        <button
          aria-label="Close menu"
          onClick={onClose}
          className="absolute right-3 top-3 rounded-md border border-border bg-surface/70 p-1.5 text-muted hover:text-text"
        >
          <X className="h-4 w-4" />
        </button>
        <SidebarContent onNavigate={onClose} />
      </aside>

      <aside className="sticky top-0 hidden h-screen w-[280px] flex-col border-r border-border bg-panel/90 backdrop-blur lg:flex">
        <SidebarContent onNavigate={() => undefined} />
      </aside>
    </>
  );
}
