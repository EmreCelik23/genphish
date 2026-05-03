"use client";

import { useState } from "react";

import { Sidebar } from "@/components/layout/sidebar";
import { Topbar } from "@/components/layout/topbar";

export function WorkspaceShell({ children }: { children: React.ReactNode }) {
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <div className="min-h-screen bg-surface text-text">
      <div className="relative mx-auto flex w-full max-w-[1600px]">
        <Sidebar mobileOpen={mobileOpen} onClose={() => setMobileOpen(false)} />
        <div className="min-h-screen min-w-0 flex-1">
          <Topbar onMenuClick={() => setMobileOpen(true)} />
          <main className="p-4 lg:p-6">{children}</main>
        </div>
      </div>
    </div>
  );
}
