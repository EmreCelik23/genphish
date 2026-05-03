"use client";

import { useEffect, useState } from "react";

import { RequireAccess } from "@/components/layout/require-access";
import { Card } from "@/components/ui/card";
import { ApiClient } from "@/lib/api/client";
import { createApiServices } from "@/lib/api/services";
import type { PhishingTemplateResponse } from "@/lib/api/types";
import { useSettings } from "@/lib/settings/settings-context";

export default function TemplatesPage() {
  const { settings } = useSettings();
  const [templates, setTemplates] = useState<PhishingTemplateResponse[]>([]);

  useEffect(() => {
    if (!settings.companyId || !settings.apiToken) {
      return;
    }

    const run = async () => {
      const client = new ApiClient(settings);
      const services = createApiServices(client, settings.companyId);
      setTemplates(await services.templates.list());
    };

    run();
  }, [settings]);

  return (
    <RequireAccess>
      <div className="space-y-4">
        <h1 className="text-3xl font-semibold tracking-tight">Template Studio</h1>
        <Card>
          <p className="mb-4 text-sm text-muted">{templates.length} template</p>
          <div className="space-y-3">
            {templates.map((item) => (
              <div key={item.id} className="rounded-xl border border-border bg-surface/50 p-3">
                <p className="text-sm font-medium text-text">{item.name}</p>
                <p className="mt-1 text-xs text-muted">
                  {item.templateCategory} • {item.status}
                </p>
              </div>
            ))}
          </div>
        </Card>
      </div>
    </RequireAccess>
  );
}
