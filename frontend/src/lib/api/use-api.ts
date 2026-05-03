"use client";

import { useMemo } from "react";
import { useRouter } from "next/navigation";

import { useAuth } from "@/lib/auth/auth-context";
import { ApiClient, buildApiConfig } from "@/lib/api/client";
import { createApiServices, createGlobalApiServices } from "@/lib/api/services";
import { useSettings } from "@/lib/settings/settings-context";
import { useToast } from "@/components/ui/toast";
import { useI18n } from "@/lib/i18n/i18n-context";

/**
 * Auth-aware API hook. Returns company-scoped and global services
 * with automatic 401/403 handling.
 *
 * @example
 * const { api, globalApi, client } = useApi();
 * const campaigns = await api.campaigns.list();
 */
export function useApi() {
  const { auth, logout } = useAuth();
  const { settings } = useSettings();
  const { toast } = useToast();
  const { t } = useI18n();
  const router = useRouter();

  const client = useMemo(() => {
    const config = buildApiConfig(settings, {
      token: auth.token,
      companyId: auth.companyId,
      onUnauthorized: () => {
        logout();
        router.replace("/access");
      },
      onForbidden: () => {
        toast(t.auth.forbidden, "error");
      }
    });
    return new ApiClient(config);
  }, [settings, auth.token, auth.companyId, logout, router, toast, t.auth.forbidden]);

  const api = useMemo(
    () => createApiServices(client, auth.companyId),
    [client, auth.companyId]
  );

  const globalApi = useMemo(() => createGlobalApiServices(client), [client]);

  return { api, globalApi, client };
}
