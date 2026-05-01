package config

import (
	"reflect"
	"testing"
	"time"

	"github.com/segmentio/kafka-go"
)

func TestServerAddress(t *testing.T) {
	testCases := []struct {
		name string
		port string
		want string
	}{
		{name: "empty defaults to 8081", port: "", want: ":8081"},
		{name: "already prefixed", port: ":9090", want: ":9090"},
		{name: "plain port", port: "9090", want: ":9090"},
		{name: "trimmed plain port", port: " 9091 ", want: ":9091"},
	}

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			cfg := ServerConfig{Port: tc.port}
			if got := cfg.Address(); got != tc.want {
				t.Fatalf("Address() = %q, want %q", got, tc.want)
			}
		})
	}
}

func TestLoadDefaults(t *testing.T) {
	for _, key := range []string{
		"PORT",
		"GIN_MODE",
		"PUBLISH_TIMEOUT_MS",
		"KAFKA_BROKERS",
		"KAFKA_TOPIC",
		"KAFKA_CLIENT_ID",
		"KAFKA_WRITE_TIMEOUT_MS",
		"KAFKA_BATCH_TIMEOUT_MS",
		"KAFKA_BATCH_SIZE",
		"KAFKA_ASYNC",
		"LANDING_PAGE_URL",
		"AWARENESS_PAGE_URL",
		"REQUIRE_SIGNED_LINKS",
		"TRACKING_SIGNATURE_SECRET",
		"OAUTH_STATE_HMAC_SECRET",
		"OAUTH_STATE_TTL_SECONDS",
		"NONCE_STORE_PROVIDER",
		"REDIS_ENABLED",
		"REDIS_ADDR",
		"REDIS_PASSWORD",
		"REDIS_DB",
	} {
		t.Setenv(key, "")
	}

	cfg := Load()

	if cfg.Server.Port != "8081" {
		t.Fatalf("default port mismatch: got %q", cfg.Server.Port)
	}
	if cfg.Server.GinMode != "release" {
		t.Fatalf("default gin mode mismatch: got %q", cfg.Server.GinMode)
	}
	if cfg.Server.PublishTimeout != 300*time.Millisecond {
		t.Fatalf("default publish timeout mismatch: got %v", cfg.Server.PublishTimeout)
	}
	if !reflect.DeepEqual(cfg.Kafka.Brokers, []string{"localhost:9092"}) {
		t.Fatalf("default kafka brokers mismatch: got %v", cfg.Kafka.Brokers)
	}
	if cfg.Kafka.Topic != "tracking_events" {
		t.Fatalf("default kafka topic mismatch: got %q", cfg.Kafka.Topic)
	}
	if cfg.Kafka.ClientID != "tracker-service" {
		t.Fatalf("default kafka client id mismatch: got %q", cfg.Kafka.ClientID)
	}
	if cfg.Kafka.WriteTimeout != 750*time.Millisecond {
		t.Fatalf("default write timeout mismatch: got %v", cfg.Kafka.WriteTimeout)
	}
	if cfg.Kafka.BatchTimeout != 5*time.Millisecond {
		t.Fatalf("default batch timeout mismatch: got %v", cfg.Kafka.BatchTimeout)
	}
	if cfg.Kafka.BatchSize != 200 {
		t.Fatalf("default batch size mismatch: got %d", cfg.Kafka.BatchSize)
	}
	if !cfg.Kafka.Async {
		t.Fatalf("default async mismatch: expected true")
	}
	if cfg.Kafka.RequiredAcks != kafka.RequireOne {
		t.Fatalf("default required acks mismatch: got %v", cfg.Kafka.RequiredAcks)
	}
	if cfg.Redirects.LandingPageURL != "http://localhost:3000/phishing" {
		t.Fatalf("default landing url mismatch: got %q", cfg.Redirects.LandingPageURL)
	}
	if cfg.Redirects.AwarenessPageURL != "http://localhost:3000/awareness" {
		t.Fatalf("default awareness url mismatch: got %q", cfg.Redirects.AwarenessPageURL)
	}
	if !cfg.Security.RequireSignedLinks {
		t.Fatalf("expected signed links to be required by default")
	}
	if cfg.Security.TrackingSignatureSecret != "genphish-dev-tracking-secret" {
		t.Fatalf("default tracking secret mismatch: got %q", cfg.Security.TrackingSignatureSecret)
	}
	if cfg.Security.OAuthStateSecret != "genphish-dev-tracking-secret" {
		t.Fatalf("default oauth secret mismatch: got %q", cfg.Security.OAuthStateSecret)
	}
	if cfg.Security.OAuthStateTTL != 600*time.Second {
		t.Fatalf("default oauth state ttl mismatch: got %v", cfg.Security.OAuthStateTTL)
	}
	if cfg.Security.NonceStore.Provider != "memory" {
		t.Fatalf("default nonce provider mismatch: got %q", cfg.Security.NonceStore.Provider)
	}
	if cfg.Security.NonceStore.RedisEnabled {
		t.Fatalf("default redis enabled mismatch: expected false")
	}
	if cfg.Security.NonceStore.RedisAddr != "localhost:6379" {
		t.Fatalf("default redis addr mismatch: got %q", cfg.Security.NonceStore.RedisAddr)
	}
}

func TestLoadCustomValues(t *testing.T) {
	t.Setenv("PORT", "9099")
	t.Setenv("GIN_MODE", "debug")
	t.Setenv("PUBLISH_TIMEOUT_MS", "450")
	t.Setenv("KAFKA_BROKERS", "broker-1:9092, broker-2:9092 , ,")
	t.Setenv("KAFKA_TOPIC", "custom_tracking_events")
	t.Setenv("KAFKA_CLIENT_ID", "tracker-service-test")
	t.Setenv("KAFKA_WRITE_TIMEOUT_MS", "1000")
	t.Setenv("KAFKA_BATCH_TIMEOUT_MS", "12")
	t.Setenv("KAFKA_BATCH_SIZE", "500")
	t.Setenv("KAFKA_ASYNC", "no")
	t.Setenv("LANDING_PAGE_URL", "https://app.example.com/phishing/{campaignId}")
	t.Setenv("AWARENESS_PAGE_URL", "https://app.example.com/awareness")
	t.Setenv("REQUIRE_SIGNED_LINKS", "false")
	t.Setenv("TRACKING_SIGNATURE_SECRET", "custom-tracking-secret")
	t.Setenv("OAUTH_STATE_HMAC_SECRET", "custom-oauth-secret")
	t.Setenv("OAUTH_STATE_TTL_SECONDS", "900")
	t.Setenv("NONCE_STORE_PROVIDER", "redis")
	t.Setenv("REDIS_ENABLED", "true")
	t.Setenv("REDIS_ADDR", "redis.internal:6380")
	t.Setenv("REDIS_PASSWORD", "secret")
	t.Setenv("REDIS_DB", "3")

	cfg := Load()

	if cfg.Server.Port != "9099" {
		t.Fatalf("custom port mismatch: got %q", cfg.Server.Port)
	}
	if cfg.Server.GinMode != "debug" {
		t.Fatalf("custom gin mode mismatch: got %q", cfg.Server.GinMode)
	}
	if cfg.Server.PublishTimeout != 450*time.Millisecond {
		t.Fatalf("custom publish timeout mismatch: got %v", cfg.Server.PublishTimeout)
	}
	if !reflect.DeepEqual(cfg.Kafka.Brokers, []string{"broker-1:9092", "broker-2:9092"}) {
		t.Fatalf("custom kafka brokers mismatch: got %v", cfg.Kafka.Brokers)
	}
	if cfg.Kafka.Topic != "custom_tracking_events" {
		t.Fatalf("custom kafka topic mismatch: got %q", cfg.Kafka.Topic)
	}
	if cfg.Kafka.ClientID != "tracker-service-test" {
		t.Fatalf("custom kafka client id mismatch: got %q", cfg.Kafka.ClientID)
	}
	if cfg.Kafka.WriteTimeout != time.Second {
		t.Fatalf("custom write timeout mismatch: got %v", cfg.Kafka.WriteTimeout)
	}
	if cfg.Kafka.BatchTimeout != 12*time.Millisecond {
		t.Fatalf("custom batch timeout mismatch: got %v", cfg.Kafka.BatchTimeout)
	}
	if cfg.Kafka.BatchSize != 500 {
		t.Fatalf("custom batch size mismatch: got %d", cfg.Kafka.BatchSize)
	}
	if cfg.Kafka.Async {
		t.Fatalf("custom async mismatch: expected false")
	}
	if cfg.Redirects.LandingPageURL != "https://app.example.com/phishing/{campaignId}" {
		t.Fatalf("custom landing url mismatch: got %q", cfg.Redirects.LandingPageURL)
	}
	if cfg.Redirects.AwarenessPageURL != "https://app.example.com/awareness" {
		t.Fatalf("custom awareness url mismatch: got %q", cfg.Redirects.AwarenessPageURL)
	}
	if cfg.Security.RequireSignedLinks {
		t.Fatalf("custom signed links mismatch: expected false")
	}
	if cfg.Security.TrackingSignatureSecret != "custom-tracking-secret" {
		t.Fatalf("custom tracking secret mismatch: got %q", cfg.Security.TrackingSignatureSecret)
	}
	if cfg.Security.OAuthStateSecret != "custom-oauth-secret" {
		t.Fatalf("custom oauth secret mismatch: got %q", cfg.Security.OAuthStateSecret)
	}
	if cfg.Security.OAuthStateTTL != 900*time.Second {
		t.Fatalf("custom oauth state ttl mismatch: got %v", cfg.Security.OAuthStateTTL)
	}
	if cfg.Security.NonceStore.Provider != "redis" {
		t.Fatalf("custom nonce provider mismatch: got %q", cfg.Security.NonceStore.Provider)
	}
	if !cfg.Security.NonceStore.RedisEnabled {
		t.Fatalf("custom redis enabled mismatch: expected true")
	}
	if cfg.Security.NonceStore.RedisAddr != "redis.internal:6380" {
		t.Fatalf("custom redis addr mismatch: got %q", cfg.Security.NonceStore.RedisAddr)
	}
	if cfg.Security.NonceStore.RedisPassword != "secret" {
		t.Fatalf("custom redis password mismatch: got %q", cfg.Security.NonceStore.RedisPassword)
	}
	if cfg.Security.NonceStore.RedisDB != 3 {
		t.Fatalf("custom redis db mismatch: got %d", cfg.Security.NonceStore.RedisDB)
	}
}

func TestLoadInvalidNumericValuesFallback(t *testing.T) {
	t.Setenv("PUBLISH_TIMEOUT_MS", "not-int")
	t.Setenv("KAFKA_WRITE_TIMEOUT_MS", "invalid")
	t.Setenv("KAFKA_BATCH_TIMEOUT_MS", "invalid")
	t.Setenv("KAFKA_BATCH_SIZE", "invalid")
	t.Setenv("KAFKA_BROKERS", "   ")

	cfg := Load()

	if cfg.Server.PublishTimeout != 300*time.Millisecond {
		t.Fatalf("fallback publish timeout mismatch: got %v", cfg.Server.PublishTimeout)
	}
	if cfg.Kafka.WriteTimeout != 750*time.Millisecond {
		t.Fatalf("fallback write timeout mismatch: got %v", cfg.Kafka.WriteTimeout)
	}
	if cfg.Kafka.BatchTimeout != 5*time.Millisecond {
		t.Fatalf("fallback batch timeout mismatch: got %v", cfg.Kafka.BatchTimeout)
	}
	if cfg.Kafka.BatchSize != 200 {
		t.Fatalf("fallback batch size mismatch: got %d", cfg.Kafka.BatchSize)
	}
	if !reflect.DeepEqual(cfg.Kafka.Brokers, []string{"localhost:9092"}) {
		t.Fatalf("fallback brokers mismatch: got %v", cfg.Kafka.Brokers)
	}
}
