"use client";

import type { UserRole } from "@/lib/auth/types";
import { useAuth } from "@/lib/auth/auth-context";

type RoleGateProps = {
  /** Roles allowed to see this content */
  allow: UserRole[];
  /** Optional fallback when role is insufficient */
  fallback?: React.ReactNode;
  children: React.ReactNode;
};

/**
 * Conditionally renders children based on the current user's role.
 * Use this for inline visibility control (hiding action buttons, etc.).
 *
 * @example
 * <RoleGate allow={["admin", "operator"]}>
 *   <Button>Start Campaign</Button>
 * </RoleGate>
 */
export function RoleGate({ allow, fallback = null, children }: RoleGateProps) {
  const { auth } = useAuth();

  if (!auth.isAuthenticated) {
    return <>{fallback}</>;
  }

  if (!allow.includes(auth.role)) {
    return <>{fallback}</>;
  }

  return <>{children}</>;
}
