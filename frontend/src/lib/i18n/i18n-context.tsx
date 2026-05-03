"use client";

import { createContext, useContext, useMemo } from "react";

import { Dictionary, getDictionary } from "@/lib/i18n/dictionaries";
import { useSettings } from "@/lib/settings/settings-context";

type I18nContextValue = {
  t: Dictionary;
};

const I18nContext = createContext<I18nContextValue | null>(null);

export function I18nProvider({ children }: { children: React.ReactNode }) {
  const { settings } = useSettings();

  const value = useMemo<I18nContextValue>(() => ({ t: getDictionary(settings.language) }), [settings.language]);

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n() {
  const context = useContext(I18nContext);
  if (!context) {
    throw new Error("useI18n must be used inside I18nProvider");
  }
  return context;
}
