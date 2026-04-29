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
}
