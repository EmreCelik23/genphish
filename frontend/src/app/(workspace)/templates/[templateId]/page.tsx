"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { ArrowLeft, Copy, Download, RefreshCw } from "lucide-react";
import { useParams, useRouter } from "next/navigation";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useToast } from "@/components/ui/toast";
import { useApi } from "@/lib/api/use-api";
import type { PhishingTemplateResponse } from "@/lib/api/types";
import { useI18n } from "@/lib/i18n/i18n-context";

type BadgeTone = "neutral" | "success" | "warning" | "danger" | "info";

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

function buildHtmlFileName(template: PhishingTemplateResponse, type: "email" | "landing") {
  const safeName =
    template.name
      .toLowerCase()
      .replace(/[^a-z0-9-_]+/gi, "-")
      .replace(/-+/g, "-")
      .replace(/^-|-$/g, "") || template.id;
  return `${safeName}-${type}.html`;
}

export default function TemplatePreviewPage() {
  const router = useRouter();
  const params = useParams<{ templateId: string | string[] }>();
  const { api } = useApi();
  const { t } = useI18n();
  const { toast } = useToast();

  const templateId = useMemo(() => {
    const raw = params?.templateId;
    if (!raw) {
      return "";
    }
    return Array.isArray(raw) ? raw[0] : raw;
  }, [params]);

  const [template, setTemplate] = useState<PhishingTemplateResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchTemplate = useCallback(async () => {
    if (!templateId) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const response = await api.templates.getById(templateId);
      setTemplate(response);
    } catch (fetchError) {
      const message = fetchError instanceof Error ? fetchError.message : t.common.unknownError;
      setError(message);
      toast(message, "error");
    } finally {
      setLoading(false);
    }
  }, [api, templateId, t.common.unknownError, toast]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void fetchTemplate();
  }, [fetchTemplate]);

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

  return (
    <div className="space-y-4 lg:space-y-6">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <Button variant="ghost" onClick={() => router.push("/templates")} className="w-fit">
          <ArrowLeft className="mr-2 h-4 w-4" />
          {t.nav.templates}
        </Button>
        <Button variant="ghost" onClick={() => void fetchTemplate()} disabled={loading || !templateId}>
          <RefreshCw className="mr-2 h-4 w-4" />
          {t.common.refresh}
        </Button>
      </div>

      {error ? (
        <Card className="border-rose-500/30">
          <p className="text-sm text-rose-300">{error}</p>
          <Button className="mt-3" variant="danger" onClick={() => void fetchTemplate()}>
            {t.common.retry}
          </Button>
        </Card>
      ) : null}

      {loading && !template ? (
        <Card>
          <Skeleton className="h-8 w-64" />
          <Skeleton className="mt-3 h-4 w-72" />
          <Skeleton className="mt-4 h-[360px] w-full" />
        </Card>
      ) : null}

      {template ? (
        <>
          <Card>
            <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
              <div>
                <h1 className="text-2xl font-semibold tracking-tight text-text">{template.name}</h1>
                <p className="mt-1 font-mono text-xs text-muted">{template.id}</p>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <Badge tone={statusTone(template.status)}>{t.templates.statuses[template.status]}</Badge>
                <Badge tone="neutral">{t.templates.category}: {template.templateCategory}</Badge>
                <Badge tone={difficultyTone(template.difficultyLevel)}>{template.difficultyLevel}</Badge>
                <Badge tone="neutral">{t.templates.language}: {template.languageCode}</Badge>
              </div>
            </div>
          </Card>

          <Card>
            <div className="mb-2 flex items-center justify-between gap-2">
              <p className="text-xs uppercase tracking-[0.12em] text-muted">{t.templates.emailSubject}</p>
              <Button
                variant="ghost"
                onClick={() => void handleCopyContent(template.emailSubject, t.templates.subjectCopied)}
              >
                <Copy className="mr-2 h-4 w-4" />
                {t.templates.copySubject}
              </Button>
            </div>
            <p className="font-mono text-sm text-text">
              {template.emailSubject?.trim() || t.templates.noEmailPreview}
            </p>
          </Card>

          <div className="grid gap-4 xl:grid-cols-2">
            <Card>
              <div className="mb-2 flex items-center justify-between gap-2">
                <p className="text-xs uppercase tracking-[0.12em] text-muted">{t.templates.emailPreview}</p>
                <div className="flex items-center gap-2">
                  <Button
                    variant="ghost"
                    onClick={() => void handleCopyContent(template.emailBody, t.templates.emailHtmlCopied)}
                  >
                    <Copy className="mr-2 h-4 w-4" />
                    {t.templates.copyEmailHtml}
                  </Button>
                  <Button
                    variant="ghost"
                    onClick={() =>
                      handleDownloadContent(
                        template.emailBody,
                        buildHtmlFileName(template, "email"),
                        t.templates.emailHtmlDownloaded
                      )
                    }
                  >
                    <Download className="mr-2 h-4 w-4" />
                    {t.templates.downloadEmailHtml}
                  </Button>
                </div>
              </div>

              {template.emailBody?.trim() ? (
                <>
                  <iframe
                    title={`${template.id}-email-page-preview`}
                    srcDoc={asPreviewDocument(template.emailBody)}
                    sandbox="allow-forms allow-popups"
                    className="h-[520px] w-full rounded-lg border border-border bg-white"
                  />
                  <details className="mt-3 rounded-lg border border-border bg-surface/40 p-2">
                    <summary className="cursor-pointer text-xs uppercase tracking-[0.1em] text-muted">
                      {t.templates.rawCode}
                    </summary>
                    <pre className="mt-2 max-h-72 overflow-auto whitespace-pre-wrap break-words rounded bg-black/20 p-2 text-[11px] text-text">
                      {template.emailBody}
                    </pre>
                  </details>
                </>
              ) : (
                <p className="text-sm text-muted">{t.templates.noEmailPreview}</p>
              )}
            </Card>

            <Card>
              <div className="mb-2 flex items-center justify-between gap-2">
                <p className="text-xs uppercase tracking-[0.12em] text-muted">{t.templates.landingPreview}</p>
                <div className="flex items-center gap-2">
                  <Button
                    variant="ghost"
                    onClick={() => void handleCopyContent(template.landingPageHtml, t.templates.landingHtmlCopied)}
                  >
                    <Copy className="mr-2 h-4 w-4" />
                    {t.templates.copyLandingHtml}
                  </Button>
                  <Button
                    variant="ghost"
                    onClick={() =>
                      handleDownloadContent(
                        template.landingPageHtml,
                        buildHtmlFileName(template, "landing"),
                        t.templates.landingHtmlDownloaded
                      )
                    }
                  >
                    <Download className="mr-2 h-4 w-4" />
                    {t.templates.downloadLandingHtml}
                  </Button>
                </div>
              </div>

              {template.landingPageHtml?.trim() ? (
                <>
                  <iframe
                    title={`${template.id}-landing-page-preview`}
                    srcDoc={asPreviewDocument(template.landingPageHtml)}
                    sandbox="allow-forms allow-popups"
                    className="h-[520px] w-full rounded-lg border border-border bg-white"
                  />
                  <details className="mt-3 rounded-lg border border-border bg-surface/40 p-2">
                    <summary className="cursor-pointer text-xs uppercase tracking-[0.1em] text-muted">
                      {t.templates.rawCode}
                    </summary>
                    <pre className="mt-2 max-h-72 overflow-auto whitespace-pre-wrap break-words rounded bg-black/20 p-2 text-[11px] text-text">
                      {template.landingPageHtml}
                    </pre>
                  </details>
                </>
              ) : (
                <p className="text-sm text-muted">{t.templates.noLandingPreview}</p>
              )}
            </Card>
          </div>
        </>
      ) : null}
    </div>
  );
}

