package com.genphish.campaign.scheduler;

import com.genphish.campaign.entity.Campaign;
import com.genphish.campaign.entity.enums.CampaignStatus;
import com.genphish.campaign.messaging.producer.EmailDeliveryProducer;
import com.genphish.campaign.repository.CampaignRepository;
import com.genphish.campaign.repository.CampaignTargetRepository;
import com.genphish.campaign.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final EmployeeRepository employeeRepository;
    private final CampaignTargetRepository campaignTargetRepository;
    private final EmailDeliveryProducer emailDeliveryProducer;

    @Value("${app.campaign.high-risk-threshold:70.0}")
    private Double highRiskThreshold;

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
            launchScheduledCampaign(campaign);
        }
    }

    private void launchScheduledCampaign(Campaign campaign) {
        try {
            campaign.setTargetCount(calculateTargetCount(campaign));
            campaign.setStatus(CampaignStatus.IN_PROGRESS);
            campaignRepository.save(campaign);

            // Trigger email delivery via Kafka
            emailDeliveryProducer.sendDeliveryRequest(campaign);

            log.info("Scheduled campaign launched: {} ({})", campaign.getName(), campaign.getId());
        } catch (Exception e) {
            log.error("Failed to launch scheduled campaign: {} - {}", campaign.getId(), e.getMessage());
            campaign.setStatus(CampaignStatus.FAILED);
            campaignRepository.save(campaign);
        }
    }

    private long calculateTargetCount(Campaign campaign) {
        return switch (campaign.getTargetingType()) {
            case ALL_COMPANY -> employeeRepository.countByCompanyIdAndIsActive(campaign.getCompanyId(), true);
            case DEPARTMENT -> employeeRepository.countByCompanyIdAndDepartmentAndIsActive(
                    campaign.getCompanyId(), campaign.getTargetDepartment(), true
            );
            case INDIVIDUAL -> campaignTargetRepository.countByCampaignId(campaign.getId());
            case HIGH_RISK -> employeeRepository.findAllByCompanyIdAndRiskScoreGreaterThanEqualAndIsActive(
                    campaign.getCompanyId(), highRiskThreshold, true
            ).size();
        };
    }
}
