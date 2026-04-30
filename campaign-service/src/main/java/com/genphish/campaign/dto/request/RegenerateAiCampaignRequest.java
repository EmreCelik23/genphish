package com.genphish.campaign.dto.request;

import com.genphish.campaign.entity.enums.RegenerationScope;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegenerateAiCampaignRequest {
    
    @NotNull(message = "Regeneration scope is required (ALL, ONLY_EMAIL, ONLY_LANDING_PAGE)")
    private RegenerationScope scope;
    
    // Optional. If provided, replaces the old prompt. If null, re-uses the old prompt.
    private String newPrompt;

    // Optional runtime AI overrides
    private String languageCode; // TR or EN
    private String aiProvider;   // openai, anthropic, gemini, stub
    private String aiModel;      // provider-specific model name
    private Boolean allowFallbackTemplate; // Optional: true ise AI fail durumunda fallback template kullan
}
