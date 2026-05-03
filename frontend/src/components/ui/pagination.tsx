import { ChevronLeft, ChevronRight } from "lucide-react";

import { cn } from "@/lib/utils";

type Props = {
  page: number;
  totalPages: number;
  rangeStart: number;
  rangeEnd: number;
  total: number;
  pageSize: number;
  hasNext: boolean;
  hasPrev: boolean;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  /** i18n labels */
  labels?: {
    showing?: string;
    of?: string;
    perPage?: string;
    previous?: string;
    next?: string;
  };
};

const PAGE_SIZE_OPTIONS = [10, 25, 50];

export function Pagination({
  page,
  totalPages,
  rangeStart,
  rangeEnd,
  total,
  pageSize,
  hasNext,
  hasPrev,
  onPageChange,
  onPageSizeChange,
  labels
}: Props) {
  const showingLabel = labels?.showing ?? "Showing";
  const ofLabel = labels?.of ?? "of";
  const perPageLabel = labels?.perPage ?? "per page";

  // Generate page numbers with ellipsis
  const pages = generatePageNumbers(page, totalPages);

  if (total === 0) return null;

  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      {/* Range info */}
      <p className="text-xs text-muted">
        {showingLabel}{" "}
        <span className="font-medium text-text">
          {rangeStart}–{rangeEnd}
        </span>{" "}
        {ofLabel}{" "}
        <span className="font-medium text-text">{total}</span>
      </p>

      {/* Page controls */}
      <div className="flex items-center gap-1">
        <button
          type="button"
          onClick={() => onPageChange(page - 1)}
          disabled={!hasPrev}
          className={cn(
            "inline-flex h-8 w-8 items-center justify-center rounded-lg border border-border text-sm transition-colors",
            hasPrev
              ? "text-text hover:bg-[var(--panel-hover)]"
              : "cursor-not-allowed text-muted/40"
          )}
          aria-label={labels?.previous ?? "Previous"}
        >
          <ChevronLeft className="h-4 w-4" />
        </button>

        {pages.map((p, idx) =>
          p === "..." ? (
            <span key={`ellipsis-${idx}`} className="px-1 text-xs text-muted">
              …
            </span>
          ) : (
            <button
              key={p}
              type="button"
              onClick={() => onPageChange(p as number)}
              className={cn(
                "inline-flex h-8 min-w-8 items-center justify-center rounded-lg border text-xs font-medium transition-colors",
                p === page
                  ? "border-accent/50 bg-accent/10 text-accent"
                  : "border-border text-muted hover:bg-[var(--panel-hover)] hover:text-text"
              )}
            >
              {p}
            </button>
          )
        )}

        <button
          type="button"
          onClick={() => onPageChange(page + 1)}
          disabled={!hasNext}
          className={cn(
            "inline-flex h-8 w-8 items-center justify-center rounded-lg border border-border text-sm transition-colors",
            hasNext
              ? "text-text hover:bg-[var(--panel-hover)]"
              : "cursor-not-allowed text-muted/40"
          )}
          aria-label={labels?.next ?? "Next"}
        >
          <ChevronRight className="h-4 w-4" />
        </button>

        {/* Page size selector */}
        <select
          value={pageSize}
          onChange={(e) => onPageSizeChange(Number(e.target.value))}
          className="ml-2 h-8 rounded-lg border border-border bg-panel px-2 text-xs text-text outline-none focus:border-accent/50"
        >
          {PAGE_SIZE_OPTIONS.map((opt) => (
            <option key={opt} value={opt}>
              {opt} {perPageLabel}
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}

/**
 * Generate an array of page numbers with ellipsis for large page counts.
 *
 * Example: [1, 2, 3, "...", 8, 9, 10] for page 2 of 10.
 */
function generatePageNumbers(current: number, total: number): (number | "...")[] {
  if (total <= 7) {
    return Array.from({ length: total }, (_, i) => i + 1);
  }

  const pages: (number | "...")[] = [];

  // Always show first page
  pages.push(1);

  if (current > 3) {
    pages.push("...");
  }

  // Window around current
  const start = Math.max(2, current - 1);
  const end = Math.min(total - 1, current + 1);
  for (let i = start; i <= end; i++) {
    pages.push(i);
  }

  if (current < total - 2) {
    pages.push("...");
  }

  // Always show last page
  if (total > 1) {
    pages.push(total);
  }

  return pages;
}
