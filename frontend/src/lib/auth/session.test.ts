import { afterEach, describe, expect, it, vi } from "vitest";

import { isTokenExpired, parseTokenExpiry, parseTokenRole, sessionTimeRemaining } from "./session";

function createToken(payload: Record<string, unknown>): string {
  const encode = (value: Record<string, unknown>) =>
    Buffer.from(JSON.stringify(value)).toString("base64url");

  return `${encode({ alg: "none", typ: "JWT" })}.${encode(payload)}.signature`;
}

describe("session helpers", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("parses role from role claim", () => {
    const token = createToken({ role: "admin" });
    expect(parseTokenRole(token)).toBe("admin");
  });

  it("falls back to roles array when role is missing", () => {
    const token = createToken({ roles: ["viewer"] });
    expect(parseTokenRole(token)).toBe("viewer");
  });

  it("parses expiration from exp claim", () => {
    const token = createToken({ exp: 1_700_000_000 });
    expect(parseTokenExpiry(token)).toBe(1_700_000_000_000);
  });

  it("detects expiry using current time", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-05-04T00:00:00.000Z"));

    expect(isTokenExpired(Date.now() - 1_000)).toBe(true);
    expect(isTokenExpired(Date.now() + 1_000)).toBe(false);
  });

  it("returns remaining session time in milliseconds", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-05-04T00:00:00.000Z"));

    expect(sessionTimeRemaining(Date.now() + 5_000)).toBe(5_000);
    expect(sessionTimeRemaining(null)).toBeNull();
  });
});
