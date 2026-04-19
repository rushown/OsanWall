package e2ee

import (
	"crypto/hmac"
	"crypto/sha256"
	"crypto/subtle"
	"io"

	"golang.org/x/crypto/hkdf"
)

const (
	BlindIDLen   = 16
	DeliveryLen  = 32
	IdemHashLen  = 32
)

// RecipientBlindID = first BlindIDLen bytes of HMAC-SHA256(pepper, recipientRouteKey).
func RecipientBlindID(pepper, recipientRouteKey []byte) []byte {
	mac := hmac.New(sha256.New, pepper)
	mac.Write(recipientRouteKey)
	sum := mac.Sum(nil)
	out := make([]byte, BlindIDLen)
	copy(out, sum)
	return out
}

// DeliveryTag = HMAC-SHA256(deliveryKey, messageId || chainIndex) for server-side fetch index.
func DeliveryTag(deliveryKey, messageId []byte, chainIndex uint64) []byte {
	mac := hmac.New(sha256.New, deliveryKey)
	mac.Write(messageId)
	var idx [8]byte
	for i := 0; i < 8; i++ {
		idx[7-i] = byte(chainIndex >> (8 * i))
	}
	mac.Write(idx[:])
	return mac.Sum(nil)
}

// IdempotencyHash hashes client message id with server namespace to store dedup key.
func IdempotencyHash(serverNamespace, clientMessageID []byte) []byte {
	mac := hmac.New(sha256.New, serverNamespace)
	mac.Write(clientMessageID)
	sum := mac.Sum(nil)
	out := make([]byte, IdemHashLen)
	copy(out, sum)
	return out
}

// HKDF extracts a key of length n from ikm + salt + info.
func HKDF(ikm, salt, info []byte, n int) []byte {
	r := hkdf.New(sha256.New, ikm, salt, info)
	out := make([]byte, n)
	if _, err := io.ReadFull(r, out); err != nil {
		panic("e2ee: hkdf: " + err.Error())
	}
	return out
}

// ConstantTimeEqual wraps subtle.ConstantTimeCompare for byte slices of equal length.
func ConstantTimeEqual(a, b []byte) bool {
	if len(a) != len(b) {
		return false
	}
	return subtle.ConstantTimeCompare(a, b) == 1
}
