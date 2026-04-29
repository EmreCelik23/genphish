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

import static org.assertj.core.api.Assertions.assertThat;
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
}
