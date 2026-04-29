package com.genphish.campaign.controller;

import com.genphish.campaign.dto.response.DashboardResponse;
import com.genphish.campaign.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
