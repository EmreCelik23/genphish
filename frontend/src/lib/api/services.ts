import { ApiClient } from "@/lib/api/client";
import {
  CampaignResponse,
  CreateCampaignRequest,
  CreateEmployeeRequest,
  DashboardResponse,
  EmployeeResponse,
  GenerateTemplateRequest,
  ImportResultResponse,
  PhishingTemplateResponse,
  RegenerateTemplateRequest,
  ScheduleCampaignRequest,
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
      cancel: (campaignId: string) => client.post<CampaignResponse>(`${companyPrefix}/campaigns/${campaignId}/cancel`)
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
      deactivate: (employeeId: string) => client.delete(`${companyPrefix}/employees/${employeeId}`)
    }
  };
}
