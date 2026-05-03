"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { RefreshCw } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { ApiClient } from "@/lib/api/client";
import { createGlobalApiServices } from "@/lib/api/services";
import type { CompanyResponse } from "@/lib/api/types";
import { useI18n } from "@/lib/i18n/i18n-context";
import { useSettings } from "@/lib/settings/settings-context";

export default function AccessPage() {
  const router = useRouter();
  const { settings, setSettings } = useSettings();
  const { t } = useI18n();
  const [companies, setCompanies] = useState<CompanyResponse[]>([]);
  const [companyError, setCompanyError] = useState<string | null>(null);
  const [loadingCompanies, setLoadingCompanies] = useState(false);
  const [createCompanyName, setCreateCompanyName] = useState("");
  const [createCompanyDomain, setCreateCompanyDomain] = useState("");
  const [createCompanyError, setCreateCompanyError] = useState<string | null>(null);
  const [createCompanySuccess, setCreateCompanySuccess] = useState<string | null>(null);
  const [creatingCompany, setCreatingCompany] = useState(false);

  const canManageCompanies = Boolean(settings.apiBaseUrl.trim() && settings.apiToken.trim());

  const fetchCompanies = useCallback(async () => {
    if (!canManageCompanies) {
      setCompanies([]);
      setCompanyError(null);
      return;
    }

    setLoadingCompanies(true);
    setCompanyError(null);
    try {
      const client = new ApiClient(settings);
      const services = createGlobalApiServices(client);
      const response = await services.companies.list();
      setCompanies(response);
    } catch (fetchError) {
      const message = fetchError instanceof Error ? fetchError.message : t.common.unknownError;
      setCompanyError(message);
    } finally {
      setLoadingCompanies(false);
    }
  }, [canManageCompanies, settings, t.common.unknownError]);

  useEffect(() => {
    if (!canManageCompanies) {
      return;
    }
    // Fetch active companies after API token is provided.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void fetchCompanies();
  }, [canManageCompanies, fetchCompanies]);

  const handleCreateCompany = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!canManageCompanies) {
      return;
    }

    setCreateCompanyError(null);
    setCreateCompanySuccess(null);

    const name = createCompanyName.trim();
    const domain = createCompanyDomain.trim().toLowerCase();

    if (!name) {
      setCreateCompanyError(`${t.access.companyName} is required`);
      return;
    }
    if (!domain || !domain.includes(".")) {
      setCreateCompanyError(`${t.access.companyDomain} is invalid`);
      return;
    }

    setCreatingCompany(true);
    try {
      const client = new ApiClient(settings);
      const services = createGlobalApiServices(client);
      const created = await services.companies.create({ name, domain });
      setCompanies((prev) => [created, ...prev]);
      setSettings({ companyId: created.id });
      setCreateCompanySuccess(t.access.companyCreateSuccess);
      setCreateCompanyName("");
      setCreateCompanyDomain("");
    } catch (createError) {
      const message = createError instanceof Error ? createError.message : t.common.unknownError;
      setCreateCompanyError(message);
    } finally {
      setCreatingCompany(false);
    }
  };

  return (
    <div className="grid min-h-screen place-items-center p-6">
      <Card className="w-full max-w-xl">
        <p className="text-2xl font-semibold tracking-tight">{t.access.title}</p>
        <p className="mt-1 text-sm text-muted">{t.access.subtitle}</p>

        <div className="mt-6 space-y-4">
          <div>
            <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.access.apiBaseUrl}</label>
            <Input
              value={settings.apiBaseUrl}
              onChange={(event) => setSettings({ apiBaseUrl: event.target.value })}
              placeholder="http://localhost:8088"
            />
          </div>

          <div>
            <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.access.apiToken}</label>
            <Input
              type="password"
              value={settings.apiToken}
              onChange={(event) => setSettings({ apiToken: event.target.value })}
              placeholder="INTERNAL_SERVICE_TOKEN"
            />
          </div>

          <div>
            <div className="mb-2 flex items-center justify-between">
              <label className="block text-xs uppercase tracking-[0.12em] text-muted">{t.access.companyPicker}</label>
              <Button type="button" variant="ghost" onClick={() => void fetchCompanies()} disabled={loadingCompanies || !canManageCompanies}>
                <RefreshCw className="mr-2 h-4 w-4" />
                {t.access.companyRefresh}
              </Button>
            </div>
            <Select
              value={settings.companyId}
              onChange={(event) => setSettings({ companyId: event.target.value })}
              disabled={!canManageCompanies || loadingCompanies}
            >
              <option value="">{loadingCompanies ? t.access.companyLoading : t.access.companySelectPlaceholder}</option>
              {companies.map((company) => (
                <option key={company.id} value={company.id}>
                  {company.name} ({company.domain})
                </option>
              ))}
            </Select>
            {companyError ? <p className="mt-2 text-sm text-rose-300">{companyError}</p> : null}
            {!companyError && canManageCompanies && !loadingCompanies && companies.length === 0 ? (
              <p className="mt-2 text-xs text-muted">{t.access.companyNoData}</p>
            ) : null}
          </div>

          <div>
            <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.access.companyId}</label>
            <Input
              value={settings.companyId}
              onChange={(event) => setSettings({ companyId: event.target.value })}
              placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
            />
          </div>

          <form className="rounded-xl border border-border bg-surface/40 p-3" onSubmit={(event) => void handleCreateCompany(event)}>
            <p className="mb-3 text-xs uppercase tracking-[0.12em] text-muted">{t.access.companyCreateTitle}</p>
            <div className="grid gap-3 lg:grid-cols-2">
              <div>
                <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.access.companyName}</label>
                <Input value={createCompanyName} onChange={(event) => setCreateCompanyName(event.target.value)} placeholder="Acme Security" />
              </div>
              <div>
                <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.access.companyDomain}</label>
                <Input value={createCompanyDomain} onChange={(event) => setCreateCompanyDomain(event.target.value)} placeholder="acme.com" />
              </div>
            </div>
            {createCompanyError ? <p className="mt-2 text-sm text-rose-300">{createCompanyError}</p> : null}
            {createCompanySuccess ? <p className="mt-2 text-sm text-emerald-300">{createCompanySuccess}</p> : null}
            <Button type="submit" className="mt-3" variant="ghost" disabled={creatingCompany || !canManageCompanies}>
              {creatingCompany ? t.access.companyCreatingAction : t.access.companyCreateAction}
            </Button>
          </form>

          <Button className="w-full" onClick={() => router.push("/dashboard")}>
            {t.access.cta}
          </Button>
        </div>
      </Card>
    </div>
  );
}
