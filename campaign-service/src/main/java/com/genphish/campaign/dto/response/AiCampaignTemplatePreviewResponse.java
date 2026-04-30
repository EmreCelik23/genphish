package com.genphish.campaign.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AiCampaignTemplatePreviewResponse {
    private UUID campaignId;
    private String campaignName;
    private String mongoTemplateId;
    private boolean fallbackContentUsed;
    private String subject;
    private String bodyHtml;
    private String landingPageCode;
}
