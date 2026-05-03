import * as React from "react";

import { cn } from "@/lib/utils";

type Variant = "primary" | "ghost" | "danger";

type Props = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: Variant;
};

const variantClasses: Record<Variant, string> = {
  primary:
    "bg-[var(--button-bg)] text-[var(--button-text)] border border-[var(--button-border)] hover:bg-[var(--button-bg-hover)]",
  ghost:
    "bg-transparent text-text border border-border hover:bg-[var(--panel-hover)]",
  danger: "bg-[rgba(239,68,68,0.18)] text-red-200 border border-[rgba(239,68,68,0.3)] hover:bg-[rgba(239,68,68,0.3)]"
};

export function Button({ className, variant = "primary", ...props }: Props) {
  return (
    <button
      className={cn(
        "inline-flex h-10 items-center justify-center rounded-lg px-4 text-sm font-medium transition-colors duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/50 disabled:cursor-not-allowed disabled:opacity-50",
        variantClasses[variant],
        className
      )}
      {...props}
    />
  );
}
