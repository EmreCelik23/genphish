package handlers

import (
	"context"
	"errors"
	"fmt"
	"log"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"

	"github.com/EmreCelik23/genphish/tracker-service/internal/config"
	"github.com/EmreCelik23/genphish/tracker-service/internal/kafka"
	"github.com/EmreCelik23/genphish/tracker-service/internal/models"
)

var transparentPixelGIF = []byte{
	0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00,
	0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0xff, 0xff, 0xff, 0x21,
	0xf9, 0x04, 0x01, 0x00, 0x00, 0x00, 0x00, 0x2c, 0x00, 0x00,
	0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x02, 0x02, 0x44,
	0x01, 0x00, 0x3b,
}

type TrackingHandler struct {
	publisher      kafka.EventPublisher
	landingURL     string
	awarenessURL   string
	publishTimeout time.Duration
	logger         *log.Logger
}

type trackingIDs struct {
	campaignID uuid.UUID
	employeeID uuid.UUID
	companyID  uuid.UUID
}

func NewTrackingHandler(
	publisher kafka.EventPublisher,
	redirects config.RedirectConfig,
	publishTimeout time.Duration,
	logger *log.Logger,
) *TrackingHandler {
	return &TrackingHandler{
		publisher:      publisher,
		landingURL:     redirects.LandingPageURL,
		awarenessURL:   redirects.AwarenessPageURL,
		publishTimeout: publishTimeout,
		logger:         logger,
	}
}

func (h *TrackingHandler) TrackOpen(c *gin.Context) {
	ids, err := extractTrackingIDs(c)
	if err != nil {
		h.logger.Printf("open tracking skipped: %v", err)
		writeTransparentPixel(c)
		return
	}

	h.publish(c, ids, models.EventEmailOpened)
	writeTransparentPixel(c)
}

func (h *TrackingHandler) TrackClick(c *gin.Context) {
	languageCode := extractLanguageCode(c)

	ids, err := extractTrackingIDs(c)
	if err != nil {
		h.logger.Printf("click tracking skipped: %v", err)
		c.Redirect(http.StatusFound, appendLanguageQuery(h.landingURL, languageCode))
		return
	}

	h.publish(c, ids, models.EventLinkClicked)
	c.Redirect(http.StatusFound, appendTrackingQuery(h.landingURL, ids, languageCode))
}

func (h *TrackingHandler) TrackSubmit(c *gin.Context) {
	languageCode := extractLanguageCode(c)

	ids, err := extractTrackingIDs(c)
	if err != nil {
		h.logger.Printf("submit tracking skipped: %v", err)
		c.Redirect(http.StatusFound, appendLanguageQuery(h.awarenessURL, languageCode))
		return
	}

	// Intentionally ignores credential fields by design. Only event telemetry is emitted.
	h.publish(c, ids, models.EventCredentialsSubmitted)
	c.Redirect(http.StatusFound, appendTrackingQuery(h.awarenessURL, ids, languageCode))
}

func (h *TrackingHandler) publish(c *gin.Context, ids trackingIDs, eventType models.TrackingEventType) {
	ctx, cancel := context.WithTimeout(c.Request.Context(), h.publishTimeout)
	defer cancel()

	event := models.TrackingEvent{
		CampaignID: ids.campaignID,
		EmployeeID: ids.employeeID,
		CompanyID:  ids.companyID,
		EventType:  eventType,
		Timestamp:  time.Now().UTC(),
		UserAgent:  c.Request.UserAgent(),
		IPAddress:  c.ClientIP(),
	}

	if err := h.publisher.PublishTrackingEvent(ctx, event); err != nil {
		h.logger.Printf("failed to publish event %s: %v", eventType, err)
	}
}

func extractTrackingIDs(c *gin.Context) (trackingIDs, error) {
	campaignIDRaw := firstNonEmpty(
		c.Query("c"),
		c.Query("campaign_id"),
		c.Query("campaignId"),
	)
	employeeIDRaw := firstNonEmpty(
		c.Query("e"),
		c.Query("employee_id"),
		c.Query("employeeId"),
		c.Query("user_id"),
		c.Query("userId"),
	)
	companyIDRaw := firstNonEmpty(
		c.Query("co"),
		c.Query("company_id"),
		c.Query("companyId"),
	)

	if campaignIDRaw == "" || employeeIDRaw == "" || companyIDRaw == "" {
		return trackingIDs{}, errors.New("missing one or more required identifiers (campaign, employee, company)")
	}

	campaignID, err := uuid.Parse(campaignIDRaw)
	if err != nil {
		return trackingIDs{}, fmt.Errorf("invalid campaign id: %w", err)
	}
	employeeID, err := uuid.Parse(employeeIDRaw)
	if err != nil {
		return trackingIDs{}, fmt.Errorf("invalid employee id: %w", err)
	}
	companyID, err := uuid.Parse(companyIDRaw)
	if err != nil {
		return trackingIDs{}, fmt.Errorf("invalid company id: %w", err)
	}

	return trackingIDs{
		campaignID: campaignID,
		employeeID: employeeID,
		companyID:  companyID,
	}, nil
}

func writeTransparentPixel(c *gin.Context) {
	c.Header("Content-Type", "image/gif")
	c.Header("Cache-Control", "no-store, no-cache, must-revalidate, private, max-age=0")
	c.Header("Pragma", "no-cache")
	c.Data(http.StatusOK, "image/gif", transparentPixelGIF)
}

func appendTrackingQuery(base string, ids trackingIDs, languageCode string) string {
	withCampaignPath := strings.Replace(base, "{campaignId}", ids.campaignID.String(), 1)

	parsed, err := url.Parse(withCampaignPath)
	if err != nil {
		return withCampaignPath
	}

	query := parsed.Query()
	if !strings.Contains(base, "{campaignId}") {
		query.Set("c", ids.campaignID.String())
	}
	query.Set("e", ids.employeeID.String())
	query.Set("co", ids.companyID.String())
	if languageCode != "" {
		query.Set("lang", languageCode)
	}
	parsed.RawQuery = query.Encode()
	return parsed.String()
}

func appendLanguageQuery(base string, languageCode string) string {
	if languageCode == "" {
		return base
	}

	parsed, err := url.Parse(base)
	if err != nil {
		return base
	}

	query := parsed.Query()
	query.Set("lang", languageCode)
	parsed.RawQuery = query.Encode()
	return parsed.String()
}

func extractLanguageCode(c *gin.Context) string {
	raw := firstNonEmpty(
		c.Query("lang"),
		c.Query("language"),
		c.Query("languageCode"),
	)
	if raw == "" {
		return "TR"
	}

	normalized := strings.ToLower(strings.TrimSpace(raw))
	if strings.HasPrefix(normalized, "en") || normalized == "english" || normalized == "ingilizce" {
		return "EN"
	}
	if strings.HasPrefix(normalized, "tr") || normalized == "turkish" || normalized == "turkce" || normalized == "türkçe" {
		return "TR"
	}
	return "TR"
}

func firstNonEmpty(values ...string) string {
	for _, value := range values {
		if value != "" {
			return value
		}
	}
	return ""
}
