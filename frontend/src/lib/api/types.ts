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

export type CampaignResponse = {
  id: string;
  companyId: string;
  name: string;
  targetingType: "ALL_COMPANY" | "DEPARTMENT" | "INDIVIDUAL" | "HIGH_RISK";
  targetDepartment?: string;
  templateId: string;
  qrCodeEnabled: boolean;
  status: "DRAFT" | "GENERATING" | "READY" | "SCHEDULED" | "IN_PROGRESS" | "COMPLETED" | "FAILED" | "CANCELED";
  scheduledFor?: string;
  createdAt: string;
};

export type PhishingTemplateResponse = {
  id: string;
  companyId: string;
  name: string;
  category: string;
  templateCategory: "CREDENTIAL_HARVESTING" | "CLICK_ONLY" | "MALWARE_DELIVERY" | "OAUTH_CONSENT";
  type: "STATIC" | "AI_GENERATED";
  status: "GENERATING" | "READY" | "FAILED";
  difficultyLevel: "AMATEUR" | "PROFESSIONAL";
  languageCode: "TR" | "EN";
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
