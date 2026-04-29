package com.genphish.campaign.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

import com.genphish.campaign.entity.enums.RegenerationScope;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiGenerationRequestEvent {
    private UUID campaignId;
    private UUID companyId;
    private String prompt;            // Scenario description
    private String targetUrl;         // URL to clone for landing page
    private String difficultyLevel;   // AMATEUR or PROFESSIONAL
    
    // Support for partial regeneration
    private RegenerationScope regenerationScope;
    private String existingMongoTemplateId;
}
