package e2ee

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"errors"
	"io"
)

const (
	AESKeySize   = 32
	GCMNonceSize = 12
	GCMTagSize   = 16
	// Payload layout: nonce(12) || ciphertext+tag || hmac(32) over (nonce||ct)
)

// EncryptMessageAES256GCM encrypts plaintext with separate HMAC over ciphertext for oracle hardening.
func EncryptMessageAES256GCM(key, additionalData, plaintext []byte, rng io.Reader) ([]byte, error) {
	if len(key) != AESKeySize {
		return nil, errors.New("e2ee: aes key size")
	}
	if rng == nil {
		rng = rand.Reader
	}
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return nil, err
	}
	nonce := make([]byte, GCMNonceSize)
	if _, err = io.ReadFull(rng, nonce); err != nil {
		return nil, err
	}
	ct := gcm.Seal(nil, nonce, plaintext, additionalData)
	hk := hmac.New(sha256.New, deriveHMACKey(key))
	hk.Write(nonce)
	hk.Write(ct)
	mac := hk.Sum(nil)
	out := make([]byte, 0, len(nonce)+len(ct)+len(mac))
	out = append(out, nonce...)
	out = append(out, ct...)
	out = append(out, mac...)
	return out, nil
}

func deriveHMACKey(aesKey []byte) []byte {
	mac := hmac.New(sha256.New, []byte("e2ee-hmac-v1"))
	mac.Write(aesKey)
	return mac.Sum(nil)
}

// DecryptMessageAES256GCM verifies HMAC then decrypts; rejects on any failure (no early plaintext branch).
func DecryptMessageAES256GCM(key, additionalData, blob []byte) ([]byte, error) {
	if len(key) != AESKeySize {
		return nil, errors.New("e2ee: aes key size")
	}
	if len(blob) < GCMNonceSize+GCMTagSize+sha256.Size {
		return nil, errors.New("e2ee: blob too short")
	}
	nonce := blob[:GCMNonceSize]
	ct := blob[GCMNonceSize : len(blob)-sha256.Size]
	expectedMAC := blob[len(blob)-sha256.Size:]
	hk := hmac.New(sha256.New, deriveHMACKey(key))
	hk.Write(nonce)
	hk.Write(ct)
	sum := hk.Sum(nil)
	if !ConstantTimeEqual(sum, expectedMAC) {
		return nil, errors.New("e2ee: hmac mismatch")
	}
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return nil, err
	}
	return gcm.Open(nil, nonce, ct, additionalData)
}

// PadRandom pads plaintext to targetLen with random bytes (hides length class).
func PadRandom(plaintext []byte, targetLen int, rng io.Reader) ([]byte, error) {
	if rng == nil {
		rng = rand.Reader
	}
	if len(plaintext) > targetLen-4 {
		return nil, errors.New("e2ee: plaintext too long for pad target")
	}
	out := make([]byte, targetLen)
	binaryLen := uint32(len(plaintext))
	out[0] = byte(binaryLen >> 24)
	out[1] = byte(binaryLen >> 16)
	out[2] = byte(binaryLen >> 8)
	out[3] = byte(binaryLen)
	copy(out[4:], plaintext)
	if _, err := io.ReadFull(rng, out[4+len(plaintext):]); err != nil {
		return nil, err
	}
	return out, nil
}

// UnpadRandom extracts plaintext from PadRandom output; validates length in constant time where possible.
func UnpadRandom(padded []byte) ([]byte, error) {
	if len(padded) < 4 {
		return nil, errors.New("e2ee: invalid pad")
	}
	n := int(uint32(padded[0])<<24 | uint32(padded[1])<<16 | uint32(padded[2])<<8 | uint32(padded[3]))
	if n < 0 || 4+n > len(padded) {
		return nil, errors.New("e2ee: invalid pad length")
	}
	return padded[4 : 4+n], nil
}
