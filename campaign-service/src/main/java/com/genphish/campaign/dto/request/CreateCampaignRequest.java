package com.genphish.campaign.dto.request;

import com.genphish.campaign.entity.enums.DifficultyLevel;
import com.genphish.campaign.entity.enums.LanguageCode;
import com.genphish.campaign.entity.enums.TargetingType;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class CreateCampaignRequest {

    @NotBlank(message = "Campaign name is required")
    private String name;

    // ── Targeting ──

    @NotNull(message = "Targeting type must be specified")
    private TargetingType targetingType; // ALL_COMPANY, DEPARTMENT, INDIVIDUAL, HIGH_RISK

    private String targetDepartment; // Required when targetingType = DEPARTMENT

    private List<UUID> targetEmployeeIds; // Required when targetingType = INDIVIDUAL

    // ── AI vs Static ──

    @NotNull(message = "AI generation mode must be specified")
    private Boolean isAiGenerated; // true = AI mode, false = static template

    // AI mode fields (required when isAiGenerated = true)
    private String aiPrompt; // Scenario description for the LLM
    private String targetUrl; // URL to clone for landing page
    private String languageCode = LanguageCode.TR.name(); // Optional: TR or EN (default TR)
    private String aiProvider; // Optional: openai, anthropic, gemini, stub
    private String aiModel; // Optional: provider-specific model override
    private boolean allowFallbackTemplate = false; // Optional: AI fail olursa fallback template kullan

    @NotNull(message = "Difficulty level must be specified")
    private DifficultyLevel difficultyLevel = DifficultyLevel.PROFESSIONAL; // AMATEUR or PROFESSIONAL (default PROFESSIONAL)

    // Static mode field (required when isAiGenerated = false)
    private UUID staticTemplateId; // Reference to pre-built template

    // ── Scheduling ──

    @FutureOrPresent(message = "Scheduled time must be in the present or future")
    private LocalDateTime scheduledFor; // Null = start immediately when triggered

    // ── QR Code (Quishing) ──

    private boolean qrCodeEnabled; // Include QR code instead of link in email
}
