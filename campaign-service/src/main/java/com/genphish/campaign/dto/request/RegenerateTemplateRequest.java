package com.genphish.campaign.dto.request;

import com.genphish.campaign.entity.enums.RegenerationScope;
import com.genphish.campaign.entity.enums.TemplateCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegenerateTemplateRequest {

    @NotBlank(message = "Prompt cannot be empty for regeneration")
    private String prompt;

    @NotNull(message = "Regeneration scope is required")
    private RegenerationScope scope;

    // Optional overrides for AI Engine
    private String aiProvider;
    private String aiModel;
    private TemplateCategory templateCategory;
    private String referenceImageUrl;
}
