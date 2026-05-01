package com.genphish.campaign.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignFunnelResponse {
    private UUID campaignId;
    private long targetCount;
    private long emailsDelivered; // Simplified to targetCount for now
    private long emailsOpened;
    private long linksClicked;
    private long credentialsSubmitted;
    private long downloadTriggered;
    private long consentGranted;
    private long actionsTaken;
    private double openRate;
    private double clickRate;
    private double submitRate;
    private double actionRate;
}
