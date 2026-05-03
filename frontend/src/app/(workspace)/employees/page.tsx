"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { CircleUserRound, RefreshCw, ShieldAlert, Upload, UserPlus, UserX, UsersRound } from "lucide-react";

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
import type { EmployeeResponse, EmployeeRiskProfileResponse, ImportResultResponse } from "@/lib/api/types";
import { usePagination } from "@/lib/hooks/use-pagination";
import { useSearch } from "@/lib/hooks/use-search";
import { useI18n } from "@/lib/i18n/i18n-context";
import { useSettings } from "@/lib/settings/settings-context";

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
  const { api } = useApi();
  const { settings } = useSettings();
  const { t } = useI18n();
  const { toast } = useToast();

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

  const [selectedImportFile, setSelectedImportFile] = useState<File | null>(null);
  const [importing, setImporting] = useState(false);
  const [importResult, setImportResult] = useState<ImportResultResponse | null>(null);

  const [deactivatingEmployeeId, setDeactivatingEmployeeId] = useState<string | null>(null);
  const [expandedProfileEmployeeId, setExpandedProfileEmployeeId] = useState<string | null>(null);
  const [profileLoadingEmployeeId, setProfileLoadingEmployeeId] = useState<string | null>(null);
  const [profileError, setProfileError] = useState<string | null>(null);
  const [riskProfiles, setRiskProfiles] = useState<Record<string, EmployeeRiskProfileResponse>>({});

  const locale = settings.language === "tr" ? "tr-TR" : "en-US";

  // ── Search & Filter ────────────────────────────────────────────────
  const [activeFilter, setActiveFilter] = useState<"" | "active" | "passive">("")
  const [deptFilter, setDeptFilter] = useState("");
  const { filtered: searchedEmployees, query: searchQuery, setQuery: setSearchQuery } = useSearch(employees, {
    keys: ["firstName", "lastName", "email", "department"],
    debounceMs: 200
  });

  const departments = useMemo(
    () => [...new Set(employees.map((e) => e.department).filter((d) => d.trim().length > 0))].sort(),
    [employees]
  );

  const filteredEmployees = useMemo(() => {
    let result = searchedEmployees;
    if (activeFilter === "active") result = result.filter((e) => e.active);
    if (activeFilter === "passive") result = result.filter((e) => !e.active);
    if (deptFilter) result = result.filter((e) => e.department === deptFilter);
    return result;
  }, [searchedEmployees, activeFilter, deptFilter]);

  const pag = usePagination(filteredEmployees, { defaultPageSize: 10 });

  // ── Deactivate dialog ─────────────────────────────────────────────
  const [deactivateDialogId, setDeactivateDialogId] = useState<string | null>(null);

  const fetchEmployees = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await api.employees.list();
      setEmployees(response);
    } catch (fetchError) {
      const message = fetchError instanceof Error ? fetchError.message : t.common.unknownError;
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [api, t.common.unknownError]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void fetchEmployees();
  }, [fetchEmployees]);

  const handleCreateEmployee = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const firstName = createForm.firstName.trim();
    const lastName = createForm.lastName.trim();
    const email = createForm.email.trim();
    const department = createForm.department.trim();

    if (!firstName) {
      toast(t.validation.required, "error");
      return;
    }
    if (!lastName) {
      toast(t.validation.required, "error");
      return;
    }
    if (!email || !email.includes("@")) {
      toast(t.validation.invalidEmail, "error");
      return;
    }
    if (!department) {
      toast(t.validation.required, "error");
      return;
    }

    setCreating(true);
    try {
      const created = await api.employees.create({ firstName, lastName, email, department });
      setEmployees((prev) => [created, ...prev]);
      setCreateForm({ firstName: "", lastName: "", email: "", department: "" });
      toast(t.employees.createSuccess, "success");
    } catch (createEmployeeError) {
      const message = createEmployeeError instanceof Error ? createEmployeeError.message : t.common.unknownError;
      toast(message, "error");
    } finally {
      setCreating(false);
    }
  };

  const handleImportEmployees = async () => {
    if (!selectedImportFile) {
      toast(t.validation.required, "error");
      return;
    }

    setImporting(true);
    try {
      const result = await api.employees.import(selectedImportFile);
      setImportResult(result);
      toast(t.employees.importSuccess, "success");
      setSelectedImportFile(null);
      const updatedEmployees = await api.employees.list();
      setEmployees(updatedEmployees);
    } catch (importEmployeesError) {
      const message = importEmployeesError instanceof Error ? importEmployeesError.message : t.common.unknownError;
      toast(message, "error");
    } finally {
      setImporting(false);
    }
  };

  const handleDeactivateEmployee = async (employeeId: string) => {
    setDeactivateDialogId(null);
    setDeactivatingEmployeeId(employeeId);

    try {
      await api.employees.deactivate(employeeId);
      setEmployees((prev) => prev.map((item) => (item.id === employeeId ? { ...item, active: false } : item)));
      toast(t.employees.deactivateSuccess, "success");
    } catch (deactivateError) {
      const message = deactivateError instanceof Error ? deactivateError.message : t.common.unknownError;
      toast(message, "error");
    } finally {
      setDeactivatingEmployeeId(null);
    }
  };

  const handleToggleRiskProfile = async (employeeId: string) => {
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
      const profile = await api.employees.riskProfile(employeeId);
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
                <FormField label={t.employees.firstName} required>
                  <Input
                    value={createForm.firstName}
                    onChange={(event) => setCreateForm((prev) => ({ ...prev, firstName: event.target.value }))}
                  />
                </FormField>
                <FormField label={t.employees.lastName} required>
                  <Input
                    value={createForm.lastName}
                    onChange={(event) => setCreateForm((prev) => ({ ...prev, lastName: event.target.value }))}
                  />
                </FormField>
              </div>

              <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
                <FormField label={t.employees.email} required>
                  <Input
                    type="email"
                    value={createForm.email}
                    onChange={(event) => setCreateForm((prev) => ({ ...prev, email: event.target.value }))}
                  />
                </FormField>
                <FormField label={t.employees.department} required>
                  <Input
                    value={createForm.department}
                    onChange={(event) => setCreateForm((prev) => ({ ...prev, department: event.target.value }))}
                  />
                </FormField>
              </div>

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
            <EmptyState
              icon={UsersRound}
              title={t.employees.noData}
              description={t.employees.noDataHint}
            />
          </Card>
        ) : null}

        {employees.length ? (
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
                value={deptFilter}
                onChange={(e) => setDeptFilter(e.target.value)}
                className="sm:max-w-[180px]"
              >
                <option value="">{t.filter.all} — {t.filter.department}</option>
                {departments.map((d) => (
                  <option key={d} value={d}>{d}</option>
                ))}
              </Select>
              <Select
                value={activeFilter}
                onChange={(e) => setActiveFilter(e.target.value as "" | "active" | "passive")}
                className="sm:max-w-[140px]"
              >
                <option value="">{t.filter.all}</option>
                <option value="active">{t.filter.active}</option>
                <option value="passive">{t.filter.passive}</option>
              </Select>
            </div>

            {filteredEmployees.length === 0 ? (
              <EmptyState icon={UsersRound} title={t.search.noResults} />
            ) : (
              <>
            <div className="space-y-3">
              {pag.paginated.map((item) => (
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
                          onClick={() => setDeactivateDialogId(item.id)}
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

        {/* Deactivate confirmation dialog */}
        <Dialog
          open={deactivateDialogId !== null}
          onClose={() => setDeactivateDialogId(null)}
          title={t.employees.deactivateAction}
        >
          <p className="text-sm text-muted">{t.employees.deactivateAction}?</p>
          <div className="mt-4 flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setDeactivateDialogId(null)}>
              {t.common.cancel}
            </Button>
            <Button
              variant="danger"
              onClick={() => { if (deactivateDialogId) void handleDeactivateEmployee(deactivateDialogId); }}
            >
              <UserX className="mr-2 h-4 w-4" />
              {t.employees.deactivateAction}
            </Button>
          </div>
        </Dialog>
      </div>
  );
}
