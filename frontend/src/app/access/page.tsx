"use client";

import { useRouter } from "next/navigation";

import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { useI18n } from "@/lib/i18n/i18n-context";
import { useSettings } from "@/lib/settings/settings-context";

export default function AccessPage() {
  const router = useRouter();
  const { settings, setSettings } = useSettings();
  const { t } = useI18n();

  return (
    <div className="grid min-h-screen place-items-center p-6">
      <Card className="w-full max-w-xl">
        <p className="text-2xl font-semibold tracking-tight">{t.access.title}</p>
        <p className="mt-1 text-sm text-muted">{t.access.subtitle}</p>

        <div className="mt-6 space-y-4">
          <div>
            <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">API Base URL</label>
            <Input
              value={settings.apiBaseUrl}
              onChange={(event) => setSettings({ apiBaseUrl: event.target.value })}
              placeholder="http://localhost:8088"
            />
          </div>

          <div>
            <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">API Token</label>
            <Input
              type="password"
              value={settings.apiToken}
              onChange={(event) => setSettings({ apiToken: event.target.value })}
              placeholder="INTERNAL_SERVICE_TOKEN"
            />
          </div>

          <div>
            <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">Company ID</label>
            <Input
              value={settings.companyId}
              onChange={(event) => setSettings({ companyId: event.target.value })}
              placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
            />
          </div>

          <Button className="w-full" onClick={() => router.push("/dashboard")}>
            {t.access.cta}
          </Button>
        </div>
      </Card>
    </div>
  );
}
