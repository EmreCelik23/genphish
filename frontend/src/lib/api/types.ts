export type DashboardResponse = {
  totalEmployees: number;
  totalCampaigns: number;
  activeCampaigns: number;
  overallPhishingRate: number;
  departmentStats: {
    department: string;
    employeeCount: number;
    phishingRate: number;
    emailsOpened: number;
    linksClicked: number;
    credentialsSubmitted: number;
    downloadTriggered: number;
    consentGranted: number;
    actionsTaken: number;
  }[];
  recentCampaigns: {
    campaignId: string;
    campaignName: string;
    status: string;
    targetCount: number;
    emailsOpened: number;
    linksClicked: number;
    credentialsSubmitted: number;
    downloadTriggered: number;
    consentGranted: number;
    actionsTaken: number;
    successRate: number;
  }[];
};

export type CampaignTargetingType = "ALL_COMPANY" | "DEPARTMENT" | "INDIVIDUAL" | "HIGH_RISK";
export type CampaignStatus = "DRAFT" | "GENERATING" | "READY" | "SCHEDULED" | "IN_PROGRESS" | "COMPLETED" | "FAILED" | "CANCELED";
export type TemplateCategory = "CREDENTIAL_HARVESTING" | "CLICK_ONLY" | "MALWARE_DELIVERY" | "OAUTH_CONSENT";
export type TemplateStatus = "GENERATING" | "READY" | "FAILED";
export type TemplateType = "STATIC" | "AI_GENERATED";
export type DifficultyLevel = "AMATEUR" | "PROFESSIONAL";
export type LanguageCode = "TR" | "EN";
export type AiProvider = "openai" | "anthropic" | "gemini" | "stub";

export type CampaignResponse = {
  id: string;
  companyId: string;
  name: string;
  targetingType: CampaignTargetingType;
  targetDepartment?: string;
  templateId: string;
  qrCodeEnabled: boolean;
  status: CampaignStatus;
  scheduledFor?: string;
  createdAt: string;
};

export type PhishingTemplateResponse = {
  id: string;
  companyId: string;
  name: string;
  category: string;
  templateCategory: TemplateCategory;
  type: TemplateType;
  status: TemplateStatus;
  difficultyLevel: DifficultyLevel;
  languageCode: LanguageCode;
  emailSubject?: string;
  emailBody?: string;
  landingPageHtml?: string;
  prompt?: string;
  targetUrl?: string;
  referenceImageUrl?: string;
  fallbackContentUsed: boolean;
};

export type EmployeeResponse = {
  id: string;
  companyId: string;
  firstName: string;
  lastName: string;
  email: string;
  department: string;
  riskScore: number;
  active: boolean;
  createdAt: string;
};

export type CreateCampaignRequest = {
  name: string;
  targetingType: CampaignTargetingType;
  targetDepartment?: string;
  targetEmployeeIds?: string[];
  templateId: string;
  qrCodeEnabled: boolean;
};

export type GenerateTemplateRequest = {
  name: string;
  category?: string;
  prompt: string;
  targetUrl?: string;
  templateCategory: TemplateCategory;
  referenceImageUrl?: string;
  languageCode: LanguageCode;
  difficultyLevel: DifficultyLevel;
  aiProvider?: AiProvider;
  aiModel?: string;
  allowFallbackTemplate?: boolean;
};

export type ScheduleCampaignRequest = {
  scheduledFor: string;
};

export type RegenerationScope = "ALL" | "ONLY_EMAIL" | "ONLY_LANDING_PAGE";

export type RegenerateTemplateRequest = {
  prompt: string;
  scope: RegenerationScope;
  aiProvider?: AiProvider;
  aiModel?: string;
  templateCategory?: TemplateCategory;
  referenceImageUrl?: string;
};

export type UploadReferenceImageResponse = {
  referenceImageUrl: string;
};

export type CreateEmployeeRequest = {
  firstName: string;
  lastName: string;
  email: string;
  department: string;
};

export type ImportResultResponse = {
  totalRows: number;
  imported: number;
  duplicates: number;
  failed: number;
};
