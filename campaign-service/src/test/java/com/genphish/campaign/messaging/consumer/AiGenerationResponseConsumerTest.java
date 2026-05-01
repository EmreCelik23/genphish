package com.genphish.campaign.messaging.consumer;

import com.genphish.campaign.client.PythonServiceClient;
import com.genphish.campaign.entity.PhishingTemplate;
import com.genphish.campaign.entity.enums.TemplateStatus;
import com.genphish.campaign.messaging.event.AiGenerationResponseEvent;
import com.genphish.campaign.repository.PhishingTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiGenerationResponseConsumerTest {

    @Mock
    private PhishingTemplateRepository phishingTemplateRepository;

    @Mock
    private PythonServiceClient pythonServiceClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AiGenerationResponseConsumer consumer;

    private UUID templateId;
    private PhishingTemplate template;

    @BeforeEach
    void setUp() {
        templateId = UUID.randomUUID();
        template = PhishingTemplate.builder()
                .id(templateId)
                .status(TemplateStatus.GENERATING)
                .build();
    }

    @Test
    void consume_Success() {
        AiGenerationResponseEvent event = AiGenerationResponseEvent.builder()
                .templateId(templateId)
                .status(com.genphish.campaign.entity.enums.AiGenerationStatus.SUCCESS)
                .mongoTemplateId("mongo_789")
                .fallbackUsed(false)
                .build();

        String fakeHtml = "{\"subject\":\"Hi\", \"bodyHtml\":\"<html>Body</html>\", \"landingPageCode\":\"<html>Landing</html>\"}";

        when(phishingTemplateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(pythonServiceClient.getTemplateById(eq("mongo_789"), isNull())).thenReturn(fakeHtml);

        consumer.consume(event);

        assertEquals(TemplateStatus.READY, template.getStatus());
        assertEquals("mongo_789", template.getMongoTemplateId());
        assertEquals("Hi", template.getEmailSubject());
        assertEquals("<html>Body</html>", template.getEmailBody());
        assertEquals("<html>Landing</html>", template.getLandingPageHtml());
        assertFalse(template.isFallbackContentUsed());

        verify(phishingTemplateRepository).save(template);
    }

    @Test
    void consume_Failure_FromAiEngine() {
        AiGenerationResponseEvent event = AiGenerationResponseEvent.builder()
                .templateId(templateId)
                .status(com.genphish.campaign.entity.enums.AiGenerationStatus.FAILED)
                .build();

        when(phishingTemplateRepository.findById(templateId)).thenReturn(Optional.of(template));

        consumer.consume(event);

        assertEquals(TemplateStatus.FAILED, template.getStatus());
        verify(phishingTemplateRepository).save(template);
        verifyNoInteractions(pythonServiceClient);
    }

    @Test
    void consume_Success_FailsToFetchHtml() {
        AiGenerationResponseEvent event = AiGenerationResponseEvent.builder()
                .templateId(templateId)
                .status(com.genphish.campaign.entity.enums.AiGenerationStatus.SUCCESS)
                .mongoTemplateId("mongo_789")
                .build();

        when(phishingTemplateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(pythonServiceClient.getTemplateById(eq("mongo_789"), isNull())).thenReturn(null);

        consumer.consume(event);

        assertEquals(TemplateStatus.FAILED, template.getStatus());
        verify(phishingTemplateRepository).save(template);
    }
}
