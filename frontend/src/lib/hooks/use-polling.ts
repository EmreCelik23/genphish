"use client";

import { useCallback, useEffect, useRef, useState } from "react";

type PollingOptions = {
  /** Interval in ms between polls. Default: 5000 */
  intervalMs?: number;
  /** Whether polling is active. Toggling to false stops the interval. Default: true */
  enabled?: boolean;
};

type PollingState = {
  isPolling: boolean;
  stop: () => void;
  start: () => void;
};

/**
 * Repeatedly calls `fn` at a fixed interval while `enabled` is true.
 * Automatically clears the interval on unmount or when disabled.
 */
export function usePolling(fn: () => void | Promise<void>, options: PollingOptions = {}): PollingState {
  const { intervalMs = 5000, enabled = true } = options;

  const fnRef = useRef(fn);
  const [isPolling, setIsPolling] = useState(true);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    fnRef.current = fn;
  }, [fn]);

  const clear = useCallback(() => {
    if (intervalRef.current !== null) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
  }, []);

  const start = useCallback(() => {
    setIsPolling(true);
  }, []);

  const stop = useCallback(() => {
    setIsPolling(false);
    clear();
  }, [clear]);

  useEffect(() => {
    if (!isPolling || !enabled) {
      clear();
      return;
    }

    intervalRef.current = setInterval(() => {
      void fnRef.current();
    }, intervalMs);

    return clear;
  }, [isPolling, enabled, intervalMs, clear]);

  return { isPolling: isPolling && enabled, start, stop };
}
