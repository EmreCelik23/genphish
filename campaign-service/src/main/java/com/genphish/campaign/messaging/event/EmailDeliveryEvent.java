package com.genphish.campaign.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailDeliveryEvent {
    private UUID campaignId;
    private UUID companyId;
    private UUID employeeId;
    private String recipientName;     // For {{name}} placeholder replacement
    private String recipientEmail;
    private String department;        // For {{department}} placeholder replacement
    private String emailSubject;
    private String emailBodyHtml;     // Full HTML email content
    private String trackingPixelUrl;  // Unique 1x1 pixel URL for open tracking
    private String phishingLinkUrl;   // Unique phishing link for click tracking
    private boolean qrCodeEnabled;    // Include QR code instead of link
}
