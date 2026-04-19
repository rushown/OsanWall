package e2ee

import (
	"bytes"
	"testing"
)

func TestEncryptDecryptRoundTrip(t *testing.T) {
	key := make([]byte, AESKeySize)
	for i := range key {
		key[i] = byte(i + 1)
	}
	aad := []byte("associated-data")
	pt := []byte("hello e2ee")

	ct, err := EncryptMessageAES256GCM(key, aad, pt, nil)
	if err != nil {
		t.Fatal(err)
	}
	out, err := DecryptMessageAES256GCM(key, aad, ct)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(out, pt) {
		t.Fatalf("plaintext mismatch")
	}
}

func TestPadRandom(t *testing.T) {
	pt := []byte("short")
	padded, err := PadRandom(pt, 1024, nil)
	if err != nil {
		t.Fatal(err)
	}
	if len(padded) != 1024 {
		t.Fatalf("len %d", len(padded))
	}
	un, err := UnpadRandom(padded)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(un, pt) {
		t.Fatalf("unpad mismatch")
	}
}
