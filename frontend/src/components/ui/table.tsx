import { type ReactNode, createContext, useContext } from "react";
import { ArrowDown, ArrowUp, ArrowUpDown } from "lucide-react";
import { cn } from "@/lib/utils";

// ── Types ─────────────────────────────────────────────────────────────

export type SortDirection = "asc" | "desc";

type TableContextValue = {
  compact: boolean;
  striped: boolean;
};

const TableContext = createContext<TableContextValue>({ compact: false, striped: false });

// ── Root ──────────────────────────────────────────────────────────────

type TableProps = {
  children: ReactNode;
  /** Smaller row padding */
  compact?: boolean;
  /** Alternating row background */
  striped?: boolean;
  className?: string;
};

export function Table({ children, compact = false, striped = false, className }: TableProps) {
  return (
    <TableContext.Provider value={{ compact, striped }}>
      <div className={cn("w-full overflow-x-auto rounded-xl border border-border", className)}>
        <table className="w-full border-collapse text-sm">{children}</table>
      </div>
    </TableContext.Provider>
  );
}

// ── Head ──────────────────────────────────────────────────────────────

export function TableHead({ children }: { children: ReactNode }) {
  return (
    <thead className="border-b border-border bg-surface/60">
      {children}
    </thead>
  );
}

// ── Body ──────────────────────────────────────────────────────────────

export function TableBody({ children }: { children: ReactNode }) {
  return <tbody>{children}</tbody>;
}

// ── Row ───────────────────────────────────────────────────────────────

type TableRowProps = {
  children: ReactNode;
  /** Index (0-based) used for striping */
  index?: number;
  onClick?: () => void;
  className?: string;
};

export function TableRow({ children, index, onClick, className }: TableRowProps) {
  const { striped } = useContext(TableContext);
  const isOdd = typeof index === "number" && index % 2 !== 0;

  return (
    <tr
      onClick={onClick}
      className={cn(
        "border-b border-border/50 last:border-0 transition-colors",
        striped && isOdd ? "bg-surface/30" : "bg-transparent",
        onClick && "cursor-pointer hover:bg-surface/50",
        className
      )}
    >
      {children}
    </tr>
  );
}

// ── Cell ──────────────────────────────────────────────────────────────

type TableCellProps = {
  children?: ReactNode;
  /** Renders as <th> with header styles */
  header?: boolean;
  /** Sort key for this column */
  sortKey?: string;
  /** Active sort key */
  activeSortKey?: string;
  /** Active sort direction */
  sortDirection?: SortDirection;
  /** Callback when header clicked */
  onSort?: (key: string) => void;
  /** Text alignment */
  align?: "left" | "center" | "right";
  /** Width hint */
  width?: string;
  /** Colspan */
  colSpan?: number;
  className?: string;
};

export function TableCell({
  children,
  header = false,
  sortKey,
  activeSortKey,
  sortDirection,
  onSort,
  align = "left",
  width,
  colSpan,
  className
}: TableCellProps) {
  const { compact } = useContext(TableContext);

  const padding = compact ? "px-3 py-2" : "px-4 py-3";
  const alignClass = align === "center" ? "text-center" : align === "right" ? "text-right" : "text-left";
  const isSorted = sortKey && activeSortKey === sortKey;
  const sortable = !!sortKey && !!onSort;

  const SortIcon = isSorted
    ? sortDirection === "asc"
      ? ArrowUp
      : ArrowDown
    : ArrowUpDown;

  if (header) {
    return (
      <th
        className={cn(
          padding,
          alignClass,
          "text-[11px] font-medium uppercase tracking-[0.12em] text-muted",
          sortable && "cursor-pointer select-none hover:text-text",
          className
        )}
        style={width ? { width } : undefined}
        colSpan={colSpan}
        onClick={sortable ? () => onSort(sortKey!) : undefined}
      >
        <span className="inline-flex items-center gap-1.5">
          {children}
          {sortable && <SortIcon className={cn("h-3 w-3", isSorted ? "text-accent" : "opacity-40")} />}
        </span>
      </th>
    );
  }

  return (
    <td
      className={cn(padding, alignClass, "text-text", className)}
      style={width ? { width } : undefined}
      colSpan={colSpan}
    >
      {children}
    </td>
  );
}
