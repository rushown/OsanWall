#!/usr/bin/env python3
"""
Reference cryptography for OsanWall E2EE plane (PyNaCl / cryptography).
Install: pip install pynacl cryptography
"""
from __future__ import annotations

import hashlib
import hmac
import secrets
from typing import Tuple

from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives import hashes
from nacl.bindings import crypto_scalarmult, crypto_scalarmult_base, crypto_sign_keypair
from nacl.pwhash.argon2id import kdf as argon2id_kdf


AES_KEY_LEN = 32
GCM_NONCE_LEN = 12
BLIND_ID_LEN = 16


def generate_ed25519_identity() -> Tuple[bytes, bytes]:
    """Returns (signing_public_32, signing_secret_64) via libsodium-compatible API."""
    return crypto_sign_keypair()


def generate_x25519_keypair() -> Tuple[bytes, bytes]:
    """Returns (public_32, secret_32) for X25519."""
    sk = secrets.token_bytes(32)
    pk = crypto_scalarmult_base(sk)
    return pk, sk


def x25519_shared_secret(sk: bytes, pk: bytes) -> bytes:
    return crypto_scalarmult(sk, pk)


def derive_storage_key(
    password: bytes, salt: bytes, mem_kib: int = 65536, ops: int = 3
) -> bytes:
    s = salt[:16].ljust(16, b"\0")
    return argon2id_kdf(
        AES_KEY_LEN, password, s, opslimit=ops, memlimit=mem_kib * 1024
    )


def hkdf_sha256(ikm: bytes, salt: bytes | None, info: bytes, length: int) -> bytes:
    hk = HKDF(algorithm=hashes.SHA256(), length=length, salt=salt, info=info)
    return hk.derive(ikm)


def recipient_blind_id(pepper: bytes, recipient_route_key: bytes) -> bytes:
    mac = hmac.new(pepper, recipient_route_key, hashlib.sha256).digest()
    return mac[:BLIND_ID_LEN]


def delivery_tag(delivery_key: bytes, message_id: bytes, chain_index: int) -> bytes:
    idx = chain_index.to_bytes(8, "big")
    mac = hmac.new(delivery_key, message_id + idx, hashlib.sha256)
    return mac.digest()


def _hmac_key_for_aes(aes_key: bytes) -> bytes:
    return hmac.new(b"e2ee-hmac-v1", aes_key, hashlib.sha256).digest()


def encrypt_aes256_gcm_with_outer_hmac(
    aes_key: bytes, aad: bytes, plaintext: bytes
) -> bytes:
    if len(aes_key) != AES_KEY_LEN:
        raise ValueError("aes key length")
    nonce = secrets.token_bytes(GCM_NONCE_LEN)
    aesgcm = AESGCM(aes_key)
    ct = aesgcm.encrypt(nonce, plaintext, aad)
    inner_key = _hmac_key_for_aes(aes_key)
    hmk = hmac.new(inner_key, digestmod=hashlib.sha256)
    hmk.update(nonce)
    hmk.update(ct)
    outer = hmk.digest()
    return nonce + ct + outer


def decrypt_aes256_gcm_with_outer_hmac(aes_key: bytes, aad: bytes, blob: bytes) -> bytes:
    if len(blob) < GCM_NONCE_LEN + 16 + 32:
        raise ValueError("blob too short")
    nonce = blob[:GCM_NONCE_LEN]
    ct = blob[GCM_NONCE_LEN:-32]
    outer = blob[-32:]
    inner_key = _hmac_key_for_aes(aes_key)
    hmk = hmac.new(inner_key, digestmod=hashlib.sha256)
    hmk.update(nonce)
    hmk.update(ct)
    expected = hmk.digest()
    if not hmac.compare_digest(expected, outer):
        raise ValueError("hmac mismatch")
    aesgcm = AESGCM(aes_key)
    return aesgcm.decrypt(nonce, ct, aad)
