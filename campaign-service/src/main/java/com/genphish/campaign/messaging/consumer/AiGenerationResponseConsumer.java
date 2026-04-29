package com.genphish.campaign.messaging.consumer;

import com.genphish.campaign.config.KafkaConfig;
import com.genphish.campaign.entity.enums.AiGenerationStatus;
import com.genphish.campaign.entity.enums.CampaignStatus;
import com.genphish.campaign.messaging.event.AiGenerationResponseEvent;
import com.genphish.campaign.repository.CampaignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiGenerationResponseConsumer {

    private final CampaignRepository campaignRepository;

    // Listens for AI-generated content responses from Python service
    @KafkaListener(
            topics = KafkaConfig.TOPIC_AI_GENERATION_RESPONSES,
            groupId = "campaign-service-group"
    )
    public void consume(AiGenerationResponseEvent event) {
        log.info("Received AI generation response for campaign: {} - status: {}",
                event.getCampaignId(), event.getStatus());

        campaignRepository.findById(event.getCampaignId()).ifPresent(campaign -> {
            if (event.getStatus() == AiGenerationStatus.SUCCESS) {
                // Store MongoDB reference and update status
                campaign.setMongoTemplateId(event.getMongoTemplateId());

                // If scheduled for later, set to SCHEDULED; otherwise set to DRAFT (awaiting manual start)
                if (campaign.getScheduledFor() != null) {
                    campaign.setStatus(CampaignStatus.SCHEDULED);
                } else {
                    campaign.setStatus(CampaignStatus.DRAFT);
                }

                log.info("AI content ready for campaign: {}, mongoId: {}",
                        campaign.getId(), event.getMongoTemplateId());
            } else {
                // AI generation failed — Graceful Degradation: fall back to FAILED state
                campaign.setStatus(CampaignStatus.FAILED);
                log.error("AI generation FAILED for campaign: {}. Error: {}. User can retry or fallback to static.",
                        campaign.getId(), event.getErrorMessage());
            }

            campaignRepository.save(campaign);
        });
    }
}
