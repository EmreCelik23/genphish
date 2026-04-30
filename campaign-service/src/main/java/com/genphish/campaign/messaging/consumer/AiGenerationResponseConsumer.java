package com.genphish.campaign.messaging.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genphish.campaign.client.PythonServiceClient;
import com.genphish.campaign.config.KafkaConfig;
import com.genphish.campaign.entity.enums.AiGenerationStatus;
import com.genphish.campaign.entity.enums.TemplateStatus;
import com.genphish.campaign.messaging.event.AiGenerationResponseEvent;
import com.genphish.campaign.repository.PhishingTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiGenerationResponseConsumer {

    private final PhishingTemplateRepository phishingTemplateRepository;
    private final PythonServiceClient pythonServiceClient;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaConfig.TOPIC_AI_GENERATION_RESPONSES,
            groupId = "campaign-service-group"
    )
    public void consume(AiGenerationResponseEvent event) {
        log.info("Received AI generation response for template: {} - status: {}",
                event.getTemplateId(), event.getStatus());

        phishingTemplateRepository.findById(event.getTemplateId()).ifPresent(template -> {
            if (event.getStatus() == AiGenerationStatus.SUCCESS) {
                boolean fallbackUsed = Boolean.TRUE.equals(event.getFallbackUsed());
                template.setMongoTemplateId(event.getMongoTemplateId());
                template.setFallbackContentUsed(fallbackUsed);
                template.setStatus(TemplateStatus.READY);

                try {
                    String payload = pythonServiceClient.getTemplateById(event.getMongoTemplateId());
                    AiTemplatePayload aiPayload = objectMapper.readValue(payload, AiTemplatePayload.class);
                    template.setEmailSubject(aiPayload.subject);
                    template.setEmailBody(aiPayload.bodyHtml);
                    template.setLandingPageHtml(aiPayload.landingPageCode);
                } catch (Exception e) {
                    log.error("Failed to fetch template HTML from python service for template: {}", template.getId(), e);
                    template.setStatus(TemplateStatus.FAILED);
                }

                log.info("AI content ready for template: {}, mongoId: {}, fallbackUsed: {}",
                        template.getId(), event.getMongoTemplateId(), fallbackUsed);
            } else {
                template.setStatus(TemplateStatus.FAILED);
                log.error("AI generation FAILED for template: {}. Error: {}.",
                        template.getId(), event.getErrorMessage());
            }

            phishingTemplateRepository.save(template);
        });
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class AiTemplatePayload {
        public String subject;
        public String bodyHtml;
        public String landingPageCode;
    }
}
