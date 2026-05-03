import { cn } from "@/lib/utils";

type ProgressBarTone = "neutral" | "success" | "warning" | "danger" | "info";

type ProgressBarProps = {
  /** Current value (0–max) */
  value: number;
  /** Maximum value. Default: 100 */
  max?: number;
  /** Visual tone */
  tone?: ProgressBarTone;
  /** Height in px. Default: 6 */
  height?: number;
  /** Show value label. Default: false */
  showLabel?: boolean;
  className?: string;
};

const toneColors: Record<ProgressBarTone, string> = {
  neutral: "bg-border",
  success: "bg-emerald-500",
  warning: "bg-amber-500",
  danger: "bg-rose-500",
  info: "bg-sky-500"
};

function toneFromPercent(pct: number): ProgressBarTone {
  if (pct >= 70) return "danger";
  if (pct >= 40) return "warning";
  return "success";
}

export function ProgressBar({
  value,
  max = 100,
  tone,
  height = 6,
  showLabel = false,
  className
}: ProgressBarProps) {
  const pct = Math.max(0, Math.min(100, max === 0 ? 0 : (value / max) * 100));
  const resolvedTone = tone ?? toneFromPercent(pct);

  return (
    <div className={cn("w-full", className)}>
      <div
        className="w-full overflow-hidden rounded-full bg-surface"
        style={{ height }}
        role="progressbar"
        aria-valuenow={Math.round(pct)}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        <div
          className={cn("h-full rounded-full transition-all duration-500 ease-out", toneColors[resolvedTone])}
          style={{ width: `${pct}%` }}
        />
      </div>
      {showLabel && (
        <p className="mt-1 text-right text-xs text-muted">{pct.toFixed(1)}%</p>
      )}
    </div>
  );
}
