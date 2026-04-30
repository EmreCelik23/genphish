package handlers

import (
	"context"
	"errors"
	"log"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"

	"github.com/EmreCelik23/genphish/tracker-service/internal/config"
	"github.com/EmreCelik23/genphish/tracker-service/internal/models"
)

type mockEventPublisher struct {
	events       []models.TrackingEvent
	publishErr   error
	publishCalls int
}

func (m *mockEventPublisher) PublishTrackingEvent(_ context.Context, event models.TrackingEvent) error {
	m.publishCalls++
	m.events = append(m.events, event)
	return m.publishErr
}

func (m *mockEventPublisher) Close() error {
	return nil
}

func newTestRouter(publisher *mockEventPublisher) *gin.Engine {
	return newTestRouterWithRedirects(
		publisher,
		"http://localhost:3000/phishing",
		"http://localhost:3000/awareness",
	)
}

func newTestRouterWithRedirects(
	publisher *mockEventPublisher,
	landingURL string,
	awarenessURL string,
) *gin.Engine {
	gin.SetMode(gin.TestMode)
	router := gin.New()
	handler := NewTrackingHandler(
		publisher,
		config.RedirectConfig{
			LandingPageURL:   landingURL,
			AwarenessPageURL: awarenessURL,
		},
		250*time.Millisecond,
		log.Default(),
	)
	RegisterRoutes(router, handler)
	return router
}

func TestTrackOpenPublishesEmailOpenedAndReturnsPixel(t *testing.T) {
	publisher := &mockEventPublisher{}
	router := newTestRouter(publisher)

	campaignID := uuid.New()
	employeeID := uuid.New()
	companyID := uuid.New()

	req := httptest.NewRequest(http.MethodGet,
		"/track/open?c="+campaignID.String()+"&e="+employeeID.String()+"&co="+companyID.String(), nil)
	req.Header.Set("User-Agent", "Mozilla/5.0 (GenPhish Test)")
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}
	if got := rec.Header().Get("Content-Type"); got != "image/gif" {
		t.Fatalf("expected image/gif content type, got %s", got)
	}
	if len(publisher.events) != 1 {
		t.Fatalf("expected exactly 1 published event, got %d", len(publisher.events))
	}
	if publisher.events[0].EventType != models.EventEmailOpened {
		t.Fatalf("expected EMAIL_OPENED, got %s", publisher.events[0].EventType)
	}
	if publisher.events[0].UserAgent == "" {
		t.Fatalf("expected user agent to be populated")
	}
	if publisher.events[0].IPAddress == "" {
		t.Fatalf("expected client IP to be populated")
	}
}

func TestTrackClickPublishesAndRedirectsToLanding(t *testing.T) {
	publisher := &mockEventPublisher{}
	router := newTestRouter(publisher)

	campaignID := uuid.New()
	employeeID := uuid.New()
	companyID := uuid.New()

	req := httptest.NewRequest(http.MethodGet,
		"/track/click?c="+campaignID.String()+"&e="+employeeID.String()+"&co="+companyID.String(), nil)
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusFound {
		t.Fatalf("expected 302, got %d", rec.Code)
	}
	location := rec.Header().Get("Location")
	expectedPrefix := "http://localhost:3000/phishing?"
	if len(location) < len(expectedPrefix) || location[:len(expectedPrefix)] != expectedPrefix {
		t.Fatalf("expected redirect to landing page, got %s", location)
	}
	if len(publisher.events) != 1 || publisher.events[0].EventType != models.EventLinkClicked {
		t.Fatalf("expected LINK_CLICKED event to be published")
	}
}

func TestTrackClickRedirectSupportsCampaignIdPathPlaceholder(t *testing.T) {
	publisher := &mockEventPublisher{}
	router := newTestRouterWithRedirects(
		publisher,
		"http://localhost:3000/phishing/{campaignId}",
		"http://localhost:3000/awareness",
	)

	campaignID := uuid.New()
	employeeID := uuid.New()
	companyID := uuid.New()

	req := httptest.NewRequest(http.MethodGet,
		"/track/click?c="+campaignID.String()+"&e="+employeeID.String()+"&co="+companyID.String(), nil)
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusFound {
		t.Fatalf("expected 302, got %d", rec.Code)
	}

	location := rec.Header().Get("Location")
	parsed, err := url.Parse(location)
	if err != nil {
		t.Fatalf("expected valid redirect URL, got parse error: %v", err)
	}

	expectedPath := "/phishing/" + campaignID.String()
	if parsed.Path != expectedPath {
		t.Fatalf("expected path %s, got %s", expectedPath, parsed.Path)
	}
	if parsed.Query().Get("e") != employeeID.String() {
		t.Fatalf("expected employee query param to be propagated")
	}
	if parsed.Query().Get("co") != companyID.String() {
		t.Fatalf("expected company query param to be propagated")
	}
	if parsed.Query().Get("c") != "" {
		t.Fatalf("did not expect campaign query param when placeholder is used")
	}
}

func TestTrackClickPropagatesLanguageCode(t *testing.T) {
	publisher := &mockEventPublisher{}
	router := newTestRouter(publisher)

	campaignID := uuid.New()
	employeeID := uuid.New()
	companyID := uuid.New()

	req := httptest.NewRequest(
		http.MethodGet,
		"/track/click?c="+campaignID.String()+"&e="+employeeID.String()+"&co="+companyID.String()+"&lang=en-US",
		nil,
	)
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusFound {
		t.Fatalf("expected 302, got %d", rec.Code)
	}

	location := rec.Header().Get("Location")
	parsed, err := url.Parse(location)
	if err != nil {
		t.Fatalf("expected valid redirect URL, got parse error: %v", err)
	}
	if parsed.Query().Get("lang") != "EN" {
		t.Fatalf("expected normalized lang=EN in redirect, got %s", parsed.Query().Get("lang"))
	}
}

func TestTrackSubmitPublishesAndRedirectsToAwareness(t *testing.T) {
	publisher := &mockEventPublisher{}
	router := newTestRouter(publisher)

	campaignID := uuid.New()
	employeeID := uuid.New()
	companyID := uuid.New()

	req := httptest.NewRequest(http.MethodPost,
		"/track/submit?c="+campaignID.String()+"&e="+employeeID.String()+"&co="+companyID.String(), nil)
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusFound {
		t.Fatalf("expected 302, got %d", rec.Code)
	}
	location := rec.Header().Get("Location")
	expectedPrefix := "http://localhost:3000/awareness?"
	if len(location) < len(expectedPrefix) || location[:len(expectedPrefix)] != expectedPrefix {
		t.Fatalf("expected redirect to awareness page, got %s", location)
	}
	if len(publisher.events) != 1 || publisher.events[0].EventType != models.EventCredentialsSubmitted {
		t.Fatalf("expected CREDENTIALS_SUBMITTED event to be published")
	}
}

func TestTrackSubmitPropagatesLanguageCode(t *testing.T) {
	publisher := &mockEventPublisher{}
	router := newTestRouter(publisher)

	campaignID := uuid.New()
	employeeID := uuid.New()
	companyID := uuid.New()

	req := httptest.NewRequest(
		http.MethodPost,
		"/track/submit?c="+campaignID.String()+"&e="+employeeID.String()+"&co="+companyID.String()+"&languageCode=tr-TR",
		nil,
	)
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusFound {
		t.Fatalf("expected 302, got %d", rec.Code)
	}

	location := rec.Header().Get("Location")
	parsed, err := url.Parse(location)
	if err != nil {
		t.Fatalf("expected valid redirect URL, got parse error: %v", err)
	}
	if parsed.Query().Get("lang") != "TR" {
		t.Fatalf("expected normalized lang=TR in redirect, got %s", parsed.Query().Get("lang"))
	}
}

func TestTrackOpenMissingIDsStillReturnsPixelAndSkipsPublish(t *testing.T) {
	publisher := &mockEventPublisher{}
	router := newTestRouter(publisher)

	req := httptest.NewRequest(http.MethodGet, "/track/open?c="+uuid.New().String(), nil)
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}
	if len(publisher.events) != 0 {
		t.Fatalf("expected no events to be published when identifiers are missing")
	}
}

func TestTrackClickMissingIDsSkipsPublishAndRedirectsBaseURL(t *testing.T) {
	publisher := &mockEventPublisher{}
	router := newTestRouter(publisher)

	req := httptest.NewRequest(http.MethodGet, "/track/click?c="+uuid.New().String(), nil)
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusFound {
		t.Fatalf("expected 302, got %d", rec.Code)
	}
	if location := rec.Header().Get("Location"); location != "http://localhost:3000/phishing" {
		t.Fatalf("expected base landing redirect, got %s", location)
	}
	if publisher.publishCalls != 0 {
		t.Fatalf("expected no publish attempts, got %d", publisher.publishCalls)
	}
}

func TestTrackClickMissingIDsStillPropagatesLanguageIfProvided(t *testing.T) {
	publisher := &mockEventPublisher{}
	router := newTestRouter(publisher)

	req := httptest.NewRequest(http.MethodGet, "/track/click?c="+uuid.New().String()+"&lang=english", nil)
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusFound {
		t.Fatalf("expected 302, got %d", rec.Code)
	}

	location := rec.Header().Get("Location")
	if !strings.HasPrefix(location, "http://localhost:3000/phishing?") {
		t.Fatalf("expected landing redirect with language query, got %s", location)
	}
	parsed, err := url.Parse(location)
	if err != nil {
		t.Fatalf("expected valid redirect URL, got parse error: %v", err)
	}
	if parsed.Query().Get("lang") != "EN" {
		t.Fatalf("expected lang=EN, got %s", parsed.Query().Get("lang"))
	}
	if publisher.publishCalls != 0 {
		t.Fatalf("expected no publish attempts, got %d", publisher.publishCalls)
	}
}

func TestTrackSubmitMissingIDsSkipsPublishAndRedirectsBaseURL(t *testing.T) {
	publisher := &mockEventPublisher{}
	router := newTestRouter(publisher)

	req := httptest.NewRequest(http.MethodPost, "/track/submit?c="+uuid.New().String(), nil)
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusFound {
		t.Fatalf("expected 302, got %d", rec.Code)
	}
	if location := rec.Header().Get("Location"); location != "http://localhost:3000/awareness" {
		t.Fatalf("expected base awareness redirect, got %s", location)
	}
	if publisher.publishCalls != 0 {
		t.Fatalf("expected no publish attempts, got %d", publisher.publishCalls)
	}
}

func TestTrackOpenPublisherErrorStillReturnsPixel(t *testing.T) {
	publisher := &mockEventPublisher{publishErr: errors.New("broker down")}
	router := newTestRouter(publisher)

	campaignID := uuid.New()
	employeeID := uuid.New()
	companyID := uuid.New()

	req := httptest.NewRequest(http.MethodGet,
		"/track/open?c="+campaignID.String()+"&e="+employeeID.String()+"&co="+companyID.String(), nil)
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}
	if publisher.publishCalls != 1 {
		t.Fatalf("expected one publish attempt, got %d", publisher.publishCalls)
	}
}

func TestTrackClickInvalidUUIDSkipsPublishAndRedirectsBaseURL(t *testing.T) {
	publisher := &mockEventPublisher{}
	router := newTestRouter(publisher)

	req := httptest.NewRequest(http.MethodGet,
		"/track/click?c=not-a-uuid&e="+uuid.New().String()+"&co="+uuid.New().String(), nil)
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusFound {
		t.Fatalf("expected 302, got %d", rec.Code)
	}
	if location := rec.Header().Get("Location"); location != "http://localhost:3000/phishing" {
		t.Fatalf("expected base landing redirect, got %s", location)
	}
	if publisher.publishCalls != 0 {
		t.Fatalf("expected no publish attempts, got %d", publisher.publishCalls)
	}
}

func TestExtractTrackingIDsRejectsInvalidIdentifiers(t *testing.T) {
	campaignID := uuid.New().String()
	employeeID := uuid.New().String()
	companyID := uuid.New().String()

	testCases := []struct {
		name          string
		query         string
		expectedInErr string
	}{
		{
			name:          "invalid campaign id",
			query:         "c=invalid&e=" + employeeID + "&co=" + companyID,
			expectedInErr: "invalid campaign id",
		},
		{
			name:          "invalid employee id",
			query:         "c=" + campaignID + "&e=invalid&co=" + companyID,
			expectedInErr: "invalid employee id",
		},
		{
			name:          "invalid company id",
			query:         "c=" + campaignID + "&e=" + employeeID + "&co=invalid",
			expectedInErr: "invalid company id",
		},
	}

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			rec := httptest.NewRecorder()
			c, _ := gin.CreateTestContext(rec)
			c.Request = httptest.NewRequest(http.MethodGet, "/track/open?"+tc.query, nil)

			_, err := extractTrackingIDs(c)
			if err == nil {
				t.Fatalf("expected an error")
			}
			if !strings.Contains(err.Error(), tc.expectedInErr) {
				t.Fatalf("expected error to contain %q, got %q", tc.expectedInErr, err.Error())
			}
		})
	}
}

func TestAppendTrackingQueryPreservesExistingQueryValues(t *testing.T) {
	ids := trackingIDs{
		campaignID: uuid.New(),
		employeeID: uuid.New(),
		companyID:  uuid.New(),
	}
	result := appendTrackingQuery("http://localhost:3000/phishing?source=email", ids, "EN")

	parsed, err := url.Parse(result)
	if err != nil {
		t.Fatalf("expected valid URL, got error: %v", err)
	}
	if parsed.Query().Get("source") != "email" {
		t.Fatalf("expected existing query param to be preserved")
	}
	if parsed.Query().Get("c") != ids.campaignID.String() {
		t.Fatalf("expected campaign id query param to be set")
	}
	if parsed.Query().Get("e") != ids.employeeID.String() {
		t.Fatalf("expected employee id query param to be set")
	}
	if parsed.Query().Get("co") != ids.companyID.String() {
		t.Fatalf("expected company id query param to be set")
	}
	if parsed.Query().Get("lang") != "EN" {
		t.Fatalf("expected language query param to be set")
	}
}

func TestAppendTrackingQueryReturnsReplacedBaseWhenURLIsInvalid(t *testing.T) {
	ids := trackingIDs{
		campaignID: uuid.New(),
		employeeID: uuid.New(),
		companyID:  uuid.New(),
	}
	base := "://bad-host/phishing/{campaignId}"
	got := appendTrackingQuery(base, ids, "TR")

	expected := strings.Replace(base, "{campaignId}", ids.campaignID.String(), 1)
	if got != expected {
		t.Fatalf("expected %s, got %s", expected, got)
	}
}
