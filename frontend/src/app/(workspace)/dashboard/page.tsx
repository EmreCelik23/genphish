"use client";

import { useEffect, useMemo, useState } from "react";
import { motion } from "framer-motion";
import { Activity, AlertTriangle, Gauge, Megaphone, ShieldCheck, Users } from "lucide-react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from "recharts";

import { RequireAccess } from "@/components/layout/require-access";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { ApiClient } from "@/lib/api/client";
import { createApiServices } from "@/lib/api/services";
import type { DashboardResponse } from "@/lib/api/types";
import { useI18n } from "@/lib/i18n/i18n-context";
import { useSettings } from "@/lib/settings/settings-context";

type BadgeTone = "neutral" | "success" | "warning" | "danger" | "info";

const itemVariants = {
  hidden: { opacity: 0, y: 10 },
  visible: { opacity: 1, y: 0 }
};

function clampPercent(value: number) {
  if (Number.isNaN(value)) return 0;
  return Math.max(0, Math.min(100, value));
}

function statusTone(status: string): BadgeTone {
  switch (status) {
    case "COMPLETED":
    case "READY":
      return "success";
    case "IN_PROGRESS":
    case "GENERATING":
      return "info";
    case "SCHEDULED":
      return "warning";
    case "FAILED":
    case "CANCELED":
      return "danger";
    default:
      return "neutral";
  }
}

function MetricCard({
  label,
  value,
  helper,
  icon: Icon
}: {
  label: string;
  value: string;
  helper: string;
  icon: React.ComponentType<{ className?: string }>;
}) {
  return (
    <Card>
      <div className="flex items-start justify-between">
        <div>
          <p className="text-[11px] uppercase tracking-[0.14em] text-muted">{label}</p>
          <p className="mt-3 text-3xl font-semibold tracking-tight text-text">{value}</p>
          <p className="mt-1 text-xs text-muted">{helper}</p>
        </div>
        <div className="rounded-lg border border-border bg-surface p-2 text-accent">
          <Icon className="h-4 w-4" />
        </div>
      </div>
    </Card>
  );
}

function ProgressRow({ label, value }: { label: string; value: number }) {
  return (
    <div>
      <div className="mb-1 flex items-center justify-between text-xs text-muted">
        <span>{label}</span>
        <span className="font-mono">{clampPercent(value).toFixed(1)}%</span>
      </div>
      <div className="h-2 rounded-full bg-[var(--panel-hover)]">
        <div
          className="h-full rounded-full bg-[linear-gradient(90deg,var(--accent),rgba(56,189,248,0.45))]"
          style={{ width: `${clampPercent(value)}%` }}
        />
      </div>
    </div>
  );
}

function DashboardSkeleton() {
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        {Array.from({ length: 4 }).map((_, index) => (
          <Card key={index}>
            <Skeleton className="h-3 w-24" />
            <Skeleton className="mt-4 h-8 w-24" />
            <Skeleton className="mt-3 h-3 w-36" />
          </Card>
        ))}
      </div>
      <div className="grid grid-cols-1 gap-4 xl:grid-cols-12">
        <Card className="xl:col-span-8">
          <Skeleton className="h-[320px] w-full" />
        </Card>
        <Card className="xl:col-span-4">
          <Skeleton className="h-[320px] w-full" />
        </Card>
      </div>
    </div>
  );
}

export default function DashboardPage() {
  const { settings } = useSettings();
  const { t } = useI18n();

  const [data, setData] = useState<DashboardResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  useEffect(() => {
    if (!settings.companyId || !settings.apiToken) {
      return;
    }

    let active = true;

    const fetchDashboard = async () => {
      setLoading(true);
      setError(null);
      try {
        const client = new ApiClient(settings);
        const services = createApiServices(client, settings.companyId);
        const response = await services.dashboard.get();
        if (active) {
          setData(response);
          setLastUpdated(new Date());
        }
      } catch (fetchError) {
        if (active) {
          const message = fetchError instanceof Error ? fetchError.message : "Unknown error";
          setError(message);
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };

    fetchDashboard();

    return () => {
      active = false;
    };
  }, [settings]);

  const departmentChart = useMemo(
    () =>
      (data?.departmentStats ?? [])
        .map((item) => ({
          department: item.department,
          phishingRate: Number(item.phishingRate.toFixed(2))
        }))
        .sort((a, b) => b.phishingRate - a.phishingRate)
        .slice(0, 8),
    [data]
  );

  const telemetryChart = useMemo(
    () =>
      (data?.recentCampaigns ?? []).slice(0, 8).reverse().map((campaign) => ({
        campaignName:
          campaign.campaignName.length > 16
            ? `${campaign.campaignName.slice(0, 16)}...`
            : campaign.campaignName,
        open: campaign.emailsOpened,
        click: campaign.linksClicked,
        submit: campaign.credentialsSubmitted
      })),
    [data]
  );

  const aggregate = useMemo(() => {
    const totals = (data?.recentCampaigns ?? []).reduce(
      (acc, item) => {
        acc.target += item.targetCount;
        acc.open += item.emailsOpened;
        acc.click += item.linksClicked;
        acc.submit += item.credentialsSubmitted;
        acc.actions += item.actionsTaken;
        return acc;
      },
      { target: 0, open: 0, click: 0, submit: 0, actions: 0 }
    );

    const safeRate = clampPercent(100 - (data?.overallPhishingRate ?? 0));
    const clickPressure = totals.target > 0 ? (totals.click / totals.target) * 100 : 0;
    const submitPressure = totals.target > 0 ? (totals.submit / totals.target) * 100 : 0;

    return {
      totals,
      safeRate,
      clickPressure,
      submitPressure
    };
  }, [data]);

  return (
    <RequireAccess>
      <motion.div initial="hidden" animate="visible" transition={{ staggerChildren: 0.06 }} className="space-y-4 lg:space-y-6">
        <motion.div variants={itemVariants}>
          <div className="flex flex-col gap-2 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <h1 className="text-3xl font-semibold tracking-tight">{t.dashboard.title}</h1>
              <p className="mt-1 text-sm text-muted">{t.dashboard.subtitle}</p>
            </div>
            <Badge tone="neutral" className="w-fit">
              {t.dashboard.snapshot} • {lastUpdated ? `${lastUpdated.toLocaleTimeString()} ${t.dashboard.updatedNow}` : t.common.loading}
            </Badge>
          </div>
        </motion.div>

        {error ? (
          <motion.div variants={itemVariants}>
            <Card className="border-rose-500/30">
              <p className="text-sm text-rose-300">{error}</p>
            </Card>
          </motion.div>
        ) : null}

        {loading && !data ? (
          <motion.div variants={itemVariants}>
            <DashboardSkeleton />
          </motion.div>
        ) : null}

        {data ? (
          <>
            <motion.div variants={itemVariants} className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
              <MetricCard
                label={t.dashboard.totalEmployees}
                value={String(data.totalEmployees)}
                helper={t.dashboard.riskOverview}
                icon={Users}
              />
              <MetricCard
                label={t.dashboard.totalCampaigns}
                value={String(data.totalCampaigns)}
                helper={t.dashboard.campaignTelemetry}
                icon={Megaphone}
              />
              <MetricCard
                label={t.dashboard.phishingRate}
                value={`%${clampPercent(data.overallPhishingRate).toFixed(1)}`}
                helper={t.dashboard.actionPressure}
                icon={AlertTriangle}
              />
              <MetricCard
                label={t.dashboard.safeRate}
                value={`%${aggregate.safeRate.toFixed(1)}`}
                helper={t.dashboard.riskOverview}
                icon={ShieldCheck}
              />
            </motion.div>

            <motion.div variants={itemVariants} className="grid grid-cols-1 gap-4 xl:grid-cols-12">
              <Card className="xl:col-span-8">
                <div className="mb-4 flex items-center justify-between">
                  <p className="text-sm font-medium text-text">{t.dashboard.departmentExposure}</p>
                  <Badge tone="info">
                    {departmentChart.length} {t.dashboard.departments}
                  </Badge>
                </div>
                {departmentChart.length ? (
                  <div className="h-[320px] w-full">
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={departmentChart}>
                        <CartesianGrid stroke="var(--border)" vertical={false} />
                        <XAxis dataKey="department" stroke="var(--muted)" tickLine={false} axisLine={false} />
                        <YAxis stroke="var(--muted)" tickLine={false} axisLine={false} />
                        <Tooltip
                          cursor={{ fill: "rgba(148,163,184,0.08)" }}
                          contentStyle={{
                            background: "var(--panel)",
                            border: "1px solid var(--border)",
                            borderRadius: 12,
                            color: "var(--text)"
                          }}
                        />
                        <Bar dataKey="phishingRate" fill="var(--accent)" radius={[8, 8, 0, 0]} />
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                ) : (
                  <p className="text-sm text-muted">{t.dashboard.noData}</p>
                )}
              </Card>

              <Card className="xl:col-span-4">
                <div className="mb-4 flex items-center justify-between">
                  <p className="text-sm font-medium text-text">{t.dashboard.actionPressure}</p>
                  <Gauge className="h-4 w-4 text-accent" />
                </div>

                <div className="space-y-4">
                  <div className="rounded-xl border border-border bg-surface/55 p-3">
                    <p className="text-xs uppercase tracking-[0.12em] text-muted">{t.dashboard.totalActions}</p>
                    <p className="mt-2 text-2xl font-semibold text-text">{aggregate.totals.actions}</p>
                  </div>

                  <ProgressRow label={t.dashboard.clickPressure} value={aggregate.clickPressure} />
                  <ProgressRow label={t.dashboard.submitPressure} value={aggregate.submitPressure} />
                </div>
              </Card>
            </motion.div>

            <motion.div variants={itemVariants} className="grid grid-cols-1 gap-4 xl:grid-cols-12">
              <Card className="xl:col-span-7">
                <div className="mb-4 flex items-center justify-between">
                  <p className="text-sm font-medium text-text">{t.dashboard.campaignTelemetry}</p>
                  <Activity className="h-4 w-4 text-accent" />
                </div>

                {telemetryChart.length ? (
                  <div className="h-[300px] w-full">
                    <ResponsiveContainer width="100%" height="100%">
                      <LineChart data={telemetryChart}>
                        <CartesianGrid stroke="var(--border)" vertical={false} />
                        <XAxis dataKey="campaignName" stroke="var(--muted)" tickLine={false} axisLine={false} />
                        <YAxis stroke="var(--muted)" tickLine={false} axisLine={false} />
                        <Tooltip
                          contentStyle={{
                            background: "var(--panel)",
                            border: "1px solid var(--border)",
                            borderRadius: 12,
                            color: "var(--text)"
                          }}
                        />
                        <Line type="monotone" dataKey="open" stroke="#38bdf8" strokeWidth={2.2} dot={false} />
                        <Line type="monotone" dataKey="click" stroke="#f59e0b" strokeWidth={2} dot={false} />
                        <Line type="monotone" dataKey="submit" stroke="#f87171" strokeWidth={2} dot={false} />
                      </LineChart>
                    </ResponsiveContainer>
                  </div>
                ) : (
                  <p className="text-sm text-muted">{t.dashboard.noCampaigns}</p>
                )}
              </Card>

              <Card className="xl:col-span-5">
                <p className="mb-4 text-sm font-medium text-text">{t.dashboard.recentCampaigns}</p>
                <div className="space-y-3">
                  {(data.recentCampaigns ?? []).slice(0, 6).map((campaign) => (
                    <div key={campaign.campaignId} className="rounded-xl border border-border bg-surface/50 p-3">
                      <div className="flex items-center justify-between gap-2">
                        <p className="truncate text-sm font-medium text-text">{campaign.campaignName}</p>
                        <Badge tone={statusTone(campaign.status)}>{campaign.status}</Badge>
                      </div>
                      <div className="mt-2 flex items-center justify-between text-xs text-muted">
                        <span>
                          {campaign.targetCount} {t.dashboard.targets}
                        </span>
                        <span className="font-mono">
                          %{clampPercent(campaign.successRate).toFixed(1)} {t.dashboard.success}
                        </span>
                      </div>
                    </div>
                  ))}
                  {!data.recentCampaigns?.length ? <p className="text-sm text-muted">{t.dashboard.noData}</p> : null}
                </div>
              </Card>
            </motion.div>
          </>
        ) : null}
      </motion.div>
    </RequireAccess>
  );
}
