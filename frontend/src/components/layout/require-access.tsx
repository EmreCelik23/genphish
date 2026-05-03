"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { motion } from "framer-motion";
import { Lock, ShieldAlert } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { useAuth } from "@/lib/auth/auth-context";
import type { UserRole } from "@/lib/auth/types";
import { useI18n } from "@/lib/i18n/i18n-context";

type AuthGuardProps = {
  children: React.ReactNode;
  requiredRole?: UserRole;
};

export function AuthGuard({ children, requiredRole }: AuthGuardProps) {
  const router = useRouter();
  const { auth, hasRole } = useAuth();
  const { t } = useI18n();

  // Redirect unauthenticated users
  useEffect(() => {
    if (!auth.isAuthenticated) {
      router.replace("/access");
    }
  }, [auth.isAuthenticated, router]);

  if (!auth.isAuthenticated) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center">
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.3 }}
        >
          <Card className="max-w-sm text-center">
            <Lock className="mx-auto h-8 w-8 text-muted" />
            <p className="mt-3 text-base font-medium text-text">{t.common.notConfigured}</p>
            <Button className="mt-4" onClick={() => router.push("/access")}>
              {t.common.goAccess}
            </Button>
          </Card>
        </motion.div>
      </div>
    );
  }

  if (requiredRole && !hasRole(requiredRole)) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center">
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.3 }}
        >
          <Card className="max-w-sm text-center">
            <ShieldAlert className="mx-auto h-8 w-8 text-amber-400" />
            <p className="mt-3 text-base font-medium text-text">{t.auth.forbidden}</p>
            <p className="mt-1 text-sm text-muted">
              {t.auth.requiredRoleLabel}: {requiredRole} — {t.auth.currentRoleLabel}: {auth.role}
            </p>
          </Card>
        </motion.div>
      </div>
    );
  }

  return <>{children}</>;
}

// ── Legacy alias for backward compatibility ──────────────────────────

export { AuthGuard as RequireAccess };
