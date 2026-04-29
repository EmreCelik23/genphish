package com.genphish.campaign.messaging.consumer;

import com.genphish.campaign.entity.Campaign;
import com.genphish.campaign.entity.enums.AiGenerationStatus;
import com.genphish.campaign.entity.enums.CampaignStatus;
import com.genphish.campaign.messaging.event.AiGenerationResponseEvent;
import com.genphish.campaign.repository.CampaignRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiGenerationResponseConsumerTest {

    @Mock
    private CampaignRepository campaignRepository;

    @InjectMocks
    private AiGenerationResponseConsumer consumer;

    @Test
    void consume_WhenSuccessAndScheduledForNull_ShouldSetDraft() {
        UUID campaignId = UUID.randomUUID();
        Campaign campaign = Campaign.builder()
                .id(campaignId)
                .status(CampaignStatus.GENERATING)
                .scheduledFor(null)
                .build();

        AiGenerationResponseEvent event = AiGenerationResponseEvent.builder()
                .campaignId(campaignId)
                .status(AiGenerationStatus.SUCCESS)
                .mongoTemplateId("mongo-1")
                .build();

        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

        consumer.consume(event);

        assertThat(campaign.getMongoTemplateId()).isEqualTo("mongo-1");
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.DRAFT);
        verify(campaignRepository).save(campaign);
    }

    @Test
    void consume_WhenSuccessAndScheduledForExists_ShouldSetScheduled() {
        UUID campaignId = UUID.randomUUID();
        Campaign campaign = Campaign.builder()
                .id(campaignId)
                .status(CampaignStatus.GENERATING)
                .scheduledFor(LocalDateTime.now().plusHours(1))
                .build();

        AiGenerationResponseEvent event = AiGenerationResponseEvent.builder()
                .campaignId(campaignId)
                .status(AiGenerationStatus.SUCCESS)
                .mongoTemplateId("mongo-2")
                .build();

        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

        consumer.consume(event);

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.SCHEDULED);
        verify(campaignRepository).save(campaign);
    }

    @Test
    void consume_WhenFailed_ShouldSetFailedStatus() {
        UUID campaignId = UUID.randomUUID();
        Campaign campaign = Campaign.builder()
                .id(campaignId)
                .status(CampaignStatus.GENERATING)
                .build();

        AiGenerationResponseEvent event = AiGenerationResponseEvent.builder()
                .campaignId(campaignId)
                .status(AiGenerationStatus.FAILED)
                .errorMessage("Timeout")
                .build();

        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

        consumer.consume(event);

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.FAILED);
        verify(campaignRepository).save(campaign);
    }

    @Test
    void consume_WhenCampaignNotFound_ShouldNotSave() {
        UUID campaignId = UUID.randomUUID();
        AiGenerationResponseEvent event = AiGenerationResponseEvent.builder()
                .campaignId(campaignId)
                .status(AiGenerationStatus.SUCCESS)
                .mongoTemplateId("mongo-3")
                .build();

        when(campaignRepository.findById(campaignId)).thenReturn(Optional.empty());

        consumer.consume(event);

        verify(campaignRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
