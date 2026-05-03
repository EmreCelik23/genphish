import type { Config } from "tailwindcss";

const config: Config = {
  darkMode: ["class", '[data-theme="dark"]'],
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}"
  ],
  theme: {
    extend: {
      colors: {
        surface: "var(--surface)",
        panel: "var(--panel)",
        border: "var(--border)",
        muted: "var(--muted)",
        text: "var(--text)",
        accent: "var(--accent)",
        danger: "var(--danger)",
        success: "var(--success)"
      },
      borderRadius: {
        xl2: "1rem"
      },
      boxShadow: {
        panel: "0 0 0 1px var(--border), 0 10px 40px -18px rgba(0,0,0,0.5)",
        hover: "0 0 0 1px var(--border), 0 18px 50px -22px rgba(20, 184, 166, 0.28)"
      },
      backgroundImage: {
        "grid-fade": "linear-gradient(to right, var(--grid) 1px, transparent 1px), linear-gradient(to bottom, var(--grid) 1px, transparent 1px)",
        "spotlight": "radial-gradient(600px circle at var(--spot-x,50%) var(--spot-y,0%), rgba(56,189,248,0.08), transparent 40%)"
      },
      fontFamily: {
        sans: ["var(--font-space-grotesk)", "system-ui", "sans-serif"],
        mono: ["var(--font-plex-mono)", "ui-monospace", "SFMono-Regular"]
      }
    }
  },
  plugins: []
};

export default config;
