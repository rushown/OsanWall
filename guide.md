# OsanWall — Production E2EE Chat & Privacy-Preserving Personalization

This document is the **protocol specification**, **database design**, **reference implementations**, **deployment hardening**, and **free-tier setup guide** for an end-to-end encrypted messaging plane that can sit alongside the existing OsanWall Android app (Firebase today). It is written so a **malicious server**, **stolen database**, or **TLS MITM** cannot read message content or reliably infer social graphs if clients follow the protocol.

**Repository layout**

| Path | Purpose |
|------|---------|
| `e2ee-reference/schema.sql` | PostgreSQL DDL (blinded indexes, TTL, audit) |
| `e2ee-reference/go/` | Go reference: X25519/Ed25519, Argon2id, HKDF, AES-256-GCM + outer HMAC, blinded IDs, minimal ratchet helpers |
| `e2ee-reference/python/crypto_reference.py` | Python mirror (PyNaCl + cryptography) |
| `e2ee-reference/nodejs/secure_push.js` | Silent push envelope without metadata |
| `e2ee-reference/nginx/osanwall-e2ee.conf` | TLS + security headers + proxy hygiene |
| `docker-compose.yml` | Local PostgreSQL + Redis (extend for E2EE API) |

---

## 1. Threat model

| Actor | Capability | Outcome if protocol followed |
|-------|------------|------------------------------|
| Network attacker | Read/modify TLS, replay | Cannot decrypt; replays fail (idempotency + ratchet) |
| Malicious server | Full DB, push infra, API | Cannot decrypt; cannot forge signatures; minimal metadata |
| DBA / admin | SQL, backups | Ciphertext + blinded fields only; no pairwise plaintext |
| Compromised device | Memory, local DB | That device only; recovery via new keys + optional social recovery |

**Non-goals:** Hiding that *a given blinded token* used bandwidth; hiding global uptime. You **do** minimize: who talks to whom, message sizes, timing correlation, and content.

---

## 2. Cryptographic primitives

| Use | Primitive | Notes |
|-----|-----------|--------|
| Identity | **Ed25519** | Long-term signing; certificates for prekeys |
| Key agreement | **X25519** (ECDH) | X3DH-style async setup |
| KDF | **HKDF-SHA256** | Root/chain/message keys |
| AEAD | **AES-256-GCM** | Unique 12-byte nonce per message |
| Integrity (extra) | **HMAC-SHA256** | Over `nonce ‖ ciphertext` with key derived from AEAD key — mitigates some cross-protocol oracle scenarios |
| Password-based wrapping | **Argon2id** | Client derives local storage key; wraps private material for optional backup blob |
| Password hashing (if server verifies) | **Argon2id** | Server never stores keys to message plaintext |

**Double ratchet:** After initial X3DH, run a **double ratchet** (DH ratchet + symmetric ratchet) per Signal specification. The Go package `e2ee-reference/go/ratchet.go` illustrates **symmetric ratchet stepping** and KDF labels; for production, integrate **libsignal** (or **MLS** for groups) and run the official test vectors.

---

## 3. Protocol specification (bullet points)

### 3.1 Key material (per user)

- **IK** — Ed25519 identity key pair (signing).
- **SPK** — Signed prekey (X25519), rotated on a schedule; signature by IK over `SPK_pub ‖ key_id`.
- **OPK** — One-time prekeys (X25519), batch-published; consumed once.
- **Upload**: Server stores **only public** keys + encrypted-at-rest bundle (client encrypts sensitive fields with **Argon2id(password, salt)**-derived key before upload if you store anything beyond public bytes).

### 3.2 Session setup (X3DH sketch)

1. Initiator fetches responder’s IK, SPK, one OPK (if any).
2. Initiator generates ephemeral X25519 **EK**.
3. DH outputs: `DH1 = DH(EK, IK_r)`, `DH2 = DH(EK, SPK_r)`, `DH3 = DH(EK, OPK_r)` (and variants if OPK missing per Signal rules).
4. **SK** = HKDF(`DH1 ‖ DH2 ‖ DH3 ‖ …`, salt, info="OsanWall-X3DH-v1").
5. Initiator sends first message containing: EK_pub, used prekey ids, **encrypted** initial payload.
6. Responder completes ratchet init; both sides **delete** OPK private key material after use.

### 3.3 Double ratchet (runtime)

- Maintain **root key**, **sending/receiving chain keys**, **DH ratchet** steps when reply flows.
- Each message: derive **message key** from chain key; **advance** chain; encrypt with AES-256-GCM; **no plaintext sequence numbers** — counters live inside **encrypted header blob**.
- **Forward secrecy:** Old chain keys deleted after use. **Break-in recovery:** New DH step advances root.

### 3.4 Message envelope (on the wire / in DB)

| Field | Server visibility | Purpose |
|-------|-------------------|---------|
| `recipient_blind_id` | Opaque 16-byte | HMAC(pepper, route_key) — index for fetch |
| `delivery_tag` | Opaque 32-byte | Lookup / ACK without identity |
| `ciphertext` | Blob | nonce ‖ AEAD ‖ outer HMAC |
| `encrypted_header` | Blob | ratchet counters, inner message id, typing — **never parsed server-side** |
| `padded_len` | Integer bucket | 1024–4096 — hides exact length class |
| `expires_at` | Time | TTL for sealed storage |
| `idempotency_hash` | Hash | dedup |

**Plaintext metadata policy:** Round **timestamps to 1 minute** client-side before optional non-E2EE analytics; server stores **expiry** only.

### 3.5 Anti-tracking layers

- **Ephemeral routing IDs:** Client rotates **route_id** (random 256-bit) hourly; registers `H(route_secret)` with server. Server maps **route_id → blind_bucket** without stable user id in same table as messages (see `e2ee_route_buckets`).
- **Sealed sender (practical variant):** Sender obtains **delivery capability** for a blinded inbox id (from prior out-of-band or PSI). Upload path accepts only **ciphertext + blinded recipient**. Server **cannot** verify sender identity unless you add **anonymous credentials**: issuer signs `(blinded_inbox)` without linking to Firebase UID — implement via **blind RSA** or **privacy pass–style** tokens. Minimum viable: **sender signs** inner ciphertext with session key only recipients verify — server sees nothing.
- **No IP logging:** Edge sets `X-Forwarded-For` empty to app; rate limit by **HMAC(server_pepper, blinded_session_token)**.
- **Padding:** Pad body to random length in **fixed set** {1024, 2048, 4096} bytes **before** AEAD on inner plaintext, or pad ciphertext to fixed size **after** encryption with random junk (second layer).

### 3.6 Sequence diagrams (text)

**Registration / key upload**

```
Client                          Server                    DB
  | generate IK, SPK, OPKs         |                        |
  | sign SPK with IK               |                        |
  | POST /keys (pub bundle)        | store public keys     | INSERT e2ee_users, prekeys
  |<------------------------------| OK                      |
```

**First message (async)**

```
Alice                           Server                     Bob
  | X3DH → SK                     |                         |
  | encrypt(inner)                |                         |
  | POST /msg blinded_recipient   | index by delivery_tag   |
  |------------------------------>| store ciphertext       |
  |                               | silent push (opaque)   |--> fetch by blind_id
  |                               |                         | decrypt
```

**Double ratchet step**

```
A                                      B
| msg_n encrypted with MK_n            |
|-------------------------------------->|
|                                        | derive MK_n, verify, advance chain
|                                        | reply msg_{n+1} with new DH output
|<---------------------------------------|
```

---

## 4. Brutal security checklist (answers)

1. **Malicious server modifies message?** **Impossible** if clients verify **AEAD** and **outer HMAC**, and **Ed25519** on signed prekeys / inner frames as per your signed framing. Server cannot forge without breaking primitives.

2. **Server learns friends graph?** **No** if you use **blinded recipient ids**, **no pairwise plaintext**, **contact discovery** via **PSI** (e.g. ECDH-PSI) or **double-blinded hashed identifiers** with per-device salts never uploaded.

3. **Timing / side channels?** Use **constant-time** `HMAC.compare` / `subtle.ConstantTimeCompare` for MACs and tags; **constant-time** DH validation; avoid branchy decrypt error messages to clients.

4. **DB stolen?** **Ciphertext only**; **pepper** for blinded ids in **HSM/KMS** or env (not in DB); **TLS keys** rotated; **no** message keys in DB.

5. **Reorder messages?** **No**: encrypted header carries **chain index + message number**; after decrypt, discard if not next expected (or use sliding window).

6. **Metadata leakage?** **Pad** sizes; **bucket** timestamps; **batch** fetches; **noise** requests.

7. **Lost private keys?** **No escrow.** Optional **Shamir** shares to trusted contacts; **export** encrypted backup with Argon2id.

---

## 5. Database schema (SQL)

See `e2ee-reference/schema.sql` for full DDL. Design rules:

- **Indexes:** `delivery_tag`, `recipient_blind_id`, `expires_at` — **never** raw user id on message rows.
- **Row-level encryption:** Application encrypts **encrypted_key_bundle**; PostgreSQL **TDE** optional extra layer.
- **Redis:** OTP prekeys with **TTL**; PostgreSQL optional archive.
- **Retention:** `expires_at` + periodic `DELETE`; **VACUUM**; **no** logical replication of decrypted fields (there are none).

---

## 6. Personalization without leaking interests

**Client**

- Maintains vector **v** locally (never uploaded).
- Periodically downloads **encrypted buckets** `B_i` each containing **content ids** encrypted under **bucket key K_i** known only after client unwraps with **derived key** from prior session (or public random beacon).

**Server**

- Publishes **Bloom filters** per topic bucket with **salted hashes** `H(salt_j, tag)` — salt rotates per bucket version.
- Logs only **bucket id** fetch counts, not item-level matches.

**PSI-style match (simplified)**

```text
CLIENT:
  for each local interest tag t:
    h = HMAC(device_secret, t)
  request buckets matching coarse region (from IP geo you already have — optional) OR global rotation
  for each bucket ciphertext:
    decrypt to candidate set S
    intersection = S ∩ {local hashed tags}
  rank locally, enqueue UI

SERVER:
  serve Enc_K(bucket_payload) without knowing K
  rotate bucket keys when cohort expires
```

**Bloom + salted hashes:** Prevents server from learning **which** tags hit from a single fetch.

---

## 7. Reference code map

- **Go:** `GenerateIdentityKeyPair`, `GenerateX25519KeyPair`, `X25519SharedSecret`, `DeriveStorageKey`, `RecipientBlindID`, `DeliveryTag`, `EncryptMessageAES256GCM` / `DecryptMessageAES256GCM`, `PadRandom`, `RatchetSession` — see `e2ee-reference/go/*.go`.
- **Python:** Same surface in `e2ee-reference/python/crypto_reference.py`.
- **Node push:** `e2ee-reference/nodejs/secure_push.js`.
- **DB interfaces:** `e2ee-reference/go/db.go` — implement with `database/sql` or pgx.

---

## 8. Security headers, CSP, deployment

### 8.1 HTTP headers (API)

- `Strict-Transport-Security: max-age=63072000; includeSubDomains; preload`
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Referrer-Policy: no-referrer`
- `Permissions-Policy: geolocation=(), microphone=(), camera=()`
- `Cross-Origin-Opener-Policy: same-origin`
- `Cross-Origin-Resource-Policy: same-site`
- `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'; base-uri 'none'` (for any HTML error pages)

### 8.2 mTLS

- Internal API ↔ DB: **PostgreSQL SSL** with client certs rotated weekly.
- Redis: **TLS** + **ACL** + strong password; **no** plaintext in VPC.

### 8.3 WAF / firewall

- Allow **443** only to edge; **SSH** bastion or **Tailscale**; deny DB ports from internet.
- WAF rules: **block** SQLi patterns on JSON; **size cap** body; **rate limit** per blinded token (see nginx map).

### 8.4 Nginx

See `e2ee-reference/nginx/osanwall-e2ee.conf`. **Strip** `X-Forwarded-For` to backend if you want **no IP** — then you **must** rate-limit on **blinded token** header.

---

## 9. Zero-day / global key rotation (no server trust)

**Goal:** Rotate **all** server-side secrets and **client trust anchors** without assuming server honesty.

1. **Publish** new **IK** for service (`service_identity.pem`) out-of-band (app store release + pinned keys in binary).
2. **Clients** verify **signature** on a **RotationManifest** delivered **only** via **signed update channel** (app signing key) containing: new server epoch, new WAF keys, **new pepper** for blinded ids.
3. **Pepper rotation:** Clients recompute **blinded ids**; **dual-write** messages with **epoch tag** in encrypted header; server supports **two epochs** during migration window.
4. **TLS:** Issue new certs; **pin** SPKI hashes in app until rotation completes.
5. **User keys:** Users **do not** rotate message history keys automatically — **optional** re-encrypt locally when opening app after manifest.
6. **Redis/Postgres credentials:** Rotate via **vault**; **invalidate** all sessions; **force** new blinded session tokens.

**Clients must not** fetch new trust roots only from the same TLS endpoint without **code signing** or **TOFU pin** update — otherwise compromised TLS wins.

---

## 10. Free / low-cost setup guide

### 10.1 Prerequisites

- Docker + Docker Compose (already in repo).
- A VPS (e.g. **Hetzner**, **Oracle Free Tier**, **Fly.io** free allowances) or home lab.

### 10.2 Local development

```bash
cd /home/ocean/vscode/MeroWall
docker compose up -d
# PostgreSQL: localhost:5432  user osanwall / db osanwall (change in production)
# Redis: localhost:6379
```

Apply schema:

```bash
psql "postgresql://osanwall:osanwall@127.0.0.1:5432/osanwall" -f e2ee-reference/schema.sql
```

### 10.3 Go reference (optional)

Install Go 1.22+, then:

```bash
cd e2ee-reference/go
go test ./...
go build ./...
```

### 10.4 Python reference

```bash
cd e2ee-reference
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python -c "from crypto_reference import generate_x25519_keypair; print(len(generate_x25519_keypair()[0]))"
```

### 10.5 Production checklist (no fee stack)

| Item | Free / open approach |
|------|----------------------|
| TLS | **Let’s Encrypt** (certbot) |
| Secrets | **SOPS** + gitignored env; later **Vault** |
| DB | Self-hosted **PostgreSQL 16** |
| Cache / OTP | **Redis 7** with TLS + ACL |
| Push | **FCM** (free tier); payload **silent + opaque** |
| Monitoring | **Grafana Cloud** free tier or self-hosted |
| Backups | **pg_dump** encrypted to **S3-compatible** (Cloudflare R2 free tier) |

### 10.6 Integrating with the existing Android app

Today `ChatRepository` uses **Firebase RTDB** for plaintext-style messages. A migration path:

1. Add **E2EE message type** where `content` is **base64(ciphertext)** and keys live in **Room** encrypted with Argon2id-derived key.
2. Replace transport with **E2EE API** + blinded ids; keep Firebase only for **auth** or migrate auth to **OIDC** later.
3. Run **double ratchet** in Kotlin using **libsignal** JNI bindings or **Rust** crate via JNI.

---

## 11. Audit logging (safe)

`e2ee_audit_events` stores **event_type** + **opaque_actor** (HMAC of blinded token) + **detail_hmac** — no pairwise ids. Example events: `message_queued`, `message_delivered`, `rate_limited`, `key_rotated`.

---

## 12. Operational notes

- **Weekly:** Rotate DB passwords, Redis ACL, HMAC peppers (with dual epoch).
- **Idempotency:** Unique `(recipient_blind_id, idempotency_hash)` prevents replay storage.
- **Offline:** Messages retained until `expires_at`; client ACK deletes or marks delivered.
- **Compromised device:** New IK + broadcast **key change** message inside E2EE to contacts; re-encrypt local Room DB.

---

*This guide is intentionally conservative: where a full Signal-sized implementation is required (full double ratchet + X3DH test vectors), use **audited libraries** (libsignal / OpenMLS) rather than extending the minimal reference in this repo alone.*
