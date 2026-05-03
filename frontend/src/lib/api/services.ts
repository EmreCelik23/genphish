import { ApiClient } from "@/lib/api/client";
import {
  CampaignFunnelResponse,
  CampaignResponse,
  CompanyResponse,
  CreateCampaignRequest,
  CreateCompanyRequest,
  CreateEmployeeRequest,
  DashboardResponse,
  EmployeeResponse,
  EmployeeRiskProfileResponse,
  GenerateTemplateRequest,
  ImportResultResponse,
  PhishingTemplateResponse,
  RegenerateTemplateRequest,
  ScheduleCampaignRequest,
  TrackingEventResponse,
  UploadReferenceImageResponse
} from "@/lib/api/types";

export function createApiServices(client: ApiClient, companyId: string) {
  const companyPrefix = `/api/v1/companies/${companyId}`;

  return {
    dashboard: {
      get: () => client.get<DashboardResponse>(`${companyPrefix}/analytics/dashboard`)
    },
    campaigns: {
      list: () => client.get<CampaignResponse[]>(`${companyPrefix}/campaigns`),
      create: (payload: CreateCampaignRequest) => client.post<CampaignResponse>(`${companyPrefix}/campaigns`, payload),
      start: (campaignId: string) => client.post<CampaignResponse>(`${companyPrefix}/campaigns/${campaignId}/start`),
      schedule: (campaignId: string, payload: ScheduleCampaignRequest) =>
        client.post<CampaignResponse>(`${companyPrefix}/campaigns/${campaignId}/schedule`, payload),
      cancel: (campaignId: string) => client.post<CampaignResponse>(`${companyPrefix}/campaigns/${campaignId}/cancel`),
      delete: (campaignId: string) => client.delete(`${companyPrefix}/campaigns/${campaignId}`)
    },
    analytics: {
      campaignFunnel: (campaignId: string) =>
        client.get<CampaignFunnelResponse>(`${companyPrefix}/analytics/campaigns/${campaignId}/funnel`),
      campaignEvents: (campaignId: string) =>
        client.get<TrackingEventResponse[]>(`${companyPrefix}/analytics/campaigns/${campaignId}/events`)
    },
    templates: {
      list: () => client.get<PhishingTemplateResponse[]>(`${companyPrefix}/templates`),
      generate: (payload: GenerateTemplateRequest) =>
        client.post<PhishingTemplateResponse>(`${companyPrefix}/templates/generate`, payload),
      regenerate: (templateId: string, payload: RegenerateTemplateRequest) =>
        client.post<PhishingTemplateResponse>(`${companyPrefix}/templates/${templateId}/regenerate`, payload),
      uploadReference: async (file: File) => {
        const formData = new FormData();
        formData.append("file", file);
        return client.postForm<UploadReferenceImageResponse>(`${companyPrefix}/templates/upload-reference`, formData);
      }
    },
    employees: {
      list: () => client.get<EmployeeResponse[]>(`${companyPrefix}/employees`),
      create: (payload: CreateEmployeeRequest) => client.post<EmployeeResponse>(`${companyPrefix}/employees`, payload),
      import: async (file: File) => {
        const formData = new FormData();
        formData.append("file", file);
        return client.postForm<ImportResultResponse>(`${companyPrefix}/employees/import`, formData);
      },
      deactivate: (employeeId: string) => client.delete(`${companyPrefix}/employees/${employeeId}`),
      riskProfile: (employeeId: string) =>
        client.get<EmployeeRiskProfileResponse>(`${companyPrefix}/employees/${employeeId}/risk-profile`)
    }
  };
}

export function createGlobalApiServices(client: ApiClient) {
  return {
    companies: {
      list: () => client.get<CompanyResponse[]>("/api/v1/companies"),
      create: (payload: CreateCompanyRequest) => client.post<CompanyResponse>("/api/v1/companies", payload),
      getById: (companyId: string) => client.get<CompanyResponse>(`/api/v1/companies/${companyId}`)
    }
  };
}
