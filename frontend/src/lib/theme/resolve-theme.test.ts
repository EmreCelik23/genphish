import { afterEach, describe, expect, it } from "vitest";

import { resolveTheme } from "./resolve-theme";

const originalWindow = globalThis.window;

function mockMatchMedia(matches: boolean) {
  Object.defineProperty(globalThis, "window", {
    value: {
      matchMedia: () => ({ matches })
    },
    writable: true,
    configurable: true
  });
}

describe("resolveTheme", () => {
  afterEach(() => {
    if (originalWindow === undefined) {
      Reflect.deleteProperty(globalThis, "window");
      return;
    }

    Object.defineProperty(globalThis, "window", {
      value: originalWindow,
      writable: true,
      configurable: true
    });
  });

  it("returns explicit mode for dark/light", () => {
    expect(resolveTheme("dark")).toBe("dark");
    expect(resolveTheme("light")).toBe("light");
  });

  it("returns dark for system mode when media query matches", () => {
    mockMatchMedia(true);
    expect(resolveTheme("system")).toBe("dark");
  });

  it("returns light for system mode when media query does not match", () => {
    mockMatchMedia(false);
    expect(resolveTheme("system")).toBe("light");
  });
});
