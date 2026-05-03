"use client";

import { useCallback, useEffect, useRef } from "react";

type AbortHandle = {
  /** The signal to pass to fetch/API calls. */
  signal: AbortSignal;
  /** Manually abort the current signal and create a new one. */
  abort: () => void;
  /** Create a fresh AbortController (use when re-fetching). */
  reset: () => AbortSignal;
};

/**
 * Provides an AbortSignal that is automatically aborted when the component unmounts.
 * Use this to cancel in-flight fetch requests on navigation / unmount.
 */
export function useAbort(): AbortHandle {
  const controllerRef = useRef<AbortController>(new AbortController());

  // Abort on unmount
  useEffect(() => {
    return () => {
      controllerRef.current.abort();
    };
  }, []);

  const abort = useCallback(() => {
    controllerRef.current.abort();
    controllerRef.current = new AbortController();
  }, []);

  const reset = useCallback((): AbortSignal => {
    controllerRef.current.abort();
    controllerRef.current = new AbortController();
    return controllerRef.current.signal;
  }, []);

  return {
    get signal() {
      return controllerRef.current.signal;
    },
    abort,
    reset
  };
}
