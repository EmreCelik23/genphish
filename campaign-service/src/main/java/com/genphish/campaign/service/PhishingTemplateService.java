package com.genphish.campaign.service;

import com.genphish.campaign.dto.request.GenerateTemplateRequest;
import com.genphish.campaign.dto.response.PhishingTemplateResponse;

import java.util.List;
import java.util.UUID;

public interface PhishingTemplateService {

    List<PhishingTemplateResponse> getAllActiveTemplates(UUID companyId);

    PhishingTemplateResponse getTemplateById(UUID companyId, UUID templateId);

    PhishingTemplateResponse generateAiTemplate(UUID companyId, GenerateTemplateRequest request);
}
