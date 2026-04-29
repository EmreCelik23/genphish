package com.genphish.campaign.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

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
}
