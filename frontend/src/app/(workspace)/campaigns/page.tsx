"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { BarChart3, CalendarClock, CheckCircle2, Megaphone, RefreshCw, Target, Trash2, Users } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { Pagination } from "@/components/ui/pagination";
import { SearchInput } from "@/components/ui/search-input";
import { Select } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { useToast } from "@/components/ui/toast";
import { useApi } from "@/lib/api/use-api";
import type {
  CampaignFunnelResponse,
  CampaignResponse,
  CampaignStatus,
  CampaignTargetingType,
  EmployeeResponse,
  PhishingTemplateResponse,
  TrackingEventResponse,
  TrackingEventType
} from "@/lib/api/types";
import { usePagination } from "@/lib/hooks/use-pagination";
import { usePolling } from "@/lib/hooks/use-polling";
import { useSearch } from "@/lib/hooks/use-search";
import { useI18n } from "@/lib/i18n/i18n-context";
import { useSettings } from "@/lib/settings/settings-context";

type BadgeTone = "neutral" | "success" | "warning" | "danger" | "info";
type CampaignAction = "start" | "schedule" | "cancel";

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

function toDateTimeLocalValue(value?: string) {
  if (!value) {
    return "";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  const pad = (input: number) => String(input).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function clampPercent(value: number) {
  if (Number.isNaN(value)) {
    return 0;
  }
  return Math.max(0, Math.min(100, value));
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
  const { api } = useApi();
  const { settings } = useSettings();
  const { t } = useI18n();
  const { toast } = useToast();
  const locale = settings.language === "tr" ? "tr-TR" : "en-US";

  const [campaigns, setCampaigns] = useState<CampaignResponse[]>([]);
  const [templates, setTemplates] = useState<PhishingTemplateResponse[]>([]);
  const [employees, setEmployees] = useState<EmployeeResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [creating, setCreating] = useState(false);
  const [pendingActionKey, setPendingActionKey] = useState<string | null>(null);
  const [scheduleDrafts, setScheduleDrafts] = useState<Record<string, string>>({});
  const [deletingCampaignId, setDeletingCampaignId] = useState<string | null>(null);
  const [expandedAnalyticsCampaignId, setExpandedAnalyticsCampaignId] = useState<string | null>(null);
  const [analyticsLoadingCampaignId, setAnalyticsLoadingCampaignId] = useState<string | null>(null);
  const [analyticsError, setAnalyticsError] = useState<string | null>(null);
  const [campaignFunnels, setCampaignFunnels] = useState<Record<string, CampaignFunnelResponse>>({});
  const [campaignEvents, setCampaignEvents] = useState<Record<string, TrackingEventResponse[]>>({}); 

  const [form, setForm] = useState<CampaignFormState>({
    name: "",
    templateId: "",
    targetingType: "ALL_COMPANY",
    targetDepartment: "",
    targetEmployeeIds: [],
    qrCodeEnabled: false
  });

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

  // ── Search & Filter ────────────────────────────────────────────────
  const [statusFilter, setStatusFilter] = useState<CampaignStatus | "">("")
  const { filtered: searchedCampaigns, query: searchQuery, setQuery: setSearchQuery } = useSearch(sortedCampaigns, {
    keys: ["name", "id", "targetingType"],
    debounceMs: 200
  });

  const filteredCampaigns = useMemo(
    () => statusFilter ? searchedCampaigns.filter((c) => c.status === statusFilter) : searchedCampaigns,
    [searchedCampaigns, statusFilter]
  );

  const pag = usePagination(filteredCampaigns, { defaultPageSize: 10 });

  // ── Auto-refresh polling (GENERATING / IN_PROGRESS campaigns) ────────
  const hasActiveCampaigns = useMemo(
    () => campaigns.some((c) => c.status === "GENERATING" || c.status === "IN_PROGRESS"),
    [campaigns]
  );

  const { isPolling } = usePolling(
    useCallback(() => { void fetchCampaignData(); }, []),
    { intervalMs: 5000, enabled: hasActiveCampaigns }
  );

  // ── Delete dialog ──────────────────────────────────────────────────
  const [deleteDialogId, setDeleteDialogId] = useState<string | null>(null);

  const upsertCampaign = (updated: CampaignResponse) => {
    setCampaigns((prev) => {
      const exists = prev.some((item) => item.id === updated.id);
      if (!exists) {
        return [updated, ...prev];
      }
      return prev.map((item) => (item.id === updated.id ? updated : item));
    });
  };

  const fetchCampaignData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [campaignList, templateList, employeeList] = await Promise.all([
        api.campaigns.list(),
        api.templates.list(),
        api.employees.list()
      ]);
      setCampaigns(campaignList);
      setTemplates(templateList);
      setEmployees(employeeList);
      setLastUpdated(new Date());
      setForm((prev) => ({
        ...prev,
        templateId: prev.templateId || templateList.find((item) => item.status === "READY")?.id || ""
      }));
      setScheduleDrafts((prev) => {
        const next = { ...prev };
        campaignList.forEach((campaign) => {
          if (!next[campaign.id]) {
            next[campaign.id] = toDateTimeLocalValue(campaign.scheduledFor);
          }
        });
        return next;
      });
    } catch (fetchError) {
      const message = fetchError instanceof Error ? fetchError.message : t.common.unknownError;
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [api, t.common.unknownError]);

  useEffect(() => {
    // Trigger initial fetch.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void fetchCampaignData();
  }, [fetchCampaignData]);

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
    setCreating(true);
    try {
      const name = form.name.trim();
      if (!name) {
        toast(t.validation.required, "error");
        return;
      }

      if (!form.templateId) {
        toast(t.campaigns.noReadyTemplates, "error");
        return;
      }

      if (form.targetingType === "DEPARTMENT" && !form.targetDepartment.trim()) {
        toast(t.validation.required, "error");
        return;
      }

      if (form.targetingType === "INDIVIDUAL" && form.targetEmployeeIds.length === 0) {
        toast(t.validation.required, "error");
        return;
      }

      const created = await api.campaigns.create({
        name,
        templateId: form.templateId,
        targetingType: form.targetingType,
        qrCodeEnabled: form.qrCodeEnabled,
        ...(form.targetingType === "DEPARTMENT" ? { targetDepartment: form.targetDepartment.trim() } : {}),
        ...(form.targetingType === "INDIVIDUAL" ? { targetEmployeeIds: form.targetEmployeeIds } : {})
      });

      upsertCampaign(created);
      toast(t.campaigns.createSuccess, "success");
      setForm((prev) => ({
        ...prev,
        name: "",
        targetDepartment: "",
        targetEmployeeIds: [],
        qrCodeEnabled: false
      }));
    } catch (createCampaignError) {
      const message = createCampaignError instanceof Error ? createCampaignError.message : t.common.unknownError;
      toast(message, "error");
    } finally {
      setCreating(false);
    }
  };

  const runCampaignAction = async (campaignId: string, action: CampaignAction) => {
    const actionKey = `${campaignId}:${action}`;
    setPendingActionKey(actionKey);

    try {
      let updated: CampaignResponse;

      if (action === "start") {
        updated = await api.campaigns.start(campaignId);
      } else if (action === "cancel") {
        updated = await api.campaigns.cancel(campaignId);
      } else {
        const scheduledFor = scheduleDrafts[campaignId];
        if (!scheduledFor) {
          toast(`${t.campaigns.scheduledForInput} is required`, "error");
          return;
        }
        updated = await api.campaigns.schedule(campaignId, { scheduledFor });
      }

      upsertCampaign(updated);
      toast(
        action === "start" ? t.campaigns.startSuccess
          : action === "schedule" ? t.campaigns.scheduleSuccess
          : t.campaigns.cancelSuccess,
        "success"
      );
      setScheduleDrafts((prev) => ({
        ...prev,
        [campaignId]: toDateTimeLocalValue(updated.scheduledFor)
      }));
    } catch (campaignActionError) {
      const message = campaignActionError instanceof Error ? campaignActionError.message : t.common.unknownError;
      toast(message, "error");
    } finally {
      setPendingActionKey(null);
    }
  };

  const fetchCampaignAnalytics = useCallback(
    async (campaignId: string) => {
      setAnalyticsError(null);
      setAnalyticsLoadingCampaignId(campaignId);

      try {
        const [funnel, events] = await Promise.all([
          api.analytics.campaignFunnel(campaignId),
          api.analytics.campaignEvents(campaignId)
        ]);
        setCampaignFunnels((prev) => ({ ...prev, [campaignId]: funnel }));
        setCampaignEvents((prev) => ({ ...prev, [campaignId]: events }));
      } catch (analyticsFetchError) {
        const message = analyticsFetchError instanceof Error ? analyticsFetchError.message : t.common.unknownError;
        setAnalyticsError(message);
      } finally {
        setAnalyticsLoadingCampaignId(null);
      }
    },
    [api, t.common.unknownError]
  );

  const toggleAnalytics = async (campaignId: string) => {
    if (expandedAnalyticsCampaignId === campaignId) {
      setExpandedAnalyticsCampaignId(null);
      setAnalyticsError(null);
      return;
    }

    setAnalyticsError(null);
    setExpandedAnalyticsCampaignId(campaignId);
    if (!campaignFunnels[campaignId] || !campaignEvents[campaignId]) {
      await fetchCampaignAnalytics(campaignId);
    }
  };

  const handleDeleteCampaign = async (campaignId: string) => {
    setDeleteDialogId(null);
    setDeletingCampaignId(campaignId);

    try {
      await api.campaigns.delete(campaignId);

      setCampaigns((prev) => prev.filter((item) => item.id !== campaignId));
      setCampaignFunnels((prev) => {
        const next = { ...prev };
        delete next[campaignId];
        return next;
      });
      setCampaignEvents((prev) => {
        const next = { ...prev };
        delete next[campaignId];
        return next;
      });

      if (expandedAnalyticsCampaignId === campaignId) {
        setExpandedAnalyticsCampaignId(null);
      }

      toast(t.campaigns.deleteSuccess, "success");
    } catch (deleteCampaignError) {
      const message = deleteCampaignError instanceof Error ? deleteCampaignError.message : t.common.unknownError;
      toast(message, "error");
    } finally {
      setDeletingCampaignId(null);
    }
  };

  const formatDateTime = (value?: string) => {
    if (!value) {
      return "-";
    }

    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) {
      return "-";
    }

    return parsed.toLocaleString(locale, {
      year: "numeric",
      month: "short",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit"
    });
  };

  const eventTypeLabel = (eventType: TrackingEventType) => {
    return t.campaigns.eventTypes[eventType] ?? eventType;
  };

  const isPending = (campaignId: string, action: CampaignAction) => pendingActionKey === `${campaignId}:${action}`;

  return (
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
            {isPolling && (
              <Badge tone="info">
                <RefreshCw className="mr-1 h-3 w-3 animate-spin" />
                {t.campaigns.autoRefreshActive}
              </Badge>
            )}
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
              <FormField label={t.campaigns.campaignName} required>
                <Input
                  value={form.name}
                  onChange={(event) => setForm((prev) => ({ ...prev, name: event.target.value }))}
                  placeholder="Q3 Finance Simulation"
                />
              </FormField>

              <FormField label={t.campaigns.template} required>
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
              </FormField>
            </div>

            <div className="grid gap-3 lg:grid-cols-2">
              <FormField label={t.campaigns.targetingTypeLabel}>
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
              </FormField>

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
              <FormField label={t.campaigns.targetDepartmentInput} required>
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
              </FormField>
            ) : null}

            {form.targetingType === "INDIVIDUAL" ? (
              <FormField label={t.campaigns.targetEmployees} required hint="Ctrl/Cmd + click">
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
              </FormField>
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
            <EmptyState
              icon={Megaphone}
              title={t.campaigns.noData}
              description={t.campaigns.noDataHint}
            />
          </Card>
        ) : null}

        {sortedCampaigns.length ? (
          <Card>
            {/* Search & Filter toolbar */}
            <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center">
              <SearchInput
                value={searchQuery}
                onChange={setSearchQuery}
                placeholder={t.search.placeholder}
                className="sm:max-w-xs"
              />
              <Select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value as CampaignStatus | "")}
                className="sm:max-w-[180px]"
              >
                <option value="">{t.filter.all} — {t.filter.status}</option>
                {(["DRAFT", "GENERATING", "READY", "SCHEDULED", "IN_PROGRESS", "COMPLETED", "FAILED", "CANCELED"] as CampaignStatus[]).map((s) => (
                  <option key={s} value={s}>{t.campaigns.statuses[s]}</option>
                ))}
              </Select>
            </div>

            {filteredCampaigns.length === 0 ? (
              <EmptyState icon={Megaphone} title={t.search.noResults} />
            ) : (
              <>
                <div className="space-y-3">
                  {pag.paginated.map((item) => (
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

                  <div className="mt-3 flex flex-wrap items-end gap-2">
                    {item.status === "READY" ? (
                      <>
                        <Button
                          onClick={() => void runCampaignAction(item.id, "start")}
                          disabled={isPending(item.id, "start") || pendingActionKey !== null || deletingCampaignId !== null}
                        >
                          {isPending(item.id, "start") ? t.campaigns.startingAction : t.campaigns.startAction}
                        </Button>

                        <div className="min-w-[220px]">
                          <label className="mb-1 block text-[11px] uppercase tracking-[0.12em] text-muted">
                            {t.campaigns.scheduledForInput}
                          </label>
                          <Input
                            type="datetime-local"
                            value={scheduleDrafts[item.id] ?? ""}
                            onChange={(event) =>
                              setScheduleDrafts((prev) => ({
                                ...prev,
                                [item.id]: event.target.value
                              }))
                            }
                          />
                        </div>
                        <Button
                          variant="ghost"
                          onClick={() => void runCampaignAction(item.id, "schedule")}
                          disabled={isPending(item.id, "schedule") || pendingActionKey !== null || deletingCampaignId !== null}
                        >
                          {isPending(item.id, "schedule") ? t.campaigns.schedulingAction : t.campaigns.scheduleAction}
                        </Button>
                      </>
                    ) : null}

                    {(item.status === "SCHEDULED" || item.status === "IN_PROGRESS") ? (
                      <Button
                        variant="danger"
                        onClick={() => void runCampaignAction(item.id, "cancel")}
                        disabled={isPending(item.id, "cancel") || pendingActionKey !== null || deletingCampaignId !== null}
                      >
                        {isPending(item.id, "cancel") ? t.campaigns.cancelingAction : t.campaigns.cancelAction}
                      </Button>
                    ) : null}

                    <Button
                      variant="ghost"
                      onClick={() => void toggleAnalytics(item.id)}
                      disabled={analyticsLoadingCampaignId === item.id || deletingCampaignId !== null}
                    >
                      <BarChart3 className="mr-2 h-4 w-4" />
                      {expandedAnalyticsCampaignId === item.id ? t.campaigns.analyticsHide : t.campaigns.analyticsAction}
                    </Button>

                    {item.status !== "IN_PROGRESS" && item.status !== "SCHEDULED" ? (
                      <Button
                        variant="danger"
                        onClick={() => setDeleteDialogId(item.id)}
                        disabled={deletingCampaignId !== null || pendingActionKey !== null}
                      >
                        <Trash2 className="mr-2 h-4 w-4" />
                        {deletingCampaignId === item.id ? t.campaigns.deletingAction : t.campaigns.deleteAction}
                      </Button>
                    ) : null}
                  </div>

                  {expandedAnalyticsCampaignId === item.id ? (
                    <div className="mt-3 rounded-xl border border-border bg-surface/40 p-3">
                      <div className="mb-3 flex flex-col gap-2 lg:flex-row lg:items-center lg:justify-between">
                        <p className="text-xs uppercase tracking-[0.12em] text-muted">{t.campaigns.analyticsTitle}</p>
                        <Button
                          variant="ghost"
                          onClick={() => void fetchCampaignAnalytics(item.id)}
                          disabled={analyticsLoadingCampaignId === item.id}
                        >
                          <RefreshCw className="mr-2 h-4 w-4" />
                          {t.campaigns.analyticsRefresh}
                        </Button>
                      </div>

                      {analyticsError ? <p className="mb-2 text-sm text-rose-300">{analyticsError}</p> : null}
                      {analyticsLoadingCampaignId === item.id ? (
                        <div className="grid grid-cols-2 gap-2">
                          <Skeleton className="h-8 w-full" />
                          <Skeleton className="h-8 w-full" />
                          <Skeleton className="h-8 w-full" />
                          <Skeleton className="h-8 w-full" />
                        </div>
                      ) : null}

                      {analyticsLoadingCampaignId !== item.id && campaignFunnels[item.id] ? (
                        <div className="space-y-3">
                          <div className="grid grid-cols-2 gap-2 text-xs text-muted lg:grid-cols-4">
                            <div className="rounded-lg border border-border bg-surface/50 px-3 py-2">
                              {t.campaigns.targetCount}:{" "}
                              <span className="text-text">{campaignFunnels[item.id].targetCount}</span>
                            </div>
                            <div className="rounded-lg border border-border bg-surface/50 px-3 py-2">
                              {t.campaigns.emailsDelivered}:{" "}
                              <span className="text-text">{campaignFunnels[item.id].emailsDelivered}</span>
                            </div>
                            <div className="rounded-lg border border-border bg-surface/50 px-3 py-2">
                              {t.campaigns.emailsOpened}:{" "}
                              <span className="text-text">{campaignFunnels[item.id].emailsOpened}</span>
                            </div>
                            <div className="rounded-lg border border-border bg-surface/50 px-3 py-2">
                              {t.campaigns.linksClicked}:{" "}
                              <span className="text-text">{campaignFunnels[item.id].linksClicked}</span>
                            </div>
                            <div className="rounded-lg border border-border bg-surface/50 px-3 py-2">
                              {t.campaigns.credentialsSubmitted}:{" "}
                              <span className="text-text">{campaignFunnels[item.id].credentialsSubmitted}</span>
                            </div>
                            <div className="rounded-lg border border-border bg-surface/50 px-3 py-2">
                              {t.campaigns.actionsTaken}:{" "}
                              <span className="text-text">{campaignFunnels[item.id].actionsTaken}</span>
                            </div>
                            <div className="rounded-lg border border-border bg-surface/50 px-3 py-2">
                              {t.campaigns.openRate}:{" "}
                              <span className="text-text">{clampPercent(campaignFunnels[item.id].openRate).toFixed(1)}%</span>
                            </div>
                            <div className="rounded-lg border border-border bg-surface/50 px-3 py-2">
                              {t.campaigns.clickRate}:{" "}
                              <span className="text-text">{clampPercent(campaignFunnels[item.id].clickRate).toFixed(1)}%</span>
                            </div>
                            <div className="rounded-lg border border-border bg-surface/50 px-3 py-2">
                              {t.campaigns.submitRate}:{" "}
                              <span className="text-text">{clampPercent(campaignFunnels[item.id].submitRate).toFixed(1)}%</span>
                            </div>
                            <div className="rounded-lg border border-border bg-surface/50 px-3 py-2">
                              {t.campaigns.actionRate}:{" "}
                              <span className="text-text">{clampPercent(campaignFunnels[item.id].actionRate).toFixed(1)}%</span>
                            </div>
                          </div>

                          <div>
                            <p className="mb-2 text-xs uppercase tracking-[0.12em] text-muted">{t.campaigns.analyticsEvents}</p>
                            {campaignEvents[item.id]?.length ? (
                              <div className="space-y-2">
                                {campaignEvents[item.id].slice(0, 8).map((event) => (
                                  <div
                                    key={event.eventId}
                                    className="grid grid-cols-1 gap-2 rounded-lg border border-border bg-surface/50 px-3 py-2 text-xs text-muted lg:grid-cols-3"
                                  >
                                    <div>
                                      {t.campaigns.employee}:{" "}
                                      <span className="text-text">
                                        {event.employeeName} ({event.employeeDepartment})
                                      </span>
                                    </div>
                                    <div>
                                      {t.campaigns.eventType}: <span className="text-text">{eventTypeLabel(event.eventType)}</span>
                                    </div>
                                    <div>
                                      {t.campaigns.occurredAt}: <span className="text-text">{formatDateTime(event.occurredAt)}</span>
                                    </div>
                                  </div>
                                ))}
                              </div>
                            ) : (
                              <p className="text-xs text-muted">{t.campaigns.noEvents}</p>
                            )}
                          </div>
                        </div>
                      ) : null}
                    </div>
                  ) : null}
                </div>
                ))}
              </div>

              {/* Pagination */}
              <div className="mt-4">
                <Pagination
                  page={pag.page}
                  totalPages={pag.totalPages}
                  rangeStart={pag.rangeStart}
                  rangeEnd={pag.rangeEnd}
                  total={pag.total}
                  pageSize={pag.pageSize}
                  hasNext={pag.hasNext}
                  hasPrev={pag.hasPrev}
                  onPageChange={pag.setPage}
                  onPageSizeChange={pag.setPageSize}
                  labels={{
                    showing: t.pagination.showing,
                    of: t.pagination.of,
                    perPage: t.pagination.perPage,
                    previous: t.pagination.previous,
                    next: t.pagination.next
                  }}
                />
              </div>
              </>
            )}
          </Card>
        ) : null}

        {/* Delete confirmation dialog */}
        <Dialog
          open={deleteDialogId !== null}
          onClose={() => setDeleteDialogId(null)}
          title={t.campaigns.deleteConfirm}
        >
          <p className="text-sm text-muted">{t.campaigns.deleteConfirm}</p>
          <div className="mt-4 flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setDeleteDialogId(null)}>
              {t.common.cancel}
            </Button>
            <Button
              variant="danger"
              onClick={() => { if (deleteDialogId) void handleDeleteCampaign(deleteDialogId); }}
            >
              <Trash2 className="mr-2 h-4 w-4" />
              {t.campaigns.deleteAction}
            </Button>
          </div>
        </Dialog>
      </div>
  );
}
