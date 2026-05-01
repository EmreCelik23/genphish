package com.genphish.campaign.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
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

    @Value("${app.security.ai-service-token:genphish-internal-token}")
    private String aiServiceToken;

    @Value("${app.security.service-token-header:X-Service-Token}")
    private String serviceTokenHeaderName;

    @Value("${app.security.company-header:X-Company-Id}")
    private String companyHeaderName;

    /**
     * Synchronously fetches the AI generated template from the Python service.
     * Used by the Kafka consumer to populate the PhishingTemplate entity with HTML content.
     */
    public String getTemplateById(String mongoTemplateId, UUID companyId) {
        String url = String.format("%s/api/templates/%s", pythonServiceBaseUrl, mongoTemplateId);
        try {
            log.info("Fetching AI template directly from Python Service. URL: {}", url);
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(aiServiceToken);
            headers.set(serviceTokenHeaderName, aiServiceToken);
            if (companyId != null) {
                headers.set(companyHeaderName, companyId.toString());
            }

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );
            return response.getBody();
        } catch (RestClientException e) {
            log.error("Failed to fetch template from Python Service for ID: {}. Error: {}", mongoTemplateId, e.getMessage());
            return null; // Return null on failure so caller can handle the terminal error
        }
    }
}
