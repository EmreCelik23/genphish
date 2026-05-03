"use client";

import { Search, X } from "lucide-react";

import { cn } from "@/lib/utils";
import { useI18n } from "@/lib/i18n/i18n-context";

type Props = {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
};

export function SearchInput({ value, onChange, placeholder = "Search...", className }: Props) {
  const { t } = useI18n();

  return (
    <div className={cn("relative", className)}>
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" />
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className="h-10 w-full rounded-lg border border-border bg-panel pl-10 pr-9 text-sm text-text outline-none transition-[border-color,box-shadow] placeholder:text-muted focus:border-accent/70 focus:shadow-[0_0_0_3px_rgba(56,189,248,0.12)]"
      />
      {value ? (
        <button
          type="button"
          onClick={() => onChange("")}
          className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded p-0.5 text-muted transition-colors hover:text-text"
          aria-label={t.search.clearSearch}
        >
          <X className="h-3.5 w-3.5" />
        </button>
      ) : null}
    </div>
  );
}
