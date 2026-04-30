package com.genphish.campaign.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PythonServiceClientTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private PythonServiceClient pythonServiceClient;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(pythonServiceClient, "pythonServiceBaseUrl", "http://python-service");
    }

    @Test
    void getTemplateById_WhenRequestSucceeds_ShouldReturnTemplate() {
        String templateId = "mongo-template-1";
        String expectedResponse = "{\"subject\":\"Hi\"}";

        when(restTemplate.getForObject("http://python-service/api/templates/mongo-template-1", String.class))
                .thenReturn(expectedResponse);

        String actual = pythonServiceClient.getTemplateById(templateId);

        assertThat(actual).isEqualTo(expectedResponse);
        verify(restTemplate).getForObject("http://python-service/api/templates/mongo-template-1", String.class);
    }

    @Test
    void getTemplateById_WhenRequestFails_ShouldReturnNull() {
        String templateId = "missing-template";

        when(restTemplate.getForObject("http://python-service/api/templates/missing-template", String.class))
                .thenThrow(new RestClientException("Connection failed"));

        String actual = pythonServiceClient.getTemplateById(templateId);

        assertThat(actual).isNull();
    }

    @Test
    void cloneTemplateForCampaign_WhenRequestSucceeds_ShouldReturnTemplateId() {
        String sourceTemplateId = "mongo-source-1";
        UUID campaignId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        // Use real deserialization path via RestTemplate typed response.
        when(restTemplate.postForObject(
                eq("http://python-service/api/templates/mongo-source-1/clone"),
                org.mockito.ArgumentMatchers.any(),
                eq(com.genphish.campaign.client.PythonServiceClient.TemplateCloneResponse.class)
        )).thenReturn(new com.genphish.campaign.client.PythonServiceClient.TemplateCloneResponse("mongo-clone-1"));

        String cloned = pythonServiceClient.cloneTemplateForCampaign(sourceTemplateId, campaignId, companyId);

        assertThat(cloned).isEqualTo("mongo-clone-1");
        verify(restTemplate).postForObject(
                eq("http://python-service/api/templates/mongo-source-1/clone"),
                org.mockito.ArgumentMatchers.any(),
                eq(com.genphish.campaign.client.PythonServiceClient.TemplateCloneResponse.class)
        );
    }

    @Test
    void cloneTemplateForCampaign_WhenRequestFails_ShouldReturnNull() {
        String sourceTemplateId = "mongo-source-1";
        UUID campaignId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        when(restTemplate.postForObject(
                eq("http://python-service/api/templates/mongo-source-1/clone"),
                org.mockito.ArgumentMatchers.any(),
                eq(com.genphish.campaign.client.PythonServiceClient.TemplateCloneResponse.class)
        )).thenThrow(new RestClientException("Connection failed"));

        String cloned = pythonServiceClient.cloneTemplateForCampaign(sourceTemplateId, campaignId, companyId);

        assertThat(cloned).isNull();
    }
}
