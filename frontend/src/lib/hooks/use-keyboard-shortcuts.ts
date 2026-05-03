"use client";

import { useEffect, useRef } from "react";

type KeyHandler = () => void;
type Shortcut = {
  keys: string; // e.g. "g c" or "?"
  handler: KeyHandler;
  description?: string;
};

const SEQUENCE_TIMEOUT = 800; // ms between key presses in a sequence

/**
 * Registers keyboard shortcuts. Sequences (e.g. "g c") are supported.
 * Shortcuts are automatically disabled when focus is inside input/textarea/select.
 */
export function useKeyboardShortcuts(shortcuts: Shortcut[]) {
  const pending = useRef<string | null>(null);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Ignore when typing in form elements
      const target = e.target as HTMLElement;
      if (
        target.tagName === "INPUT" ||
        target.tagName === "TEXTAREA" ||
        target.tagName === "SELECT" ||
        target.isContentEditable
      ) {
        return;
      }

      // Ignore modifier combos (Ctrl/Meta)
      if (e.ctrlKey || e.metaKey || e.altKey) return;

      const key = e.key.toLowerCase();

      // Try sequence: pending + key (e.g. "g" then "c" → "g c")
      if (pending.current !== null) {
        const sequence = `${pending.current} ${key}`;
        const match = shortcuts.find((s) => s.keys === sequence);
        if (match) {
          e.preventDefault();
          match.handler();
        }
        pending.current = null;
        if (timeoutRef.current) clearTimeout(timeoutRef.current);
        return;
      }

      // Try single key
      const singleMatch = shortcuts.find((s) => s.keys === key);
      if (singleMatch) {
        e.preventDefault();
        singleMatch.handler();
        return;
      }

      // Check if this key is a prefix in any sequence
      const isPrefix = shortcuts.some((s) => s.keys.startsWith(`${key} `));
      if (isPrefix) {
        pending.current = key;
        if (timeoutRef.current) clearTimeout(timeoutRef.current);
        timeoutRef.current = setTimeout(() => {
          pending.current = null;
        }, SEQUENCE_TIMEOUT);
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => {
      window.removeEventListener("keydown", handleKeyDown);
      if (timeoutRef.current) clearTimeout(timeoutRef.current);
    };
  }, [shortcuts]);
}
