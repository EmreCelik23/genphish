package security

import (
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"fmt"
	"testing"
	"time"

	"github.com/google/uuid"
)

const (
	testTrackSecret      = "track-secret"
	testOAuthSecretValue = "oauth-secret"
)

func TestVerifyTrackingQuery(t *testing.T) {
	verifier := NewVerifier(true, testTrackSecret, testOAuthSecretValue, 10*time.Minute, NewInMemoryNonceStore())
	campaignID := uuid.New()
	employeeID := uuid.New()
	companyID := uuid.New()
	exp := time.Now().UTC().Add(5 * time.Minute).Unix()
	payload := fmt.Sprintf("c=%s&e=%s&co=%s&exp=%d", campaignID, employeeID, companyID, exp)
	sig := signTest(testTrackSecret, payload)

	if err := verifier.VerifyTrackingQuery(campaignID, employeeID, companyID, fmt.Sprintf("%d", exp), sig); err != nil {
		t.Fatalf("expected valid signature, got error: %v", err)
	}

	if err := verifier.VerifyTrackingQuery(campaignID, employeeID, companyID, fmt.Sprintf("%d", exp), "bad"); err == nil {
		t.Fatalf("expected invalid signature error")
	}

	expired := time.Now().UTC().Add(-2 * time.Minute).Unix()
	expiredPayload := fmt.Sprintf("c=%s&e=%s&co=%s&exp=%d", campaignID, employeeID, companyID, expired)
	expiredSig := signTest(testTrackSecret, expiredPayload)
	if err := verifier.VerifyTrackingQuery(campaignID, employeeID, companyID, fmt.Sprintf("%d", expired), expiredSig); err == nil {
		t.Fatalf("expected expired signature error")
	}
}

func TestParseAndVerifyOAuthState(t *testing.T) {
	store := NewInMemoryNonceStore()
	verifier := NewVerifier(true, testTrackSecret, testOAuthSecretValue, 10*time.Minute, store)

	campaignID := uuid.New()
	employeeID := uuid.New()
	companyID := uuid.New()
	exp := time.Now().UTC().Add(5 * time.Minute).Unix()
	payload := fmt.Sprintf("c=%s&e=%s&co=%s&lang=en-US&exp=%d&nonce=testnonce123", campaignID, employeeID, companyID, exp)
	encodedPayload := base64.RawURLEncoding.EncodeToString([]byte(payload))
	signature := signTest(testOAuthSecretValue, payload)
	state := encodedPayload + "." + signature

	claims, err := verifier.ParseAndVerifyOAuthState(context.Background(), state)
	if err != nil {
		t.Fatalf("expected valid oauth state, got error: %v", err)
	}
	if claims.LanguageCode != "EN" {
		t.Fatalf("expected language EN, got %s", claims.LanguageCode)
	}

	_, replayErr := verifier.ParseAndVerifyOAuthState(context.Background(), state)
	if replayErr == nil {
		t.Fatalf("expected replayed nonce to be rejected")
	}

	badState := encodedPayload + ".invalid"
	if _, err := verifier.ParseAndVerifyOAuthState(context.Background(), badState); err == nil {
		t.Fatalf("expected invalid signature error")
	}
}

func signTest(secret string, payload string) string {
	mac := hmac.New(sha256.New, []byte(secret))
	mac.Write([]byte(payload))
	return base64.RawURLEncoding.EncodeToString(mac.Sum(nil))
}
