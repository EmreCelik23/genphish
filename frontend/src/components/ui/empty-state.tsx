import type { LucideIcon } from "lucide-react";

import { cn } from "@/lib/utils";

type Props = {
  /** Icon component from lucide-react. */
  icon: LucideIcon;
  /** Main title. */
  title: string;
  /** Description text. */
  description?: string;
  /** Optional action element (e.g. a Button). */
  action?: React.ReactNode;
  /** Extra class names. */
  className?: string;
};

export function EmptyState({ icon: Icon, title, description, action, className }: Props) {
  return (
    <div className={cn("flex flex-col items-center justify-center py-12 text-center", className)}>
      <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl border border-border bg-surface/60">
        <Icon className="h-6 w-6 text-muted" />
      </div>
      <p className="text-sm font-medium text-text">{title}</p>
      {description ? <p className="mt-1 max-w-xs text-xs text-muted">{description}</p> : null}
      {action ? <div className="mt-4">{action}</div> : null}
    </div>
  );
}
