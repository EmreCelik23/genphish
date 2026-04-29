package com.genphish.campaign.service.impl;

import com.genphish.campaign.dto.request.CreateCampaignRequest;
import com.genphish.campaign.dto.request.RegenerateAiCampaignRequest;
import com.genphish.campaign.dto.response.CampaignResponse;
import com.genphish.campaign.entity.Campaign;
import com.genphish.campaign.entity.CampaignTarget;
import com.genphish.campaign.entity.enums.CampaignStatus;
import com.genphish.campaign.entity.enums.TargetingType;
import com.genphish.campaign.exception.InvalidOperationException;
import com.genphish.campaign.exception.ResourceNotFoundException;
import com.genphish.campaign.messaging.producer.AiGenerationRequestProducer;
import com.genphish.campaign.messaging.producer.EmailDeliveryProducer;
import com.genphish.campaign.repository.CampaignRepository;
import com.genphish.campaign.repository.CampaignTargetRepository;
import com.genphish.campaign.repository.EmployeeRepository;
import com.genphish.campaign.repository.PhishingTemplateRepository;
import com.genphish.campaign.service.CampaignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignServiceImpl implements CampaignService {

    private final CampaignRepository campaignRepository;
    private final CampaignTargetRepository campaignTargetRepository;
    private final EmployeeRepository employeeRepository;
    private final PhishingTemplateRepository phishingTemplateRepository;
    private final AiGenerationRequestProducer aiGenerationRequestProducer;
    private final EmailDeliveryProducer emailDeliveryProducer;

    @org.springframework.beans.factory.annotation.Value("${app.campaign.high-risk-threshold:70.0}")
    private Double highRiskThreshold;

    @Override
    @Transactional
    public CampaignResponse createCampaign(UUID companyId, CreateCampaignRequest request) {

        // 1. Validate inputs
        if (Boolean.FALSE.equals(request.getIsAiGenerated())) {
            if (request.getStaticTemplateId() == null) {
                throw new InvalidOperationException("Static template ID is required for non-AI campaigns.");
            }
            phishingTemplateRepository.findByIdAndIsActive(request.getStaticTemplateId(), true)
                    .orElseThrow(() -> new ResourceNotFoundException("PhishingTemplate", "id", request.getStaticTemplateId()));
        }

        // 2. Build campaign entity
        Campaign campaign = Campaign.builder()
                .companyId(companyId)
                .name(request.getName())
                .targetingType(request.getTargetingType())
                .targetDepartment(request.getTargetDepartment())
                .isAiGenerated(request.getIsAiGenerated())
                .aiPrompt(request.getAiPrompt())
                .targetUrl(request.getTargetUrl())
                .difficultyLevel(request.getDifficultyLevel())
                .staticTemplateId(request.getStaticTemplateId())
                .status(CampaignStatus.DRAFT)
                .scheduledFor(request.getScheduledFor())
                .build();

        // 3. Persist campaign
        Campaign savedCampaign = campaignRepository.save(campaign);

        // 4. If individual targeting, validate and save selected employee IDs
        if (request.getTargetingType() == TargetingType.INDIVIDUAL && request.getTargetEmployeeIds() != null) {
            List<UUID> validEmployeeIds = employeeRepository.findAllById(request.getTargetEmployeeIds()).stream()
                    .filter(e -> e.getCompanyId().equals(companyId) && e.isActive())
                    .map(com.genphish.campaign.entity.Employee::getId)
                    .toList();

            if (validEmployeeIds.isEmpty()) {
                throw new InvalidOperationException("No valid active employees found for the provided IDs in your company.");
            }

            List<CampaignTarget> targets = validEmployeeIds.stream()
                    .map(empId -> CampaignTarget.builder()
                            .campaignId(savedCampaign.getId())
                            .employeeId(empId)
                            .build())
                    .toList();
            campaignTargetRepository.saveAll(targets);
        }

        // 5. If AI mode, fire Kafka event to Python AI service
        if (Boolean.TRUE.equals(request.getIsAiGenerated())) {
            savedCampaign.setStatus(CampaignStatus.GENERATING);
            campaignRepository.save(savedCampaign);
            aiGenerationRequestProducer.sendGenerationRequest(savedCampaign);
            log.info("AI generation request sent for campaign: {}", savedCampaign.getId());
        } else {
            log.info("Static campaign created successfully: {} for company: {}", savedCampaign.getId(), companyId);
        }

        return mapToResponse(savedCampaign);
    }

    @Override
    public CampaignResponse getCampaignById(UUID companyId, UUID campaignId) {
        Campaign campaign = findCampaignOrThrow(companyId, campaignId);
        return mapToResponse(campaign);
    }

    @Override
    @Transactional
    public CampaignResponse regenerateAiContent(UUID companyId, UUID campaignId, RegenerateAiCampaignRequest request) {
        Campaign campaign = findCampaignOrThrow(companyId, campaignId);

        if (!Boolean.TRUE.equals(campaign.isAiGenerated())) {
            throw new InvalidOperationException("Cannot regenerate AI content for a non-AI (static) campaign.");
        }

        if (campaign.getStatus() != CampaignStatus.DRAFT && campaign.getStatus() != CampaignStatus.FAILED && campaign.getStatus() != CampaignStatus.SCHEDULED) {
            throw new InvalidOperationException("Cannot regenerate AI content for a campaign that is " + campaign.getStatus());
        }

        // Update prompt if provided
        if (request.getNewPrompt() != null && !request.getNewPrompt().isBlank()) {
            campaign.setAiPrompt(request.getNewPrompt());
        }

        // Set status to generating
        campaign.setStatus(CampaignStatus.GENERATING);
        Campaign savedCampaign = campaignRepository.save(campaign);

        // Fire Kafka event to Python AI service
        aiGenerationRequestProducer.sendGenerationRequest(
                savedCampaign, 
                request.getScope(), 
                savedCampaign.getMongoTemplateId()
        );
        
        log.info("AI regeneration request sent for campaign: {} with scope: {}", savedCampaign.getId(), request.getScope());

        return mapToResponse(savedCampaign);
    }

    @Override
    public List<CampaignResponse> getAllCampaigns(UUID companyId) {
        return campaignRepository.findAllByCompanyIdAndIsDeleted(companyId, false).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional
    public CampaignResponse startCampaign(UUID companyId, UUID campaignId) {
        Campaign campaign = findCampaignOrThrow(companyId, campaignId);

        // Can only start from DRAFT (static) or SCHEDULED states
        if (campaign.getStatus() != CampaignStatus.DRAFT && campaign.getStatus() != CampaignStatus.SCHEDULED) {
            throw new InvalidOperationException("Campaign can only be started from DRAFT or SCHEDULED status. Current: " + campaign.getStatus());
        }

        // AI campaigns must have content generated first
        if (campaign.isAiGenerated() && campaign.getMongoTemplateId() == null) {
            throw new InvalidOperationException("AI campaign content has not been generated yet. Wait for AI processing to complete.");
        }

        // Calculate and save the target count before launching
        long targetCount = switch (campaign.getTargetingType()) {
            case ALL_COMPANY -> employeeRepository.countByCompanyIdAndIsActive(companyId, true);
            case DEPARTMENT -> employeeRepository.countByCompanyIdAndDepartmentAndIsActive(companyId, campaign.getTargetDepartment(), true);
            case INDIVIDUAL -> campaignTargetRepository.countByCampaignId(campaignId);
            case HIGH_RISK -> employeeRepository.findAllByCompanyIdAndRiskScoreGreaterThanEqualAndIsActive(companyId, highRiskThreshold, true).size();
        };
        campaign.setTargetCount(targetCount);
        campaign.setStatus(CampaignStatus.IN_PROGRESS);
        campaignRepository.save(campaign);

        // Fire email delivery event to Go service via Kafka
        emailDeliveryProducer.sendDeliveryRequest(campaign);
        log.info("Campaign started, email delivery triggered: {}", campaignId);

        return mapToResponse(campaign);
    }

    @Override
    @Transactional
    public void deleteCampaign(UUID companyId, UUID campaignId) {
        Campaign campaign = findCampaignOrThrow(companyId, campaignId);

        if (campaign.getStatus() == CampaignStatus.IN_PROGRESS) {
            throw new InvalidOperationException("Cannot delete an active campaign. Complete or pause it first.");
        }

        // Soft delete: preserve data for analytics, just hide from UI
        campaign.setDeleted(true);
        campaign.setDeletedAt(java.time.LocalDateTime.now());
        campaignRepository.save(campaign);
        log.info("Campaign soft-deleted: {}", campaignId);
    }

    // ── Private Helpers ──

    private Campaign findCampaignOrThrow(UUID companyId, UUID campaignId) {
        return campaignRepository.findById(campaignId)
                .filter(c -> c.getCompanyId().equals(companyId))
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", "id", campaignId));
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
                .staticTemplateId(campaign.getStaticTemplateId())
                .status(campaign.getStatus())
                .scheduledFor(campaign.getScheduledFor())
                .createdAt(campaign.getCreatedAt())
                .build();
    }
}