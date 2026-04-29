package com.genphish.campaign.service.impl;

import com.genphish.campaign.dto.request.RegenerateAiCampaignRequest;
import com.genphish.campaign.dto.response.CampaignResponse;
import com.genphish.campaign.entity.Campaign;
import com.genphish.campaign.entity.Employee;
import com.genphish.campaign.entity.enums.CampaignStatus;
import com.genphish.campaign.entity.enums.DifficultyLevel;
import com.genphish.campaign.entity.enums.RegenerationScope;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignServiceImplAdditionalTest {

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
    private UUID campaignId;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        campaignId = UUID.randomUUID();
        ReflectionTestUtils.setField(campaignService, "highRiskThreshold", 70.0);
    }

    @Test
    void regenerateAiContent_WhenCampaignIsStatic_ShouldThrow() {
        Campaign campaign = Campaign.builder()
                .id(campaignId)
                .companyId(companyId)
                .isAiGenerated(false)
                .status(CampaignStatus.DRAFT)
                .build();

        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

        RegenerateAiCampaignRequest request = RegenerateAiCampaignRequest.builder()
                .scope(RegenerationScope.ALL)
                .build();

        assertThatThrownBy(() -> campaignService.regenerateAiContent(companyId, campaignId, request))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Cannot regenerate AI content for a non-AI");
    }

    @Test
    void regenerateAiContent_WhenStatusInvalid_ShouldThrow() {
        Campaign campaign = Campaign.builder()
                .id(campaignId)
                .companyId(companyId)
                .isAiGenerated(true)
                .status(CampaignStatus.IN_PROGRESS)
                .build();

        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

        RegenerateAiCampaignRequest request = RegenerateAiCampaignRequest.builder()
                .scope(RegenerationScope.ONLY_EMAIL)
                .build();

        assertThatThrownBy(() -> campaignService.regenerateAiContent(companyId, campaignId, request))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Cannot regenerate AI content for a campaign that is IN_PROGRESS");
    }

    @Test
    void regenerateAiContent_WhenValid_ShouldSetGeneratingAndSendEvent() {
        Campaign campaign = Campaign.builder()
                .id(campaignId)
                .companyId(companyId)
                .name("AI Campaign")
                .isAiGenerated(true)
                .aiPrompt("old prompt")
                .targetUrl("https://example.com")
                .difficultyLevel(DifficultyLevel.AMATEUR)
                .mongoTemplateId("mongo-old")
                .status(CampaignStatus.DRAFT)
                .build();

        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
        when(campaignRepository.save(campaign)).thenReturn(campaign);

        RegenerateAiCampaignRequest request = RegenerateAiCampaignRequest.builder()
                .scope(RegenerationScope.ONLY_EMAIL)
                .newPrompt("new prompt")
                .build();

        CampaignResponse response = campaignService.regenerateAiContent(companyId, campaignId, request);

        assertThat(response.getStatus()).isEqualTo(CampaignStatus.GENERATING);
        assertThat(campaign.getAiPrompt()).isEqualTo("new prompt");
        verify(aiGenerationRequestProducer).sendGenerationRequest(campaign, RegenerationScope.ONLY_EMAIL, "mongo-old");
    }

    @Test
    void startCampaign_WhenAiContentMissing_ShouldThrow() {
        Campaign campaign = Campaign.builder()
                .id(campaignId)
                .companyId(companyId)
                .isAiGenerated(true)
                .mongoTemplateId(null)
                .status(CampaignStatus.DRAFT)
                .targetingType(TargetingType.ALL_COMPANY)
                .build();

        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> campaignService.startCampaign(companyId, campaignId))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("AI campaign content has not been generated yet");
    }

    @Test
    void startCampaign_WhenHighRiskTargeting_ShouldUseConfiguredThreshold() {
        Campaign campaign = Campaign.builder()
                .id(campaignId)
                .companyId(companyId)
                .isAiGenerated(false)
                .status(CampaignStatus.DRAFT)
                .targetingType(TargetingType.HIGH_RISK)
                .build();

        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
        when(employeeRepository.findAllByCompanyIdAndRiskScoreGreaterThanEqualAndIsActive(companyId, 70.0, true))
                .thenReturn(List.of(Employee.builder().id(UUID.randomUUID()).companyId(companyId).build(),
                        Employee.builder().id(UUID.randomUUID()).companyId(companyId).build()));

        campaignService.startCampaign(companyId, campaignId);

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.IN_PROGRESS);
        assertThat(campaign.getTargetCount()).isEqualTo(2);
        verify(emailDeliveryProducer).sendDeliveryRequest(campaign);
    }

    @Test
    void deleteCampaign_WhenInProgress_ShouldThrow() {
        Campaign campaign = Campaign.builder()
                .id(campaignId)
                .companyId(companyId)
                .status(CampaignStatus.IN_PROGRESS)
                .build();

        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> campaignService.deleteCampaign(companyId, campaignId))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Cannot delete an active campaign");
    }

    @Test
    void deleteCampaign_WhenNotInProgress_ShouldSoftDelete() {
        Campaign campaign = Campaign.builder()
                .id(campaignId)
                .companyId(companyId)
                .status(CampaignStatus.DRAFT)
                .build();

        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

        campaignService.deleteCampaign(companyId, campaignId);

        assertThat(campaign.isDeleted()).isTrue();
        assertThat(campaign.getDeletedAt()).isNotNull();
        verify(campaignRepository).save(campaign);
    }
}
