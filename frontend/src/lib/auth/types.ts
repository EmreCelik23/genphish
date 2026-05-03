export type UserRole = "admin" | "operator" | "viewer";

export type AuthState = {
  token: string;
  companyId: string;
  companyName: string;
  role: UserRole;
  expiresAt: number | null; // epoch ms, null = never (static token)
  isAuthenticated: boolean;
};

export type LoginPayload = {
  token: string;
  companyId: string;
  companyName: string;
  remember?: boolean;
};

export const ROLE_HIERARCHY: Record<UserRole, number> = {
  viewer: 0,
  operator: 1,
  admin: 2
};

export function hasMinimumRole(current: UserRole, required: UserRole): boolean {
  return ROLE_HIERARCHY[current] >= ROLE_HIERARCHY[required];
}
