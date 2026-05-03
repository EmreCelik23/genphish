"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";

type Options<T> = {
  /** Object keys to search against. */
  keys: (keyof T & string)[];
  /** Debounce duration in ms. Defaults to 200. */
  debounceMs?: number;
};

export function useSearch<T extends Record<string, unknown>>(items: T[], options: Options<T>) {
  const { keys, debounceMs = 200 } = options;

  const [query, setQueryRaw] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const setQuery = useCallback(
    (value: string) => {
      setQueryRaw(value);

      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
      timerRef.current = setTimeout(() => {
        setDebouncedQuery(value);
        timerRef.current = null;
      }, debounceMs);
    },
    [debounceMs]
  );

  const clearQuery = useCallback(() => {
    setQueryRaw("");
    setDebouncedQuery("");
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  }, []);

  useEffect(() => {
    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
    };
  }, []);

  const filtered = useMemo(() => {
    const q = debouncedQuery.trim().toLowerCase();
    if (!q) return items;

    return items.filter((item) =>
      keys.some((key) => {
        const value = item[key];
        if (typeof value === "string") {
          return value.toLowerCase().includes(q);
        }
        if (typeof value === "number") {
          return String(value).includes(q);
        }
        return false;
      })
    );
  }, [items, debouncedQuery, keys]);

  return { filtered, query, setQuery, clearQuery, debouncedQuery };
}
