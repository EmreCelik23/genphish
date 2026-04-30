package com.genphish.campaign.controller;

import com.genphish.campaign.dto.response.CampaignFunnelResponse;
import com.genphish.campaign.dto.response.DashboardResponse;
import com.genphish.campaign.dto.response.TrackingEventResponse;
import com.genphish.campaign.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/analytics")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    // GET /api/v1/companies/{companyId}/analytics/dashboard
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard(@PathVariable UUID companyId) {
        log.info("Received request to fetch analytics dashboard for company: {}", companyId);
        DashboardResponse response = analyticsService.getDashboard(companyId);
        log.info("Successfully fetched analytics dashboard for company: {}", companyId);
        return ResponseEntity.ok(response);
    }

    // GET /api/v1/companies/{companyId}/analytics/campaigns/{campaignId}/funnel
    @GetMapping("/campaigns/{campaignId}/funnel")
    public ResponseEntity<CampaignFunnelResponse> getCampaignFunnel(
            @PathVariable UUID companyId,
            @PathVariable UUID campaignId) {
        log.info("Received request to fetch funnel for campaign: {} company: {}", campaignId, companyId);
        CampaignFunnelResponse response = analyticsService.getCampaignFunnel(companyId, campaignId);
        return ResponseEntity.ok(response);
    }

    // GET /api/v1/companies/{companyId}/analytics/campaigns/{campaignId}/events
    @GetMapping("/campaigns/{campaignId}/events")
    public ResponseEntity<List<TrackingEventResponse>> getCampaignEvents(
            @PathVariable UUID companyId,
            @PathVariable UUID campaignId) {
        log.info("Received request to fetch events for campaign: {} company: {}", campaignId, companyId);
        List<TrackingEventResponse> response = analyticsService.getCampaignEvents(companyId, campaignId);
        return ResponseEntity.ok(response);
    }
}
