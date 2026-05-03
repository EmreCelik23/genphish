import type { AuthState, UserRole } from "@/lib/auth/types";

const SESSION_KEY = "genphish.session.v1";
const REMEMBER_KEY = "genphish.session.remember";

// ── Persistence ──────────────────────────────────────────────────────

type PersistedSession = {
  token: string;
  companyId: string;
  companyName: string;
  role: UserRole;
  expiresAt: number | null;
};

export function saveSession(state: AuthState, remember: boolean): void {
  const data: PersistedSession = {
    token: state.token,
    companyId: state.companyId,
    companyName: state.companyName,
    role: state.role,
    expiresAt: state.expiresAt
  };

  const serialized = JSON.stringify(data);
  sessionStorage.setItem(SESSION_KEY, serialized);

  if (remember) {
    localStorage.setItem(REMEMBER_KEY, serialized);
  } else {
    localStorage.removeItem(REMEMBER_KEY);
  }
}

export function loadSession(): AuthState | null {
  if (typeof window === "undefined") {
    return null;
  }

  const raw = sessionStorage.getItem(SESSION_KEY) ?? localStorage.getItem(REMEMBER_KEY);
  if (!raw) {
    return null;
  }

  try {
    const parsed = JSON.parse(raw) as PersistedSession;

    if (!parsed.token || !parsed.companyId) {
      return null;
    }

    if (isTokenExpired(parsed.expiresAt)) {
      clearSession();
      return null;
    }

    return {
      ...parsed,
      isAuthenticated: true
    };
  } catch {
    clearSession();
    return null;
  }
}

export function clearSession(): void {
  sessionStorage.removeItem(SESSION_KEY);
  localStorage.removeItem(REMEMBER_KEY);
}

// ── Token parsing ────────────────────────────────────────────────────

export function parseTokenExpiry(token: string): number | null {
  const payload = decodeJwtPayload(token);
  if (!payload || typeof payload.exp !== "number") {
    return null;
  }
  return payload.exp * 1000; // seconds → ms
}

export function parseTokenRole(token: string): UserRole {
  const payload = decodeJwtPayload(token);
  if (!payload) {
    return "operator";
  }

  const role = (payload.role ?? (payload.roles as string[] | undefined)?.[0]) as string | undefined;
  if (role === "admin" || role === "operator" || role === "viewer") {
    return role;
  }
  return "operator";
}

export function isTokenExpired(expiresAt: number | null): boolean {
  if (expiresAt === null) {
    return false; // static token, never expires
  }
  return Date.now() >= expiresAt;
}

export function sessionTimeRemaining(expiresAt: number | null): number | null {
  if (expiresAt === null) {
    return null; // infinite
  }
  return Math.max(0, expiresAt - Date.now());
}

// ── JWT helpers (decode-only, validation is backend's job) ───────────

function decodeJwtPayload(token: string): Record<string, unknown> | null {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) {
      return null;
    }

    const base64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64 + "=".repeat((4 - (base64.length % 4)) % 4);
    const decoded = atob(padded);
    return JSON.parse(decoded) as Record<string, unknown>;
  } catch {
    return null;
  }
}
