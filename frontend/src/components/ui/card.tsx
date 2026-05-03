import { cn } from "@/lib/utils";

export function Card({ className, children }: { className?: string; children: React.ReactNode }) {
  return (
    <div
      className={cn(
        "group relative overflow-hidden rounded-2xl border border-border bg-panel p-5 shadow-panel transition duration-300 hover:shadow-hover",
        className
      )}
    >
      <div className="pointer-events-none absolute inset-0 bg-spotlight opacity-0 transition-opacity duration-300 group-hover:opacity-100" />
      <div className="relative z-10">{children}</div>
    </div>
  );
}
