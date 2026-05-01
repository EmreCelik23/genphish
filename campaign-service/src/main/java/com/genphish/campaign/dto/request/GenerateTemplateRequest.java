package com.genphish.campaign.dto.request;

import com.genphish.campaign.entity.enums.DifficultyLevel;
import com.genphish.campaign.entity.enums.LanguageCode;
import com.genphish.campaign.entity.enums.TemplateCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GenerateTemplateRequest {

    @NotBlank(message = "Template name is required")
    private String name;

    private String category = "AI Generated"; // Optional display tag

    @NotBlank(message = "AI prompt is required")
    private String prompt; // Scenario description for the LLM

    private String targetUrl; // URL to clone for landing page

    @NotNull(message = "Template category is required")
    private TemplateCategory templateCategory;

    private String referenceImageUrl; // Optional reference image used for multimodal generation

    @NotNull(message = "Language code is required")
    private LanguageCode languageCode = LanguageCode.TR;

    @NotNull(message = "Difficulty level must be specified")
    private DifficultyLevel difficultyLevel = DifficultyLevel.PROFESSIONAL;

    private String aiProvider; // Optional: openai, anthropic, gemini, stub
    
    private String aiModel; // Optional: provider-specific model override
    
    private boolean allowFallbackTemplate = false; // Optional: AI fail olursa fallback template kullan
}
