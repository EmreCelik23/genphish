"use client";

import { I18nProvider } from "@/lib/i18n/i18n-context";
import { SettingsProvider } from "@/lib/settings/settings-context";

export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <SettingsProvider>
      <I18nProvider>{children}</I18nProvider>
    </SettingsProvider>
  );
}
