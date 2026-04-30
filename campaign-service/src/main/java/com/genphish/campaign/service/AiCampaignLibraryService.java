package com.genphish.campaign.service;

import com.genphish.campaign.dto.request.CloneCampaignRequest;
import com.genphish.campaign.dto.response.AiCampaignLibraryItemResponse;
import com.genphish.campaign.dto.response.AiCampaignTemplatePreviewResponse;
import com.genphish.campaign.dto.response.CampaignResponse;

import java.util.List;
import java.util.UUID;

public interface AiCampaignLibraryService {

    List<AiCampaignLibraryItemResponse> getAiCampaignLibrary(UUID companyId);

    AiCampaignTemplatePreviewResponse getAiCampaignPreview(UUID companyId, UUID campaignId);

    CampaignResponse cloneCampaign(UUID companyId, UUID campaignId, CloneCampaignRequest request);
}
