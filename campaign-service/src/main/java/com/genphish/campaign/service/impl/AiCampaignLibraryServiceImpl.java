package com.genphish.campaign.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genphish.campaign.client.PythonServiceClient;
import com.genphish.campaign.dto.request.CloneCampaignRequest;
import com.genphish.campaign.dto.response.AiCampaignLibraryItemResponse;
import com.genphish.campaign.dto.response.AiCampaignTemplatePreviewResponse;
import com.genphish.campaign.dto.response.CampaignResponse;
import com.genphish.campaign.entity.Campaign;
import com.genphish.campaign.entity.CampaignTarget;
import com.genphish.campaign.entity.enums.CampaignStatus;
import com.genphish.campaign.exception.InvalidOperationException;
import com.genphish.campaign.exception.ResourceNotFoundException;
import com.genphish.campaign.repository.CampaignRepository;
import com.genphish.campaign.repository.CampaignTargetRepository;
import com.genphish.campaign.service.AiCampaignLibraryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiCampaignLibraryServiceImpl implements AiCampaignLibraryService {

    private final CampaignRepository campaignRepository;
    private final CampaignTargetRepository campaignTargetRepository;
    private final PythonServiceClient pythonServiceClient;
    private final ObjectMapper objectMapper;

    @Override
    public List<AiCampaignLibraryItemResponse> getAiCampaignLibrary(UUID companyId) {
        return campaignRepository.findAllByCompanyIdAndIsDeleted(companyId, false).stream()
                .filter(Campaign::isAiGenerated)
                .filter(this::hasReusableTemplateReference)
                .sorted(java.util.Comparator.comparing(Campaign::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())).reversed())
                .map(this::mapAiLibraryItem)
                .toList();
    }

    @Override
    public AiCampaignTemplatePreviewResponse getAiCampaignPreview(UUID companyId, UUID campaignId) {
        Campaign campaign = findCampaignOrThrow(companyId, campaignId);
        if (!campaign.isAiGenerated()) {
            throw new InvalidOperationException("Preview endpoint supports only AI campaigns.");
        }
        if (!hasReusableTemplateReference(campaign)) {
            throw new InvalidOperationException("AI campaign does not have reusable generated content yet.");
        }

        String payload = pythonServiceClient.getTemplateById(campaign.getMongoTemplateId());
        if (payload == null || payload.isBlank()) {
            throw new InvalidOperationException("Unable to retrieve AI template preview from AI Engine.");
        }

        try {
            AiTemplatePayload template = objectMapper.readValue(payload, AiTemplatePayload.class);
            if (isBlank(template.subject) || isBlank(template.bodyHtml) || isBlank(template.landingPageCode)) {
                throw new InvalidOperationException("AI template preview payload is incomplete.");
            }
            return AiCampaignTemplatePreviewResponse.builder()
                    .campaignId(campaign.getId())
                    .campaignName(campaign.getName())
                    .mongoTemplateId(campaign.getMongoTemplateId())
                    .fallbackContentUsed(campaign.isFallbackContentUsed())
                    .subject(template.subject)
                    .bodyHtml(template.bodyHtml)
                    .landingPageCode(template.landingPageCode)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse AI template preview for campaign {}", campaignId, e);
            throw new InvalidOperationException("AI template preview payload is invalid.");
        }
    }

    @Override
    @Transactional
    public CampaignResponse cloneCampaign(UUID companyId, UUID campaignId, CloneCampaignRequest request) {
        Campaign source = findCampaignOrThrow(companyId, campaignId);
        if (source.isAiGenerated() && !hasReusableTemplateReference(source)) {
            throw new InvalidOperationException("Cannot clone AI campaign before template generation is completed.");
        }

        Campaign clonedCampaign = Campaign.builder()
                .companyId(companyId)
                .name(request.getName())
                .targetingType(source.getTargetingType())
                .targetDepartment(source.getTargetDepartment())
                .isAiGenerated(source.isAiGenerated())
                .aiPrompt(source.getAiPrompt())
                .targetUrl(source.getTargetUrl())
                .difficultyLevel(source.getDifficultyLevel())
                .aiLanguageCode(source.getAiLanguageCode())
                .aiProvider(source.getAiProvider())
                .aiModel(source.getAiModel())
                .allowFallbackTemplate(source.isAllowFallbackTemplate())
                .staticTemplateId(source.getStaticTemplateId())
                .mongoTemplateId(source.getMongoTemplateId())
                .fallbackContentUsed(source.isFallbackContentUsed())
                .qrCodeEnabled(source.isQrCodeEnabled())
                .status(resolveCloneStatus(request.getScheduledFor(), source))
                .scheduledFor(request.getScheduledFor())
                .build();

        Campaign savedClone = campaignRepository.save(clonedCampaign);
        cloneIndividualTargetsIfRequired(source, savedClone);
        savedClone = cloneAiTemplateIfNeeded(source, savedClone, companyId);

        return mapToResponse(savedClone);
    }

    private Campaign findCampaignOrThrow(UUID companyId, UUID campaignId) {
        return campaignRepository.findById(campaignId)
                .filter(campaign -> campaign.getCompanyId().equals(companyId))
                .filter(campaign -> !campaign.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", "id", campaignId));
    }

    private CampaignStatus resolveCloneStatus(java.time.LocalDateTime scheduledFor, Campaign source) {
        if (scheduledFor == null) {
            return CampaignStatus.DRAFT;
        }
        if (source.isFallbackContentUsed()) {
            return CampaignStatus.DRAFT;
        }
        return CampaignStatus.SCHEDULED;
    }

    private boolean hasReusableTemplateReference(Campaign campaign) {
        return campaign.getMongoTemplateId() != null && !campaign.getMongoTemplateId().isBlank();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void cloneIndividualTargetsIfRequired(Campaign source, Campaign clone) {
        if (source.getTargetingType() != com.genphish.campaign.entity.enums.TargetingType.INDIVIDUAL) {
            return;
        }

        List<CampaignTarget> clonedTargets = campaignTargetRepository.findAllByCampaignId(source.getId()).stream()
                .map(target -> CampaignTarget.builder()
                        .campaignId(clone.getId())
                        .employeeId(target.getEmployeeId())
                        .build())
                .toList();
        campaignTargetRepository.saveAll(clonedTargets);
    }

    private Campaign cloneAiTemplateIfNeeded(Campaign source, Campaign clone, UUID companyId) {
        if (!source.isAiGenerated()) {
            return clone;
        }

        String clonedTemplateId = pythonServiceClient.cloneTemplateForCampaign(
                source.getMongoTemplateId(),
                clone.getId(),
                companyId
        );
        if (clonedTemplateId == null || clonedTemplateId.isBlank()) {
            throw new InvalidOperationException("AI template could not be cloned for the new campaign.");
        }

        clone.setMongoTemplateId(clonedTemplateId);
        return campaignRepository.save(clone);
    }

    private AiCampaignLibraryItemResponse mapAiLibraryItem(Campaign campaign) {
        return AiCampaignLibraryItemResponse.builder()
                .campaignId(campaign.getId())
                .name(campaign.getName())
                .aiPrompt(campaign.getAiPrompt())
                .difficultyLevel(campaign.getDifficultyLevel())
                .languageCode(campaign.getAiLanguageCode())
                .aiProvider(campaign.getAiProvider())
                .aiModel(campaign.getAiModel())
                .allowFallbackTemplate(campaign.isAllowFallbackTemplate())
                .fallbackContentUsed(campaign.isFallbackContentUsed())
                .status(campaign.getStatus())
                .createdAt(campaign.getCreatedAt())
                .build();
    }

    private CampaignResponse mapToResponse(Campaign campaign) {
        return CampaignResponse.builder()
                .id(campaign.getId())
                .companyId(campaign.getCompanyId())
                .name(campaign.getName())
                .targetingType(campaign.getTargetingType())
                .targetDepartment(campaign.getTargetDepartment())
                .isAiGenerated(campaign.isAiGenerated())
                .aiPrompt(campaign.getAiPrompt())
                .targetUrl(campaign.getTargetUrl())
                .difficultyLevel(campaign.getDifficultyLevel())
                .languageCode(campaign.getAiLanguageCode())
                .aiProvider(campaign.getAiProvider())
                .aiModel(campaign.getAiModel())
                .allowFallbackTemplate(campaign.isAllowFallbackTemplate())
                .fallbackContentUsed(campaign.isFallbackContentUsed())
                .staticTemplateId(campaign.getStaticTemplateId())
                .qrCodeEnabled(campaign.isQrCodeEnabled())
                .status(campaign.getStatus())
                .scheduledFor(campaign.getScheduledFor())
                .createdAt(campaign.getCreatedAt())
                .build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class AiTemplatePayload {
        public String subject;
        public String bodyHtml;
        public String landingPageCode;
    }
}
