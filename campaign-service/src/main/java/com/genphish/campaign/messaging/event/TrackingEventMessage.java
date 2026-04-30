package com.genphish.campaign.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TrackingEventMessage {
    private UUID campaignId;
    private UUID employeeId;
    private UUID companyId;
    private String eventType;          // EMAIL_OPENED, LINK_CLICKED, CREDENTIALS_SUBMITTED
    private Instant timestamp;
    private String userAgent;
    private String ipAddress;
}
