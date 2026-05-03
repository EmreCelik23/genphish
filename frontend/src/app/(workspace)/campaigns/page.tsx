"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { CalendarClock, CheckCircle2, Megaphone, RefreshCw, Target, Users } from "lucide-react";

import { RequireAccess } from "@/components/layout/require-access";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { ApiClient } from "@/lib/api/client";
import { createApiServices } from "@/lib/api/services";
import type { CampaignResponse, CampaignTargetingType, EmployeeResponse, PhishingTemplateResponse } from "@/lib/api/types";
import { useI18n } from "@/lib/i18n/i18n-context";
import { useSettings } from "@/lib/settings/settings-context";
import type { AppSettings } from "@/lib/settings/types";

type BadgeTone = "neutral" | "success" | "warning" | "danger" | "info";

type CampaignFormState = {
  name: string;
  templateId: string;
  targetingType: CampaignTargetingType;
  targetDepartment: string;
  targetEmployeeIds: string[];
  qrCodeEnabled: boolean;
};

function statusTone(status: CampaignResponse["status"]): BadgeTone {
  switch (status) {
    case "COMPLETED":
    case "READY":
      return "success";
    case "IN_PROGRESS":
    case "GENERATING":
      return "info";
    case "SCHEDULED":
      return "warning";
    case "FAILED":
    case "CANCELED":
      return "danger";
    default:
      return "neutral";
  }
}

function CampaignListSkeleton() {
  return (
    <div className="space-y-3">
      {Array.from({ length: 5 }).map((_, index) => (
        <div key={index} className="rounded-xl border border-border bg-surface/50 p-4">
          <Skeleton className="h-4 w-40" />
          <Skeleton className="mt-2 h-3 w-24" />
          <div className="mt-3 grid grid-cols-2 gap-2 lg:grid-cols-4">
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
          </div>
        </div>
      ))}
    </div>
  );
}

export default function CampaignsPage() {
  const { settings } = useSettings();
  const { t } = useI18n();
  const locale = settings.language === "tr" ? "tr-TR" : "en-US";

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

  const [campaigns, setCampaigns] = useState<CampaignResponse[]>([]);
  const [templates, setTemplates] = useState<PhishingTemplateResponse[]>([]);
  const [employees, setEmployees] = useState<EmployeeResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [createSuccess, setCreateSuccess] = useState<string | null>(null);

  const [form, setForm] = useState<CampaignFormState>({
    name: "",
    templateId: "",
    targetingType: "ALL_COMPANY",
    targetDepartment: "",
    targetEmployeeIds: [],
    qrCodeEnabled: false
  });

  const canFetch = Boolean(apiSettings.companyId && apiSettings.apiToken);

  const readyTemplates = useMemo(() => templates.filter((item) => item.status === "READY"), [templates]);

  const departments = useMemo(
    () => [...new Set(employees.map((item) => item.department).filter((value) => value.trim().length > 0))].sort(),
    [employees]
  );

  const sortedCampaigns = useMemo(
    () =>
      [...campaigns].sort((a, b) => {
        const left = Date.parse(a.createdAt);
        const right = Date.parse(b.createdAt);
        if (Number.isNaN(left) || Number.isNaN(right)) {
          return 0;
        }
        return right - left;
      }),
    [campaigns]
  );

  const fetchCampaignData = useCallback(async () => {
    if (!canFetch) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const client = new ApiClient(apiSettings);
      const services = createApiServices(client, apiSettings.companyId);
      const [campaignList, templateList, employeeList] = await Promise.all([
        services.campaigns.list(),
        services.templates.list(),
        services.employees.list()
      ]);
      setCampaigns(campaignList);
      setTemplates(templateList);
      setEmployees(employeeList);
      setLastUpdated(new Date());
      setForm((prev) => ({
        ...prev,
        templateId: prev.templateId || templateList.find((item) => item.status === "READY")?.id || ""
      }));
    } catch (fetchError) {
      const message = fetchError instanceof Error ? fetchError.message : t.common.unknownError;
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [apiSettings, canFetch, t.common.unknownError]);

  useEffect(() => {
    if (!canFetch) {
      return;
    }
    // Trigger initial fetch when access credentials are available.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void fetchCampaignData();
  }, [canFetch, fetchCampaignData]);

  const targetingLabel = (targetingType: CampaignResponse["targetingType"]) => {
    switch (targetingType) {
      case "ALL_COMPANY":
        return t.campaigns.allCompany;
      case "DEPARTMENT":
        return t.campaigns.department;
      case "INDIVIDUAL":
        return t.campaigns.individual;
      case "HIGH_RISK":
        return t.campaigns.highRisk;
      default:
        return targetingType;
    }
  };

  const formatDate = (value?: string) => {
    if (!value) {
      return t.campaigns.notScheduled;
    }
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) {
      return t.campaigns.notScheduled;
    }
    return parsed.toLocaleString(locale, {
      year: "numeric",
      month: "short",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit"
    });
  };

  const handleCreateCampaign = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!canFetch) {
      return;
    }

    setCreateError(null);
    setCreateSuccess(null);

    const name = form.name.trim();
    if (!name) {
      setCreateError(`${t.campaigns.campaignName} is required`);
      return;
    }

    if (!form.templateId) {
      setCreateError(t.campaigns.noReadyTemplates);
      return;
    }

    if (form.targetingType === "DEPARTMENT" && !form.targetDepartment.trim()) {
      setCreateError(`${t.campaigns.targetDepartmentInput} is required`);
      return;
    }

    if (form.targetingType === "INDIVIDUAL" && form.targetEmployeeIds.length === 0) {
      setCreateError(`${t.campaigns.targetEmployees} is required`);
      return;
    }

    setCreating(true);
    try {
      const client = new ApiClient(apiSettings);
      const services = createApiServices(client, apiSettings.companyId);

      const created = await services.campaigns.create({
        name,
        templateId: form.templateId,
        targetingType: form.targetingType,
        qrCodeEnabled: form.qrCodeEnabled,
        ...(form.targetingType === "DEPARTMENT" ? { targetDepartment: form.targetDepartment.trim() } : {}),
        ...(form.targetingType === "INDIVIDUAL" ? { targetEmployeeIds: form.targetEmployeeIds } : {})
      });

      setCampaigns((prev) => [created, ...prev]);
      setCreateSuccess(t.campaigns.createSuccess);
      setForm((prev) => ({
        ...prev,
        name: "",
        targetDepartment: "",
        targetEmployeeIds: [],
        qrCodeEnabled: false
      }));
    } catch (createCampaignError) {
      const message = createCampaignError instanceof Error ? createCampaignError.message : t.common.unknownError;
      setCreateError(message);
    } finally {
      setCreating(false);
    }
  };

  return (
    <RequireAccess>
      <div className="space-y-4 lg:space-y-6">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <h1 className="text-3xl font-semibold tracking-tight">{t.campaigns.title}</h1>
            <p className="mt-1 text-sm text-muted">{t.campaigns.subtitle}</p>
          </div>
          <div className="flex items-center gap-2">
            <Badge tone="neutral">
              {t.campaigns.total}: {campaigns.length}
            </Badge>
            <Button variant="ghost" onClick={() => void fetchCampaignData()} disabled={loading}>
              <RefreshCw className="mr-2 h-4 w-4" />
              {t.common.refresh}
            </Button>
          </div>
        </div>

        <Card>
          <div className="mb-4">
            <p className="text-sm font-medium text-text">{t.campaigns.createTitle}</p>
            <p className="mt-1 text-xs text-muted">{t.campaigns.createSubtitle}</p>
          </div>

          <form className="space-y-4" onSubmit={(event) => void handleCreateCampaign(event)}>
            <div className="grid gap-3 lg:grid-cols-2">
              <div>
                <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.campaigns.campaignName}</label>
                <Input
                  value={form.name}
                  onChange={(event) => setForm((prev) => ({ ...prev, name: event.target.value }))}
                  placeholder="Q3 Finance Simulation"
                />
              </div>

              <div>
                <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.campaigns.template}</label>
                <Select
                  value={form.templateId}
                  onChange={(event) => setForm((prev) => ({ ...prev, templateId: event.target.value }))}
                >
                  <option value="">{t.campaigns.chooseTemplate}</option>
                  {readyTemplates.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name}
                    </option>
                  ))}
                </Select>
                {!readyTemplates.length ? <p className="mt-1 text-xs text-amber-300">{t.campaigns.noReadyTemplates}</p> : null}
              </div>
            </div>

            <div className="grid gap-3 lg:grid-cols-2">
              <div>
                <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.campaigns.targetingTypeLabel}</label>
                <Select
                  value={form.targetingType}
                  onChange={(event) =>
                    setForm((prev) => ({
                      ...prev,
                      targetingType: event.target.value as CampaignTargetingType,
                      targetDepartment: "",
                      targetEmployeeIds: []
                    }))
                  }
                >
                  <option value="ALL_COMPANY">{t.campaigns.allCompany}</option>
                  <option value="DEPARTMENT">{t.campaigns.department}</option>
                  <option value="INDIVIDUAL">{t.campaigns.individual}</option>
                  <option value="HIGH_RISK">{t.campaigns.highRisk}</option>
                </Select>
              </div>

              <div className="flex items-end">
                <label className="inline-flex cursor-pointer items-center gap-2 rounded-lg border border-border bg-surface/50 px-3 py-2 text-sm text-text">
                  <input
                    type="checkbox"
                    checked={form.qrCodeEnabled}
                    onChange={(event) => setForm((prev) => ({ ...prev, qrCodeEnabled: event.target.checked }))}
                  />
                  {t.campaigns.qrCodeEnabled}
                </label>
              </div>
            </div>

            {form.targetingType === "DEPARTMENT" ? (
              <div>
                <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.campaigns.targetDepartmentInput}</label>
                <Select
                  value={form.targetDepartment}
                  onChange={(event) => setForm((prev) => ({ ...prev, targetDepartment: event.target.value }))}
                >
                  <option value="">{t.campaigns.department}</option>
                  {departments.map((department) => (
                    <option key={department} value={department}>
                      {department}
                    </option>
                  ))}
                </Select>
              </div>
            ) : null}

            {form.targetingType === "INDIVIDUAL" ? (
              <div>
                <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.campaigns.targetEmployees}</label>
                <Select
                  className="h-32"
                  multiple
                  value={form.targetEmployeeIds}
                  onChange={(event) =>
                    setForm((prev) => ({
                      ...prev,
                      targetEmployeeIds: Array.from(event.target.selectedOptions).map((option) => option.value)
                    }))
                  }
                >
                  {employees.map((employee) => (
                    <option key={employee.id} value={employee.id}>
                      {employee.firstName} {employee.lastName} ({employee.email})
                    </option>
                  ))}
                </Select>
                <p className="mt-1 text-xs text-muted">Ctrl/Cmd + click</p>
              </div>
            ) : null}

            {createError ? (
              <p className="rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-sm text-rose-300">{createError}</p>
            ) : null}
            {createSuccess ? (
              <p className="rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-3 py-2 text-sm text-emerald-300">
                {createSuccess}
              </p>
            ) : null}

            <Button type="submit" disabled={creating || !readyTemplates.length}>
              {creating ? t.campaigns.creatingAction : t.campaigns.createAction}
            </Button>
          </form>
        </Card>

        {lastUpdated ? (
          <p className="text-xs text-muted">
            {t.dashboard.snapshot} • {lastUpdated.toLocaleTimeString(locale)}
          </p>
        ) : null}

        {error ? (
          <Card className="border-rose-500/30">
            <p className="text-sm text-rose-300">{error}</p>
            <Button className="mt-3" variant="danger" onClick={() => void fetchCampaignData()}>
              {t.common.retry}
            </Button>
          </Card>
        ) : null}

        {loading && !campaigns.length ? (
          <Card>
            <CampaignListSkeleton />
          </Card>
        ) : null}

        {!loading && !campaigns.length ? (
          <Card>
            <p className="text-sm font-medium text-text">{t.campaigns.noData}</p>
            <p className="mt-1 text-xs text-muted">{t.campaigns.noDataHint}</p>
          </Card>
        ) : null}

        {sortedCampaigns.length ? (
          <Card>
            <div className="space-y-3">
              {sortedCampaigns.map((item) => (
                <div key={item.id} className="rounded-xl border border-border bg-surface/50 p-4">
                  <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                    <div>
                      <p className="text-sm font-medium text-text">{item.name}</p>
                      <p className="mt-1 text-xs text-muted">{item.id}</p>
                    </div>
                    <div className="flex items-center gap-2">
                      <Badge tone={statusTone(item.status)}>{t.campaigns.statuses[item.status]}</Badge>
                      {item.qrCodeEnabled ? (
                        <Badge tone="info">
                          <CheckCircle2 className="mr-1 h-3 w-3" />
                          QR
                        </Badge>
                      ) : null}
                    </div>
                  </div>

                  <div className="mt-3 grid grid-cols-1 gap-2 text-xs text-muted lg:grid-cols-4">
                    <div className="flex items-center gap-2 rounded-lg border border-border bg-surface/40 px-3 py-2">
                      <Target className="h-3.5 w-3.5 text-accent" />
                      <span>
                        {t.campaigns.targetingType}: {targetingLabel(item.targetingType)}
                      </span>
                    </div>
                    <div className="flex items-center gap-2 rounded-lg border border-border bg-surface/40 px-3 py-2">
                      <Megaphone className="h-3.5 w-3.5 text-accent" />
                      <span>
                        {t.campaigns.createdAt}: {formatDate(item.createdAt)}
                      </span>
                    </div>
                    <div className="flex items-center gap-2 rounded-lg border border-border bg-surface/40 px-3 py-2">
                      <Users className="h-3.5 w-3.5 text-accent" />
                      <span>
                        {t.campaigns.template}: {item.templateId}
                      </span>
                    </div>
                    <div className="flex items-center gap-2 rounded-lg border border-border bg-surface/40 px-3 py-2">
                      <CalendarClock className="h-3.5 w-3.5 text-accent" />
                      <span>
                        {t.campaigns.scheduledFor}: {formatDate(item.scheduledFor)}
                      </span>
                    </div>
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
