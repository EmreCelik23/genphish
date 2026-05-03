import type { Language } from "@/lib/settings/types";

export const dictionaries = {
  tr: {
    appName: "GenPhish Console",
    nav: {
      dashboard: "Dashboard",
      campaigns: "Kampanyalar",
      templates: "Template Studio",
      employees: "Çalışanlar",
      settings: "Ayarlar",
      access: "Erişim"
    },
    common: {
      loading: "Yükleniyor...",
      save: "Kaydet",
      cancel: "İptal",
      notConfigured: "Önce erişim bilgilerini tanımla",
      goAccess: "Erişim ayarlarına git"
    },
    dashboard: {
      title: "Risk Operasyon Paneli",
      subtitle: "Şirket risk durumunu canlı KPI'larla izle.",
      totalEmployees: "Toplam Çalışan",
      totalCampaigns: "Toplam Kampanya",
      activeCampaigns: "Aktif Kampanya",
      phishingRate: "Genel Oltalama Oranı",
      safeRate: "Güvenli Davranış Oranı",
      snapshot: "Anlık görünüm",
      updatedNow: "şimdi güncellendi",
      riskOverview: "Risk Özeti",
      actionPressure: "Aksiyon Baskısı",
      departmentExposure: "Departman Maruziyeti",
      campaignTelemetry: "Kampanya Telemetrisi",
      recentCampaigns: "Son Kampanyalar",
      totalActions: "Toplam riskli aksiyon",
      clickPressure: "Tıklama baskısı",
      submitPressure: "Form gönderimi baskısı",
      departments: "departman",
      targets: "hedef",
      success: "başarı",
      noData: "Henüz dashboard verisi yok",
      noCampaigns: "Henüz kampanya aktivitesi yok"
    },
    settings: {
      title: "Tercihler",
      subtitle: "Tema, dil ve API erişim ayarları",
      theme: "Tema",
      language: "Dil",
      density: "Yoğunluk",
      apiBaseUrl: "API Base URL",
      apiToken: "API Token",
      companyId: "Company ID",
      reset: "Varsayılana dön"
    },
    access: {
      title: "Erişim Kurulumu",
      subtitle: "API token ve company id girerek çalışma alanını aktif et.",
      cta: "Panele geç"
    }
  },
  en: {
    appName: "GenPhish Console",
    nav: {
      dashboard: "Dashboard",
      campaigns: "Campaigns",
      templates: "Template Studio",
      employees: "Employees",
      settings: "Settings",
      access: "Access"
    },
    common: {
      loading: "Loading...",
      save: "Save",
      cancel: "Cancel",
      notConfigured: "Configure access settings first",
      goAccess: "Go to access settings"
    },
    dashboard: {
      title: "Risk Operations Panel",
      subtitle: "Monitor company exposure with live KPI cards.",
      totalEmployees: "Total Employees",
      totalCampaigns: "Total Campaigns",
      activeCampaigns: "Active Campaigns",
      phishingRate: "Overall Phishing Rate",
      safeRate: "Safe Behavior Rate",
      snapshot: "Live snapshot",
      updatedNow: "updated now",
      riskOverview: "Risk Overview",
      actionPressure: "Action Pressure",
      departmentExposure: "Department Exposure",
      campaignTelemetry: "Campaign Telemetry",
      recentCampaigns: "Recent Campaigns",
      totalActions: "Total risky actions",
      clickPressure: "Click pressure",
      submitPressure: "Submit pressure",
      departments: "departments",
      targets: "targets",
      success: "success",
      noData: "No dashboard data yet",
      noCampaigns: "No campaign activity yet"
    },
    settings: {
      title: "Preferences",
      subtitle: "Theme, language, and API access settings",
      theme: "Theme",
      language: "Language",
      density: "Density",
      apiBaseUrl: "API Base URL",
      apiToken: "API Token",
      companyId: "Company ID",
      reset: "Reset defaults"
    },
    access: {
      title: "Access Setup",
      subtitle: "Enter API token and company id to activate workspace.",
      cta: "Go to workspace"
    }
  }
} as const;

export function getDictionary(language: Language) {
  return dictionaries[language];
}

export type Dictionary = ReturnType<typeof getDictionary>;
