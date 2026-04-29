package kafka

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"log"
	"strings"
	"testing"
	"time"

	"github.com/google/uuid"
	segmentkafka "github.com/segmentio/kafka-go"

	"github.com/EmreCelik23/genphish/tracker-service/internal/config"
	"github.com/EmreCelik23/genphish/tracker-service/internal/models"
)

type mockMessageWriter struct {
	messages   []segmentkafka.Message
	writeErr   error
	closeErr   error
	closeCalls int
}

func (m *mockMessageWriter) WriteMessages(_ context.Context, msgs ...segmentkafka.Message) error {
	m.messages = append(m.messages, msgs...)
	return m.writeErr
}

func (m *mockMessageWriter) Close() error {
	m.closeCalls++
	return m.closeErr
}

func TestNewEventPublisherValidation(t *testing.T) {
	logger := log.New(io.Discard, "", 0)

	_, err := NewEventPublisher(config.KafkaConfig{
		Brokers: nil,
		Topic:   "tracking_events",
	}, logger)
	if err == nil || !strings.Contains(err.Error(), "brokers cannot be empty") {
		t.Fatalf("expected brokers validation error, got %v", err)
	}

	_, err = NewEventPublisher(config.KafkaConfig{
		Brokers: []string{"localhost:9092"},
		Topic:   "",
	}, logger)
	if err == nil || !strings.Contains(err.Error(), "topic cannot be empty") {
		t.Fatalf("expected topic validation error, got %v", err)
	}
}

func TestNewEventPublisherSuccess(t *testing.T) {
	logger := log.New(io.Discard, "", 0)
	publisher, err := NewEventPublisher(config.KafkaConfig{
		Brokers:      []string{"localhost:9092"},
		Topic:        "tracking_events",
		BatchTimeout: 5 * time.Millisecond,
		BatchSize:    100,
		WriteTimeout: 750 * time.Millisecond,
		Async:        true,
	}, logger)
	if err != nil {
		t.Fatalf("expected publisher creation to succeed, got error: %v", err)
	}
	if publisher == nil {
		t.Fatalf("expected non-nil publisher")
	}
}

func TestPublishTrackingEventSuccess(t *testing.T) {
	writer := &mockMessageWriter{}
	publisher := &trackingEventPublisher{
		writer: writer,
		logger: log.New(io.Discard, "", 0),
	}

	event := models.TrackingEvent{
		CampaignID: uuid.New(),
		EmployeeID: uuid.New(),
		CompanyID:  uuid.New(),
		EventType:  models.EventLinkClicked,
		Timestamp:  time.Now().UTC().Truncate(time.Millisecond),
		UserAgent:  "Mozilla/5.0",
		IPAddress:  "203.0.113.20",
	}

	if err := publisher.PublishTrackingEvent(context.Background(), event); err != nil {
		t.Fatalf("expected publish to succeed, got error: %v", err)
	}

	if len(writer.messages) != 1 {
		t.Fatalf("expected exactly one kafka message, got %d", len(writer.messages))
	}

	msg := writer.messages[0]
	if string(msg.Key) != event.CampaignID.String() {
		t.Fatalf("expected key %s, got %s", event.CampaignID, string(msg.Key))
	}

	var got models.TrackingEvent
	if err := json.Unmarshal(msg.Value, &got); err != nil {
		t.Fatalf("expected valid message payload JSON, got error: %v", err)
	}
	if got.EventType != models.EventLinkClicked {
		t.Fatalf("expected event type %s, got %s", models.EventLinkClicked, got.EventType)
	}
	if got.UserAgent != event.UserAgent {
		t.Fatalf("expected user agent %q, got %q", event.UserAgent, got.UserAgent)
	}
	if got.IPAddress != event.IPAddress {
		t.Fatalf("expected IP %q, got %q", event.IPAddress, got.IPAddress)
	}
}

func TestPublishTrackingEventWriteError(t *testing.T) {
	writer := &mockMessageWriter{writeErr: errors.New("write failed")}
	publisher := &trackingEventPublisher{
		writer: writer,
		logger: log.New(io.Discard, "", 0),
	}

	err := publisher.PublishTrackingEvent(context.Background(), models.TrackingEvent{
		CampaignID: uuid.New(),
		EmployeeID: uuid.New(),
		CompanyID:  uuid.New(),
		EventType:  models.EventEmailOpened,
		Timestamp:  time.Now().UTC(),
	})
	if err == nil || !strings.Contains(err.Error(), "failed to publish tracking event") {
		t.Fatalf("expected wrapped write error, got %v", err)
	}
}

func TestCloseDelegatesToWriter(t *testing.T) {
	writer := &mockMessageWriter{}
	publisher := &trackingEventPublisher{
		writer: writer,
		logger: log.New(io.Discard, "", 0),
	}

	if err := publisher.Close(); err != nil {
		t.Fatalf("expected close to succeed, got %v", err)
	}
	if writer.closeCalls != 1 {
		t.Fatalf("expected writer close to be called once, got %d", writer.closeCalls)
	}
}
