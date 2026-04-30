package com.genphish.campaign.messaging.producer;

import com.genphish.campaign.config.KafkaConfig;
import com.genphish.campaign.entity.PhishingTemplate;
import com.genphish.campaign.entity.enums.DifficultyLevel;
import com.genphish.campaign.entity.enums.LanguageCode;
import com.genphish.campaign.entity.enums.RegenerationScope;
import com.genphish.campaign.messaging.event.AiGenerationRequestEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiGenerationRequestProducerTest {

    @Mock
    private KafkaTemplate<String, AiGenerationRequestEvent> kafkaTemplate;

    @InjectMocks
    private AiGenerationRequestProducer producer;

    @Captor
    private ArgumentCaptor<AiGenerationRequestEvent> eventCaptor;

    private UUID templateId;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        templateId = UUID.randomUUID();
        companyId = UUID.randomUUID();
    }

    @Test
    void sendGenerationRequest_Success() {
        PhishingTemplate template = PhishingTemplate.builder()
                .id(templateId)
                .companyId(companyId)
                .prompt("Generate a test email")
                .targetUrl("https://example.com")
                .difficultyLevel(DifficultyLevel.PROFESSIONAL)
                .languageCode(LanguageCode.EN)
                .build();

        producer.sendGenerationRequest(template, "openai", "gpt-4", true, RegenerationScope.ONLY_EMAIL, "mongo_456");

        verify(kafkaTemplate).send(
                eq(KafkaConfig.TOPIC_AI_GENERATION_REQUESTS),
                eq(templateId.toString()),
                eventCaptor.capture()
        );

        AiGenerationRequestEvent sentEvent = eventCaptor.getValue();
        assertEquals(templateId, sentEvent.getTemplateId());
        assertEquals("Generate a test email", sentEvent.getPrompt());
        assertEquals(DifficultyLevel.PROFESSIONAL.name(), sentEvent.getDifficultyLevel());
        assertEquals(LanguageCode.EN, sentEvent.getLanguageCode());
        assertEquals(RegenerationScope.ONLY_EMAIL, sentEvent.getRegenerationScope());
        assertEquals("mongo_456", sentEvent.getExistingMongoTemplateId());
    }

    @Test
    void sendGenerationRequest_WithDefaults_Success() {
        PhishingTemplate template = PhishingTemplate.builder()
                .id(templateId)
                .companyId(companyId)
                .prompt("Generate a default email")
                .build();

        producer.sendGenerationRequest(template, "openai", "gpt-3.5-turbo", false);

        verify(kafkaTemplate).send(
                eq(KafkaConfig.TOPIC_AI_GENERATION_REQUESTS),
                eq(templateId.toString()),
                eventCaptor.capture()
        );

        AiGenerationRequestEvent sentEvent = eventCaptor.getValue();
        assertEquals(DifficultyLevel.PROFESSIONAL.name(), sentEvent.getDifficultyLevel()); // Default
        assertEquals(LanguageCode.TR, sentEvent.getLanguageCode()); // Default
        assertEquals(RegenerationScope.ALL, sentEvent.getRegenerationScope()); // Default
        assertNull(sentEvent.getExistingMongoTemplateId()); // Default
    }
}
