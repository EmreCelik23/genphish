package com.genphish.campaign.service.impl;

import com.genphish.campaign.dto.request.CreateCampaignRequest;
import com.genphish.campaign.dto.response.CampaignResponse;
import com.genphish.campaign.entity.Campaign;
import com.genphish.campaign.entity.PhishingTemplate;
import com.genphish.campaign.entity.enums.CampaignStatus;
import com.genphish.campaign.entity.enums.DifficultyLevel;
import com.genphish.campaign.entity.enums.TargetingType;
import com.genphish.campaign.exception.InvalidOperationException;
import com.genphish.campaign.messaging.producer.AiGenerationRequestProducer;
import com.genphish.campaign.messaging.producer.EmailDeliveryProducer;
import com.genphish.campaign.repository.CampaignRepository;
import com.genphish.campaign.repository.CampaignTargetRepository;
import com.genphish.campaign.repository.EmployeeRepository;
import com.genphish.campaign.repository.PhishingTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampaignServiceImplTest {

    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private CampaignTargetRepository campaignTargetRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private PhishingTemplateRepository phishingTemplateRepository;
    @Mock
    private AiGenerationRequestProducer aiGenerationRequestProducer;
    @Mock
    private EmailDeliveryProducer emailDeliveryProducer;

    @InjectMocks
    private CampaignServiceImpl campaignService;

    private UUID companyId;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        ReflectionTestUtils.setField(campaignService, "highRiskThreshold", 70.0);
    }

    @Test
    void createCampaign_WhenStaticWithoutTemplateId_ShouldThrowException() {
        // Given
        CreateCampaignRequest request = new CreateCampaignRequest();
        request.setIsAiGenerated(false);
        request.setStaticTemplateId(null);

        // When & Then
        assertThatThrownBy(() -> campaignService.createCampaign(companyId, request))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Static template ID is required");
    }

    @Test
    void createCampaign_WhenAiGenerated_ShouldSendKafkaMessage() {
        // Given
        CreateCampaignRequest request = new CreateCampaignRequest();
        request.setName("AI Campaign");
        request.setIsAiGenerated(true);
        request.setAiPrompt("Fake login page");
        request.setDifficultyLevel(DifficultyLevel.PROFESSIONAL);
        request.setTargetingType(TargetingType.ALL_COMPANY);

        Campaign savedCampaign = Campaign.builder().id(UUID.randomUUID()).build();
        when(campaignRepository.save(any(Campaign.class))).thenReturn(savedCampaign);

        // When
        CampaignResponse response = campaignService.createCampaign(companyId, request);

        // Then
        assertThat(response).isNotNull();
        verify(aiGenerationRequestProducer, times(1)).sendGenerationRequest(savedCampaign);
    }

    @Test
    void createCampaign_WhenStaticScheduled_ShouldRemainScheduled() {
        CreateCampaignRequest request = new CreateCampaignRequest();
        request.setName("Static Scheduled");
        request.setIsAiGenerated(false);
        request.setTargetingType(TargetingType.ALL_COMPANY);
        request.setStaticTemplateId(UUID.randomUUID());
        request.setScheduledFor(LocalDateTime.now().plusDays(1));

        PhishingTemplate template = PhishingTemplate.builder()
                .id(request.getStaticTemplateId())
                .isActive(true)
                .build();
        when(phishingTemplateRepository.findByIdAndIsActive(request.getStaticTemplateId(), true))
                .thenReturn(java.util.Optional.of(template));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CampaignResponse response = campaignService.createCampaign(companyId, request);

        assertThat(response.getStatus()).isEqualTo(CampaignStatus.SCHEDULED);
        verify(aiGenerationRequestProducer, never()).sendGenerationRequest(any(Campaign.class));
    }

    @Test
    void createCampaign_WhenStaticAndAllowFallbackTrue_ShouldThrowException() {
        CreateCampaignRequest request = new CreateCampaignRequest();
        request.setName("Static");
        request.setIsAiGenerated(false);
        request.setTargetingType(TargetingType.ALL_COMPANY);
        request.setStaticTemplateId(UUID.randomUUID());
        request.setAllowFallbackTemplate(true);

        assertThatThrownBy(() -> campaignService.createCampaign(companyId, request))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("allowFallbackTemplate can only be used for AI campaigns");
    }

    @Test
    void startCampaign_WhenNotInDraftOrScheduled_ShouldThrowException() {
        // Given
        UUID campaignId = UUID.randomUUID();
        Campaign campaign = Campaign.builder().id(campaignId).companyId(companyId).status(CampaignStatus.IN_PROGRESS).build();
        
        when(campaignRepository.findById(campaignId))
                .thenReturn(java.util.Optional.of(campaign));

        // When & Then
        assertThatThrownBy(() -> campaignService.startCampaign(companyId, campaignId))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Campaign can only be started from DRAFT or SCHEDULED status");
    }

    @Test
    void startCampaign_WhenDraft_ShouldUpdateStatusAndSendDeliveryEvents() {
        // Given
        UUID campaignId = UUID.randomUUID();
        Campaign campaign = Campaign.builder().id(campaignId).companyId(companyId).status(CampaignStatus.DRAFT).build();
        
        when(campaignRepository.findById(campaignId))
                .thenReturn(java.util.Optional.of(campaign));
                
        // Assuming target ALL_COMPANY
        campaign.setTargetingType(TargetingType.ALL_COMPANY);
        when(employeeRepository.countByCompanyIdAndIsActive(companyId, true)).thenReturn(5L);

        // When
        campaignService.startCampaign(companyId, campaignId);

        // Then
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.IN_PROGRESS);
        verify(campaignRepository).save(campaign);
        verify(emailDeliveryProducer, times(1)).sendDeliveryRequest(campaign);
    }
}
