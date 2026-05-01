package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/gin-gonic/gin"
	redis "github.com/redis/go-redis/v9"

	"github.com/EmreCelik23/genphish/tracker-service/internal/config"
	"github.com/EmreCelik23/genphish/tracker-service/internal/handlers"
	"github.com/EmreCelik23/genphish/tracker-service/internal/kafka"
	"github.com/EmreCelik23/genphish/tracker-service/internal/security"
)

func main() {
	cfg := config.Load()

	logger := log.New(os.Stdout, "[tracker-service] ", log.LstdFlags|log.LUTC)
	gin.SetMode(cfg.Server.GinMode)

	publisher, err := kafka.NewEventPublisher(cfg.Kafka, logger)
	if err != nil {
		logger.Fatalf("failed to initialize kafka publisher: %v", err)
	}
	defer func() {
		if closeErr := publisher.Close(); closeErr != nil {
			logger.Printf("failed to close kafka publisher: %v", closeErr)
		}
	}()

	var nonceStore security.NonceStore = security.NewInMemoryNonceStore()
	useRedisNonceStore := cfg.Security.NonceStore.RedisEnabled || cfg.Security.NonceStore.Provider == "redis"
	if useRedisNonceStore {
		redisClient := redis.NewClient(&redis.Options{
			Addr:     cfg.Security.NonceStore.RedisAddr,
			Password: cfg.Security.NonceStore.RedisPassword,
			DB:       cfg.Security.NonceStore.RedisDB,
		})
		if pingErr := redisClient.Ping(context.Background()).Err(); pingErr != nil {
			logger.Printf("redis nonce-store unavailable, falling back to memory store: %v", pingErr)
			_ = redisClient.Close()
		} else {
			nonceStore = security.NewRedisNonceStore(redisClient, "genphish:oauth:nonce:")
			logger.Printf("oauth nonce-store provider=redis")
		}
	} else {
		logger.Printf("oauth nonce-store provider=memory")
	}
	defer func() {
		if closeErr := nonceStore.Close(); closeErr != nil {
			logger.Printf("failed to close nonce store: %v", closeErr)
		}
	}()

	verifier := security.NewVerifier(
		cfg.Security.RequireSignedLinks,
		cfg.Security.TrackingSignatureSecret,
		cfg.Security.OAuthStateSecret,
		cfg.Security.OAuthStateTTL,
		nonceStore,
	)

	router := gin.New()
	router.Use(gin.Logger(), gin.Recovery())

	trackingHandler := handlers.NewTrackingHandler(
		publisher,
		verifier,
		cfg.Redirects,
		cfg.Server.PublishTimeout,
		logger,
	)
	handlers.RegisterRoutes(router, trackingHandler)

	server := &http.Server{
		Addr:              cfg.Server.Address(),
		Handler:           router,
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       10 * time.Second,
		WriteTimeout:      10 * time.Second,
		IdleTimeout:       120 * time.Second,
	}

	go func() {
		logger.Printf("listening on %s", cfg.Server.Address())
		if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			logger.Fatalf("http server error: %v", err)
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	logger.Println("shutdown signal received")
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := server.Shutdown(shutdownCtx); err != nil {
		logger.Printf("graceful shutdown failed: %v", err)
	}
}
