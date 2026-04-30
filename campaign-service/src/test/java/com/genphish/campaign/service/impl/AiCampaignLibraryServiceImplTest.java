package com.genphish.campaign.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genphish.campaign.client.PythonServiceClient;
import com.genphish.campaign.dto.request.CloneCampaignRequest;
import com.genphish.campaign.dto.response.AiCampaignLibraryItemResponse;
import com.genphish.campaign.dto.response.AiCampaignTemplatePreviewResponse;
import com.genphish.campaign.dto.response.CampaignResponse;
import com.genphish.campaign.entity.Campaign;
import com.genphish.campaign.entity.CampaignTarget;
import com.genphish.campaign.entity.enums.CampaignStatus;
import com.genphish.campaign.entity.enums.DifficultyLevel;
import com.genphish.campaign.entity.enums.LanguageCode;
import com.genphish.campaign.entity.enums.TargetingType;
import com.genphish.campaign.exception.InvalidOperationException;
import com.genphish.campaign.repository.CampaignRepository;
import com.genphish.campaign.repository.CampaignTargetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCampaignLibraryServiceImplTest {

    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private CampaignTargetRepository campaignTargetRepository;
    @Mock
    private PythonServiceClient pythonServiceClient;

    @InjectMocks
    private AiCampaignLibraryServiceImpl service;

    @Test
    void getAiCampaignLibrary_ShouldReturnOnlyCampaignsWithReusableTemplate() {
        UUID companyId = UUID.randomUUID();
        Campaign reusable = Campaign.builder()
                .id(UUID.randomUUID())
                .companyId(companyId)
                .name("Reusable")
                .isAiGenerated(true)
                .mongoTemplateId("mongo-1")
                .difficultyLevel(DifficultyLevel.PROFESSIONAL)
                .aiLanguageCode(LanguageCode.TR)
                .status(CampaignStatus.DRAFT)
                .createdAt(LocalDateTime.now())
                .build();
        Campaign missingTemplate = Campaign.builder()
                .id(UUID.randomUUID())
                .companyId(companyId)
                .name("Missing")
                .isAiGenerated(true)
                .mongoTemplateId(null)
                .build();

        when(campaignRepository.findAllByCompanyIdAndIsDeleted(companyId, false))
                .thenReturn(List.of(reusable, missingTemplate));

        List<AiCampaignLibraryItemResponse> result = service.getAiCampaignLibrary(companyId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCampaignId()).isEqualTo(reusable.getId());
    }

    @Test
    void getAiCampaignPreview_ShouldReturnParsedTemplatePayload() {
        UUID companyId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        Campaign campaign = Campaign.builder()
                .id(campaignId)
                .companyId(companyId)
                .name("AI Preview")
                .isAiGenerated(true)
                .mongoTemplateId("mongo-123")
                .fallbackContentUsed(false)
                .build();
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
        when(pythonServiceClient.getTemplateById("mongo-123"))
                .thenReturn("{\"subject\":\"Sub\",\"bodyHtml\":\"<p>Body</p>\",\"landingPageCode\":\"code\"}");

        service = new AiCampaignLibraryServiceImpl(
                campaignRepository,
                campaignTargetRepository,
                pythonServiceClient,
                new ObjectMapper()
        );

        AiCampaignTemplatePreviewResponse preview = service.getAiCampaignPreview(companyId, campaignId);

        assertThat(preview.getCampaignId()).isEqualTo(campaignId);
        assertThat(preview.getSubject()).isEqualTo("Sub");
        assertThat(preview.getBodyHtml()).contains("Body");
    }

    @Test
    void cloneCampaign_WhenSourceHasFallbackAndScheduledForProvided_ShouldStayDraft() {
        UUID companyId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID cloneId = UUID.randomUUID();
        Campaign source = Campaign.builder()
                .id(sourceId)
                .companyId(companyId)
                .name("Source")
                .targetingType(TargetingType.ALL_COMPANY)
                .isAiGenerated(true)
                .mongoTemplateId("mongo-1")
                .fallbackContentUsed(true)
                .status(CampaignStatus.DRAFT)
                .build();
        when(campaignRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> {
            Campaign saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(cloneId);
            }
            return saved;
        });
        when(pythonServiceClient.cloneTemplateForCampaign("mongo-1", cloneId, companyId))
                .thenReturn("mongo-clone-1");

        CloneCampaignRequest request = new CloneCampaignRequest();
        request.setName("Clone");
        request.setScheduledFor(LocalDateTime.now().plusDays(1));

        CampaignResponse response = service.cloneCampaign(companyId, sourceId, request);

        assertThat(response.getStatus()).isEqualTo(CampaignStatus.DRAFT);
        assertThat(response.getId()).isEqualTo(cloneId);
        verify(pythonServiceClient).cloneTemplateForCampaign("mongo-1", cloneId, companyId);
    }

    @Test
    void cloneCampaign_WhenAiSourceTemplateMissing_ShouldThrow() {
        UUID companyId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Campaign source = Campaign.builder()
                .id(sourceId)
                .companyId(companyId)
                .name("Source")
                .targetingType(TargetingType.ALL_COMPANY)
                .isAiGenerated(true)
                .mongoTemplateId(null)
                .build();
        when(campaignRepository.findById(sourceId)).thenReturn(Optional.of(source));

        CloneCampaignRequest request = new CloneCampaignRequest();
        request.setName("Clone");

        assertThatThrownBy(() -> service.cloneCampaign(companyId, sourceId, request))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("before template generation is completed");
    }

    @Test
    void cloneCampaign_WhenIndividualTargets_ShouldDuplicateTargets() {
        UUID companyId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        Campaign source = Campaign.builder()
                .id(sourceId)
                .companyId(companyId)
                .name("Source")
                .targetingType(TargetingType.INDIVIDUAL)
                .isAiGenerated(false)
                .staticTemplateId(UUID.randomUUID())
                .build();
        when(campaignRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> {
            Campaign saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(campaignTargetRepository.findAllByCampaignId(sourceId))
                .thenReturn(List.of(CampaignTarget.builder().campaignId(sourceId).employeeId(employeeId).build()));

        CloneCampaignRequest request = new CloneCampaignRequest();
        request.setName("Clone Individual");

        service.cloneCampaign(companyId, sourceId, request);

        verify(campaignTargetRepository).saveAll(any(List.class));
    }

    @Test
    void cloneCampaign_WhenAiTemplateCloneFails_ShouldThrow() {
        UUID companyId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID cloneId = UUID.randomUUID();
        Campaign source = Campaign.builder()
                .id(sourceId)
                .companyId(companyId)
                .name("Source")
                .targetingType(TargetingType.ALL_COMPANY)
                .isAiGenerated(true)
                .mongoTemplateId("mongo-1")
                .build();
        when(campaignRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> {
            Campaign saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(cloneId);
            }
            return saved;
        });
        when(pythonServiceClient.cloneTemplateForCampaign(eq("mongo-1"), eq(cloneId), eq(companyId)))
                .thenReturn(null);

        CloneCampaignRequest request = new CloneCampaignRequest();
        request.setName("Clone");

        assertThatThrownBy(() -> service.cloneCampaign(companyId, sourceId, request))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("AI template could not be cloned");
    }
}
