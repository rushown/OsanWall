-- OsanWall E2EE message plane — PostgreSQL schema
-- Application-layer encryption: server stores ciphertext and blinded lookup keys only.
-- Run after: CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------------
-- Users: long-term identity is Ed25519 public key bytes (not Firebase UID in plaintext for E2EE plane).
-- uid_internal is an opaque server-side UUID; clients never need it for crypto.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS e2ee_users (
    uid_internal            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    identity_ed25519_pub    BYTEA NOT NULL CHECK (octet_length(identity_ed25519_pub) = 32),
    -- Encrypted key bundle (Argon2id-wrapped by client before upload).
    encrypted_key_bundle    BYTEA NOT NULL,
    bundle_nonce            BYTEA NOT NULL CHECK (octet_length(bundle_nonce) = 24),
    bundle_version          INT NOT NULL DEFAULT 1,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (identity_ed25519_pub)
);

CREATE INDEX IF NOT EXISTS idx_e2ee_users_updated ON e2ee_users (updated_at DESC);

-- ---------------------------------------------------------------------------
-- Signed prekeys (medium-term) + one-time prekeys: public parts only; private keys never leave client.
-- OTP stored in Redis with TTL in production; this table is optional cold archive / audit.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS e2ee_signed_prekeys (
    id              BIGSERIAL PRIMARY KEY,
    uid_internal    UUID NOT NULL REFERENCES e2ee_users(uid_internal) ON DELETE CASCADE,
    key_id          INT NOT NULL,
    signed_prekey_x25519_pub BYTEA NOT NULL CHECK (octet_length(signed_prekey_x25519_pub) = 32),
    signature       BYTEA NOT NULL CHECK (octet_length(signature) = 64),
    valid_from      TIMESTAMPTZ NOT NULL DEFAULT now(),
    valid_until     TIMESTAMPTZ NOT NULL,
    UNIQUE (uid_internal, key_id)
);

CREATE INDEX IF NOT EXISTS idx_signed_prekeys_lookup
    ON e2ee_signed_prekeys (uid_internal, valid_until DESC);

-- ---------------------------------------------------------------------------
-- Inbox: one row per ciphertext chunk. No plaintext (sender_id, recipient_id) — only blinded tags.
-- recipient_blind_id = HMAC-SHA256(pepper_recipient, recipient_route_key) truncated for index
-- delivery_tag       = blinded lookup for fetch / ACK (server cannot invert).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS e2ee_messages (
    id                  BIGSERIAL PRIMARY KEY,
    delivery_tag        BYTEA NOT NULL,
    recipient_blind_id  BYTEA NOT NULL,
    ciphertext          BYTEA NOT NULL,
    -- AES-GCM tag is inside ciphertext layout: nonce || ct || tag (client-defined) OR separate:
    auth_tag            BYTEA CHECK (auth_tag IS NULL OR octet_length(auth_tag) = 16),
    inner_hmac          BYTEA,
    padded_len          INT NOT NULL CHECK (padded_len >= 1024 AND padded_len <= 4096),
    expires_at          TIMESTAMPTZ NOT NULL,
    -- Encrypted header blob (ratchet counters, message id) — never parse server-side.
    encrypted_header    BYTEA NOT NULL,
    -- Idempotency: client-supplied ULID / hash; stored hashed so DB leak does not reveal equality easily.
    idempotency_hash    BYTEA NOT NULL,
    inserted_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Only blinded columns — no raw user ids.
CREATE UNIQUE INDEX IF NOT EXISTS uq_e2ee_messages_idem
    ON e2ee_messages (recipient_blind_id, idempotency_hash);

CREATE INDEX IF NOT EXISTS idx_e2ee_messages_delivery
    ON e2ee_messages (delivery_tag);

CREATE INDEX IF NOT EXISTS idx_e2ee_messages_recipient_expires
    ON e2ee_messages (recipient_blind_id, expires_at);

-- Partition by month optional at scale: LIST/RANGE on date_trunc('month', inserted_at).

-- ---------------------------------------------------------------------------
-- Ephemeral routing: rotated hourly on client; server maps route_id -> blinded bucket only.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS e2ee_route_buckets (
    route_id        BYTEA PRIMARY KEY CHECK (octet_length(route_id) = 32),
    blind_bucket    BYTEA NOT NULL,
    valid_until     TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_route_buckets_valid ON e2ee_route_buckets (valid_until);

-- ---------------------------------------------------------------------------
-- Audit: no pairwise metadata — only aggregate events.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS e2ee_audit_events (
    id              BIGSERIAL PRIMARY KEY,
    event_type      TEXT NOT NULL CHECK (event_type IN ('message_queued','message_delivered','rate_limited','key_rotated')),
    opaque_actor    BYTEA,
    detail_hmac     BYTEA NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- TTL cleanup job (pg_cron or application): DELETE FROM e2ee_messages WHERE expires_at < now();
-- REINDEX periodically if high churn; use BRIN on inserted_at for append-only analytics if needed.
