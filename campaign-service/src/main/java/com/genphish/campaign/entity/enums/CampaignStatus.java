package com.genphish.campaign.entity.enums;

public enum CampaignStatus {
    DRAFT,          // AI campaign created but content not yet generated
    GENERATING,     // AI is generating content
    READY,          // Content is available (AI or static), awaiting user approval
    SCHEDULED,      // User approved + picked a future launch time
    IN_PROGRESS,    // Campaign is active, emails are being sent
    COMPLETED,      // Campaign finished, reports are ready
    FAILED,         // AI Generation failed or other terminal error
    CANCELED        // Campaign was manually stopped by user
}
