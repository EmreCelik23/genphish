"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { CircleUserRound, RefreshCw, ShieldAlert, Upload, UserPlus, UserX, UsersRound } from "lucide-react";

import { RequireAccess } from "@/components/layout/require-access";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { ApiClient } from "@/lib/api/client";
import { createApiServices } from "@/lib/api/services";
import type { EmployeeResponse, EmployeeRiskProfileResponse, ImportResultResponse } from "@/lib/api/types";
import { useI18n } from "@/lib/i18n/i18n-context";
import { useSettings } from "@/lib/settings/settings-context";
import type { AppSettings } from "@/lib/settings/types";

type BadgeTone = "neutral" | "success" | "warning" | "danger" | "info";

type CreateEmployeeFormState = {
  firstName: string;
  lastName: string;
  email: string;
  department: string;
};

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

  const [createForm, setCreateForm] = useState<CreateEmployeeFormState>({
    firstName: "",
    lastName: "",
    email: "",
    department: ""
  });
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [createSuccess, setCreateSuccess] = useState<string | null>(null);

  const [selectedImportFile, setSelectedImportFile] = useState<File | null>(null);
  const [importing, setImporting] = useState(false);
  const [importError, setImportError] = useState<string | null>(null);
  const [importSuccess, setImportSuccess] = useState<string | null>(null);
  const [importResult, setImportResult] = useState<ImportResultResponse | null>(null);

  const [actionError, setActionError] = useState<string | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);
  const [deactivatingEmployeeId, setDeactivatingEmployeeId] = useState<string | null>(null);
  const [expandedProfileEmployeeId, setExpandedProfileEmployeeId] = useState<string | null>(null);
  const [profileLoadingEmployeeId, setProfileLoadingEmployeeId] = useState<string | null>(null);
  const [profileError, setProfileError] = useState<string | null>(null);
  const [riskProfiles, setRiskProfiles] = useState<Record<string, EmployeeRiskProfileResponse>>({});

  const canFetch = Boolean(apiSettings.companyId && apiSettings.apiToken);
  const locale = settings.language === "tr" ? "tr-TR" : "en-US";

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
    void fetchEmployees();
  }, [canFetch, fetchEmployees]);

  const handleCreateEmployee = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!canFetch) {
      return;
    }

    setCreateError(null);
    setCreateSuccess(null);

    const firstName = createForm.firstName.trim();
    const lastName = createForm.lastName.trim();
    const email = createForm.email.trim();
    const department = createForm.department.trim();

    if (!firstName) {
      setCreateError(`${t.employees.firstName} is required`);
      return;
    }
    if (!lastName) {
      setCreateError(`${t.employees.lastName} is required`);
      return;
    }
    if (!email || !email.includes("@")) {
      setCreateError(`${t.employees.email} is invalid`);
      return;
    }
    if (!department) {
      setCreateError(`${t.employees.department} is required`);
      return;
    }

    setCreating(true);
    try {
      const client = new ApiClient(apiSettings);
      const services = createApiServices(client, apiSettings.companyId);
      const created = await services.employees.create({
        firstName,
        lastName,
        email,
        department
      });
      setEmployees((prev) => [created, ...prev]);
      setCreateForm({
        firstName: "",
        lastName: "",
        email: "",
        department: ""
      });
      setCreateSuccess(t.employees.createSuccess);
    } catch (createEmployeeError) {
      const message = createEmployeeError instanceof Error ? createEmployeeError.message : t.common.unknownError;
      setCreateError(message);
    } finally {
      setCreating(false);
    }
  };

  const handleImportEmployees = async () => {
    if (!canFetch) {
      return;
    }

    setImportError(null);
    setImportSuccess(null);

    if (!selectedImportFile) {
      setImportError(`${t.employees.selectFile} is required`);
      return;
    }

    setImporting(true);
    try {
      const client = new ApiClient(apiSettings);
      const services = createApiServices(client, apiSettings.companyId);
      const result = await services.employees.import(selectedImportFile);
      setImportResult(result);
      setImportSuccess(t.employees.importSuccess);
      setSelectedImportFile(null);

      const updatedEmployees = await services.employees.list();
      setEmployees(updatedEmployees);
    } catch (importEmployeesError) {
      const message = importEmployeesError instanceof Error ? importEmployeesError.message : t.common.unknownError;
      setImportError(message);
    } finally {
      setImporting(false);
    }
  };

  const handleDeactivateEmployee = async (employeeId: string) => {
    if (!canFetch) {
      return;
    }

    setActionError(null);
    setActionSuccess(null);
    setDeactivatingEmployeeId(employeeId);

    try {
      const client = new ApiClient(apiSettings);
      const services = createApiServices(client, apiSettings.companyId);
      await services.employees.deactivate(employeeId);
      setEmployees((prev) => prev.map((item) => (item.id === employeeId ? { ...item, active: false } : item)));
      setActionSuccess(t.employees.deactivateSuccess);
    } catch (deactivateError) {
      const message = deactivateError instanceof Error ? deactivateError.message : t.common.unknownError;
      setActionError(message);
    } finally {
      setDeactivatingEmployeeId(null);
    }
  };

  const handleToggleRiskProfile = async (employeeId: string) => {
    if (!canFetch) {
      return;
    }

    if (expandedProfileEmployeeId === employeeId) {
      setExpandedProfileEmployeeId(null);
      setProfileError(null);
      return;
    }

    setExpandedProfileEmployeeId(employeeId);
    setProfileError(null);

    if (riskProfiles[employeeId]) {
      return;
    }

    setProfileLoadingEmployeeId(employeeId);
    try {
      const client = new ApiClient(apiSettings);
      const services = createApiServices(client, apiSettings.companyId);
      const profile = await services.employees.riskProfile(employeeId);
      setRiskProfiles((prev) => ({ ...prev, [employeeId]: profile }));
    } catch (fetchProfileError) {
      const message = fetchProfileError instanceof Error ? fetchProfileError.message : t.common.unknownError;
      setProfileError(message);
    } finally {
      setProfileLoadingEmployeeId(null);
    }
  };

  const formatDateTime = (value?: string) => {
    if (!value) {
      return t.employees.neverPhished;
    }
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) {
      return t.employees.neverPhished;
    }
    return parsed.toLocaleString(locale, {
      year: "numeric",
      month: "short",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit"
    });
  };

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

        <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
          <Card>
            <div className="mb-4">
              <p className="text-sm font-medium text-text">{t.employees.createTitle}</p>
              <p className="mt-1 text-xs text-muted">{t.employees.createSubtitle}</p>
            </div>

            <form className="space-y-3" onSubmit={(event) => void handleCreateEmployee(event)}>
              <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
                <div>
                  <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.employees.firstName}</label>
                  <Input
                    value={createForm.firstName}
                    onChange={(event) => setCreateForm((prev) => ({ ...prev, firstName: event.target.value }))}
                  />
                </div>
                <div>
                  <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.employees.lastName}</label>
                  <Input
                    value={createForm.lastName}
                    onChange={(event) => setCreateForm((prev) => ({ ...prev, lastName: event.target.value }))}
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
                <div>
                  <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.employees.email}</label>
                  <Input
                    type="email"
                    value={createForm.email}
                    onChange={(event) => setCreateForm((prev) => ({ ...prev, email: event.target.value }))}
                  />
                </div>
                <div>
                  <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.employees.department}</label>
                  <Input
                    value={createForm.department}
                    onChange={(event) => setCreateForm((prev) => ({ ...prev, department: event.target.value }))}
                  />
                </div>
              </div>

              {createError ? (
                <p className="rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-sm text-rose-300">{createError}</p>
              ) : null}
              {createSuccess ? (
                <p className="rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-3 py-2 text-sm text-emerald-300">
                  {createSuccess}
                </p>
              ) : null}

              <Button type="submit" disabled={creating}>
                <UserPlus className="mr-2 h-4 w-4" />
                {creating ? t.employees.creatingAction : t.employees.createAction}
              </Button>
            </form>
          </Card>

          <Card>
            <div className="mb-4">
              <p className="text-sm font-medium text-text">{t.employees.importTitle}</p>
              <p className="mt-1 text-xs text-muted">{t.employees.importSubtitle}</p>
            </div>

            <div className="space-y-3">
              <Input
                type="file"
                accept=".csv,.xlsx,.xls,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel,text/csv"
                onChange={(event) => setSelectedImportFile(event.target.files?.[0] ?? null)}
              />

              {importError ? (
                <p className="rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-sm text-rose-300">{importError}</p>
              ) : null}
              {importSuccess ? (
                <p className="rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-3 py-2 text-sm text-emerald-300">
                  {importSuccess}
                </p>
              ) : null}

              <Button type="button" variant="ghost" onClick={() => void handleImportEmployees()} disabled={importing}>
                <Upload className="mr-2 h-4 w-4" />
                {importing ? t.employees.importingAction : t.employees.importAction}
              </Button>

              {importResult ? (
                <div className="grid grid-cols-2 gap-2">
                  <div className="rounded-lg border border-border bg-surface/40 px-3 py-2 text-xs text-muted">
                    {t.employees.totalRows}: <span className="text-text">{importResult.totalRows}</span>
                  </div>
                  <div className="rounded-lg border border-border bg-surface/40 px-3 py-2 text-xs text-muted">
                    {t.employees.importedRows}: <span className="text-emerald-300">{importResult.imported}</span>
                  </div>
                  <div className="rounded-lg border border-border bg-surface/40 px-3 py-2 text-xs text-muted">
                    {t.employees.duplicateRows}: <span className="text-amber-300">{importResult.duplicates}</span>
                  </div>
                  <div className="rounded-lg border border-border bg-surface/40 px-3 py-2 text-xs text-muted">
                    {t.employees.failedRows}: <span className="text-rose-300">{importResult.failed}</span>
                  </div>
                </div>
              ) : null}
            </div>
          </Card>
        </div>

        {actionError ? (
          <Card className="border-rose-500/30">
            <p className="text-sm text-rose-300">{actionError}</p>
          </Card>
        ) : null}
        {actionSuccess ? (
          <Card className="border-emerald-500/30">
            <p className="text-sm text-emerald-300">{actionSuccess}</p>
          </Card>
        ) : null}

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
                    <div className="flex items-center gap-2">
                      <Badge tone={item.active ? "success" : "neutral"}>{item.active ? t.employees.active : t.employees.passive}</Badge>
                      <Button variant="ghost" onClick={() => void handleToggleRiskProfile(item.id)}>
                        {profileLoadingEmployeeId === item.id ? t.employees.loadingProfile : t.employees.riskProfileAction}
                      </Button>
                      {item.active ? (
                        <Button
                          variant="danger"
                          onClick={() => void handleDeactivateEmployee(item.id)}
                          disabled={deactivatingEmployeeId !== null}
                        >
                          <UserX className="mr-2 h-4 w-4" />
                          {deactivatingEmployeeId === item.id ? t.employees.deactivatingAction : t.employees.deactivateAction}
                        </Button>
                      ) : null}
                    </div>
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

                  {expandedProfileEmployeeId === item.id ? (
                    <div className="mt-3 rounded-xl border border-border bg-surface/40 p-3">
                      <p className="mb-3 text-xs uppercase tracking-[0.12em] text-muted">{t.employees.profileTitle}</p>
                      {profileError ? <p className="mb-2 text-sm text-rose-300">{profileError}</p> : null}
                      {profileLoadingEmployeeId === item.id ? (
                        <div className="grid grid-cols-2 gap-2">
                          <Skeleton className="h-8 w-full" />
                          <Skeleton className="h-8 w-full" />
                          <Skeleton className="h-8 w-full" />
                          <Skeleton className="h-8 w-full" />
                        </div>
                      ) : null}
                      {profileLoadingEmployeeId !== item.id && riskProfiles[item.id] ? (
                        <div className="grid grid-cols-2 gap-2 text-xs text-muted">
                          <div className="rounded-lg border border-border bg-surface/50 px-3 py-2">
                            {t.employees.totalCampaigns}:{" "}
                            <span className="text-text">{riskProfiles[item.id].totalCampaigns}</span>
                          </div>
                          <div className="rounded-lg border border-border bg-surface/50 px-3 py-2">
                            {t.employees.actionsTaken}:{" "}
                            <span className="text-text">{riskProfiles[item.id].actionsTaken}</span>
                          </div>
                          <div className="rounded-lg border border-border bg-surface/50 px-3 py-2">
                            {t.employees.emailsOpened}:{" "}
                            <span className="text-text">{riskProfiles[item.id].emailsOpened}</span>
                          </div>
                          <div className="rounded-lg border border-border bg-surface/50 px-3 py-2">
                            {t.employees.linksClicked}:{" "}
                            <span className="text-text">{riskProfiles[item.id].linksClicked}</span>
                          </div>
                          <div className="rounded-lg border border-border bg-surface/50 px-3 py-2">
                            {t.employees.credentialsSubmitted}:{" "}
                            <span className="text-text">{riskProfiles[item.id].credentialsSubmitted}</span>
                          </div>
                          <div className="rounded-lg border border-border bg-surface/50 px-3 py-2">
                            {t.employees.downloadTriggered}:{" "}
                            <span className="text-text">{riskProfiles[item.id].downloadTriggered}</span>
                          </div>
                          <div className="rounded-lg border border-border bg-surface/50 px-3 py-2">
                            {t.employees.consentGranted}:{" "}
                            <span className="text-text">{riskProfiles[item.id].consentGranted}</span>
                          </div>
                          <div className="rounded-lg border border-border bg-surface/50 px-3 py-2">
                            {t.employees.lastPhishedAt}:{" "}
                            <span className="text-text">{formatDateTime(riskProfiles[item.id].lastPhishedAt)}</span>
                          </div>
                        </div>
                      ) : null}
                    </div>
                  ) : null}
                </div>
              ))}
            </div>
          </Card>
        ) : null}
      </div>
    </RequireAccess>
  );
}
