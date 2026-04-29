package com.genphish.campaign.entity.enums;

public enum CampaignStatus {
    DRAFT,          // Not sent to AI yet
    GENERATING,     // AI is generating content
    SCHEDULED,      // Content is ready, waiting to be sent
    IN_PROGRESS,    // Campaign is active, emails are being sent
    COMPLETED,      // Campaign finished, reports are ready
    FAILED          // AI Generation failed or other terminal error
}