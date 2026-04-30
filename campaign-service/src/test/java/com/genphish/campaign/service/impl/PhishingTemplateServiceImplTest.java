package com.genphish.campaign.service.impl;

import com.genphish.campaign.dto.request.GenerateTemplateRequest;
import com.genphish.campaign.dto.request.RegenerateTemplateRequest;
import com.genphish.campaign.dto.request.UpdateTemplateRequest;
import com.genphish.campaign.dto.response.PhishingTemplateResponse;
import com.genphish.campaign.entity.PhishingTemplate;
import com.genphish.campaign.entity.enums.RegenerationScope;
import com.genphish.campaign.entity.enums.TemplateStatus;
import com.genphish.campaign.messaging.producer.AiGenerationRequestProducer;
import com.genphish.campaign.repository.PhishingTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PhishingTemplateServiceImplTest {

    @Mock
    private PhishingTemplateRepository phishingTemplateRepository;

    @Mock
    private AiGenerationRequestProducer aiGenerationRequestProducer;

    @InjectMocks
    private PhishingTemplateServiceImpl phishingTemplateService;

    private UUID companyId;
    private UUID templateId;
    private PhishingTemplate template;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        templateId = UUID.randomUUID();

        template = PhishingTemplate.builder()
                .id(templateId)
                .companyId(companyId)
                .name("Old Name")
                .status(TemplateStatus.READY)
                .mongoTemplateId("mongo_123")
                .isActive(true)
                .build();
    }

    @Test
    void generateAiTemplate_Success() {
        GenerateTemplateRequest request = new GenerateTemplateRequest();
        request.setName("New AI Template");
        request.setPrompt("Generate password reset");
        request.setCategory("Test");

        PhishingTemplateResponse response = phishingTemplateService.generateAiTemplate(companyId, request);

        assertNotNull(response);
        assertEquals("New AI Template", response.getName());
        assertEquals(TemplateStatus.GENERATING, response.getStatus());

        verify(phishingTemplateRepository).save(any(PhishingTemplate.class));
        verify(aiGenerationRequestProducer).sendGenerationRequest(any(), any(), any(), anyBoolean());
    }

    @Test
    void updateTemplate_Success() {
        UpdateTemplateRequest request = new UpdateTemplateRequest();
        request.setName("Updated Name");
        request.setEmailSubject("Updated Subject");
        request.setEmailBody("<html>Updated</html>");

        when(phishingTemplateRepository.findByIdAndIsActive(templateId, true))
                .thenReturn(Optional.of(template));

        PhishingTemplateResponse response = phishingTemplateService.updateTemplate(companyId, templateId, request);

        assertEquals("Updated Name", response.getName());
        assertEquals("Updated Subject", response.getEmailSubject());
        verify(phishingTemplateRepository).save(template);
    }

    @Test
    void regenerateAiTemplate_Success() {
        RegenerateTemplateRequest request = new RegenerateTemplateRequest();
        request.setPrompt("Make it more professional");
        request.setScope(RegenerationScope.ONLY_EMAIL);

        when(phishingTemplateRepository.findByIdAndIsActive(templateId, true))
                .thenReturn(Optional.of(template));

        PhishingTemplateResponse response = phishingTemplateService.regenerateAiTemplate(companyId, templateId, request);

        assertEquals(TemplateStatus.GENERATING, response.getStatus());
        assertEquals("Make it more professional", template.getPrompt());

        verify(aiGenerationRequestProducer).sendGenerationRequest(
                eq(template), any(), any(), eq(false), eq(RegenerationScope.ONLY_EMAIL), eq("mongo_123")
        );
        verify(phishingTemplateRepository).save(template);
    }

    @Test
    void deleteTemplate_Success() {
        when(phishingTemplateRepository.findByIdAndIsActive(templateId, true))
                .thenReturn(Optional.of(template));

        phishingTemplateService.deleteTemplate(companyId, templateId);

        assertFalse(template.isActive());
        verify(phishingTemplateRepository).save(template);
    }
}
