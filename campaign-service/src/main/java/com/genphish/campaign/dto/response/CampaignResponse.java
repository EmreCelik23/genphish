package com.genphish.campaign.dto.response;

import com.genphish.campaign.entity.enums.CampaignStatus;
import com.genphish.campaign.entity.enums.TargetingType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class CampaignResponse {
    private UUID id;
    private UUID companyId;
    private String name;
    private TargetingType targetingType;
    private String targetDepartment;
    private UUID templateId;
    private boolean qrCodeEnabled;
    private CampaignStatus status;
    private LocalDateTime scheduledFor;
    private LocalDateTime createdAt;
}
