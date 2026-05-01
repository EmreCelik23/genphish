package models

import (
	"time"

	"github.com/google/uuid"
)

type TrackingEventType string

const (
	EventEmailOpened          TrackingEventType = "EMAIL_OPENED"
	EventLinkClicked          TrackingEventType = "LINK_CLICKED"
	EventCredentialsSubmitted TrackingEventType = "CREDENTIALS_SUBMITTED"
	EventDownloadTriggered    TrackingEventType = "DOWNLOAD_TRIGGERED"
	EventConsentGranted       TrackingEventType = "CONSENT_GRANTED"
)

type TrackingEvent struct {
	CampaignID uuid.UUID         `json:"campaignId"`
	EmployeeID uuid.UUID         `json:"employeeId"`
	CompanyID  uuid.UUID         `json:"companyId"`
	EventType  TrackingEventType `json:"eventType"`
	Timestamp  time.Time         `json:"timestamp"`
	UserAgent  string            `json:"userAgent,omitempty"`
	IPAddress  string            `json:"ipAddress,omitempty"`
}
