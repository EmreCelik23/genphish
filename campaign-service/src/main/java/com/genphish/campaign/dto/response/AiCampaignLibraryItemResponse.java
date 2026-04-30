package com.genphish.campaign.dto.response;

import com.genphish.campaign.entity.enums.CampaignStatus;
import com.genphish.campaign.entity.enums.DifficultyLevel;
import com.genphish.campaign.entity.enums.LanguageCode;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AiCampaignLibraryItemResponse {
    private UUID campaignId;
    private String name;
    private String aiPrompt;
    private DifficultyLevel difficultyLevel;
    private LanguageCode languageCode;
    private String aiProvider;
    private String aiModel;
    private boolean allowFallbackTemplate;
    private boolean fallbackContentUsed;
    private CampaignStatus status;
    private LocalDateTime createdAt;
}
