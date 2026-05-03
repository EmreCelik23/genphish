"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { CircleUserRound, RefreshCw, ShieldAlert, UsersRound } from "lucide-react";

import { RequireAccess } from "@/components/layout/require-access";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { ApiClient } from "@/lib/api/client";
import { createApiServices } from "@/lib/api/services";
import type { EmployeeResponse } from "@/lib/api/types";
import { useI18n } from "@/lib/i18n/i18n-context";
import { useSettings } from "@/lib/settings/settings-context";
import type { AppSettings } from "@/lib/settings/types";

type BadgeTone = "neutral" | "success" | "warning" | "danger" | "info";

function riskTone(score: number): BadgeTone {
  if (score >= 70) return "danger";
  if (score >= 45) return "warning";
  if (score >= 20) return "info";
  return "success";
}

function EmployeeListSkeleton() {
  return (
    <div className="space-y-3">
      {Array.from({ length: 6 }).map((_, index) => (
        <div key={index} className="rounded-xl border border-border bg-surface/50 p-4">
          <Skeleton className="h-4 w-40" />
          <Skeleton className="mt-2 h-3 w-52" />
          <div className="mt-3 grid grid-cols-2 gap-2">
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
          </div>
        </div>
      ))}
    </div>
  );
}

export default function EmployeesPage() {
  const { settings } = useSettings();
  const { t } = useI18n();

  const apiSettings = useMemo<AppSettings>(
    () => ({
      theme: "system",
      language: "tr",
      density: "comfortable",
      apiBaseUrl: settings.apiBaseUrl,
      apiToken: settings.apiToken,
      companyId: settings.companyId
    }),
    [settings.apiBaseUrl, settings.apiToken, settings.companyId]
  );

  const [employees, setEmployees] = useState<EmployeeResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canFetch = Boolean(apiSettings.companyId && apiSettings.apiToken);

  const fetchEmployees = useCallback(async () => {
    if (!canFetch) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const client = new ApiClient(apiSettings);
      const services = createApiServices(client, apiSettings.companyId);
      const response = await services.employees.list();
      setEmployees(response);
    } catch (fetchError) {
      const message = fetchError instanceof Error ? fetchError.message : "Unknown error";
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [apiSettings, canFetch]);

  useEffect(() => {
    if (!canFetch) {
      return;
    }
    // Trigger initial fetch when access credentials are available.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void fetchEmployees();
  }, [canFetch, fetchEmployees]);

  return (
    <RequireAccess>
      <div className="space-y-4 lg:space-y-6">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <h1 className="text-3xl font-semibold tracking-tight">{t.employees.title}</h1>
            <p className="mt-1 text-sm text-muted">{t.employees.subtitle}</p>
          </div>
          <div className="flex items-center gap-2">
            <Badge tone="neutral">
              {t.employees.total}: {employees.length}
            </Badge>
            <Button variant="ghost" onClick={() => void fetchEmployees()} disabled={loading}>
              <RefreshCw className="mr-2 h-4 w-4" />
              {t.common.refresh}
            </Button>
          </div>
        </div>

        {error ? (
          <Card className="border-rose-500/30">
            <p className="text-sm text-rose-300">{error}</p>
            <Button className="mt-3" variant="danger" onClick={() => void fetchEmployees()}>
              {t.common.retry}
            </Button>
          </Card>
        ) : null}

        {loading && !employees.length ? (
          <Card>
            <EmployeeListSkeleton />
          </Card>
        ) : null}

        {!loading && !employees.length ? (
          <Card>
            <p className="text-sm font-medium text-text">{t.employees.noData}</p>
            <p className="mt-1 text-xs text-muted">{t.employees.noDataHint}</p>
          </Card>
        ) : null}

        {employees.length ? (
          <Card>
            <div className="space-y-3">
              {employees.map((item) => (
                <div key={item.id} className="rounded-xl border border-border bg-surface/50 p-4">
                  <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                    <div>
                      <p className="text-sm font-medium text-text">
                        {item.firstName} {item.lastName}
                      </p>
                      <p className="mt-1 text-xs text-muted">{item.email}</p>
                    </div>
                    <Badge tone={item.active ? "success" : "neutral"}>{item.active ? t.employees.active : t.employees.passive}</Badge>
                  </div>

                  <div className="mt-3 grid grid-cols-1 gap-2 text-xs text-muted lg:grid-cols-2">
                    <div className="flex items-center gap-2 rounded-lg border border-border bg-surface/40 px-3 py-2">
                      <UsersRound className="h-3.5 w-3.5 text-accent" />
                      <span>
                        {t.employees.department}: {item.department}
                      </span>
                    </div>
                    <div className="flex items-center gap-2 rounded-lg border border-border bg-surface/40 px-3 py-2">
                      <ShieldAlert className="h-3.5 w-3.5 text-accent" />
                      <span className="mr-auto">{t.employees.riskScore}</span>
                      <Badge tone={riskTone(item.riskScore)}>{item.riskScore}</Badge>
                    </div>
                  </div>

                  <div className="mt-2 flex items-center gap-2 text-[11px] text-muted">
                    <CircleUserRound className="h-3.5 w-3.5" />
                    <span>{item.id}</span>
                  </div>
                </div>
              ))}
            </div>
          </Card>
        ) : null}
      </div>
    </RequireAccess>
  );
}
