"use client";

import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { useI18n } from "@/lib/i18n/i18n-context";
import { useSettings } from "@/lib/settings/settings-context";

export default function SettingsPage() {
  const { t } = useI18n();
  const { settings, setSettings, resetSettings } = useSettings();

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-semibold tracking-tight">{t.settings.title}</h1>
        <p className="mt-1 text-sm text-muted">{t.settings.subtitle}</p>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <Card>
          <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.settings.theme}</label>
          <Select
            value={settings.theme}
            onChange={(event) =>
              setSettings({ theme: event.target.value as "dark" | "light" | "system" })
            }
          >
            <option value="system">System</option>
            <option value="dark">Dark</option>
            <option value="light">Light</option>
          </Select>
        </Card>

        <Card>
          <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.settings.language}</label>
          <Select value={settings.language} onChange={(event) => setSettings({ language: event.target.value as "tr" | "en" })}>
            <option value="tr">TR</option>
            <option value="en">EN</option>
          </Select>
        </Card>

        <Card>
          <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.settings.density}</label>
          <Select
            value={settings.density}
            onChange={(event) =>
              setSettings({ density: event.target.value as "comfortable" | "compact" })
            }
          >
            <option value="comfortable">Comfortable</option>
            <option value="compact">Compact</option>
          </Select>
        </Card>

        <Card>
          <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.settings.apiBaseUrl}</label>
          <Input value={settings.apiBaseUrl} onChange={(event) => setSettings({ apiBaseUrl: event.target.value })} />
        </Card>

        <Card>
          <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.settings.apiToken}</label>
          <Input
            type="password"
            value={settings.apiToken}
            onChange={(event) => setSettings({ apiToken: event.target.value })}
            placeholder="INTERNAL_SERVICE_TOKEN"
          />
        </Card>

        <Card>
          <label className="mb-2 block text-xs uppercase tracking-[0.12em] text-muted">{t.settings.companyId}</label>
          <Input
            value={settings.companyId}
            onChange={(event) => setSettings({ companyId: event.target.value })}
            placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
          />
        </Card>
      </div>

      <Button variant="ghost" onClick={resetSettings}>
        {t.settings.reset}
      </Button>
    </div>
  );
}
