package com.genphish.campaign.service;

import com.genphish.campaign.dto.response.CampaignFunnelResponse;
import com.genphish.campaign.dto.response.DashboardResponse;
import com.genphish.campaign.dto.response.TrackingEventResponse;

import java.util.List;
import java.util.UUID;

public interface AnalyticsService {

    // 3-Dimensional Risk Matrix: company overview + department stats + campaign stats
    DashboardResponse getDashboard(UUID companyId);

    CampaignFunnelResponse getCampaignFunnel(UUID companyId, UUID campaignId);

    List<TrackingEventResponse> getCampaignEvents(UUID companyId, UUID campaignId);
}
