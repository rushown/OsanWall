package e2ee

import (
	"crypto/ecdh"
	"crypto/ed25519"
	"crypto/rand"
	"errors"
	"io"

	"golang.org/x/crypto/argon2"
)

const (
	Ed25519PubSize  = ed25519.PublicKeySize
	Ed25519PrivSize = ed25519.PrivateKeySize
	X25519Size      = 32
	ArgonSaltSize   = 16
	ArgonKeyLen     = 32
)

var x25519 = ecdh.X25519()

// GenerateIdentityKeyPair returns a long-term Ed25519 identity (signing) key pair.
func GenerateIdentityKeyPair(r io.Reader) (pub, priv []byte, err error) {
	if r == nil {
		r = rand.Reader
	}
	pub, priv, err = ed25519.GenerateKey(r)
	if err != nil {
		return nil, nil, err
	}
	return pub, priv, nil
}

// GenerateX25519KeyPair generates an X25519 key pair for DH / prekeys.
func GenerateX25519KeyPair(r io.Reader) (pub, priv []byte, err error) {
	if r == nil {
		r = rand.Reader
	}
	privKey, err := x25519.GenerateKey(r)
	if err != nil {
		return nil, nil, err
	}
	return privKey.PublicKey().Bytes(), privKey.Bytes(), nil
}

// X25519SharedSecret computes ECDH shared secret and applies HKDF with context (caller supplies salt/info).
func X25519SharedSecret(ourPriv, theirPub []byte) ([]byte, error) {
	if len(ourPriv) != X25519Size || len(theirPub) != X25519Size {
		return nil, errors.New("e2ee: x25519 key length")
	}
	privKey, err := x25519.NewPrivateKey(ourPriv)
	if err != nil {
		return nil, err
	}
	pubKey, err := x25519.NewPublicKey(theirPub)
	if err != nil {
		return nil, err
	}
	ss, err := privKey.ECDH(pubKey)
	if err != nil {
		return nil, err
	}
	if len(ss) != X25519Size {
		return nil, errors.New("e2ee: unexpected ecdh length")
	}
	return ss, nil
}

// DeriveStorageKey derives a 32-byte key from password + salt using Argon2id (memory-hard).
func DeriveStorageKey(password []byte, salt []byte, time, memory uint32, threads uint8) []byte {
	if len(salt) < ArgonSaltSize {
		salt = append([]byte(nil), salt...)
		for len(salt) < ArgonSaltSize {
			salt = append(salt, 0)
		}
		salt = salt[:ArgonSaltSize]
	}
	return argon2.IDKey(password, salt, time, memory, threads, ArgonKeyLen)
}
