package com.genphish.campaign.service;

import com.genphish.campaign.dto.request.CreateCampaignRequest;
import com.genphish.campaign.dto.request.RegenerateAiCampaignRequest;
import com.genphish.campaign.dto.response.CampaignResponse;

import java.util.List;
import java.util.UUID;

public interface CampaignService {

    CampaignResponse createCampaign(UUID companyId, CreateCampaignRequest request);

    CampaignResponse getCampaignById(UUID companyId, UUID campaignId);

    CampaignResponse regenerateAiContent(UUID companyId, UUID campaignId, RegenerateAiCampaignRequest request);

    List<CampaignResponse> getAllCampaigns(UUID companyId);

    CampaignResponse startCampaign(UUID companyId, UUID campaignId);

    void deleteCampaign(UUID companyId, UUID campaignId);
}