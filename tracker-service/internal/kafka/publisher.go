package kafka

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log"

	"github.com/segmentio/kafka-go"

	"github.com/EmreCelik23/genphish/tracker-service/internal/config"
	"github.com/EmreCelik23/genphish/tracker-service/internal/models"
)

type EventPublisher interface {
	PublishTrackingEvent(ctx context.Context, event models.TrackingEvent) error
	Close() error
}

type trackingEventPublisher struct {
	writer *kafka.Writer
	logger *log.Logger
}

func NewEventPublisher(cfg config.KafkaConfig, logger *log.Logger) (EventPublisher, error) {
	if len(cfg.Brokers) == 0 {
		return nil, errors.New("kafka brokers cannot be empty")
	}
	if cfg.Topic == "" {
		return nil, errors.New("kafka topic cannot be empty")
	}

	writer := &kafka.Writer{
		Addr:                   kafka.TCP(cfg.Brokers...),
		Topic:                  cfg.Topic,
		Balancer:               &kafka.LeastBytes{},
		RequiredAcks:           cfg.RequiredAcks,
		BatchTimeout:           cfg.BatchTimeout,
		BatchSize:              cfg.BatchSize,
		WriteTimeout:           cfg.WriteTimeout,
		Async:                  cfg.Async,
		AllowAutoTopicCreation: true,
		Completion: func(messages []kafka.Message, err error) {
			if err != nil {
				logger.Printf("kafka write completion error: %v", err)
			}
		},
	}

	return &trackingEventPublisher{
		writer: writer,
		logger: logger,
	}, nil
}

func (p *trackingEventPublisher) PublishTrackingEvent(ctx context.Context, event models.TrackingEvent) error {
	payload, err := json.Marshal(event)
	if err != nil {
		return fmt.Errorf("failed to marshal tracking event: %w", err)
	}

	message := kafka.Message{
		Key:   []byte(event.CampaignID.String()),
		Value: payload,
		Time:  event.Timestamp,
	}

	if err := p.writer.WriteMessages(ctx, message); err != nil {
		return fmt.Errorf("failed to publish tracking event: %w", err)
	}

	p.logger.Printf("tracking event published: campaign=%s employee=%s company=%s type=%s",
		event.CampaignID, event.EmployeeID, event.CompanyID, event.EventType)
	return nil
}

func (p *trackingEventPublisher) Close() error {
	return p.writer.Close()
}
