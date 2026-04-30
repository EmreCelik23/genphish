package com.genphish.campaign.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PythonServiceClient {

    private final RestTemplate restTemplate;

    @Value("${app.python-service.base-url:http://localhost:5000}")
    private String pythonServiceBaseUrl;

    /**
     * Synchronously fetches the AI generated template from the Python service.
     * Used as a fallback when the template is missing from Redis (Cache-Aside).
     */
    public String getTemplateById(String mongoTemplateId) {
        String url = String.format("%s/api/templates/%s", pythonServiceBaseUrl, mongoTemplateId);
        try {
            log.info("Fetching AI template directly from Python Service (Cache Miss Fallback). URL: {}", url);
            return restTemplate.getForObject(url, String.class);
        } catch (RestClientException e) {
            log.error("Failed to fetch template from Python Service for ID: {}. Error: {}", mongoTemplateId, e.getMessage());
            return null; // Return null on failure so caller can handle the terminal error
        }
    }

    /**
     * Clones existing template into a brand-new template document and binds it to new campaign.
     * Used by campaign cloning to avoid shared mutable template references.
     */
    public String cloneTemplateForCampaign(String sourceMongoTemplateId, UUID campaignId, UUID companyId) {
        String url = String.format("%s/api/templates/%s/clone", pythonServiceBaseUrl, sourceMongoTemplateId);
        try {
            CloneTemplateRequest payload = new CloneTemplateRequest(campaignId, companyId);
            TemplateCloneResponse response = restTemplate.postForObject(url, payload, TemplateCloneResponse.class);
            if (response == null || response.templateId() == null || response.templateId().isBlank()) {
                log.error("AI template clone returned empty response for source template: {}", sourceMongoTemplateId);
                return null;
            }
            return response.templateId();
        } catch (RestClientException e) {
            log.error("Failed to clone template {} for campaign {}. Error: {}",
                    sourceMongoTemplateId, campaignId, e.getMessage());
            return null;
        }
    }

    record CloneTemplateRequest(UUID campaignId, UUID companyId) {}

    record TemplateCloneResponse(String templateId) {}
}
