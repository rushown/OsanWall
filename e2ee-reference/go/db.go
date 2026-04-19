package e2ee

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"errors"
	"time"
)

// MessageInsert is the server-side struct for inserting a blinded row (no plaintext identities).
type MessageInsert struct {
	DeliveryTag       []byte
	RecipientBlindID  []byte
	Ciphertext        []byte
	AuthTag           []byte // optional if merged into Ciphertext layout
	InnerHMAC         []byte
	PaddedLen         int
	EncryptedHeader   []byte
	IdempotencyHash   []byte
	ExpiresAt         time.Time
}

// MessageStore is implemented by your PostgreSQL repository layer.
type MessageStore interface {
	InsertIfAbsent(ctx context.Context, m MessageInsert) (inserted bool, err error)
	FetchByDeliveryTag(ctx context.Context, tag []byte) ([]MessageInsert, error)
	DeleteExpired(ctx context.Context, before time.Time) (int64, error)
}

// RateLimiter limits by opaque blinded token only (never IP).
type RateLimiter interface {
	Allow(ctx context.Context, blindToken []byte, window time.Duration, max int) (bool, error)
}

// NewRandomRouteID returns 32-byte random route id for hourly rotation.
func NewRandomRouteID() ([]byte, error) {
	b := make([]byte, 32)
	if _, err := rand.Read(b); err != nil {
		return nil, err
	}
	return b, nil
}

// EncodeOpaqueHeader returns base64 for HTTP headers without leaking raw bytes in logs (still treat as secret).
func EncodeOpaqueHeader(b []byte) string {
	return base64.RawURLEncoding.EncodeToString(b)
}

var ErrDuplicateIdempotency = errors.New("e2ee: duplicate idempotency key")
