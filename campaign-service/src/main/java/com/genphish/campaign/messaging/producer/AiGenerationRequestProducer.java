package com.genphish.campaign.messaging.producer;

import com.genphish.campaign.config.KafkaConfig;
import com.genphish.campaign.entity.Campaign;
import com.genphish.campaign.entity.enums.DifficultyLevel;
import com.genphish.campaign.entity.enums.LanguageCode;
import com.genphish.campaign.entity.enums.RegenerationScope;
import com.genphish.campaign.messaging.event.AiGenerationRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiGenerationRequestProducer {

    private final KafkaTemplate<String, AiGenerationRequestEvent> kafkaTemplate;

    // Sends AI generation request to Python service (Full Generation)
    public void sendGenerationRequest(Campaign campaign) {
        sendGenerationRequest(campaign, RegenerationScope.ALL, null);
    }

    // Overloaded method to support partial regeneration
    public void sendGenerationRequest(Campaign campaign, RegenerationScope scope, String existingMongoTemplateId) {
        String difficultyLevel = campaign.getDifficultyLevel() != null
                ? campaign.getDifficultyLevel().name()
                : DifficultyLevel.PROFESSIONAL.name(); // Default to PROFESSIONAL if not set
        LanguageCode languageCode = campaign.getAiLanguageCode() != null
                ? campaign.getAiLanguageCode()
                : LanguageCode.TR;

        AiGenerationRequestEvent event = AiGenerationRequestEvent.builder()
                .campaignId(campaign.getId())
                .companyId(campaign.getCompanyId())
                .prompt(campaign.getAiPrompt())
                .targetUrl(campaign.getTargetUrl())
                .difficultyLevel(difficultyLevel)
                .languageCode(languageCode)
                .provider(campaign.getAiProvider())
                .model(campaign.getAiModel())
                .regenerationScope(scope)
                .existingMongoTemplateId(existingMongoTemplateId)
                .build();

        kafkaTemplate.send(
                KafkaConfig.TOPIC_AI_GENERATION_REQUESTS,
                campaign.getId().toString(), // Partition key
                event
        );

        log.info("Sent AI generation request for campaign: {}", campaign.getId());
    }
}
