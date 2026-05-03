"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { LogOut, Save } from "lucide-react";

import { Avatar } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { useToast } from "@/components/ui/toast";
import { useAuth } from "@/lib/auth/auth-context";
import { useI18n } from "@/lib/i18n/i18n-context";
import { useSettings } from "@/lib/settings/settings-context";

export default function SettingsPage() {
  const { t } = useI18n();
  const { settings, setSettings, resetSettings } = useSettings();
  const { auth, logout } = useAuth();
  const { toast } = useToast();
  const router = useRouter();

  // Local draft for API URL (only persisted on Save)
  const [apiUrlDraft, setApiUrlDraft] = useState(settings.apiBaseUrl);
  const [apiUrlError, setApiUrlError] = useState<string | null>(null);

  const roleLabel = auth.role === "admin"
    ? t.auth.roleAdmin
    : auth.role === "operator"
      ? t.auth.roleOperator
      : t.auth.roleViewer;

  const roleTone = auth.role === "admin"
    ? "info" as const
    : auth.role === "operator"
      ? "success" as const
      : "neutral" as const;

  const handleLogout = () => {
    logout();
    router.push("/access");
  };

  const handleSaveApiUrl = () => {
    const trimmed = apiUrlDraft.trim();
    if (!trimmed) {
      setApiUrlError(t.validation.required);
      return;
    }
    try {
      new URL(trimmed);
    } catch {
      setApiUrlError(t.validation.invalidUrl);
      return;
    }
    setApiUrlError(null);
    setSettings({ apiBaseUrl: trimmed });
    toast(t.settings.saveSuccess, "success");
  };

  const handleReset = () => {
    resetSettings();
    setApiUrlDraft("");
    setApiUrlError(null);
    toast(t.settings.saveSuccess, "info");
  };

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
          <FormField
            label={t.settings.apiBaseUrl}
            hint={t.settings.apiUrlHint}
            error={apiUrlError ?? undefined}
          >
            <Input
              value={apiUrlDraft}
              onChange={(e) => {
                setApiUrlDraft(e.target.value);
                if (apiUrlError) setApiUrlError(null);
              }}
              placeholder="http://localhost:8088"
            />
          </FormField>
          <div className="mt-3">
            <Button onClick={handleSaveApiUrl} className="gap-2">
              <Save className="h-4 w-4" />
              {t.settings.save}
            </Button>
          </div>
        </Card>
      </div>

      {/* Session info card */}
      {auth.isAuthenticated ? (
        <Card>
          <p className="text-sm font-medium text-text">{t.settings.sessionTitle}</p>
          <div className="mt-4 space-y-3">
            <div className="flex items-center gap-3">
              <Avatar name={auth.companyName || "?"} size="lg" />
              <div>
                <p className="text-sm font-medium text-text">{auth.companyName}</p>
                <p className="font-mono text-[10px] text-muted">{auth.companyId}</p>
              </div>
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              <div className="rounded-lg border border-border bg-surface/50 p-3">
                <p className="text-[10px] uppercase tracking-[0.14em] text-muted">{t.settings.sessionRole}</p>
                <Badge tone={roleTone} className="mt-1.5">
                  {roleLabel}
                </Badge>
              </div>
              <div className="rounded-lg border border-border bg-surface/50 p-3">
                <p className="text-[10px] uppercase tracking-[0.14em] text-muted">{t.settings.sessionExpiry}</p>
                <p className="mt-1.5 text-sm text-text">
                  {auth.expiresAt
                    ? new Date(auth.expiresAt).toLocaleString()
                    : t.settings.sessionNeverExpires}
                </p>
              </div>
            </div>

            <Button variant="danger" onClick={handleLogout} className="gap-2">
              <LogOut className="h-4 w-4" />
              {t.settings.sessionLogout}
            </Button>
          </div>
        </Card>
      ) : null}

      <Button variant="ghost" onClick={handleReset}>
        {t.settings.reset}
      </Button>
    </div>
  );
}
