package com.genphish.campaign.controller;

import com.genphish.campaign.dto.request.CreateCampaignRequest;
import com.genphish.campaign.dto.request.CloneCampaignRequest;
import com.genphish.campaign.dto.request.RegenerateAiCampaignRequest;
import com.genphish.campaign.dto.response.AiCampaignLibraryItemResponse;
import com.genphish.campaign.dto.response.AiCampaignTemplatePreviewResponse;
import com.genphish.campaign.dto.response.CampaignResponse;
import com.genphish.campaign.service.AiCampaignLibraryService;
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
    private final AiCampaignLibraryService aiCampaignLibraryService;

    // POST /api/v1/companies/{companyId}/campaigns
    @PostMapping
    public ResponseEntity<CampaignResponse> createCampaign(
            @PathVariable UUID companyId,
            @Valid @RequestBody CreateCampaignRequest request) {
        log.info("Received request to create campaign for company: {}", companyId);
        CampaignResponse response = campaignService.createCampaign(companyId, request);
        log.info("Successfully created campaign: {} for company: {}", response.getId(), companyId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // GET /api/v1/companies/{companyId}/campaigns
    @GetMapping
    public ResponseEntity<List<CampaignResponse>> getAllCampaigns(@PathVariable UUID companyId) {
        log.info("Fetching all campaigns for company: {}", companyId);
        return ResponseEntity.ok(campaignService.getAllCampaigns(companyId));
    }

    // GET /api/v1/companies/{companyId}/campaigns/ai-library
    @GetMapping("/ai-library")
    public ResponseEntity<List<AiCampaignLibraryItemResponse>> getAiCampaignLibrary(@PathVariable UUID companyId) {
        log.info("Fetching AI campaign library for company: {}", companyId);
        return ResponseEntity.ok(aiCampaignLibraryService.getAiCampaignLibrary(companyId));
    }

    // GET /api/v1/companies/{companyId}/campaigns/ai-library/{campaignId}/preview
    @GetMapping("/ai-library/{campaignId}/preview")
    public ResponseEntity<AiCampaignTemplatePreviewResponse> getAiCampaignPreview(
            @PathVariable UUID companyId,
            @PathVariable UUID campaignId) {
        log.info("Fetching AI campaign preview for campaign: {} company: {}", campaignId, companyId);
        return ResponseEntity.ok(aiCampaignLibraryService.getAiCampaignPreview(companyId, campaignId));
    }

    @GetMapping("/{campaignId}")
    public ResponseEntity<CampaignResponse> getCampaignById(
            @PathVariable UUID companyId,
            @PathVariable UUID campaignId) {
        log.info("Fetching campaign: {} for company: {}", campaignId, companyId);
        return ResponseEntity.ok(campaignService.getCampaignById(companyId, campaignId));
    }

    // POST /api/v1/companies/{companyId}/campaigns/{campaignId}/regenerate
    @PostMapping("/{campaignId}/regenerate")
    public ResponseEntity<CampaignResponse> regenerateAiContent(
            @PathVariable UUID companyId,
            @PathVariable UUID campaignId,
            @Valid @RequestBody RegenerateAiCampaignRequest request) {
        log.info("Received request to regenerate AI content for campaign: {} with scope: {}", campaignId, request.getScope());
        CampaignResponse response = campaignService.regenerateAiContent(companyId, campaignId, request);
        log.info("Successfully regenerated AI content for campaign: {}", campaignId);
        return ResponseEntity.ok(response);
    }

    // POST /api/v1/companies/{companyId}/campaigns/{campaignId}/start
    @PostMapping("/{campaignId}/start")
    public ResponseEntity<CampaignResponse> startCampaign(
            @PathVariable UUID companyId,
            @PathVariable UUID campaignId) {
        log.info("Received request to start campaign: {} for company: {}", campaignId, companyId);
        CampaignResponse response = campaignService.startCampaign(companyId, campaignId);
        log.info("Successfully started campaign: {}", campaignId);
        return ResponseEntity.ok(response);
    }

    // POST /api/v1/companies/{companyId}/campaigns/{campaignId}/clone
    @PostMapping("/{campaignId}/clone")
    public ResponseEntity<CampaignResponse> cloneCampaign(
            @PathVariable UUID companyId,
            @PathVariable UUID campaignId,
            @Valid @RequestBody CloneCampaignRequest request) {
        log.info("Received request to clone campaign: {} for company: {}", campaignId, companyId);
        CampaignResponse response = aiCampaignLibraryService.cloneCampaign(companyId, campaignId, request);
        log.info("Successfully cloned campaign: {} to new campaign: {}", campaignId, response.getId());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

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
