package com.genphish.campaign.scheduler;

import com.genphish.campaign.entity.Campaign;
import com.genphish.campaign.entity.enums.CampaignStatus;
import com.genphish.campaign.messaging.producer.EmailDeliveryProducer;
import com.genphish.campaign.repository.CampaignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class CampaignScheduler {

    private final CampaignRepository campaignRepository;
    private final EmailDeliveryProducer emailDeliveryProducer;

    // Runs based on configured rate (default 5 minutes)
    @Scheduled(fixedRateString = "${app.scheduler.campaign-check-rate:300000}")
    @Transactional
    public void triggerScheduledCampaigns() {
        List<Campaign> dueCampaigns = campaignRepository.findAllByStatusAndScheduledForBeforeAndIsDeleted(
                CampaignStatus.SCHEDULED,
                LocalDateTime.now(),
                false
        );

        if (dueCampaigns.isEmpty()) {
            return;
        }

        log.info("Found {} scheduled campaigns ready to launch", dueCampaigns.size());

        for (Campaign campaign : dueCampaigns) {
            try {
                campaign.setStatus(CampaignStatus.IN_PROGRESS);
                campaignRepository.save(campaign);

                // Trigger email delivery via Kafka
                emailDeliveryProducer.sendDeliveryRequest(campaign);

                log.info("Scheduled campaign launched: {} ({})", campaign.getName(), campaign.getId());
            } catch (Exception e) {
                log.error("Failed to launch scheduled campaign: {} - {}", campaign.getId(), e.getMessage());
            }
        }
    }
}
