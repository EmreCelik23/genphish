package com.genphish.campaign.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class EmployeeRiskProfileResponse {
    private UUID employeeId;
    private String fullName;
    private String email;
    private String department;
    private Double riskScore;

    // Participation stats
    private long totalCampaigns;         // Total simulations participated in
    private long emailsOpened;           // Times the tracking pixel was triggered
    private long linksClicked;           // Times the phishing link was clicked
    private long credentialsSubmitted;   // Times data was entered on fake page

    private LocalDateTime lastPhishedAt;
}
