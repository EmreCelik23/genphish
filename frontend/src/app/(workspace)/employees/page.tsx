"use client";

import { useEffect, useState } from "react";

import { RequireAccess } from "@/components/layout/require-access";
import { Card } from "@/components/ui/card";
import { ApiClient } from "@/lib/api/client";
import { createApiServices } from "@/lib/api/services";
import type { EmployeeResponse } from "@/lib/api/types";
import { useSettings } from "@/lib/settings/settings-context";

export default function EmployeesPage() {
  const { settings } = useSettings();
  const [employees, setEmployees] = useState<EmployeeResponse[]>([]);

  useEffect(() => {
    if (!settings.companyId || !settings.apiToken) {
      return;
    }

    const run = async () => {
      const client = new ApiClient(settings);
      const services = createApiServices(client, settings.companyId);
      setEmployees(await services.employees.list());
    };

    run();
  }, [settings]);

  return (
    <RequireAccess>
      <div className="space-y-4">
        <h1 className="text-3xl font-semibold tracking-tight">Employees</h1>
        <Card>
          <p className="mb-4 text-sm text-muted">{employees.length} employee</p>
          <div className="space-y-3">
            {employees.map((item) => (
              <div key={item.id} className="flex items-center justify-between rounded-xl border border-border bg-surface/50 p-3">
                <div>
                  <p className="text-sm font-medium text-text">
                    {item.firstName} {item.lastName}
                  </p>
                  <p className="text-xs text-muted">{item.email}</p>
                </div>
                <span className="rounded-md border border-border px-2 py-1 font-mono text-xs text-muted">
                  {item.department}
                </span>
              </div>
            ))}
          </div>
        </Card>
      </div>
    </RequireAccess>
  );
}
