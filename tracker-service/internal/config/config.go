package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/segmentio/kafka-go"
)

type Config struct {
	AppEnv    string
	Server    ServerConfig
	Kafka     KafkaConfig
	Redirects RedirectConfig
	Security  SecurityConfig
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

type SecurityConfig struct {
	RequireSignedLinks      bool
	TrackingSignatureSecret string
	OAuthStateSecret        string
	OAuthStateTTL           time.Duration
	NonceStore              NonceStoreConfig
}

type NonceStoreConfig struct {
	Provider      string
	RedisEnabled  bool
	RedisAddr     string
	RedisPassword string
	RedisDB       int
	Strict        bool
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
	appEnv := strings.ToLower(getEnv("APP_ENV", "local"))
	strictNonceStore := getEnvAsBool("NONCE_STORE_STRICT", appEnv == "prod")

	return Config{
		AppEnv: appEnv,
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
		Security: SecurityConfig{
			RequireSignedLinks:      getEnvAsBool("REQUIRE_SIGNED_LINKS", true),
			TrackingSignatureSecret: getEnv("TRACKING_SIGNATURE_SECRET", "genphish-dev-tracking-secret"),
			OAuthStateSecret:        getEnv("OAUTH_STATE_HMAC_SECRET", getEnv("TRACKING_SIGNATURE_SECRET", "genphish-dev-tracking-secret")),
			OAuthStateTTL:           time.Duration(getEnvAsInt("OAUTH_STATE_TTL_SECONDS", 600)) * time.Second,
			NonceStore: NonceStoreConfig{
				Provider:      strings.ToLower(getEnv("NONCE_STORE_PROVIDER", "memory")),
				RedisEnabled:  getEnvAsBool("REDIS_ENABLED", false),
				RedisAddr:     getEnv("REDIS_ADDR", "localhost:6379"),
				RedisPassword: getEnv("REDIS_PASSWORD", ""),
				RedisDB:       getEnvAsInt("REDIS_DB", 0),
				Strict:        strictNonceStore,
			},
		},
	}
}

func (c Config) Validate() error {
	var failures []string

	if c.AppEnv == "prod" {
		if !c.Security.RequireSignedLinks {
			failures = append(failures, "REQUIRE_SIGNED_LINKS must be true in prod")
		}
		if isDefaultOrWeakSecret(c.Security.TrackingSignatureSecret) {
			failures = append(failures, "TRACKING_SIGNATURE_SECRET is unsafe for prod")
		}
		if isDefaultOrWeakSecret(c.Security.OAuthStateSecret) {
			failures = append(failures, "OAUTH_STATE_HMAC_SECRET is unsafe for prod")
		}
		if c.Security.NonceStore.Provider != "redis" && !c.Security.NonceStore.RedisEnabled {
			failures = append(failures, "NONCE_STORE_PROVIDER must be redis (or REDIS_ENABLED=true) in prod")
		}
	}

	if (c.Security.NonceStore.Provider == "redis" || c.Security.NonceStore.RedisEnabled) && strings.TrimSpace(c.Security.NonceStore.RedisAddr) == "" {
		failures = append(failures, "REDIS_ADDR cannot be empty when redis nonce store is enabled")
	}

	if len(failures) > 0 {
		return fmt.Errorf(strings.Join(failures, "; "))
	}
	return nil
}

func isDefaultOrWeakSecret(value string) bool {
	normalized := strings.TrimSpace(value)
	if normalized == "" {
		return true
	}
	if len(normalized) < 32 {
		return true
	}
	return normalized == "genphish-dev-tracking-secret"
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
