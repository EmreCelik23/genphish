package config

import (
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/segmentio/kafka-go"
)

type Config struct {
	Server    ServerConfig
	Kafka     KafkaConfig
	Redirects RedirectConfig
}

type ServerConfig struct {
	Port           string
	GinMode        string
	PublishTimeout time.Duration
}

type KafkaConfig struct {
	Brokers      []string
	Topic        string
	ClientID     string
	WriteTimeout time.Duration
	BatchTimeout time.Duration
	BatchSize    int
	Async        bool
	RequiredAcks kafka.RequiredAcks
}

type RedirectConfig struct {
	LandingPageURL   string
	AwarenessPageURL string
}

func (c ServerConfig) Address() string {
	port := strings.TrimSpace(c.Port)
	if port == "" {
		return ":8081"
	}
	if strings.HasPrefix(port, ":") {
		return port
	}
	return ":" + port
}

func Load() Config {
	kafkaWriteTimeout := time.Duration(getEnvAsInt("KAFKA_WRITE_TIMEOUT_MS", 750)) * time.Millisecond
	kafkaBatchTimeout := time.Duration(getEnvAsInt("KAFKA_BATCH_TIMEOUT_MS", 5)) * time.Millisecond
	publishTimeout := time.Duration(getEnvAsInt("PUBLISH_TIMEOUT_MS", 300)) * time.Millisecond

	return Config{
		Server: ServerConfig{
			Port:           getEnv("PORT", "8081"),
			GinMode:        getEnv("GIN_MODE", "release"),
			PublishTimeout: publishTimeout,
		},
		Kafka: KafkaConfig{
			Brokers:      parseCSV(getEnv("KAFKA_BROKERS", "localhost:9092")),
			Topic:        getEnv("KAFKA_TOPIC", "tracking_events"),
			ClientID:     getEnv("KAFKA_CLIENT_ID", "tracker-service"),
			WriteTimeout: kafkaWriteTimeout,
			BatchTimeout: kafkaBatchTimeout,
			BatchSize:    getEnvAsInt("KAFKA_BATCH_SIZE", 200),
			Async:        getEnvAsBool("KAFKA_ASYNC", true),
			RequiredAcks: kafka.RequireOne,
		},
		Redirects: RedirectConfig{
			LandingPageURL:   getEnv("LANDING_PAGE_URL", "http://localhost:3000/phishing"),
			AwarenessPageURL: getEnv("AWARENESS_PAGE_URL", "http://localhost:3000/awareness"),
		},
	}
}

func getEnv(key string, fallback string) string {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	return value
}

func getEnvAsInt(key string, fallback int) int {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}

	parsed, err := strconv.Atoi(value)
	if err != nil {
		return fallback
	}
	return parsed
}

func getEnvAsBool(key string, fallback bool) bool {
	value := strings.TrimSpace(strings.ToLower(os.Getenv(key)))
	if value == "" {
		return fallback
	}

	switch value {
	case "1", "true", "yes", "y", "on":
		return true
	case "0", "false", "no", "n", "off":
		return false
	default:
		return fallback
	}
}

func parseCSV(value string) []string {
	parts := strings.Split(value, ",")
	result := make([]string, 0, len(parts))
	for _, part := range parts {
		trimmed := strings.TrimSpace(part)
		if trimmed != "" {
			result = append(result, trimmed)
		}
	}
	if len(result) == 0 {
		return []string{"localhost:9092"}
	}
	return result
}
