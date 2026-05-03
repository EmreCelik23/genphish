import { ApiClient } from "@/lib/api/client";
import {
  CampaignResponse,
  CreateCampaignRequest,
  DashboardResponse,
  EmployeeResponse,
  GenerateTemplateRequest,
  PhishingTemplateResponse
} from "@/lib/api/types";

export function createApiServices(client: ApiClient, companyId: string) {
  const companyPrefix = `/api/v1/companies/${companyId}`;

  return {
    dashboard: {
      get: () => client.get<DashboardResponse>(`${companyPrefix}/analytics/dashboard`)
    },
    campaigns: {
      list: () => client.get<CampaignResponse[]>(`${companyPrefix}/campaigns`),
      create: (payload: CreateCampaignRequest) => client.post<CampaignResponse>(`${companyPrefix}/campaigns`, payload)
    },
    templates: {
      list: () => client.get<PhishingTemplateResponse[]>(`${companyPrefix}/templates`),
      generate: (payload: GenerateTemplateRequest) =>
        client.post<PhishingTemplateResponse>(`${companyPrefix}/templates/generate`, payload)
    },
    employees: {
      list: () => client.get<EmployeeResponse[]>(`${companyPrefix}/employees`)
    }
  };
}
