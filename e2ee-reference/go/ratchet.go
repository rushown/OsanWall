package e2ee

import (
	"crypto/sha256"
	"encoding/binary"
	"errors"
)

// Simplified double-ratchet state (single peer pair). Production deployments should use
// libsignal-protocol (or MLS for groups) with full spec test vectors; this module shows
// KDF chain layout and root/chain/message key derivation compatible with Signal-style HKDF labels.

const (
	chainLabelRoot  = "OsanWall-DR-Root-v1"
	chainLabelChain = "OsanWall-DR-Chain-v1"
	chainLabelMsg   = "OsanWall-DR-MessageKey-v1"
)

// RatchetSession holds root + sending/receiving chain keys after initial X3DH-derived secret.
type RatchetSession struct {
	RootKey       [32]byte
	SendChainKey  [32]byte
	RecvChainKey  [32]byte
	SendCounter   uint64
	RecvCounter   uint64
	DHSendPriv    []byte
	DHSendPub     []byte
	DHRecvPub     []byte
}

// NewRatchetSessionFromSharedSecret builds session from 32-byte X3DH output (already HKDF-expanded).
func NewRatchetSessionFromSharedSecret(sharedSecret []byte) (*RatchetSession, error) {
	if len(sharedSecret) != 32 {
		return nil, errors.New("e2ee: shared secret length")
	}
	var s RatchetSession
	copy(s.RootKey[:], HKDF(sharedSecret, nil, []byte(chainLabelRoot), 32))
	copy(s.SendChainKey[:], HKDF(s.RootKey[:], nil, []byte(chainLabelChain+"-send"), 32))
	copy(s.RecvChainKey[:], HKDF(s.RootKey[:], nil, []byte(chainLabelChain+"-recv"), 32))
	return &s, nil
}

// NextMessageKey derives per-message key from current chain key and advances chain (symmetric ratchet step).
func NextMessageKey(chainKey *[32]byte, counter *uint64) ([32]byte, error) {
	var mk [32]byte
	h := sha256.New()
	h.Write(chainKey[:])
	h.Write([]byte(chainLabelMsg))
	buf := make([]byte, 8)
	binary.BigEndian.PutUint64(buf, *counter)
	h.Write(buf)
	copy(mk[:], h.Sum(nil))
	// Advance chain: CK = HMAC-SHA256(CK, 0x01) — simplified; Signal uses HMAC with fixed salt.
	h2 := sha256.New()
	h2.Write(chainKey[:])
	h2.Write([]byte{0x01})
	copy(chainKey[:], h2.Sum(nil))
	*counter++
	return mk, nil
}

// EncryptRatchetPayload returns nonce||ct||hmac from message key (uses EncryptMessageAES256GCM).
func EncryptRatchetPayload(msgKey [32]byte, aad, plaintext []byte) ([]byte, error) {
	// Split message key: 32 bytes -> 16 AES + 16 HMAC key material via HKDF
	keys := HKDF(msgKey[:], nil, []byte("OsanWall-aead-split"), 64)
	aesKey := keys[:32]
	return EncryptMessageAES256GCM(aesKey, aad, plaintext, nil)
}

// DecryptRatchetPayload mirrors EncryptRatchetPayload.
func DecryptRatchetPayload(msgKey [32]byte, aad, blob []byte) ([]byte, error) {
	keys := HKDF(msgKey[:], nil, []byte("OsanWall-aead-split"), 64)
	aesKey := keys[:32]
	return DecryptMessageAES256GCM(aesKey, aad, blob)
}
