"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { motion, AnimatePresence } from "framer-motion";
import {
  Building2,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Loader2,
  Lock,
  Plus,
  RefreshCw,
  Shield,
  Sparkles
} from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { useAuth } from "@/lib/auth/auth-context";
import { ApiClient, ApiError } from "@/lib/api/client";
import { createGlobalApiServices } from "@/lib/api/services";
import type { CompanyResponse } from "@/lib/api/types";
import { useI18n } from "@/lib/i18n/i18n-context";
import { useSettings } from "@/lib/settings/settings-context";

type Step = "token" | "company";

export default function AccessPage() {
  const router = useRouter();
  const { auth, login } = useAuth();
  const { settings, setSettings } = useSettings();
  const { t } = useI18n();

  // Redirect if already authenticated
  useEffect(() => {
    if (auth.isAuthenticated) {
      router.replace("/dashboard");
    }
  }, [auth.isAuthenticated, router]);

  // ── State ────────────────────────────────────────────────────────

  const [step, setStep] = useState<Step>("token");
  const [token, setToken] = useState("");
  const [tokenValidated, setTokenValidated] = useState(false);
  const [validating, setValidating] = useState(false);
  const [tokenError, setTokenError] = useState<string | null>(null);

  const [companies, setCompanies] = useState<CompanyResponse[]>([]);
  const [selectedCompanyId, setSelectedCompanyId] = useState("");
  const [loadingCompanies, setLoadingCompanies] = useState(false);
  const [remember, setRemember] = useState(false);

  const [showCreateForm, setShowCreateForm] = useState(false);
  const [createName, setCreateName] = useState("");
  const [createDomain, setCreateDomain] = useState("");
  const [createError, setCreateError] = useState<string | null>(null);
  const [creatingCompany, setCreatingCompany] = useState(false);

  // ── Token validation ─────────────────────────────────────────────

  const validateToken = useCallback(async () => {
    const trimmedToken = token.trim();
    if (!trimmedToken) return;

    setValidating(true);
    setTokenError(null);

    try {
      const client = new ApiClient({
        ...settings,
        apiToken: trimmedToken,
        companyId: ""
      });
      const services = createGlobalApiServices(client);
      const response = await services.companies.list();

      setTokenValidated(true);
      setCompanies(response);
      setStep("company");

      if (response.length === 1) {
        setSelectedCompanyId(response[0].id);
      }
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setTokenError(t.auth.tokenUnauthorized);
      } else if (error instanceof TypeError) {
        setTokenError(t.auth.tokenConnectionFailed);
      } else {
        setTokenError(t.auth.tokenInvalid);
      }
      setTokenValidated(false);
    } finally {
      setValidating(false);
    }
  }, [token, settings, t.auth.tokenConnectionFailed, t.auth.tokenInvalid, t.auth.tokenUnauthorized]);

  // ── Fetch companies ──────────────────────────────────────────────

  const fetchCompanies = useCallback(async () => {
    setLoadingCompanies(true);
    try {
      const client = new ApiClient({
        ...settings,
        apiToken: token.trim(),
        companyId: ""
      });
      const services = createGlobalApiServices(client);
      const response = await services.companies.list();
      setCompanies(response);
    } catch {
      // silent
    } finally {
      setLoadingCompanies(false);
    }
  }, [token, settings]);

  // ── Create company ───────────────────────────────────────────────

  const handleCreateCompany = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const name = createName.trim();
    const domain = createDomain.trim().toLowerCase();

    if (!name) {
      setCreateError(t.validation.required);
      return;
    }
    if (!domain || !domain.includes(".")) {
      setCreateError(t.validation.invalidDomain);
      return;
    }

    setCreatingCompany(true);
    setCreateError(null);
    try {
      const client = new ApiClient({
        ...settings,
        apiToken: token.trim(),
        companyId: ""
      });
      const services = createGlobalApiServices(client);
      const created = await services.companies.create({ name, domain });
      setCompanies((prev) => [created, ...prev]);
      setSelectedCompanyId(created.id);
      setShowCreateForm(false);
      setCreateName("");
      setCreateDomain("");
    } catch (error) {
      const message = error instanceof Error ? error.message : t.common.unknownError;
      setCreateError(message);
    } finally {
      setCreatingCompany(false);
    }
  };

  // ── Login ────────────────────────────────────────────────────────

  const handleLogin = () => {
    if (!token.trim() || !selectedCompanyId) return;

    const company = companies.find((c) => c.id === selectedCompanyId);
    login({
      token: token.trim(),
      companyId: selectedCompanyId,
      companyName: company?.name ?? selectedCompanyId,
      remember
    });

    router.push("/dashboard");
  };

  // ── UI ───────────────────────────────────────────────────────────

  const selectedCompany = companies.find((c) => c.id === selectedCompanyId);

  return (
    <div className="relative flex min-h-screen">
      {/* ── Left branding panel ─────────────────────────────────── */}
      <div className="relative hidden w-[480px] flex-col justify-between overflow-hidden bg-[var(--button-bg)] p-10 lg:flex">
        {/* Gradient mesh */}
        <div className="pointer-events-none absolute inset-0">
          <div className="absolute -left-24 -top-24 h-80 w-80 rounded-full bg-sky-500/20 blur-[100px]" />
          <div className="absolute bottom-20 right-0 h-64 w-64 rounded-full bg-violet-500/15 blur-[80px]" />
          <div className="absolute left-1/2 top-1/2 h-40 w-40 -translate-x-1/2 -translate-y-1/2 rounded-full bg-cyan-400/10 blur-[60px]" />
        </div>

        {/* Logo & branding */}
        <div className="relative z-10">
          <div className="flex items-center gap-3">
            <div className="rounded-xl border border-white/10 bg-white/5 p-2.5 backdrop-blur-sm">
              <Shield className="h-6 w-6 text-sky-400" />
            </div>
            <div>
              <p className="text-lg font-semibold text-white">GenPhish</p>
              <p className="text-xs text-white/50">Console</p>
            </div>
          </div>
        </div>

        {/* Tagline */}
        <div className="relative z-10 space-y-4">
          <div className="flex items-center gap-2 text-sky-400/80">
            <Sparkles className="h-4 w-4" />
            <span className="text-xs font-medium uppercase tracking-[0.2em]">
              {t.auth.tagline}
            </span>
          </div>
          <p className="text-2xl font-light leading-relaxed text-white/80">
            {t.auth.securityNote}
          </p>
          <div className="flex gap-2">
            {["SOC", "Red Team", "Compliance"].map((tag) => (
              <span
                key={tag}
                className="rounded-md border border-white/10 bg-white/5 px-2.5 py-1 text-[10px] font-medium uppercase tracking-widest text-white/40"
              >
                {tag}
              </span>
            ))}
          </div>
        </div>

        {/* Footer */}
        <div className="relative z-10">
          <p className="text-xs text-white/25">© 2026 GenPhish — Security Operations Platform</p>
        </div>
      </div>

      {/* ── Right login panel ───────────────────────────────────── */}
      <div className="flex flex-1 items-center justify-center p-6 lg:p-12">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
          className="w-full max-w-md"
        >
          {/* Mobile logo */}
          <div className="mb-8 flex items-center gap-3 lg:hidden">
            <div className="rounded-xl border border-border bg-surface p-2">
              <Shield className="h-5 w-5 text-accent" />
            </div>
            <p className="text-lg font-semibold text-text">GenPhish Console</p>
          </div>

          <h1 className="text-2xl font-semibold tracking-tight text-text">{t.auth.loginTitle}</h1>
          <p className="mt-1.5 text-sm text-muted">{t.auth.loginSubtitle}</p>

          <div className="mt-8 space-y-5">
            {/* ── API Base URL ─────────────────────────────────── */}
            <div>
              <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">
                {t.auth.apiBaseUrl}
              </label>
              <Input
                value={settings.apiBaseUrl}
                onChange={(event) => setSettings({ apiBaseUrl: event.target.value })}
                placeholder="http://localhost:8088"
                disabled={step === "company"}
              />
            </div>

            {/* ── Token ────────────────────────────────────────── */}
            <div>
              <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">
                {t.auth.tokenLabel}
              </label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" />
                <Input
                  type="password"
                  value={token}
                  onChange={(event) => {
                    setToken(event.target.value);
                    setTokenValidated(false);
                    if (step === "company") setStep("token");
                  }}
                  placeholder={t.auth.tokenPlaceholder}
                  className="pl-9"
                />
                {tokenValidated ? (
                  <motion.div
                    initial={{ scale: 0 }}
                    animate={{ scale: 1 }}
                    className="absolute right-3 top-1/2 -translate-y-1/2"
                  >
                    <CheckCircle2 className="h-4 w-4 text-emerald-400" />
                  </motion.div>
                ) : null}
              </div>
              {tokenError ? (
                <motion.p
                  initial={{ opacity: 0, y: -4 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="mt-2 text-sm text-rose-400"
                >
                  {tokenError}
                </motion.p>
              ) : null}
            </div>

            {/* ── Validate button (step 1) ─────────────────────── */}
            <AnimatePresence mode="wait">
              {step === "token" ? (
                <motion.div
                  key="validate-step"
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0, height: 0 }}
                >
                  <Button
                    className="w-full"
                    disabled={!token.trim() || validating}
                    onClick={() => void validateToken()}
                  >
                    {validating ? (
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    ) : (
                      <Shield className="mr-2 h-4 w-4" />
                    )}
                    {validating ? t.auth.validatingAction : t.auth.validateAction}
                  </Button>
                </motion.div>
              ) : null}
            </AnimatePresence>

            {/* ── Company selection (step 2) ────────────────────── */}
            <AnimatePresence mode="wait">
              {step === "company" ? (
                <motion.div
                  key="company-step"
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0 }}
                  className="space-y-4"
                >
                  {/* Token validated badge */}
                  <div className="flex items-center gap-2 rounded-lg border border-emerald-500/20 bg-emerald-500/5 px-3 py-2">
                    <CheckCircle2 className="h-4 w-4 text-emerald-400" />
                    <span className="text-xs text-emerald-400">{t.auth.tokenValid}</span>
                  </div>

                  {/* Company picker */}
                  <div>
                    <div className="mb-2 flex items-center justify-between">
                      <label className="block text-xs uppercase tracking-[0.12em] text-muted">
                        <Building2 className="mr-1.5 inline h-3.5 w-3.5" />
                        {t.auth.companyLabel}
                      </label>
                      <button
                        type="button"
                        onClick={() => void fetchCompanies()}
                        disabled={loadingCompanies}
                        className="inline-flex items-center gap-1.5 text-xs text-muted hover:text-text"
                      >
                        <RefreshCw className={`h-3 w-3 ${loadingCompanies ? "animate-spin" : ""}`} />
                        {t.auth.companyRefresh}
                      </button>
                    </div>
                    <Select
                      value={selectedCompanyId}
                      onChange={(event) => setSelectedCompanyId(event.target.value)}
                      disabled={loadingCompanies}
                    >
                      <option value="">
                        {loadingCompanies
                          ? t.auth.companyLoading
                          : t.auth.companySelectPlaceholder}
                      </option>
                      {companies.map((company) => (
                        <option key={company.id} value={company.id}>
                          {company.name} ({company.domain})
                        </option>
                      ))}
                    </Select>
                    {!loadingCompanies && companies.length === 0 ? (
                      <p className="mt-2 text-xs text-muted">{t.auth.companyNoData}</p>
                    ) : null}
                  </div>

                  {/* Create company accordion */}
                  <div className="rounded-xl border border-border bg-surface/40">
                    <button
                      type="button"
                      onClick={() => setShowCreateForm((prev) => !prev)}
                      className="flex w-full items-center justify-between px-4 py-3 text-xs uppercase tracking-[0.12em] text-muted hover:text-text"
                    >
                      <span className="flex items-center gap-2">
                        <Plus className="h-3.5 w-3.5" />
                        {t.auth.companyCreateTitle}
                      </span>
                      {showCreateForm ? (
                        <ChevronUp className="h-3.5 w-3.5" />
                      ) : (
                        <ChevronDown className="h-3.5 w-3.5" />
                      )}
                    </button>

                    <AnimatePresence>
                      {showCreateForm ? (
                        <motion.form
                          initial={{ height: 0, opacity: 0 }}
                          animate={{ height: "auto", opacity: 1 }}
                          exit={{ height: 0, opacity: 0 }}
                          transition={{ duration: 0.2 }}
                          className="overflow-hidden"
                          onSubmit={(event) => void handleCreateCompany(event)}
                        >
                          <div className="space-y-3 px-4 pb-4">
                            <div className="grid gap-3 sm:grid-cols-2">
                              <div>
                                <label className="mb-1.5 block text-xs text-muted">
                                  {t.auth.companyName}
                                </label>
                                <Input
                                  value={createName}
                                  onChange={(event) => setCreateName(event.target.value)}
                                  placeholder="Acme Security"
                                />
                              </div>
                              <div>
                                <label className="mb-1.5 block text-xs text-muted">
                                  {t.auth.companyDomain}
                                </label>
                                <Input
                                  value={createDomain}
                                  onChange={(event) => setCreateDomain(event.target.value)}
                                  placeholder="acme.com"
                                />
                              </div>
                            </div>
                            {createError ? (
                              <p className="text-xs text-rose-400">{createError}</p>
                            ) : null}
                            <Button
                              type="submit"
                              variant="ghost"
                              className="w-full"
                              disabled={creatingCompany}
                            >
                              {creatingCompany ? (
                                <Loader2 className="mr-2 h-3.5 w-3.5 animate-spin" />
                              ) : null}
                              {creatingCompany
                                ? t.auth.companyCreatingAction
                                : t.auth.companyCreateAction}
                            </Button>
                          </div>
                        </motion.form>
                      ) : null}
                    </AnimatePresence>
                  </div>

                  {/* Remember me */}
                  <label className="flex items-center gap-2.5 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={remember}
                      onChange={(event) => setRemember(event.target.checked)}
                      className="h-4 w-4 rounded border-border bg-panel text-accent focus:ring-accent/30"
                    />
                    <span className="text-sm text-muted">{t.auth.rememberMe}</span>
                  </label>

                  {/* Login button */}
                  <Button
                    className="w-full"
                    disabled={!selectedCompanyId}
                    onClick={handleLogin}
                  >
                    {t.auth.loginAction}
                  </Button>

                  {/* Selected company summary */}
                  {selectedCompany ? (
                    <motion.div
                      initial={{ opacity: 0 }}
                      animate={{ opacity: 1 }}
                      className="rounded-lg border border-border bg-surface/50 px-3 py-2"
                    >
                      <p className="text-[10px] uppercase tracking-[0.14em] text-muted">
                        {t.auth.companyLabel}
                      </p>
                      <p className="mt-0.5 text-sm font-medium text-text">
                        {selectedCompany.name}
                      </p>
                      <p className="font-mono text-[10px] text-muted">{selectedCompany.id}</p>
                    </motion.div>
                  ) : null}
                </motion.div>
              ) : null}
            </AnimatePresence>
          </div>
        </motion.div>
      </div>
    </div>
  );
}
