package com.genphish.campaign.messaging.producer;

import com.genphish.campaign.config.KafkaConfig;
import com.genphish.campaign.entity.Campaign;
import com.genphish.campaign.entity.enums.DifficultyLevel;
import com.genphish.campaign.entity.enums.LanguageCode;
import com.genphish.campaign.entity.enums.RegenerationScope;
import com.genphish.campaign.messaging.event.AiGenerationRequestEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiGenerationRequestProducerTest {

    @Mock
    private KafkaTemplate<String, AiGenerationRequestEvent> kafkaTemplate;

    @InjectMocks
    private AiGenerationRequestProducer producer;

    @Test
    void sendGenerationRequest_DefaultOverload_ShouldSendAllScopeEvent() {
        UUID campaignId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Campaign campaign = Campaign.builder()
                .id(campaignId)
                .companyId(companyId)
                .aiPrompt("Generate realistic HR email")
                .targetUrl("https://example.com/login")
                .difficultyLevel(DifficultyLevel.PROFESSIONAL)
                .build();

        producer.sendGenerationRequest(campaign);

        ArgumentCaptor<AiGenerationRequestEvent> eventCaptor = ArgumentCaptor.forClass(AiGenerationRequestEvent.class);
        verify(kafkaTemplate).send(
                eq(KafkaConfig.TOPIC_AI_GENERATION_REQUESTS),
                eq(campaignId.toString()),
                eventCaptor.capture()
        );

        AiGenerationRequestEvent event = eventCaptor.getValue();
        assertThat(event.getCampaignId()).isEqualTo(campaignId);
        assertThat(event.getCompanyId()).isEqualTo(companyId);
        assertThat(event.getPrompt()).isEqualTo("Generate realistic HR email");
        assertThat(event.getTargetUrl()).isEqualTo("https://example.com/login");
        assertThat(event.getDifficultyLevel()).isEqualTo(DifficultyLevel.PROFESSIONAL.name());
        assertThat(event.getLanguageCode()).isEqualTo(LanguageCode.TR);
        assertThat(event.getProvider()).isNull();
        assertThat(event.getModel()).isNull();
        assertThat(event.getRegenerationScope()).isEqualTo(RegenerationScope.ALL);
        assertThat(event.getExistingMongoTemplateId()).isNull();
    }

    @Test
    void sendGenerationRequest_WithCustomScope_ShouldUseProvidedValues() {
        UUID campaignId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Campaign campaign = Campaign.builder()
                .id(campaignId)
                .companyId(companyId)
                .aiPrompt("Regenerate landing page")
                .targetUrl("https://portal.example")
                .difficultyLevel(DifficultyLevel.AMATEUR)
                .aiLanguageCode(LanguageCode.EN)
                .aiProvider("openai")
                .aiModel("gpt-4o-mini")
                .build();

        producer.sendGenerationRequest(campaign, RegenerationScope.ONLY_LANDING_PAGE, "mongo-123");

        ArgumentCaptor<AiGenerationRequestEvent> eventCaptor = ArgumentCaptor.forClass(AiGenerationRequestEvent.class);
        verify(kafkaTemplate).send(
                eq(KafkaConfig.TOPIC_AI_GENERATION_REQUESTS),
                eq(campaignId.toString()),
                eventCaptor.capture()
        );

        AiGenerationRequestEvent event = eventCaptor.getValue();
        assertThat(event.getLanguageCode()).isEqualTo(LanguageCode.EN);
        assertThat(event.getProvider()).isEqualTo("openai");
        assertThat(event.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(event.getRegenerationScope()).isEqualTo(RegenerationScope.ONLY_LANDING_PAGE);
        assertThat(event.getExistingMongoTemplateId()).isEqualTo("mongo-123");
    }
}
