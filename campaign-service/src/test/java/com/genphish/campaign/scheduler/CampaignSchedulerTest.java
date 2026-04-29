package com.genphish.campaign.scheduler;

import com.genphish.campaign.entity.Campaign;
import com.genphish.campaign.entity.enums.CampaignStatus;
import com.genphish.campaign.messaging.producer.EmailDeliveryProducer;
import com.genphish.campaign.repository.CampaignRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignSchedulerTest {

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private EmailDeliveryProducer emailDeliveryProducer;

    @InjectMocks
    private CampaignScheduler scheduler;

    @Test
    void triggerScheduledCampaigns_WhenNoDueCampaigns_ShouldDoNothing() {
        when(campaignRepository.findAllByStatusAndScheduledForBeforeAndIsDeleted(
                org.mockito.ArgumentMatchers.eq(CampaignStatus.SCHEDULED),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(false)
        )).thenReturn(List.of());

        scheduler.triggerScheduledCampaigns();

        verify(campaignRepository, never()).save(org.mockito.ArgumentMatchers.any(Campaign.class));
        verify(emailDeliveryProducer, never()).sendDeliveryRequest(org.mockito.ArgumentMatchers.any(Campaign.class));
    }

    @Test
    void triggerScheduledCampaigns_WhenDueCampaignsExist_ShouldLaunchAll() {
        Campaign c1 = Campaign.builder().id(UUID.randomUUID()).name("Q1").status(CampaignStatus.SCHEDULED).build();
        Campaign c2 = Campaign.builder().id(UUID.randomUUID()).name("Q2").status(CampaignStatus.SCHEDULED).build();

        when(campaignRepository.findAllByStatusAndScheduledForBeforeAndIsDeleted(
                org.mockito.ArgumentMatchers.eq(CampaignStatus.SCHEDULED),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(false)
        )).thenReturn(List.of(c1, c2));

        scheduler.triggerScheduledCampaigns();

        assertThat(c1.getStatus()).isEqualTo(CampaignStatus.IN_PROGRESS);
        assertThat(c2.getStatus()).isEqualTo(CampaignStatus.IN_PROGRESS);
        verify(campaignRepository, times(2)).save(org.mockito.ArgumentMatchers.any(Campaign.class));
        verify(emailDeliveryProducer).sendDeliveryRequest(c1);
        verify(emailDeliveryProducer).sendDeliveryRequest(c2);
    }

    @Test
    void triggerScheduledCampaigns_WhenFirstFails_ShouldContinueNextCampaign() {
        Campaign c1 = Campaign.builder().id(UUID.randomUUID()).name("Failing").status(CampaignStatus.SCHEDULED).build();
        Campaign c2 = Campaign.builder().id(UUID.randomUUID()).name("Healthy").status(CampaignStatus.SCHEDULED).build();

        when(campaignRepository.findAllByStatusAndScheduledForBeforeAndIsDeleted(
                org.mockito.ArgumentMatchers.eq(CampaignStatus.SCHEDULED),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(false)
        )).thenReturn(List.of(c1, c2));

        doThrow(new RuntimeException("Kafka unavailable"))
                .when(emailDeliveryProducer).sendDeliveryRequest(c1);

        scheduler.triggerScheduledCampaigns();

        verify(emailDeliveryProducer).sendDeliveryRequest(c1);
        verify(emailDeliveryProducer).sendDeliveryRequest(c2);
        verify(campaignRepository, times(2)).save(org.mockito.ArgumentMatchers.any(Campaign.class));
    }
}
