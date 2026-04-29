package handlers

import (
	"context"
	"log"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"

	"github.com/EmreCelik23/genphish/tracker-service/internal/config"
	"github.com/EmreCelik23/genphish/tracker-service/internal/models"
)

type mockEventPublisher struct {
	events []models.TrackingEvent
}

func (m *mockEventPublisher) PublishTrackingEvent(_ context.Context, event models.TrackingEvent) error {
	m.events = append(m.events, event)
	return nil
}

func (m *mockEventPublisher) Close() error {
	return nil
}

func newTestRouter(publisher *mockEventPublisher) *gin.Engine {
	gin.SetMode(gin.TestMode)
	router := gin.New()
	handler := NewTrackingHandler(
		publisher,
		config.RedirectConfig{
			LandingPageURL:   "http://localhost:3000/phishing",
			AwarenessPageURL: "http://localhost:3000/awareness",
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
