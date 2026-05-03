"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { Copy, Download, ExternalLink, Eye, Languages, RefreshCw, ShieldCheck, Sparkles, Upload } from "lucide-react";
import type { Route } from "next";
import { useRouter } from "next/navigation";

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
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import { useApi } from "@/lib/api/use-api";
import type {
  AiProvider,
  DifficultyLevel,
  GenerateTemplateRequest,
  LanguageCode,
  PhishingTemplateResponse,
  RegenerateTemplateRequest,
  RegenerationScope,
  TemplateCategory,
  TemplateStatus
} from "@/lib/api/types";
import { usePagination } from "@/lib/hooks/use-pagination";
import { useSearch } from "@/lib/hooks/use-search";
import { useI18n } from "@/lib/i18n/i18n-context";

type BadgeTone = "neutral" | "success" | "warning" | "danger" | "info";

type TemplateFormState = {
  name: string;
  category: string;
  prompt: string;
  targetUrl: string;
  templateCategory: TemplateCategory;
  languageCode: LanguageCode;
  difficultyLevel: DifficultyLevel;
  aiProvider: AiProvider;
  aiModel: string;
  referenceImageUrl: string;
  allowFallbackTemplate: boolean;
};

function statusTone(status: PhishingTemplateResponse["status"]): BadgeTone {
  switch (status) {
    case "READY":
      return "success";
    case "GENERATING":
      return "info";
    case "FAILED":
      return "danger";
    default:
      return "neutral";
  }
}

function TemplateListSkeleton() {
  return (
    <div className="space-y-3">
      {Array.from({ length: 5 }).map((_, index) => (
        <div key={index} className="rounded-xl border border-border bg-surface/50 p-4">
          <Skeleton className="h-4 w-40" />
          <Skeleton className="mt-2 h-3 w-28" />
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

function difficultyTone(level: PhishingTemplateResponse["difficultyLevel"]): BadgeTone {
  switch (level) {
    case "PROFESSIONAL":
      return "danger";
    case "AMATEUR":
      return "warning";
    default:
      return "neutral";
  }
}

function asPreviewDocument(content?: string) {
  const value = content?.trim();
  if (!value) {
    return "";
  }

  if (/<html[\s>]/i.test(value)) {
    return value;
  }

  return `<!doctype html><html><head><meta charset="utf-8" /><meta name="viewport" content="width=device-width, initial-scale=1" /></head><body>${value}</body></html>`;
}

export default function TemplatesPage() {
  const router = useRouter();
  const { api } = useApi();
  const { t } = useI18n();
  const { toast } = useToast();

  const [templates, setTemplates] = useState<PhishingTemplateResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [generating, setGenerating] = useState(false);
  const [uploadingReference, setUploadingReference] = useState(false);
  const [selectedReferenceFile, setSelectedReferenceFile] = useState<File | null>(null);
  const [regeneratingTemplateId, setRegeneratingTemplateId] = useState<string | null>(null);
  const [regeneratePrompts, setRegeneratePrompts] = useState<Record<string, string>>({});
  const [regenerateScopes, setRegenerateScopes] = useState<Record<string, RegenerationScope>>({});
  const [previewTemplateId, setPreviewTemplateId] = useState<string | null>(null);
  const [previewDetails, setPreviewDetails] = useState<Record<string, PhishingTemplateResponse>>({});
  const [previewLoadingTemplateId, setPreviewLoadingTemplateId] = useState<string | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);

  const [form, setForm] = useState<TemplateFormState>({
    name: "",
    category: "AI Generated",
    prompt: "",
    targetUrl: "",
    templateCategory: "CREDENTIAL_HARVESTING",
    languageCode: "TR",
    difficultyLevel: "PROFESSIONAL",
    aiProvider: "gemini",
    aiModel: "",
    referenceImageUrl: "",
    allowFallbackTemplate: true
  });

  // ── Search & Filter ────────────────────────────────────────────────
  const [statusFilter, setStatusFilter] = useState<TemplateStatus | "">("");
  const { filtered: searchedTemplates, query: searchQuery, setQuery: setSearchQuery } = useSearch(templates, {
    keys: ["name", "id", "templateCategory"],
    debounceMs: 200
  });

  const filteredTemplates = useMemo(
    () => statusFilter ? searchedTemplates.filter((t2) => t2.status === statusFilter) : searchedTemplates,
    [searchedTemplates, statusFilter]
  );

  const pag = usePagination(filteredTemplates, { defaultPageSize: 10 });

  const upsertTemplate = (updated: PhishingTemplateResponse) => {
    setTemplates((prev) => {
      const exists = prev.some((item) => item.id === updated.id);
      if (!exists) {
        return [updated, ...prev];
      }
      return prev.map((item) => (item.id === updated.id ? updated : item));
    });
  };

  const fetchTemplates = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await api.templates.list();
      setTemplates(response);
      setRegeneratePrompts((prev) => {
        const next = { ...prev };
        response.forEach((item) => {
          if (!next[item.id]) {
            next[item.id] = item.prompt ?? "";
          }
        });
        return next;
      });
      setRegenerateScopes((prev) => {
        const next = { ...prev };
        response.forEach((item) => {
          if (!next[item.id]) {
            next[item.id] = "ALL";
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
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void fetchTemplates();
  }, [fetchTemplates]);

  const handleUploadReference = async () => {
    if (!selectedReferenceFile) {
      toast(t.validation.required, "error");
      return;
    }

    setUploadingReference(true);
    try {
      const response = await api.templates.uploadReference(selectedReferenceFile);
      setForm((prev) => ({ ...prev, referenceImageUrl: response.referenceImageUrl }));
      toast(t.templates.uploadReferenceSuccess, "success");
      setSelectedReferenceFile(null);
    } catch (uploadError) {
      const message = uploadError instanceof Error ? uploadError.message : t.common.unknownError;
      toast(message, "error");
    } finally {
      setUploadingReference(false);
    }
  };

  const handleGenerateTemplate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const name = form.name.trim();
    const prompt = form.prompt.trim();
    if (!name) {
      toast(t.validation.required, "error");
      return;
    }
    if (!prompt) {
      toast(t.validation.required, "error");
      return;
    }

    setGenerating(true);
    try {
      const payload: GenerateTemplateRequest = {
        name,
        prompt,
        templateCategory: form.templateCategory,
        languageCode: form.languageCode,
        difficultyLevel: form.difficultyLevel,
        category: form.category.trim() || undefined,
        targetUrl: form.targetUrl.trim() || undefined,
        referenceImageUrl: form.referenceImageUrl.trim() || undefined,
        aiProvider: form.aiProvider,
        aiModel: form.aiModel.trim() || undefined,
        allowFallbackTemplate: form.allowFallbackTemplate
      };

      const response = await api.templates.generate(payload);
      upsertTemplate(response);
      toast(t.templates.generateSuccess, "success");
      setForm((prev) => ({
        ...prev,
        name: "",
        prompt: "",
        targetUrl: "",
        aiModel: "",
        referenceImageUrl: ""
      }));
      setRegeneratePrompts((prev) => ({ ...prev, [response.id]: response.prompt ?? payload.prompt }));
      setRegenerateScopes((prev) => ({ ...prev, [response.id]: prev[response.id] ?? "ALL" }));
    } catch (generateTemplateError) {
      const message = generateTemplateError instanceof Error ? generateTemplateError.message : t.common.unknownError;
      toast(message, "error");
    } finally {
      setGenerating(false);
    }
  };

  const handleRegenerateTemplate = async (template: PhishingTemplateResponse) => {
    const prompt = (regeneratePrompts[template.id] ?? "").trim();
    if (!prompt) {
      toast(t.validation.required, "error");
      return;
    }

    const scope = regenerateScopes[template.id] ?? "ALL";
    const payload: RegenerateTemplateRequest = {
      prompt,
      scope,
      aiProvider: form.aiProvider,
      aiModel: form.aiModel.trim() || undefined,
      templateCategory: template.templateCategory,
      referenceImageUrl: form.referenceImageUrl.trim() || undefined
    };

    setRegeneratingTemplateId(template.id);
    try {
      const updated = await api.templates.regenerate(template.id, payload);
      upsertTemplate(updated);
      toast(t.templates.regenerateSuccess, "success");
      setRegeneratePrompts((prev) => ({ ...prev, [template.id]: updated.prompt ?? prompt }));
    } catch (regenerateError) {
      const message = regenerateError instanceof Error ? regenerateError.message : t.common.unknownError;
      toast(message, "error");
    } finally {
      setRegeneratingTemplateId(null);
    }
  };

  const selectedPreviewTemplate = useMemo(() => {
    if (!previewTemplateId) {
      return null;
    }

    return previewDetails[previewTemplateId] ?? templates.find((item) => item.id === previewTemplateId) ?? null;
  }, [previewDetails, previewTemplateId, templates]);

  const handleOpenPreview = async (templateId: string) => {
    setPreviewTemplateId(templateId);
    setPreviewError(null);

    if (previewDetails[templateId]) {
      return;
    }

    setPreviewLoadingTemplateId(templateId);
    try {
      const detailed = await api.templates.getById(templateId);
      setPreviewDetails((prev) => ({ ...prev, [templateId]: detailed }));
    } catch (previewFetchError) {
      const message = previewFetchError instanceof Error ? previewFetchError.message : t.common.unknownError;
      setPreviewError(message);
      toast(message, "error");
    } finally {
      setPreviewLoadingTemplateId(null);
    }
  };

  const handleCopyContent = async (value: string | undefined, successMessage: string) => {
    const content = value?.trim();
    if (!content) {
      toast(t.templates.noPreviewContent, "info");
      return;
    }

    try {
      await navigator.clipboard.writeText(content);
      toast(successMessage, "success");
    } catch {
      toast(t.common.unknownError, "error");
    }
  };

  const handleDownloadContent = (
    value: string | undefined,
    fileName: string,
    successMessage: string
  ) => {
    const content = value?.trim();
    if (!content) {
      toast(t.templates.noPreviewContent, "info");
      return;
    }

    try {
      const blob = new Blob([asPreviewDocument(content)], { type: "text/html;charset=utf-8" });
      const url = window.URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = fileName;
      anchor.click();
      window.URL.revokeObjectURL(url);
      toast(successMessage, "success");
    } catch {
      toast(t.common.unknownError, "error");
    }
  };

  const buildHtmlFileName = (template: PhishingTemplateResponse, type: "email" | "landing") => {
    const safeName =
      template.name
        .toLowerCase()
        .replace(/[^a-z0-9-_]+/gi, "-")
        .replace(/-+/g, "-")
        .replace(/^-|-$/g, "") || template.id;
    return `${safeName}-${type}.html`;
  };

  const goToTemplatePage = (templateId: string) => {
    router.push(`/templates/${templateId}` as Route);
  };

  return (
      <div className="space-y-4 lg:space-y-6">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <h1 className="text-3xl font-semibold tracking-tight">{t.templates.title}</h1>
            <p className="mt-1 text-sm text-muted">{t.templates.subtitle}</p>
          </div>
          <div className="flex items-center gap-2">
            <Badge tone="neutral">
              {t.templates.total}: {templates.length}
            </Badge>
            <Button variant="ghost" onClick={() => void fetchTemplates()} disabled={loading}>
              <RefreshCw className="mr-2 h-4 w-4" />
              {t.common.refresh}
            </Button>
          </div>
        </div>

        <Card>
          <div className="mb-4">
            <p className="text-sm font-medium text-text">{t.templates.generateTitle}</p>
            <p className="mt-1 text-xs text-muted">{t.templates.generateSubtitle}</p>
          </div>

          <form className="space-y-4" onSubmit={(event) => void handleGenerateTemplate(event)}>
            <div className="grid gap-3 lg:grid-cols-2">
              <FormField label={t.templates.templateName} required>
                <Input
                  value={form.name}
                  onChange={(event) => setForm((prev) => ({ ...prev, name: event.target.value }))}
                  placeholder="Invoice Impersonation v2"
                />
              </FormField>
              <FormField label={t.templates.categoryTagField}>
                <Input
                  value={form.category}
                  onChange={(event) => setForm((prev) => ({ ...prev, category: event.target.value }))}
                  placeholder="AI Generated"
                />
              </FormField>
            </div>

            <FormField label={t.templates.prompt} required>
              <Textarea
                value={form.prompt}
                onChange={(event) => setForm((prev) => ({ ...prev, prompt: event.target.value }))}
                placeholder={t.templates.promptPlaceholder}
              />
            </FormField>

            <div className="grid gap-3 lg:grid-cols-2">
              <FormField label={t.templates.targetUrl}>
                <Input
                  value={form.targetUrl}
                  onChange={(event) => setForm((prev) => ({ ...prev, targetUrl: event.target.value }))}
                  placeholder="https://portal.example.com/login"
                />
              </FormField>
              <FormField label={t.templates.referenceImageUrlField}>
                <Input
                  value={form.referenceImageUrl}
                  onChange={(event) => setForm((prev) => ({ ...prev, referenceImageUrl: event.target.value }))}
                  placeholder="https://..."
                />
              </FormField>
            </div>

            <div className="rounded-xl border border-border bg-surface/40 p-3">
              <p className="mb-2 text-xs uppercase tracking-[0.12em] text-muted">{t.templates.selectReferenceFile}</p>
              <div className="flex flex-col gap-2 lg:flex-row lg:items-center">
                <Input
                  type="file"
                  accept="image/*"
                  onChange={(event) => setSelectedReferenceFile(event.target.files?.[0] ?? null)}
                />
                <Button type="button" variant="ghost" onClick={() => void handleUploadReference()} disabled={uploadingReference}>
                  <Upload className="mr-2 h-4 w-4" />
                  {uploadingReference ? t.templates.uploadingReferenceAction : t.templates.uploadReferenceAction}
                </Button>
              </div>
            </div>

            <div className="grid gap-3 lg:grid-cols-4">
              <FormField label={t.templates.templateCategoryField}>
                <Select
                  value={form.templateCategory}
                  onChange={(event) => setForm((prev) => ({ ...prev, templateCategory: event.target.value as TemplateCategory }))}
                >
                  <option value="CREDENTIAL_HARVESTING">CREDENTIAL_HARVESTING</option>
                  <option value="CLICK_ONLY">CLICK_ONLY</option>
                  <option value="MALWARE_DELIVERY">MALWARE_DELIVERY</option>
                  <option value="OAUTH_CONSENT">OAUTH_CONSENT</option>
                </Select>
              </FormField>
              <FormField label={t.templates.languageCodeField}>
                <Select
                  value={form.languageCode}
                  onChange={(event) => setForm((prev) => ({ ...prev, languageCode: event.target.value as LanguageCode }))}
                >
                  <option value="TR">TR</option>
                  <option value="EN">EN</option>
                </Select>
              </FormField>
              <FormField label={t.templates.difficultyField}>
                <Select
                  value={form.difficultyLevel}
                  onChange={(event) =>
                    setForm((prev) => ({ ...prev, difficultyLevel: event.target.value as DifficultyLevel }))
                  }
                >
                  <option value="AMATEUR">AMATEUR</option>
                  <option value="PROFESSIONAL">PROFESSIONAL</option>
                </Select>
              </FormField>
              <FormField label={t.templates.aiProviderField}>
                <Select
                  value={form.aiProvider}
                  onChange={(event) => setForm((prev) => ({ ...prev, aiProvider: event.target.value as AiProvider }))}
                >
                  <option value="gemini">gemini</option>
                  <option value="openai">openai</option>
                  <option value="anthropic">anthropic</option>
                  <option value="stub">stub</option>
                </Select>
              </FormField>
            </div>

            <div className="grid gap-3 lg:grid-cols-2">
              <FormField label={t.templates.aiModelField}>
                <Input
                  value={form.aiModel}
                  onChange={(event) => setForm((prev) => ({ ...prev, aiModel: event.target.value }))}
                  placeholder="gemini-2.5-pro"
                />
              </FormField>
              <div className="flex items-end">
                <label className="inline-flex cursor-pointer items-center gap-2 rounded-lg border border-border bg-surface/50 px-3 py-2 text-sm text-text">
                  <input
                    type="checkbox"
                    checked={form.allowFallbackTemplate}
                    onChange={(event) => setForm((prev) => ({ ...prev, allowFallbackTemplate: event.target.checked }))}
                  />
                  {t.templates.allowFallbackTemplateField}
                </label>
              </div>
            </div>

            <Button type="submit" disabled={generating}>
              {generating ? t.templates.generatingAction : t.templates.generateAction}
            </Button>
          </form>
        </Card>


        {error ? (
          <Card className="border-rose-500/30">
            <p className="text-sm text-rose-300">{error}</p>
            <Button className="mt-3" variant="danger" onClick={() => void fetchTemplates()}>
              {t.common.retry}
            </Button>
          </Card>
        ) : null}

        {loading && !templates.length ? (
          <Card>
            <TemplateListSkeleton />
          </Card>
        ) : null}

        {!loading && !templates.length ? (
          <Card>
            <EmptyState
              icon={Sparkles}
              title={t.templates.noData}
              description={t.templates.noDataHint}
            />
          </Card>
        ) : null}

        {templates.length ? (
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
                onChange={(e) => setStatusFilter(e.target.value as TemplateStatus | "")}
                className="sm:max-w-[180px]"
              >
                <option value="">{t.filter.all} — {t.filter.status}</option>
                {(["GENERATING", "READY", "FAILED"] as TemplateStatus[]).map((s) => (
                  <option key={s} value={s}>{t.templates.statuses[s]}</option>
                ))}
              </Select>
            </div>

            {filteredTemplates.length === 0 ? (
              <EmptyState icon={Sparkles} title={t.search.noResults} />
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
                      <Badge tone={statusTone(item.status)}>{t.templates.statuses[item.status]}</Badge>
                      <Button variant="ghost" onClick={() => void handleOpenPreview(item.id)}>
                        <Eye className="mr-2 h-4 w-4" />
                        {t.templates.previewAction}
                      </Button>
                      <Button variant="ghost" onClick={() => goToTemplatePage(item.id)}>
                        <ExternalLink className="mr-2 h-4 w-4" />
                        {t.templates.openPageAction}
                      </Button>
                    </div>
                  </div>

                  <div className="mt-3 grid grid-cols-1 gap-2 text-xs text-muted lg:grid-cols-2">
                    <div className="flex items-center gap-2 rounded-lg border border-border bg-surface/40 px-3 py-2">
                      <Sparkles className="h-3.5 w-3.5 text-accent" />
                      <span>
                        {t.templates.category}: {item.templateCategory}
                      </span>
                    </div>
                    <div className="flex items-center gap-2 rounded-lg border border-border bg-surface/40 px-3 py-2">
                      <ShieldCheck className="h-3.5 w-3.5 text-accent" />
                      <span className="mr-auto">{t.templates.difficulty}</span>
                      <Badge tone={difficultyTone(item.difficultyLevel)}>{item.difficultyLevel}</Badge>
                    </div>
                  </div>

                  <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-muted">
                    <Badge tone="neutral">
                      {t.templates.type}: {item.type}
                    </Badge>
                    <Badge tone="neutral">
                      <Languages className="mr-1 h-3 w-3" />
                      {t.templates.language}: {item.languageCode}
                    </Badge>
                    <Badge tone={item.fallbackContentUsed ? "warning" : "success"}>
                      {t.templates.fallback}: {item.fallbackContentUsed ? "ON" : "OFF"}
                    </Badge>
                  </div>

                  <div className="mt-4 rounded-xl border border-border bg-surface/40 p-3">
                    <div className="grid gap-3 lg:grid-cols-3">
                      <div className="lg:col-span-2">
                        <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.templates.regeneratePrompt}</label>
                        <Textarea
                          className="min-h-[88px]"
                          value={regeneratePrompts[item.id] ?? ""}
                          onChange={(event) =>
                            setRegeneratePrompts((prev) => ({
                              ...prev,
                              [item.id]: event.target.value
                            }))
                          }
                        />
                      </div>
                      <div>
                        <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.templates.regenerateScope}</label>
                        <Select
                          value={regenerateScopes[item.id] ?? "ALL"}
                          onChange={(event) =>
                            setRegenerateScopes((prev) => ({
                              ...prev,
                              [item.id]: event.target.value as RegenerationScope
                            }))
                          }
                        >
                          <option value="ALL">{t.templates.scopeAll}</option>
                          <option value="ONLY_EMAIL">{t.templates.scopeOnlyEmail}</option>
                          <option value="ONLY_LANDING_PAGE">{t.templates.scopeOnlyLandingPage}</option>
                        </Select>
                        <Button
                          className="mt-3 w-full"
                          variant="ghost"
                          onClick={() => void handleRegenerateTemplate(item)}
                          disabled={regeneratingTemplateId !== null}
                        >
                          {regeneratingTemplateId === item.id ? t.templates.regeneratingAction : t.templates.regenerateAction}
                        </Button>
                      </div>
                    </div>
                  </div>
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

        <Dialog
          open={Boolean(previewTemplateId)}
          onClose={() => {
            setPreviewTemplateId(null);
            setPreviewError(null);
          }}
          title={selectedPreviewTemplate ? `${t.templates.previewTitle} • ${selectedPreviewTemplate.name}` : t.templates.previewTitle}
          className="max-w-6xl"
        >
          {previewError ? <p className="text-sm text-rose-300">{previewError}</p> : null}

          {previewLoadingTemplateId === previewTemplateId ? (
            <div className="space-y-3">
              <Skeleton className="h-8 w-64" />
              <Skeleton className="h-56 w-full" />
              <Skeleton className="h-56 w-full" />
            </div>
          ) : null}

          {previewLoadingTemplateId !== previewTemplateId && selectedPreviewTemplate ? (
            <div className="space-y-4">
              <div className="rounded-xl border border-border bg-surface/50 p-3">
                <div className="mb-2 flex items-center justify-between gap-2">
                  <p className="text-xs uppercase tracking-[0.12em] text-muted">{t.templates.emailSubject}</p>
                  <div className="flex items-center gap-2">
                    <Button variant="ghost" onClick={() => goToTemplatePage(selectedPreviewTemplate.id)}>
                      <ExternalLink className="mr-2 h-4 w-4" />
                      {t.templates.openPageAction}
                    </Button>
                    <Button
                      variant="ghost"
                      onClick={() => void handleCopyContent(selectedPreviewTemplate.emailSubject, t.templates.subjectCopied)}
                    >
                      <Copy className="mr-2 h-4 w-4" />
                      {t.templates.copySubject}
                    </Button>
                  </div>
                </div>
                <p className="font-mono text-sm text-text">
                  {selectedPreviewTemplate.emailSubject?.trim() || t.templates.noEmailPreview}
                </p>
              </div>

              <div className="grid gap-4 xl:grid-cols-2">
                <div className="rounded-xl border border-border bg-surface/50 p-3">
                  <div className="mb-2 flex items-center justify-between gap-2">
                    <p className="text-xs uppercase tracking-[0.12em] text-muted">{t.templates.emailPreview}</p>
                    <div className="flex items-center gap-2">
                      <Button
                        variant="ghost"
                        onClick={() => void handleCopyContent(selectedPreviewTemplate.emailBody, t.templates.emailHtmlCopied)}
                      >
                        <Copy className="mr-2 h-4 w-4" />
                        {t.templates.copyEmailHtml}
                      </Button>
                      <Button
                        variant="ghost"
                        onClick={() =>
                          handleDownloadContent(
                            selectedPreviewTemplate.emailBody,
                            buildHtmlFileName(selectedPreviewTemplate, "email"),
                            t.templates.emailHtmlDownloaded
                          )
                        }
                      >
                        <Download className="mr-2 h-4 w-4" />
                        {t.templates.downloadEmailHtml}
                      </Button>
                    </div>
                  </div>
                  {selectedPreviewTemplate.emailBody?.trim() ? (
                    <>
                      <iframe
                        title={`${selectedPreviewTemplate.id}-email-preview`}
                        srcDoc={asPreviewDocument(selectedPreviewTemplate.emailBody)}
                        sandbox="allow-forms allow-popups"
                        className="h-[360px] w-full rounded-lg border border-border bg-white"
                      />
                      <details className="mt-3 rounded-lg border border-border bg-surface/40 p-2">
                        <summary className="cursor-pointer text-xs uppercase tracking-[0.1em] text-muted">
                          {t.templates.rawCode}
                        </summary>
                        <pre className="mt-2 max-h-56 overflow-auto whitespace-pre-wrap break-words rounded bg-black/20 p-2 text-[11px] text-text">
                          {selectedPreviewTemplate.emailBody}
                        </pre>
                      </details>
                    </>
                  ) : (
                    <p className="text-sm text-muted">{t.templates.noEmailPreview}</p>
                  )}
                </div>

                <div className="rounded-xl border border-border bg-surface/50 p-3">
                  <div className="mb-2 flex items-center justify-between gap-2">
                    <p className="text-xs uppercase tracking-[0.12em] text-muted">{t.templates.landingPreview}</p>
                    <div className="flex items-center gap-2">
                      <Button
                        variant="ghost"
                        onClick={() =>
                          void handleCopyContent(selectedPreviewTemplate.landingPageHtml, t.templates.landingHtmlCopied)
                        }
                      >
                        <Copy className="mr-2 h-4 w-4" />
                        {t.templates.copyLandingHtml}
                      </Button>
                      <Button
                        variant="ghost"
                        onClick={() =>
                          handleDownloadContent(
                            selectedPreviewTemplate.landingPageHtml,
                            buildHtmlFileName(selectedPreviewTemplate, "landing"),
                            t.templates.landingHtmlDownloaded
                          )
                        }
                      >
                        <Download className="mr-2 h-4 w-4" />
                        {t.templates.downloadLandingHtml}
                      </Button>
                    </div>
                  </div>
                  {selectedPreviewTemplate.landingPageHtml?.trim() ? (
                    <>
                      <iframe
                        title={`${selectedPreviewTemplate.id}-landing-preview`}
                        srcDoc={asPreviewDocument(selectedPreviewTemplate.landingPageHtml)}
                        sandbox="allow-forms allow-popups"
                        className="h-[360px] w-full rounded-lg border border-border bg-white"
                      />
                      <details className="mt-3 rounded-lg border border-border bg-surface/40 p-2">
                        <summary className="cursor-pointer text-xs uppercase tracking-[0.1em] text-muted">
                          {t.templates.rawCode}
                        </summary>
                        <pre className="mt-2 max-h-56 overflow-auto whitespace-pre-wrap break-words rounded bg-black/20 p-2 text-[11px] text-text">
                          {selectedPreviewTemplate.landingPageHtml}
                        </pre>
                      </details>
                    </>
                  ) : (
                    <p className="text-sm text-muted">{t.templates.noLandingPreview}</p>
                  )}
                </div>
              </div>
            </div>
          ) : null}
        </Dialog>
      </div>
  );
}
