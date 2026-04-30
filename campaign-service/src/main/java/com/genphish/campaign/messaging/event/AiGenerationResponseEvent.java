package com.genphish.campaign.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

import com.genphish.campaign.entity.enums.AiGenerationStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiGenerationResponseEvent {
    private UUID campaignId;
    private String mongoTemplateId;   // Reference to AI-generated content in MongoDB
    private AiGenerationStatus status;
    private String errorMessage;      // Populated if status is FAILED
    private Boolean fallbackUsed;     // true ise içerik fallback template üzerinden üretildi
}
