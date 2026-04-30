package com.genphish.campaign.dto.request;

import com.genphish.campaign.entity.enums.DifficultyLevel;
import com.genphish.campaign.entity.enums.LanguageCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GenerateTemplateRequest {

    @NotBlank(message = "Template name is required")
    private String name;

    @NotBlank(message = "Category is required")
    private String category; // e.g., "Custom AI"

    @NotBlank(message = "AI prompt is required")
    private String prompt; // Scenario description for the LLM

    private String targetUrl; // URL to clone for landing page

    @NotNull(message = "Language code is required")
    private LanguageCode languageCode = LanguageCode.TR;

    @NotNull(message = "Difficulty level must be specified")
    private DifficultyLevel difficultyLevel = DifficultyLevel.PROFESSIONAL;

    private String aiProvider; // Optional: openai, anthropic, gemini, stub
    
    private String aiModel; // Optional: provider-specific model override
    
    private boolean allowFallbackTemplate = false; // Optional: AI fail olursa fallback template kullan
}
