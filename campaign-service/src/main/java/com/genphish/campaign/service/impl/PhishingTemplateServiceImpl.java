package com.genphish.campaign.service.impl;

import com.genphish.campaign.dto.request.GenerateTemplateRequest;
import com.genphish.campaign.dto.response.PhishingTemplateResponse;
import com.genphish.campaign.entity.PhishingTemplate;
import com.genphish.campaign.entity.enums.TemplateStatus;
import com.genphish.campaign.entity.enums.TemplateType;
import com.genphish.campaign.exception.ResourceNotFoundException;
import com.genphish.campaign.messaging.producer.AiGenerationRequestProducer;
import com.genphish.campaign.repository.PhishingTemplateRepository;
import com.genphish.campaign.service.PhishingTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhishingTemplateServiceImpl implements PhishingTemplateService {

    private final PhishingTemplateRepository phishingTemplateRepository;
    private final AiGenerationRequestProducer aiGenerationRequestProducer;

    @Override
    @Transactional(readOnly = true)
    public List<PhishingTemplateResponse> getAllActiveTemplates(UUID companyId) {
        log.debug("Fetching all active phishing templates for company {}", companyId);
        return phishingTemplateRepository.findAll().stream()
                .filter(PhishingTemplate::isActive)
                .filter(t -> t.getCompanyId() == null || t.getCompanyId().equals(companyId)) // Static (global) OR specific to this company
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PhishingTemplateResponse getTemplateById(UUID companyId, UUID templateId) {
        log.debug("Fetching phishing template with id: {}", templateId);
        PhishingTemplate template = phishingTemplateRepository.findByIdAndIsActive(templateId, true)
                .orElseThrow(() -> new ResourceNotFoundException("PhishingTemplate", "id", templateId));
                
        // Ensure the template is either global or belongs to the requesting company
        if (template.getCompanyId() != null && !template.getCompanyId().equals(companyId)) {
            throw new ResourceNotFoundException("PhishingTemplate", "id", templateId);
        }
        
        return mapToResponse(template);
    }

    @Override
    @Transactional
    public PhishingTemplateResponse generateAiTemplate(UUID companyId, GenerateTemplateRequest request) {
        log.info("Generating AI Phishing Template: {} for company: {}", request.getName(), companyId);

        PhishingTemplate template = PhishingTemplate.builder()
                .companyId(companyId)
                .name(request.getName())
                .category(request.getCategory())
                .type(TemplateType.AI_GENERATED)
                .status(TemplateStatus.GENERATING)
                .prompt(request.getPrompt())
                .difficultyLevel(request.getDifficultyLevel())
                .languageCode(request.getLanguageCode())
                .targetUrl(request.getTargetUrl())
                .fallbackContentUsed(false)
                .isActive(true)
                .build();

        phishingTemplateRepository.save(template);

        // Send Kafka event to AI Engine
        aiGenerationRequestProducer.sendGenerationRequest(template, request.getAiProvider(), request.getAiModel(), request.isAllowFallbackTemplate());

        return mapToResponse(template);
    }

    @Override
    @Transactional
    public PhishingTemplateResponse updateTemplate(UUID companyId, UUID templateId, com.genphish.campaign.dto.request.UpdateTemplateRequest request) {
        log.info("Manually updating phishing template: {} for company: {}", templateId, companyId);
        PhishingTemplate template = phishingTemplateRepository.findByIdAndIsActive(templateId, true)
                .orElseThrow(() -> new ResourceNotFoundException("PhishingTemplate", "id", templateId));

        if (template.getCompanyId() != null && !template.getCompanyId().equals(companyId)) {
            throw new ResourceNotFoundException("PhishingTemplate", "id", templateId);
        }
        
        // Cannot manually update global static templates through this API
        if (template.getCompanyId() == null) {
            throw new com.genphish.campaign.exception.InvalidOperationException("Cannot update global static templates.");
        }

        template.setName(request.getName());
        template.setEmailSubject(request.getEmailSubject());
        template.setEmailBody(request.getEmailBody());
        
        if (request.getLandingPageHtml() != null && !request.getLandingPageHtml().isBlank()) {
            template.setLandingPageHtml(request.getLandingPageHtml());
        }

        // Keep status as READY, no need to touch AI or Mongo
        phishingTemplateRepository.save(template);
        return mapToResponse(template);
    }

    @Override
    @Transactional
    public PhishingTemplateResponse regenerateAiTemplate(UUID companyId, UUID templateId, com.genphish.campaign.dto.request.RegenerateTemplateRequest request) {
        log.info("Regenerating AI Phishing Template: {} for company: {}", templateId, companyId);
        PhishingTemplate template = phishingTemplateRepository.findByIdAndIsActive(templateId, true)
                .orElseThrow(() -> new ResourceNotFoundException("PhishingTemplate", "id", templateId));

        if (template.getCompanyId() != null && !template.getCompanyId().equals(companyId)) {
            throw new ResourceNotFoundException("PhishingTemplate", "id", templateId);
        }
        
        // Cannot regenerate global static templates
        if (template.getCompanyId() == null) {
            throw new com.genphish.campaign.exception.InvalidOperationException("Cannot regenerate global static templates.");
        }
        
        if (template.getMongoTemplateId() == null) {
            throw new com.genphish.campaign.exception.InvalidOperationException("Cannot regenerate template because Mongo template ID is missing.");
        }

        template.setPrompt(request.getPrompt());
        template.setStatus(TemplateStatus.GENERATING);
        phishingTemplateRepository.save(template);

        aiGenerationRequestProducer.sendGenerationRequest(
                template,
                request.getAiProvider(),
                request.getAiModel(),
                false, // allowFallbackTemplate false for regeneration
                request.getScope(),
                template.getMongoTemplateId()
        );

        return mapToResponse(template);
    }

    @Override
    @Transactional
    public void deleteTemplate(UUID companyId, UUID templateId) {
        log.info("Attempting to delete phishing template {} for company {}", templateId, companyId);
        PhishingTemplate template = phishingTemplateRepository.findByIdAndIsActive(templateId, true)
                .orElseThrow(() -> new ResourceNotFoundException("PhishingTemplate", "id", templateId));
                
        if (template.getCompanyId() != null && !template.getCompanyId().equals(companyId)) {
            throw new ResourceNotFoundException("PhishingTemplate", "id", templateId);
        }
        
        // Cannot delete global static templates through this API
        if (template.getCompanyId() == null) {
            throw new com.genphish.campaign.exception.InvalidOperationException("Cannot delete global static templates.");
        }
        
        // We shouldn't delete if it's used in any campaigns
        // NOTE: You would need campaignRepository injected for this. 
        // We will just soft delete the template for now.
        
        template.setActive(false);
        phishingTemplateRepository.save(template);
        log.info("Successfully deleted phishing template {}", templateId);
    }

    private PhishingTemplateResponse mapToResponse(PhishingTemplate template) {
        return PhishingTemplateResponse.builder()
                .id(template.getId())
                .companyId(template.getCompanyId())
                .name(template.getName())
                .category(template.getCategory())
                .type(template.getType())
                .status(template.getStatus())
                .difficultyLevel(template.getDifficultyLevel())
                .languageCode(template.getLanguageCode())
                .emailSubject(template.getEmailSubject())
                .emailBody(template.getEmailBody())
                .landingPageHtml(template.getLandingPageHtml())
                .prompt(template.getPrompt())
                .targetUrl(template.getTargetUrl())
                .fallbackContentUsed(template.isFallbackContentUsed())
                .build();
    }
}
