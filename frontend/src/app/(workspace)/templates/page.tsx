"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import { Languages, RefreshCw, ShieldCheck, Sparkles, Upload } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { useApi } from "@/lib/api/use-api";
import type {
  AiProvider,
  DifficultyLevel,
  GenerateTemplateRequest,
  LanguageCode,
  PhishingTemplateResponse,
  RegenerateTemplateRequest,
  RegenerationScope,
  TemplateCategory
} from "@/lib/api/types";
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

export default function TemplatesPage() {
  const { api } = useApi();
  const { t } = useI18n();

  const [templates, setTemplates] = useState<PhishingTemplateResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [generating, setGenerating] = useState(false);
  const [generateError, setGenerateError] = useState<string | null>(null);
  const [generateSuccess, setGenerateSuccess] = useState<string | null>(null);
  const [uploadingReference, setUploadingReference] = useState(false);
  const [uploadReferenceError, setUploadReferenceError] = useState<string | null>(null);
  const [uploadReferenceSuccess, setUploadReferenceSuccess] = useState<string | null>(null);
  const [selectedReferenceFile, setSelectedReferenceFile] = useState<File | null>(null);
  const [templateActionError, setTemplateActionError] = useState<string | null>(null);
  const [templateActionSuccess, setTemplateActionSuccess] = useState<string | null>(null);
  const [regeneratingTemplateId, setRegeneratingTemplateId] = useState<string | null>(null);
  const [regeneratePrompts, setRegeneratePrompts] = useState<Record<string, string>>({});
  const [regenerateScopes, setRegenerateScopes] = useState<Record<string, RegenerationScope>>({});

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
    setUploadReferenceError(null);
    setUploadReferenceSuccess(null);

    if (!selectedReferenceFile) {
      setUploadReferenceError(`${t.templates.selectReferenceFile} is required`);
      return;
    }

    setUploadingReference(true);
    try {
      const response = await api.templates.uploadReference(selectedReferenceFile);
      setForm((prev) => ({ ...prev, referenceImageUrl: response.referenceImageUrl }));
      setUploadReferenceSuccess(t.templates.uploadReferenceSuccess);
      setSelectedReferenceFile(null);
    } catch (uploadError) {
      const message = uploadError instanceof Error ? uploadError.message : t.common.unknownError;
      setUploadReferenceError(message);
    } finally {
      setUploadingReference(false);
    }
  };

  const handleGenerateTemplate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    setGenerateError(null);
    setGenerateSuccess(null);

    const name = form.name.trim();
    const prompt = form.prompt.trim();
    if (!name) {
      setGenerateError(`${t.templates.templateName} is required`);
      return;
    }
    if (!prompt) {
      setGenerateError(`${t.templates.prompt} is required`);
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
      setGenerateSuccess(t.templates.generateSuccess);
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
      setGenerateError(message);
    } finally {
      setGenerating(false);
    }
  };

  const handleRegenerateTemplate = async (template: PhishingTemplateResponse) => {
    setTemplateActionError(null);
    setTemplateActionSuccess(null);

    const prompt = (regeneratePrompts[template.id] ?? "").trim();
    if (!prompt) {
      setTemplateActionError(`${t.templates.regeneratePrompt} is required`);
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
      setTemplateActionSuccess(t.templates.regenerateSuccess);
      setRegeneratePrompts((prev) => ({ ...prev, [template.id]: updated.prompt ?? prompt }));
    } catch (regenerateError) {
      const message = regenerateError instanceof Error ? regenerateError.message : t.common.unknownError;
      setTemplateActionError(message);
    } finally {
      setRegeneratingTemplateId(null);
    }
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
              <div>
                <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.templates.templateName}</label>
                <Input
                  value={form.name}
                  onChange={(event) => setForm((prev) => ({ ...prev, name: event.target.value }))}
                  placeholder="Invoice Impersonation v2"
                />
              </div>
              <div>
                <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.templates.categoryTagField}</label>
                <Input
                  value={form.category}
                  onChange={(event) => setForm((prev) => ({ ...prev, category: event.target.value }))}
                  placeholder="AI Generated"
                />
              </div>
            </div>

            <div>
              <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.templates.prompt}</label>
              <Textarea
                value={form.prompt}
                onChange={(event) => setForm((prev) => ({ ...prev, prompt: event.target.value }))}
                placeholder={t.templates.promptPlaceholder}
              />
            </div>

            <div className="grid gap-3 lg:grid-cols-2">
              <div>
                <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.templates.targetUrl}</label>
                <Input
                  value={form.targetUrl}
                  onChange={(event) => setForm((prev) => ({ ...prev, targetUrl: event.target.value }))}
                  placeholder="https://portal.example.com/login"
                />
              </div>
              <div>
                <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">
                  {t.templates.referenceImageUrlField}
                </label>
                <Input
                  value={form.referenceImageUrl}
                  onChange={(event) => setForm((prev) => ({ ...prev, referenceImageUrl: event.target.value }))}
                  placeholder="https://..."
                />
              </div>
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
              {uploadReferenceError ? <p className="mt-2 text-sm text-rose-300">{uploadReferenceError}</p> : null}
              {uploadReferenceSuccess ? <p className="mt-2 text-sm text-emerald-300">{uploadReferenceSuccess}</p> : null}
            </div>

            <div className="grid gap-3 lg:grid-cols-4">
              <div>
                <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">
                  {t.templates.templateCategoryField}
                </label>
                <Select
                  value={form.templateCategory}
                  onChange={(event) => setForm((prev) => ({ ...prev, templateCategory: event.target.value as TemplateCategory }))}
                >
                  <option value="CREDENTIAL_HARVESTING">CREDENTIAL_HARVESTING</option>
                  <option value="CLICK_ONLY">CLICK_ONLY</option>
                  <option value="MALWARE_DELIVERY">MALWARE_DELIVERY</option>
                  <option value="OAUTH_CONSENT">OAUTH_CONSENT</option>
                </Select>
              </div>
              <div>
                <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.templates.languageCodeField}</label>
                <Select
                  value={form.languageCode}
                  onChange={(event) => setForm((prev) => ({ ...prev, languageCode: event.target.value as LanguageCode }))}
                >
                  <option value="TR">TR</option>
                  <option value="EN">EN</option>
                </Select>
              </div>
              <div>
                <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.templates.difficultyField}</label>
                <Select
                  value={form.difficultyLevel}
                  onChange={(event) =>
                    setForm((prev) => ({ ...prev, difficultyLevel: event.target.value as DifficultyLevel }))
                  }
                >
                  <option value="AMATEUR">AMATEUR</option>
                  <option value="PROFESSIONAL">PROFESSIONAL</option>
                </Select>
              </div>
              <div>
                <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.templates.aiProviderField}</label>
                <Select
                  value={form.aiProvider}
                  onChange={(event) => setForm((prev) => ({ ...prev, aiProvider: event.target.value as AiProvider }))}
                >
                  <option value="gemini">gemini</option>
                  <option value="openai">openai</option>
                  <option value="anthropic">anthropic</option>
                  <option value="stub">stub</option>
                </Select>
              </div>
            </div>

            <div className="grid gap-3 lg:grid-cols-2">
              <div>
                <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.templates.aiModelField}</label>
                <Input
                  value={form.aiModel}
                  onChange={(event) => setForm((prev) => ({ ...prev, aiModel: event.target.value }))}
                  placeholder="gemini-2.5-pro"
                />
              </div>
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

            {generateError ? (
              <p className="rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-sm text-rose-300">{generateError}</p>
            ) : null}
            {generateSuccess ? (
              <p className="rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-3 py-2 text-sm text-emerald-300">
                {generateSuccess}
              </p>
            ) : null}

            <Button type="submit" disabled={generating}>
              {generating ? t.templates.generatingAction : t.templates.generateAction}
            </Button>
          </form>
        </Card>

        {templateActionError ? (
          <Card className="border-rose-500/30">
            <p className="text-sm text-rose-300">{templateActionError}</p>
          </Card>
        ) : null}
        {templateActionSuccess ? (
          <Card className="border-emerald-500/30">
            <p className="text-sm text-emerald-300">{templateActionSuccess}</p>
          </Card>
        ) : null}

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
            <p className="text-sm font-medium text-text">{t.templates.noData}</p>
            <p className="mt-1 text-xs text-muted">{t.templates.noDataHint}</p>
          </Card>
        ) : null}

        {templates.length ? (
          <Card>
            <div className="space-y-3">
              {templates.map((item) => (
                <div key={item.id} className="rounded-xl border border-border bg-surface/50 p-4">
                  <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                    <div>
                      <p className="text-sm font-medium text-text">{item.name}</p>
                      <p className="mt-1 text-xs text-muted">{item.id}</p>
                    </div>
                    <Badge tone={statusTone(item.status)}>{t.templates.statuses[item.status]}</Badge>
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
          </Card>
        ) : null}
      </div>
  );
}
