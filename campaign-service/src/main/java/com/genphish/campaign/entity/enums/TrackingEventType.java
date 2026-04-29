package com.genphish.campaign.entity.enums;

public enum TrackingEventType {
    EMAIL_OPENED,           // Tracking pixel loaded (mail opened)
    LINK_CLICKED,           // Phishing link clicked
    CREDENTIALS_SUBMITTED   // Data entered on fake page (password NOT stored, only flag)
}
