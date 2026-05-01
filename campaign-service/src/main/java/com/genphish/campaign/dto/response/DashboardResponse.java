package com.genphish.campaign.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DashboardResponse {

    // Company-wide overview
    private long totalEmployees;
    private long totalCampaigns;
    private long activeCampaigns;
    private double overallPhishingRate; // % of employees who fell for at least one simulation

    // Department-level risk breakdown
    private List<DepartmentStats> departmentStats;

    // Recent campaigns summary
    private List<CampaignStats> recentCampaigns;

    @Data
    @Builder
    public static class DepartmentStats {
        private String department;
        private long employeeCount;
        private double phishingRate;       // % who clicked or submitted credentials
        private long emailsOpened;
        private long linksClicked;
        private long credentialsSubmitted;
        private long downloadTriggered;
        private long consentGranted;
        private long actionsTaken;
    }

    @Data
    @Builder
    public static class CampaignStats {
        private String campaignId;
        private String campaignName;
        private String status;
        private long targetCount;
        private long emailsOpened;
        private long linksClicked;
        private long credentialsSubmitted;
        private long downloadTriggered;
        private long consentGranted;
        private long actionsTaken;
        private double successRate; // % who did NOT fall for it
    }
}
