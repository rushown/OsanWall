"""Reference cryptography for OsanWall E2EE plane — use libsodium/pynacl in production for X25519/Ed25519."""
from __future__ import annotations

import hashlib
import hmac
import os
import secrets
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives import hashes


AES_KEY_LEN = 32
GCM_NONCE_LEN = 12


def hkdf_sha256(ikm: bytes, salt: bytes | None, info: bytes, length: int) -> bytes:
    return HKDF(algorithm=hashes.SHA256(), length=length, salt=salt, info=info).derive(ikm)


def recipient_blind_id(pepper: bytes, recipient_route_key: bytes) -> bytes:
    mac = hmac.new(pepper, recipient_route_key, hashlib.sha256).digest()
    return mac[:16]


def delivery_tag(delivery_key: bytes, message_id: bytes, chain_index: int) -> bytes:
    idx = chain_index.to_bytes(8, "big")
    mac = hmac.new(delivery_key, message_id + idx, hashlib.sha256).digest()
    return mac


def encrypt_aes256_gcm_hmac(aes_key: bytes, aad: bytes, plaintext: bytes) -> bytes:
    if len(aes_key) != AES_KEY_LEN:
        raise ValueError("aes key size")
    nonce = os.urandom(GCM_NONCE_LEN)
    aesgcm = AESGCM(aes_key)
    ct = aesgcm.encrypt(nonce, plaintext, aad)
    hmk = hmac.new(hkdf_sha256(aes_key, None, b"e2ee-hmac-v1", 32), nonce + ct, hashlib.sha256).digest()
    return nonce + ct + hmk


def decrypt_aes256_gcm_hmac(aes_key: bytes, aad: bytes, blob: bytes) -> bytes:
    if len(blob) < GCM_NONCE_LEN + 16 + 32:
        raise ValueError("blob")
    nonce = blob[:GCM_NONCE_LEN]
    ct = blob[GCM_NONCE_LEN:-32]
    expect = blob[-32:]
    hmk = hmac.new(hkdf_sha256(aes_key, None, b"e2ee-hmac-v1", 32), nonce + ct, hashlib.sha256).digest()
    if not hmac.compare_digest(hmk, expect):
        raise ValueError("hmac")
    aesgcm = AESGCM(aes_key)
    return aesgcm.decrypt(nonce, ct, aad)


def pad_random(plaintext: bytes, target_len: int) -> bytes:
    if len(plaintext) > target_len - 4:
        raise ValueError("plaintext too long")
    n = len(plaintext)
    head = n.to_bytes(4, "big")
    pad = secrets.token_bytes(target_len - 4 - n)
    return head + plaintext + pad


def unpad_random(padded: bytes) -> bytes:
    if len(padded) < 4:
        raise ValueError("pad")
    n = int.from_bytes(padded[:4], "big")
    return padded[4 : 4 + n]
