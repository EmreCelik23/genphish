"use client";

import { createContext, useContext, useEffect, useMemo, useState } from "react";

import { resolveTheme } from "@/lib/theme/resolve-theme";
import { AppSettings, defaultSettings } from "@/lib/settings/types";

const STORAGE_KEY = "genphish.settings.v1";

type SettingsContextValue = {
  settings: AppSettings;
  setSettings: (next: Partial<AppSettings>) => void;
  resetSettings: () => void;
};

const SettingsContext = createContext<SettingsContextValue | null>(null);

function readSettingsFromStorage(): AppSettings {
  if (typeof window === "undefined") {
    return defaultSettings;
  }

  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return defaultSettings;
    }

    const parsed = JSON.parse(raw) as Partial<AppSettings>;
    return {
      ...defaultSettings,
      ...parsed
    };
  } catch {
    return defaultSettings;
  }
}

function applyTheme(mode: AppSettings["theme"]) {
  const theme = resolveTheme(mode);
  const root = document.documentElement;
  root.dataset.theme = theme;
  root.style.colorScheme = theme;
}

function applyDensity(density: AppSettings["density"]) {
  document.documentElement.dataset.density = density;
}

export function SettingsProvider({ children }: { children: React.ReactNode }) {
  const [settings, setState] = useState<AppSettings>(defaultSettings);

  useEffect(() => {
    const loaded = readSettingsFromStorage();
    // Hydrate persisted client settings after mount.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setState(loaded);
    applyTheme(loaded.theme);
    applyDensity(loaded.density);
  }, []);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }

    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
    applyTheme(settings.theme);
    applyDensity(settings.density);
  }, [settings]);

  useEffect(() => {
    const listener = () => {
      setState((prev) => {
        if (prev.theme !== "system") {
          return prev;
        }
        applyTheme("system");
        return prev;
      });
    };

    window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", listener);
    return () => window.matchMedia("(prefers-color-scheme: dark)").removeEventListener("change", listener);
  }, []);

  const value = useMemo<SettingsContextValue>(
    () => ({
      settings,
      setSettings: (next) => setState((prev) => ({ ...prev, ...next })),
      resetSettings: () => setState(defaultSettings)
    }),
    [settings]
  );

  return <SettingsContext.Provider value={value}>{children}</SettingsContext.Provider>;
}

export function useSettings() {
  const context = useContext(SettingsContext);
  if (!context) {
    throw new Error("useSettings must be used inside SettingsProvider");
  }
  return context;
}
