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
