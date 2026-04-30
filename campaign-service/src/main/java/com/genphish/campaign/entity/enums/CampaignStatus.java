package com.genphish.campaign.entity.enums;

public enum CampaignStatus {
    DRAFT,          // Content exists (or AI pending approval) but campaign is not launched
    GENERATING,     // AI is generating content
    SCHEDULED,      // Content is ready, waiting to be sent
    IN_PROGRESS,    // Campaign is active, emails are being sent
    COMPLETED,      // Campaign finished, reports are ready
    FAILED          // AI Generation failed or other terminal error
}
