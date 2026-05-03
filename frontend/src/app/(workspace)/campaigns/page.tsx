"use client";

import { useEffect, useState } from "react";

import { RequireAccess } from "@/components/layout/require-access";
import { Card } from "@/components/ui/card";
import { ApiClient } from "@/lib/api/client";
import { createApiServices } from "@/lib/api/services";
import type { CampaignResponse } from "@/lib/api/types";
import { useSettings } from "@/lib/settings/settings-context";

export default function CampaignsPage() {
  const { settings } = useSettings();
  const [campaigns, setCampaigns] = useState<CampaignResponse[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!settings.companyId || !settings.apiToken) {
      return;
    }

    const run = async () => {
      setLoading(true);
      try {
        const client = new ApiClient(settings);
        const services = createApiServices(client, settings.companyId);
        setCampaigns(await services.campaigns.list());
      } finally {
        setLoading(false);
      }
    };

    run();
  }, [settings]);

  return (
    <RequireAccess>
      <div className="space-y-4">
        <h1 className="text-3xl font-semibold tracking-tight">Campaigns</h1>
        <Card>
          <p className="mb-4 text-sm text-muted">{loading ? "Loading..." : `${campaigns.length} campaign`}</p>
          <div className="space-y-3">
            {campaigns.map((item) => (
              <div key={item.id} className="flex items-center justify-between rounded-xl border border-border bg-surface/50 p-3">
                <div>
                  <p className="text-sm font-medium text-text">{item.name}</p>
                  <p className="text-xs text-muted">{item.targetingType}</p>
                </div>
                <span className="rounded-md border border-border px-2 py-1 text-xs text-muted">{item.status}</span>
              </div>
            ))}
          </div>
        </Card>
      </div>
    </RequireAccess>
  );
}
