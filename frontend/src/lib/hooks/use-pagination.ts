"use client";

import { useMemo, useState } from "react";

export type PaginationState = {
  /** Current page (1-indexed). */
  page: number;
  /** Items per page. */
  pageSize: number;
  /** Total number of items. */
  total: number;
  /** Total pages. */
  totalPages: number;
  /** Start index for display ("Showing X-Y of Z"). */
  rangeStart: number;
  /** End index for display. */
  rangeEnd: number;
  /** Paginated slice of items. */
  paginated: unknown[];
  /** Set current page. */
  setPage: (page: number) => void;
  /** Set page size (resets to page 1). */
  setPageSize: (size: number) => void;
  /** Go to next page. */
  nextPage: () => void;
  /** Go to previous page. */
  prevPage: () => void;
  /** Whether there is a next page. */
  hasNext: boolean;
  /** Whether there is a previous page. */
  hasPrev: boolean;
};

type Options = {
  defaultPageSize?: number;
};

export function usePagination<T>(items: T[], options: Options = {}): Omit<PaginationState, "paginated"> & { paginated: T[] } {
  const { defaultPageSize = 10 } = options;

  const [page, setPageRaw] = useState(1);
  const [pageSize, setPageSizeRaw] = useState(defaultPageSize);

  const total = items.length;
  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  // Clamp page to valid range
  const clampedPage = Math.min(page, totalPages);
  if (clampedPage !== page) {
    // Will be corrected on next render cycle
    setPageRaw(clampedPage);
  }

  const paginated = useMemo(() => {
    const start = (clampedPage - 1) * pageSize;
    return items.slice(start, start + pageSize);
  }, [items, clampedPage, pageSize]);

  const rangeStart = total === 0 ? 0 : (clampedPage - 1) * pageSize + 1;
  const rangeEnd = Math.min(clampedPage * pageSize, total);

  const setPage = (p: number) => setPageRaw(Math.max(1, Math.min(p, totalPages)));
  const setPageSize = (size: number) => {
    setPageSizeRaw(size);
    setPageRaw(1);
  };

  const hasNext = clampedPage < totalPages;
  const hasPrev = clampedPage > 1;
  const nextPage = () => { if (hasNext) setPageRaw(clampedPage + 1); };
  const prevPage = () => { if (hasPrev) setPageRaw(clampedPage - 1); };

  return {
    page: clampedPage,
    pageSize,
    total,
    totalPages,
    rangeStart,
    rangeEnd,
    paginated,
    setPage,
    setPageSize,
    nextPage,
    prevPage,
    hasNext,
    hasPrev
  };
}
