import { cn } from "@/lib/utils";

type BadgeTone = "neutral" | "success" | "warning" | "danger" | "info";

const toneMap: Record<BadgeTone, string> = {
  neutral: "border-border bg-surface/70 text-muted",
  success: "border-emerald-500/30 bg-emerald-500/15 text-emerald-300",
  warning: "border-amber-500/30 bg-amber-500/15 text-amber-300",
  danger: "border-rose-500/35 bg-rose-500/15 text-rose-300",
  info: "border-sky-500/30 bg-sky-500/15 text-sky-300"
};

export function Badge({
  children,
  tone = "neutral",
  className
}: {
  children: React.ReactNode;
  tone?: BadgeTone;
  className?: string;
}) {
  return (
    <span className={cn("inline-flex items-center rounded-md border px-2 py-1 text-[11px] font-medium", toneMap[tone], className)}>
      {children}
    </span>
  );
}
