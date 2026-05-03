import type { ThemeMode } from "@/lib/settings/types";

export function resolveTheme(mode: ThemeMode): "dark" | "light" {
  if (mode === "dark" || mode === "light") {
    return mode;
  }

  if (typeof window !== "undefined" && window.matchMedia("(prefers-color-scheme: dark)").matches) {
    return "dark";
  }

  return "light";
}
