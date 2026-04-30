package com.genphish.campaign.service.impl;

import com.genphish.campaign.dto.request.CreateCampaignRequest;
import com.genphish.campaign.dto.request.ScheduleCampaignRequest;
import com.genphish.campaign.dto.response.CampaignResponse;
import com.genphish.campaign.entity.Campaign;
import com.genphish.campaign.entity.CampaignTarget;
import com.genphish.campaign.entity.PhishingTemplate;
import com.genphish.campaign.entity.enums.CampaignStatus;
import com.genphish.campaign.entity.enums.TargetingType;
import com.genphish.campaign.entity.enums.TemplateStatus;
import com.genphish.campaign.exception.InvalidOperationException;
import com.genphish.campaign.exception.ResourceNotFoundException;
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

import java.util.HashSet;
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
    private final EmailDeliveryProducer emailDeliveryProducer;
    private final org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

    @org.springframework.beans.factory.annotation.Value("${app.campaign.high-risk-threshold:70.0}")
    private Double highRiskThreshold;

    @Override
    @Transactional
    public CampaignResponse createCampaign(UUID companyId, CreateCampaignRequest request) {
        validateTemplate(companyId, request.getTemplateId());
        validateDepartmentTargeting(request);
        List<UUID> validatedIndividualTargetIds = validateAndResolveIndividualTargets(companyId, request);

        Campaign savedCampaign = campaignRepository.save(buildCampaignEntity(companyId, request));
        persistIndividualTargetsIfNeeded(savedCampaign.getId(), request.getTargetingType(), validatedIndividualTargetIds);

        log.info("Campaign created successfully: {} for company: {}", savedCampaign.getId(), companyId);
        return mapToResponse(savedCampaign);
    }

    @Override
    public CampaignResponse getCampaignById(UUID companyId, UUID campaignId) {
        Campaign campaign = findCampaignOrThrow(companyId, campaignId);
        return mapToResponse(campaign);
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

        // Can only start from READY or SCHEDULED states
        if (campaign.getStatus() != CampaignStatus.READY && campaign.getStatus() != CampaignStatus.SCHEDULED) {
            throw new InvalidOperationException("Campaign can only be started from READY or SCHEDULED status. Current: " + campaign.getStatus());
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
    public CampaignResponse scheduleCampaign(UUID companyId, UUID campaignId, ScheduleCampaignRequest request) {
        Campaign campaign = findCampaignOrThrow(companyId, campaignId);

        if (campaign.getStatus() != CampaignStatus.READY) {
            throw new InvalidOperationException("Campaign can only be scheduled from READY status. Current: " + campaign.getStatus());
        }

        campaign.setScheduledFor(request.getScheduledFor());
        campaign.setStatus(CampaignStatus.SCHEDULED);
        campaignRepository.save(campaign);

        log.info("Campaign scheduled: {} for {}", campaignId, request.getScheduledFor());
        return mapToResponse(campaign);
    }

    @Override
    @Transactional
    public CampaignResponse cancelCampaign(UUID companyId, UUID campaignId) {
        Campaign campaign = findCampaignOrThrow(companyId, campaignId);

        if (campaign.getStatus() != CampaignStatus.SCHEDULED && campaign.getStatus() != CampaignStatus.IN_PROGRESS) {
            throw new InvalidOperationException("Only SCHEDULED or IN_PROGRESS campaigns can be canceled. Current status: " + campaign.getStatus());
        }

        campaign.setStatus(CampaignStatus.CANCELED);
        campaignRepository.save(campaign);

        // Publish event to notify Go-Email-Service to drop any pending emails for this campaign
        com.genphish.campaign.messaging.event.CampaignCanceledEvent event = com.genphish.campaign.messaging.event.CampaignCanceledEvent.builder()
                .campaignId(campaignId)
                .companyId(companyId)
                .canceledAt(java.time.Instant.now())
                .build();
        
        kafkaTemplate.send(
                com.genphish.campaign.config.KafkaConfig.TOPIC_CAMPAIGN_CANCELED,
                campaignId.toString(),
                event
        );

        log.info("Campaign canceled (Emergency Stop) and event published: {}", campaignId);
        return mapToResponse(campaign);
    }

    @Override
    @Transactional
    public void deleteCampaign(UUID companyId, UUID campaignId) {
        Campaign campaign = findCampaignOrThrow(companyId, campaignId);

        if (campaign.getStatus() == CampaignStatus.IN_PROGRESS || campaign.getStatus() == CampaignStatus.SCHEDULED) {
            throw new InvalidOperationException("Cannot delete an active or scheduled campaign. Please cancel it first.");
        }

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

    private void validateTemplate(UUID companyId, UUID templateId) {
        PhishingTemplate template = phishingTemplateRepository.findByIdAndIsActive(templateId, true)
                .orElseThrow(() -> new ResourceNotFoundException("PhishingTemplate", "id", templateId));
        
        if (template.getCompanyId() != null && !template.getCompanyId().equals(companyId)) {
             throw new ResourceNotFoundException("PhishingTemplate", "id", templateId);
        }
        
        if (template.getStatus() != TemplateStatus.READY) {
            throw new InvalidOperationException("Cannot create a campaign with a template that is not READY. Current status: " + template.getStatus());
        }
    }

    private void validateDepartmentTargeting(CreateCampaignRequest request) {
        if (request.getTargetingType() == TargetingType.DEPARTMENT
                && (request.getTargetDepartment() == null || request.getTargetDepartment().isBlank())) {
            throw new InvalidOperationException("Target department is required for DEPARTMENT targeting.");
        }
    }

    private List<UUID> validateAndResolveIndividualTargets(UUID companyId, CreateCampaignRequest request) {
        if (request.getTargetingType() != TargetingType.INDIVIDUAL) {
            return List.of();
        }

        if (request.getTargetEmployeeIds() == null || request.getTargetEmployeeIds().isEmpty()) {
            throw new InvalidOperationException("At least one employee ID is required for INDIVIDUAL targeting.");
        }

        List<UUID> validEmployeeIds = employeeRepository.findAllById(request.getTargetEmployeeIds()).stream()
                .filter(e -> e.getCompanyId().equals(companyId) && e.isActive())
                .map(com.genphish.campaign.entity.Employee::getId)
                .distinct()
                .toList();

        int requestedDistinctCount = new HashSet<>(request.getTargetEmployeeIds()).size();
        if (validEmployeeIds.size() != requestedDistinctCount) {
            throw new InvalidOperationException("Some selected employees are invalid, inactive, or belong to another company.");
        }
        return validEmployeeIds;
    }

    private Campaign buildCampaignEntity(UUID companyId, CreateCampaignRequest request) {
        return Campaign.builder()
                .companyId(companyId)
                .name(request.getName())
                .targetingType(request.getTargetingType())
                .targetDepartment(request.getTargetDepartment())
                .templateId(request.getTemplateId())
                .qrCodeEnabled(request.isQrCodeEnabled())
                .status(CampaignStatus.READY) // Since the template is already READY, campaign is READY immediately
                .build();
    }

    private void persistIndividualTargetsIfNeeded(UUID campaignId, TargetingType targetingType, List<UUID> targetEmployeeIds) {
        if (targetingType != TargetingType.INDIVIDUAL) {
            return;
        }

        List<CampaignTarget> targets = targetEmployeeIds.stream()
                .map(empId -> CampaignTarget.builder()
                        .campaignId(campaignId)
                        .employeeId(empId)
                        .build())
                .toList();
        campaignTargetRepository.saveAll(targets);
    }

    private CampaignResponse mapToResponse(Campaign campaign) {
        return CampaignResponse.builder()
                .id(campaign.getId())
                .companyId(campaign.getCompanyId())
                .name(campaign.getName())
                .targetingType(campaign.getTargetingType())
                .targetDepartment(campaign.getTargetDepartment())
                .templateId(campaign.getTemplateId())
                .qrCodeEnabled(campaign.isQrCodeEnabled())
                .status(campaign.getStatus())
                .scheduledFor(campaign.getScheduledFor())
                .createdAt(campaign.getCreatedAt())
                .build();
    }
}
