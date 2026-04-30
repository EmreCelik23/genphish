package com.genphish.campaign.dto.request;

import com.genphish.campaign.entity.enums.TargetingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateCampaignRequest {

    @NotBlank(message = "Campaign name is required")
    private String name;

    // ── Targeting ──

    @NotNull(message = "Targeting type must be specified")
    private TargetingType targetingType; // ALL_COMPANY, DEPARTMENT, INDIVIDUAL, HIGH_RISK

    private String targetDepartment; // Required when targetingType = DEPARTMENT

    private List<UUID> targetEmployeeIds; // Required when targetingType = INDIVIDUAL

    // ── Content ──

    @NotNull(message = "Template ID must be specified")
    private UUID templateId; // Reference to a PhishingTemplate (Static or AI generated)

    // ── Extras ──

    private boolean qrCodeEnabled; // Include QR code instead of link in email
}
