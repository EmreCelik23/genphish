package security

import (
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"errors"
	"fmt"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/google/uuid"
)

type StateClaims struct {
	CampaignID   uuid.UUID
	EmployeeID   uuid.UUID
	CompanyID    uuid.UUID
	LanguageCode string
	Nonce        string
	ExpiresAt    time.Time
}

type Verifier struct {
	requireSignedLinks bool
	trackingSecret     []byte
	oauthSecret        []byte
	oauthStateTTL      time.Duration
	nonceStore         NonceStore
	nowFn              func() time.Time
}

func NewVerifier(
	requireSignedLinks bool,
	trackingSecret string,
	oauthSecret string,
	oauthStateTTL time.Duration,
	nonceStore NonceStore,
) *Verifier {
	trackSecret := strings.TrimSpace(trackingSecret)
	if trackSecret == "" {
		trackSecret = "genphish-dev-tracking-secret"
	}

	oauth := strings.TrimSpace(oauthSecret)
	if oauth == "" {
		oauth = trackSecret
	}

	if oauthStateTTL <= 0 {
		oauthStateTTL = 10 * time.Minute
	}

	if nonceStore == nil {
		nonceStore = NewInMemoryNonceStore()
	}

	return &Verifier{
		requireSignedLinks: requireSignedLinks,
		trackingSecret:     []byte(trackSecret),
		oauthSecret:        []byte(oauth),
		oauthStateTTL:      oauthStateTTL,
		nonceStore:         nonceStore,
		nowFn:              func() time.Time { return time.Now().UTC() },
	}
}

func (v *Verifier) VerifyTrackingQuery(
	campaignID uuid.UUID,
	employeeID uuid.UUID,
	companyID uuid.UUID,
	expRaw string,
	signatureRaw string,
) error {
	if !v.requireSignedLinks {
		return nil
	}

	expRaw = strings.TrimSpace(expRaw)
	signatureRaw = strings.TrimSpace(signatureRaw)
	if expRaw == "" || signatureRaw == "" {
		return errors.New("missing signed link parameters")
	}

	exp, err := strconv.ParseInt(expRaw, 10, 64)
	if err != nil {
		return fmt.Errorf("invalid signed link expiry: %w", err)
	}

	nowUnix := v.nowFn().Unix()
	if nowUnix > exp {
		return errors.New("signed link is expired")
	}

	payload := fmt.Sprintf("c=%s&e=%s&co=%s&exp=%d", campaignID, employeeID, companyID, exp)
	expectedSig := sign(v.trackingSecret, payload)
	if !constantTimeEquals(expectedSig, signatureRaw) {
		return errors.New("signed link verification failed")
	}

	return nil
}

func (v *Verifier) ParseAndVerifyOAuthState(ctx context.Context, rawState string) (StateClaims, error) {
	state := strings.TrimSpace(rawState)
	if state == "" {
		return StateClaims{}, errors.New("missing oauth state")
	}

	parts := strings.Split(state, ".")
	if len(parts) != 2 {
		return StateClaims{}, errors.New("invalid oauth state format")
	}

	decodedPayloadBytes, err := base64.RawURLEncoding.DecodeString(parts[0])
	if err != nil {
		return StateClaims{}, fmt.Errorf("invalid oauth state encoding: %w", err)
	}
	payload := string(decodedPayloadBytes)

	expectedSig := sign(v.oauthSecret, payload)
	if !constantTimeEquals(expectedSig, parts[1]) {
		return StateClaims{}, errors.New("oauth state signature mismatch")
	}

	values, err := url.ParseQuery(payload)
	if err != nil {
		return StateClaims{}, fmt.Errorf("invalid oauth state payload: %w", err)
	}

	campaignID, err := uuid.Parse(values.Get("c"))
	if err != nil {
		return StateClaims{}, fmt.Errorf("invalid campaign id in oauth state: %w", err)
	}
	employeeID, err := uuid.Parse(values.Get("e"))
	if err != nil {
		return StateClaims{}, fmt.Errorf("invalid employee id in oauth state: %w", err)
	}
	companyID, err := uuid.Parse(values.Get("co"))
	if err != nil {
		return StateClaims{}, fmt.Errorf("invalid company id in oauth state: %w", err)
	}

	expRaw := strings.TrimSpace(values.Get("exp"))
	if expRaw == "" {
		return StateClaims{}, errors.New("missing oauth state expiry")
	}
	expUnix, err := strconv.ParseInt(expRaw, 10, 64)
	if err != nil {
		return StateClaims{}, fmt.Errorf("invalid oauth state expiry: %w", err)
	}

	nonce := strings.TrimSpace(values.Get("nonce"))
	if nonce == "" {
		return StateClaims{}, errors.New("missing oauth state nonce")
	}

	now := v.nowFn()
	expiresAt := time.Unix(expUnix, 0).UTC()
	if now.After(expiresAt) {
		return StateClaims{}, errors.New("oauth state is expired")
	}

	// Reject overly-far future tokens even if signature is valid to keep TTL bounded.
	if expiresAt.Sub(now) > v.oauthStateTTL+time.Minute {
		return StateClaims{}, errors.New("oauth state expiry exceeds allowed ttl")
	}

	marked, markErr := v.nonceStore.TryMarkUsed(ctx, nonce, time.Until(expiresAt))
	if markErr != nil {
		return StateClaims{}, fmt.Errorf("nonce store failure: %w", markErr)
	}
	if !marked {
		return StateClaims{}, errors.New("oauth state nonce already used")
	}

	return StateClaims{
		CampaignID:   campaignID,
		EmployeeID:   employeeID,
		CompanyID:    companyID,
		LanguageCode: normalizeLanguageCode(values.Get("lang")),
		Nonce:        nonce,
		ExpiresAt:    expiresAt,
	}, nil
}

func sign(secret []byte, payload string) string {
	hash := hmac.New(sha256.New, secret)
	hash.Write([]byte(payload))
	return base64.RawURLEncoding.EncodeToString(hash.Sum(nil))
}

func constantTimeEquals(expected string, actual string) bool {
	expectedBytes := []byte(strings.TrimSpace(expected))
	actualBytes := []byte(strings.TrimSpace(actual))
	if len(expectedBytes) != len(actualBytes) {
		return false
	}
	return hmac.Equal(expectedBytes, actualBytes)
}

func normalizeLanguageCode(raw string) string {
	normalized := strings.ToLower(strings.TrimSpace(raw))
	if strings.HasPrefix(normalized, "en") || normalized == "english" || normalized == "ingilizce" {
		return "EN"
	}
	return "TR"
}
