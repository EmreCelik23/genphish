package com.genphish.campaign.entity.enums;

public enum TrackingEventType {
    EMAIL_OPENED,           // Tracking pixel loaded (mail opened)
    LINK_CLICKED,           // Phishing link clicked
    CREDENTIALS_SUBMITTED,  // Data entered on fake page (password NOT stored, only flag)
    DOWNLOAD_TRIGGERED,     // User attempted to download a malicious-looking file
    CONSENT_GRANTED         // User granted OAuth app consent in external identity provider
}
