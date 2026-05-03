import { ApiClient } from "@/lib/api/client";
import { CampaignResponse, DashboardResponse, EmployeeResponse, PhishingTemplateResponse } from "@/lib/api/types";

export function createApiServices(client: ApiClient, companyId: string) {
  const companyPrefix = `/api/v1/companies/${companyId}`;

  return {
    dashboard: {
      get: () => client.get<DashboardResponse>(`${companyPrefix}/analytics/dashboard`)
    },
    campaigns: {
      list: () => client.get<CampaignResponse[]>(`${companyPrefix}/campaigns`)
    },
    templates: {
      list: () => client.get<PhishingTemplateResponse[]>(`${companyPrefix}/templates`)
    },
    employees: {
      list: () => client.get<EmployeeResponse[]>(`${companyPrefix}/employees`)
    }
  };
}
