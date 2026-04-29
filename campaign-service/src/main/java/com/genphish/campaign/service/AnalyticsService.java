package com.genphish.campaign.service;

import com.genphish.campaign.dto.response.DashboardResponse;

import java.util.UUID;

public interface AnalyticsService {

    // 3-Dimensional Risk Matrix: company overview + department stats + campaign stats
    DashboardResponse getDashboard(UUID companyId);
}
