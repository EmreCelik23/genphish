package com.genphish.campaign.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackingEventMessage {
    private UUID campaignId;
    private UUID employeeId;
    private UUID companyId;
    private String eventType;          // EMAIL_OPENED, LINK_CLICKED, CREDENTIALS_SUBMITTED
    private LocalDateTime timestamp;
}
