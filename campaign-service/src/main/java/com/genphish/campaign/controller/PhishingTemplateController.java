package com.genphish.campaign.controller;

import com.genphish.campaign.dto.request.GenerateTemplateRequest;
import com.genphish.campaign.dto.response.PhishingTemplateResponse;
import com.genphish.campaign.service.PhishingTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/templates")
@RequiredArgsConstructor
@Slf4j
public class PhishingTemplateController {

    private final PhishingTemplateService phishingTemplateService;

    // POST /api/v1/companies/{companyId}/templates/generate
    @PostMapping("/generate")
    public ResponseEntity<PhishingTemplateResponse> generateAiTemplate(
            @PathVariable UUID companyId,
            @Valid @RequestBody GenerateTemplateRequest request) {
        log.info("Received request to generate AI template for company: {}", companyId);
        PhishingTemplateResponse response = phishingTemplateService.generateAiTemplate(companyId, request);
        log.info("Started generating AI template: {} for company: {}", response.getId(), companyId);
        return new ResponseEntity<>(response, HttpStatus.ACCEPTED); // HTTP 202 Accepted because generation is async
    }

    // GET /api/v1/companies/{companyId}/templates
    @GetMapping
    public ResponseEntity<List<PhishingTemplateResponse>> getAllTemplates(@PathVariable UUID companyId) {
        log.info("Fetching all active phishing templates for company: {}", companyId);
        return ResponseEntity.ok(phishingTemplateService.getAllActiveTemplates(companyId));
    }

    // GET /api/v1/companies/{companyId}/templates/{templateId}
    @GetMapping("/{templateId}")
    public ResponseEntity<PhishingTemplateResponse> getTemplateById(
            @PathVariable UUID companyId,
            @PathVariable UUID templateId) {
        log.info("Fetching phishing template: {} for company: {}", templateId, companyId);
        return ResponseEntity.ok(phishingTemplateService.getTemplateById(companyId, templateId));
    }

    // PUT /api/v1/companies/{companyId}/templates/{templateId}
    @PutMapping("/{templateId}")
    public ResponseEntity<PhishingTemplateResponse> updateTemplate(
            @PathVariable UUID companyId,
            @PathVariable UUID templateId,
            @Valid @RequestBody com.genphish.campaign.dto.request.UpdateTemplateRequest request) {
        log.info("Received request to manually update phishing template: {} for company: {}", templateId, companyId);
        return ResponseEntity.ok(phishingTemplateService.updateTemplate(companyId, templateId, request));
    }

    // POST /api/v1/companies/{companyId}/templates/{templateId}/regenerate
    @PostMapping("/{templateId}/regenerate")
    public ResponseEntity<PhishingTemplateResponse> regenerateTemplate(
            @PathVariable UUID companyId,
            @PathVariable UUID templateId,
            @Valid @RequestBody com.genphish.campaign.dto.request.RegenerateTemplateRequest request) {
        log.info("Received request to regenerate phishing template: {} for company: {}", templateId, companyId);
        return ResponseEntity.ok(phishingTemplateService.regenerateAiTemplate(companyId, templateId, request));
    }

    // DELETE /api/v1/companies/{companyId}/templates/{templateId}
    @DeleteMapping("/{templateId}")
    public ResponseEntity<Void> deleteTemplate(
            @PathVariable UUID companyId,
            @PathVariable UUID templateId) {
        log.info("Received request to delete phishing template: {} for company: {}", templateId, companyId);
        phishingTemplateService.deleteTemplate(companyId, templateId);
        return ResponseEntity.noContent().build();
    }
}
