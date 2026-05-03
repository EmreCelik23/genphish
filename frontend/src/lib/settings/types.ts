export type ThemeMode = "dark" | "light" | "system";
export type Language = "tr" | "en";
export type Density = "comfortable" | "compact";

export type AppSettings = {
  theme: ThemeMode;
  language: Language;
  density: Density;
  apiBaseUrl: string;
  apiToken: string;
  companyId: string;
};

export const defaultSettings: AppSettings = {
  theme: "system",
  language: "tr",
  density: "comfortable",
  apiBaseUrl: process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8088",
  apiToken: "",
  companyId: ""
};
