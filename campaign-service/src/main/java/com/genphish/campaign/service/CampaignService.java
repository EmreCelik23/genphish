package com.genphish.campaign.service;

import com.genphish.campaign.dto.request.CreateCampaignRequest;
import com.genphish.campaign.dto.request.ScheduleCampaignRequest;
import com.genphish.campaign.dto.response.CampaignResponse;

import java.util.List;
import java.util.UUID;

public interface CampaignService {

    CampaignResponse createCampaign(UUID companyId, CreateCampaignRequest request);

    CampaignResponse getCampaignById(UUID companyId, UUID campaignId);

    List<CampaignResponse> getAllCampaigns(UUID companyId);

    CampaignResponse startCampaign(UUID companyId, UUID campaignId);

    CampaignResponse scheduleCampaign(UUID companyId, UUID campaignId, ScheduleCampaignRequest request);

    CampaignResponse cancelCampaign(UUID companyId, UUID campaignId);

    void deleteCampaign(UUID companyId, UUID campaignId);
}