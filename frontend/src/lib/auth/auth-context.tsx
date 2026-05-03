"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState
} from "react";

import type { AuthState, LoginPayload, UserRole } from "@/lib/auth/types";
import { hasMinimumRole } from "@/lib/auth/types";
import {
  clearSession,
  isTokenExpired,
  loadSession,
  parseTokenExpiry,
  parseTokenRole,
  saveSession,
  sessionTimeRemaining
} from "@/lib/auth/session";

// ── Context shape ────────────────────────────────────────────────────

type AuthContextValue = {
  auth: AuthState;
  login: (payload: LoginPayload) => void;
  logout: () => void;
  hasRole: (role: UserRole) => boolean;
  sessionWarning: boolean; // true when <5 min remaining
};

const emptyAuth: AuthState = {
  token: "",
  companyId: "",
  companyName: "",
  role: "operator",
  expiresAt: null,
  isAuthenticated: false
};

const AuthContext = createContext<AuthContextValue | null>(null);

// ── Session expiry thresholds ────────────────────────────────────────

const WARNING_THRESHOLD_MS = 5 * 60 * 1000; // 5 minutes
const CHECK_INTERVAL_MS = 30 * 1000; // check every 30s

// ── Provider ─────────────────────────────────────────────────────────

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [auth, setAuth] = useState<AuthState>(emptyAuth);
  const [sessionWarning, setSessionWarning] = useState(false);
  const [hydrated, setHydrated] = useState(false);

  // ── Hydrate from storage on mount ──────────────────────────────────

  useEffect(() => {
    const saved = loadSession();
    if (saved) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setAuth(saved);
    }
    setHydrated(true);
  }, []);

  // ── Login ──────────────────────────────────────────────────────────

  const login = useCallback((payload: LoginPayload) => {
    const expiresAt = parseTokenExpiry(payload.token);
    const role = parseTokenRole(payload.token);

    const nextAuth: AuthState = {
      token: payload.token,
      companyId: payload.companyId,
      companyName: payload.companyName,
      role,
      expiresAt,
      isAuthenticated: true
    };

    setAuth(nextAuth);
    saveSession(nextAuth, payload.remember ?? false);
    setSessionWarning(false);
  }, []);

  // ── Logout ─────────────────────────────────────────────────────────

  const logout = useCallback(() => {
    setAuth(emptyAuth);
    setSessionWarning(false);
    clearSession();
  }, []);

  // ── Session expiry checker ─────────────────────────────────────────

  useEffect(() => {
    if (!auth.isAuthenticated || auth.expiresAt === null) {
      return;
    }

    const check = () => {
      if (isTokenExpired(auth.expiresAt)) {
        logout();
        return;
      }

      const remaining = sessionTimeRemaining(auth.expiresAt);
      setSessionWarning(remaining !== null && remaining <= WARNING_THRESHOLD_MS);
    };

    check();
    const intervalId = window.setInterval(check, CHECK_INTERVAL_MS);
    return () => window.clearInterval(intervalId);
  }, [auth.isAuthenticated, auth.expiresAt, logout]);

  // ── Role check helper ──────────────────────────────────────────────

  const hasRole = useCallback(
    (required: UserRole) => hasMinimumRole(auth.role, required),
    [auth.role]
  );

  // ── Context value ──────────────────────────────────────────────────

  const value = useMemo<AuthContextValue>(
    () => ({ auth, login, logout, hasRole, sessionWarning }),
    [auth, login, logout, hasRole, sessionWarning]
  );

  // Render nothing until hydrated to prevent flash
  if (!hydrated) {
    return null;
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// ── Hook ─────────────────────────────────────────────────────────────

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used inside AuthProvider");
  }
  return context;
}
