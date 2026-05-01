package security

import (
	"context"
	"sync"
	"time"

	redis "github.com/redis/go-redis/v9"
)

type NonceStore interface {
	TryMarkUsed(ctx context.Context, nonce string, ttl time.Duration) (bool, error)
	Close() error
}

type InMemoryNonceStore struct {
	mu        sync.Mutex
	nonces    map[string]time.Time
	lastSweep time.Time
}

func NewInMemoryNonceStore() *InMemoryNonceStore {
	return &InMemoryNonceStore{
		nonces: make(map[string]time.Time),
	}
}

func (s *InMemoryNonceStore) TryMarkUsed(_ context.Context, nonce string, ttl time.Duration) (bool, error) {
	if nonce == "" {
		return false, nil
	}

	if ttl <= 0 {
		ttl = 5 * time.Minute
	}

	now := time.Now().UTC()
	expiresAt := now.Add(ttl)

	s.mu.Lock()
	defer s.mu.Unlock()

	if existingExpiry, exists := s.nonces[nonce]; exists && existingExpiry.After(now) {
		return false, nil
	}

	s.nonces[nonce] = expiresAt
	s.sweepExpiredLocked(now)
	return true, nil
}

func (s *InMemoryNonceStore) Close() error {
	return nil
}

func (s *InMemoryNonceStore) sweepExpiredLocked(now time.Time) {
	if !s.lastSweep.IsZero() && now.Sub(s.lastSweep) < time.Minute {
		return
	}
	for nonce, expiry := range s.nonces {
		if !expiry.After(now) {
			delete(s.nonces, nonce)
		}
	}
	s.lastSweep = now
}

type RedisNonceStore struct {
	client *redis.Client
	prefix string
}

func NewRedisNonceStore(client *redis.Client, prefix string) *RedisNonceStore {
	return &RedisNonceStore{
		client: client,
		prefix: prefix,
	}
}

func (s *RedisNonceStore) TryMarkUsed(ctx context.Context, nonce string, ttl time.Duration) (bool, error) {
	if nonce == "" {
		return false, nil
	}
	if ttl <= 0 {
		ttl = 5 * time.Minute
	}

	return s.client.SetNX(ctx, s.prefix+nonce, "1", ttl).Result()
}

func (s *RedisNonceStore) Close() error {
	return s.client.Close()
}
