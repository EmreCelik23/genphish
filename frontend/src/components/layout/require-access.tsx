"use client";

import Link from "next/link";

import { Card } from "@/components/ui/card";
import { useI18n } from "@/lib/i18n/i18n-context";
import { useSettings } from "@/lib/settings/settings-context";

export function RequireAccess({ children }: { children: React.ReactNode }) {
  const { settings } = useSettings();
  const { t } = useI18n();

  if (!settings.apiToken || !settings.companyId) {
    return (
      <Card className="max-w-xl">
        <p className="text-base font-medium text-text">{t.common.notConfigured}</p>
        <Link href="/access" className="mt-3 inline-block text-sm text-accent underline-offset-2 hover:underline">
          {t.common.goAccess}
        </Link>
      </Card>
    );
  }

  return <>{children}</>;
}
