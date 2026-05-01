package com.genphish.campaign.messaging.producer;

import com.genphish.campaign.config.KafkaConfig;
import com.genphish.campaign.entity.PhishingTemplate;
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

    public void sendGenerationRequest(PhishingTemplate template, String provider, String model, boolean allowFallbackTemplate) {
        sendGenerationRequest(template, provider, model, allowFallbackTemplate, RegenerationScope.ALL, null);
    }

    public void sendGenerationRequest(PhishingTemplate template, String provider, String model, boolean allowFallbackTemplate, RegenerationScope scope, String existingMongoTemplateId) {
        String difficultyLevel = template.getDifficultyLevel() != null
                ? template.getDifficultyLevel().name()
                : DifficultyLevel.PROFESSIONAL.name(); // Default to PROFESSIONAL if not set
        LanguageCode languageCode = template.getLanguageCode() != null
                ? template.getLanguageCode()
                : LanguageCode.TR;

        AiGenerationRequestEvent event = AiGenerationRequestEvent.builder()
                .templateId(template.getId())
                .companyId(template.getCompanyId())
                .prompt(template.getPrompt())
                .targetUrl(template.getTargetUrl())
                .referenceImageUrl(template.getReferenceImageUrl())
                .templateCategory(template.getTemplateCategory())
                .difficultyLevel(difficultyLevel)
                .languageCode(languageCode)
                .provider(provider)
                .model(model)
                .allowFallbackTemplate(allowFallbackTemplate)
                .regenerationScope(scope)
                .existingMongoTemplateId(existingMongoTemplateId)
                .build();

        kafkaTemplate.send(
                KafkaConfig.TOPIC_AI_GENERATION_REQUESTS,
                template.getId().toString(), // Partition key
                event
        );

        log.info("Sent AI generation request for template: {}", template.getId());
    }
}
