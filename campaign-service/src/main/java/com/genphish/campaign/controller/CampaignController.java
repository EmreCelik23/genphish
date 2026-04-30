package com.genphish.campaign.controller;

import com.genphish.campaign.dto.request.CreateCampaignRequest;
import com.genphish.campaign.dto.request.ScheduleCampaignRequest;
import com.genphish.campaign.dto.response.CampaignResponse;
import com.genphish.campaign.service.CampaignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/campaigns")
@RequiredArgsConstructor
@Slf4j
public class CampaignController {

    private final CampaignService campaignService;

    // Create Campaign
    @PostMapping
    public ResponseEntity<CampaignResponse> createCampaign(
            @PathVariable UUID companyId,
            @Valid @RequestBody CreateCampaignRequest request) {
        log.info("Received request to create campaign for company: {}", companyId);
        CampaignResponse response = campaignService.createCampaign(companyId, request);
        log.info("Successfully created campaign: {} for company: {}", response.getId(), companyId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // Get All Campaigns
    @GetMapping
    public ResponseEntity<List<CampaignResponse>> getAllCampaigns(@PathVariable UUID companyId) {
        log.info("Fetching all campaigns for company: {}", companyId);
        return ResponseEntity.ok(campaignService.getAllCampaigns(companyId));
    }

    // Get Campaign by ID
    @GetMapping("/{campaignId}")
    public ResponseEntity<CampaignResponse> getCampaignById(
            @PathVariable UUID companyId,
            @PathVariable UUID campaignId) {
        log.info("Fetching campaign: {} for company: {}", campaignId, companyId);
        return ResponseEntity.ok(campaignService.getCampaignById(companyId, campaignId));
    }

    // Start Campaign
    @PostMapping("/{campaignId}/start")
    public ResponseEntity<CampaignResponse> startCampaign(
            @PathVariable UUID companyId,
            @PathVariable UUID campaignId) {
        log.info("Received request to start campaign: {} for company: {}", campaignId, companyId);
        CampaignResponse response = campaignService.startCampaign(companyId, campaignId);
        log.info("Successfully started campaign: {}", campaignId);
        return ResponseEntity.ok(response);
    }

    // Schedule Campaign
    @PostMapping("/{campaignId}/schedule")
    public ResponseEntity<CampaignResponse> scheduleCampaign(
            @PathVariable UUID companyId,
            @PathVariable UUID campaignId,
            @Valid @RequestBody ScheduleCampaignRequest request) {
        log.info("Received request to schedule campaign: {} for company: {}", campaignId, companyId);
        CampaignResponse response = campaignService.scheduleCampaign(companyId, campaignId, request);
        log.info("Successfully scheduled campaign: {} for {}", campaignId, request.getScheduledFor());
        return ResponseEntity.ok(response);
    }

    // Cancel Campaign
    @PostMapping("/{campaignId}/cancel")
    public ResponseEntity<CampaignResponse> cancelCampaign(
            @PathVariable UUID companyId,
            @PathVariable UUID campaignId) {
        log.info("Received request to cancel campaign: {} for company: {}", campaignId, companyId);
        CampaignResponse response = campaignService.cancelCampaign(companyId, campaignId);
        log.info("Successfully canceled campaign: {}", campaignId);
        return ResponseEntity.ok(response);
    }

    // Delete Campaign
    @DeleteMapping("/{campaignId}")
    public ResponseEntity<Void> deleteCampaign(
            @PathVariable UUID companyId,
            @PathVariable UUID campaignId) {
        log.info("Received request to delete campaign: {} for company: {}", campaignId, companyId);
        campaignService.deleteCampaign(companyId, campaignId);
        log.info("Successfully deleted campaign: {}", campaignId);
        return ResponseEntity.noContent().build();
    }
}
