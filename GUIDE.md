# OsanWall — Complete Setup Guide

> Follow every step in order. By the end you'll have Firebase, the Cloudflare Worker, the Android app, and the E2EE chat backend all running.

---

## Prerequisites

Install all of these before starting.

| Tool | Version | Install |
|------|---------|---------|
| Android Studio | Hedgehog+ | [developer.android.com/studio](https://developer.android.com/studio) |
| JDK | 17+ | Bundled with Android Studio |
| Node.js | 18+ | [nodejs.org](https://nodejs.org) |
| Wrangler CLI | 3.x | `npm i -g wrangler` |
| Firebase CLI | latest | `npm i -g firebase-tools` |
| Docker + Compose | latest | [docker.com](https://docker.com) — required for E2EE local dev |
| Go | 1.22+ | [go.dev/dl](https://go.dev/dl) — required for E2EE reference impl |

---

## Step 1 — Clone & Install Dependencies

```bash
git clone https://github.com/your-username/OsanWall.git
cd OsanWall

# Install Cloudflare Worker dependencies
cd backend/worker
npm install
cd ../..
```

---

## Step 2 — Firebase Setup

### 2.1 Create Firebase Project

1. Go to [console.firebase.google.com](https://console.firebase.google.com)
2. Click **Add project** → name it `OsanWall`
3. Enable Google Analytics (optional but recommended) → **Create project**

### 2.2 Enable Services

Go into each service below and enable it exactly as described.

| Service | Steps |
|---------|-------|
| **Authentication** | Build → Authentication → Get started → Sign-in method → Enable **Email/Password** and **Google** |
| **Firestore** | Build → Firestore Database → Create database → **Start in production mode** → choose region |
| **Realtime Database** | Build → Realtime Database → Create database → **Start in locked mode** |
| **Storage** | Build → Storage → Get started → **Production mode** |
| **Cloud Messaging** | Automatically enabled — no action needed |
| **Crashlytics** | Release & Monitor → Crashlytics → **Enable** |

### 2.3 Add Android App to Firebase

1. Project Settings (gear icon) → **Add app** → Android
2. Package name: `com.osanwall`
3. App nickname: `OsanWall`
4. Click **Register app**
5. Download **`google-services.json`**
6. Move it into the project: replace `app/google-services.json` with your downloaded file

### 2.4 Deploy Firebase Security Rules & Indexes

```bash
# Login
firebase login

# Initialize (run in project root)
# When prompted, select: Firestore, Realtime Database, Storage
firebase init

# Deploy rules for all services
firebase deploy --only firestore:rules,storage,database

# Deploy Firestore composite indexes (required for feed queries)
firebase deploy --only firestore:indexes
```

The indexes are defined in `backend/firebase/firestore.indexes.json` and deploy automatically with the command above.

### 2.5 Get Firebase Admin SDK Key

This is used by the Cloudflare Worker to send push notifications.

1. Firebase Console → Project Settings → **Service Accounts** tab
2. Click **Generate new private key** → **Generate key** → Download the JSON file
3. Base64-encode it:
   ```bash
   base64 -i serviceAccountKey.json | tr -d '\n'
   ```
4. Copy the output string — you will paste it as `FIREBASE_ADMIN_KEY` in Step 4

### 2.6 Google Sign-In — SHA-1 Fingerprint

1. Firebase Console → Authentication → Sign-in method → **Google** → Enable → save the **Web client ID**
2. Get your app's SHA-1:
   ```bash
   ./gradlew signingReport
   ```
3. Firebase Console → Project Settings → Your Android app → **Add fingerprint** → paste the SHA-1
4. Re-download `google-services.json` and replace `app/google-services.json` again

---

## Step 3 — Get API Keys

### 3.1 Spotify

1. Go to [developer.spotify.com/dashboard](https://developer.spotify.com/dashboard)
2. **Create app** → give it any name
3. Set Redirect URI to `https://osanwall.app/callback`
4. Copy your **Client ID** and **Client Secret**

### 3.2 TMDB (The Movie Database)

1. Register at [themoviedb.org/signup](https://www.themoviedb.org/signup)
2. Account Settings → **API** → **Create** → select Developer
3. Copy the **API Read Access Token** — this is a long JWT string starting with `eyJ...`
4. ⚠️ This is **not** the short v3 API Key — you need the long bearer token

### 3.3 Google Books

1. Go to [console.cloud.google.com](https://console.cloud.google.com)
2. Create a new project (or reuse an existing one)
3. APIs & Services → **Enable APIs** → search for and enable **Books API**
4. APIs & Services → **Credentials** → **Create API Key**
5. Click **Restrict key** → API restrictions → select **Books API**
6. Copy the key

---

## Step 4 — Cloudflare Worker (API Backend)

### 4.1 Login to Cloudflare

```bash
wrangler login
# Opens browser to authenticate
```

### 4.2 Create KV Namespace

The Worker uses Cloudflare KV to cache API responses.

```bash
cd backend/worker

wrangler kv:namespace create "CACHE"
# → Outputs something like: id = "abc123def456..."

wrangler kv:namespace create "CACHE" --preview
# → Outputs: preview_id = "xyz789..."
```

Open `backend/worker/wrangler.toml` and fill in your IDs:

```toml
[[kv_namespaces]]
binding = "CACHE"
id = "YOUR_KV_ID_FROM_ABOVE"
preview_id = "YOUR_KV_PREVIEW_ID_FROM_ABOVE"
```

### 4.3 Set Worker Secrets

Run each command below and paste the value when prompted.

```bash
cd backend/worker

wrangler secret put SPOTIFY_CLIENT_ID
# paste your Spotify Client ID

wrangler secret put SPOTIFY_CLIENT_SECRET
# paste your Spotify Client Secret

wrangler secret put TMDB_API_KEY
# paste your TMDB Bearer Token (the long eyJ... string)

wrangler secret put GOOGLE_BOOKS_API_KEY
# paste your Google Books API Key

wrangler secret put FIREBASE_ADMIN_KEY
# paste the base64 string you generated in Step 2.5
```

### 4.4 Test Locally, Then Deploy

```bash
cd backend/worker
npm install

# Run locally
npm run dev
# → Worker available at http://localhost:8787

# Smoke test
curl http://localhost:8787/health
curl http://localhost:8787/api/movies/trending
curl "http://localhost:8787/api/songs/search?q=radiohead"

# Deploy to production
npm run deploy
# → Deployed to https://osanwall-api.YOUR-SUBDOMAIN.workers.dev
```

Copy the deployed URL — you'll need it for `local.properties`.

### 4.5 Worker API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/health` | Health check |
| GET | `/api/spotify/token` | Spotify access token (cached) |
| GET | `/api/songs/search?q=` | Search songs via Spotify |
| GET | `/api/movies/trending` | Trending movies from TMDB |
| GET | `/api/movies/search?q=` | Search movies via TMDB |
| GET | `/api/books/search?q=` | Search books via Google Books |
| GET | `/api/trending?type=all` | Aggregated trending content |
| POST | `/api/search` | Unified search across all types |
| GET | `/api/recommend?type=songs&genre=` | Recommendations |
| POST | `/api/notify` | Send FCM push notification |

### 4.6 Custom Domain (Optional)

Cloudflare Dashboard → Workers & Pages → your worker → **Triggers** → **Custom Domains** → add `api.osanwall.app`. Requires your domain to be on Cloudflare.

---

## Step 5 — Android App

### 5.1 Configure `local.properties`

```bash
cp local.properties.template local.properties
```

Edit `local.properties` with all your keys:

```properties
sdk.dir=/path/to/your/android/sdk
SPOTIFY_CLIENT_ID=your_spotify_client_id
SPOTIFY_CLIENT_SECRET=your_spotify_client_secret
TMDB_API_KEY=your_tmdb_bearer_token
GOOGLE_BOOKS_API_KEY=your_google_books_key
CLOUDFLARE_WORKER_URL=https://osanwall-api.your-subdomain.workers.dev/
```

> ⚠️ **Never commit `local.properties`** — it is already in `.gitignore`.

### 5.2 Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Install directly on a connected device or emulator
./gradlew installDebug

# Run unit tests
./gradlew test
```

Or open in Android Studio: **File → Open → select the OsanWall folder** → click Run.

### 5.3 Release Signing (Optional)

Generate a keystore:

```bash
keytool -genkey -v \
  -keystore keystore.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias osanwall
```

Add to `local.properties`:

```properties
KEYSTORE_PATH=../keystore.jks
KEYSTORE_PASSWORD=your_keystore_password
KEY_ALIAS=osanwall
KEY_PASSWORD=your_key_password
```

Build release:

```bash
./gradlew assembleRelease
```

---

## Step 6 — E2EE Chat Backend

OsanWall's chat is end-to-end encrypted using a Signal-style protocol (X3DH + Double Ratchet + AES-256-GCM). This step sets up the cryptographic backend. **The server stores only ciphertext and blinded routing IDs — it cannot read messages.**

### How it works (overview)

| Layer | What's used | What it protects |
|-------|-------------|-----------------|
| Key agreement | X3DH (X25519 + Ed25519) | Async session setup without both users online |
| Runtime encryption | Double ratchet (Signal spec) | Forward secrecy + break-in recovery per message |
| AEAD | AES-256-GCM + HMAC-SHA256 | Message confidentiality and integrity |
| Routing | Blinded recipient IDs (HMAC + server pepper) | Server cannot link sender to recipient |
| Length hiding | Padding to fixed buckets {1024, 2048, 4096 B} | Server cannot infer message size |
| Timing hiding | Timestamps rounded to 1-minute buckets | Prevents timing correlation attacks |
| Key storage | Argon2id key wrapping | Private keys encrypted at rest on device |

A compromised server learns nothing. A stolen database contains only ciphertext and opaque blinded IDs.

### 6.1 Start the Database Stack (Local Dev)

```bash
# From project root
docker compose up -d
# PostgreSQL → localhost:5432  (user: osanwall / db: osanwall)
# Redis      → localhost:6379
```

### 6.2 Apply the E2EE Database Schema

```bash
psql "postgresql://osanwall:osanwall@127.0.0.1:5432/osanwall" \
  -f e2ee-reference/schema.sql
```

This creates tables with blinded indexes only — no raw user IDs appear on message rows. Indexed columns: `delivery_tag`, `recipient_blind_id`, `expires_at`.

### 6.3 Run the Go Reference Implementation

The Go package contains all cryptographic primitives: X25519/Ed25519 key generation, HKDF, AES-256-GCM encrypt/decrypt, blinded ID derivation, HMAC delivery tags, padding, and the symmetric ratchet.

```bash
cd e2ee-reference/go

# Run all tests (verify primitives are working)
go test ./...

# Build
go build ./...
```

Key functions available:

| Function | Purpose |
|----------|---------|
| `GenerateIdentityKeyPair` | Ed25519 long-term signing key |
| `GenerateX25519KeyPair` | X25519 key for ECDH / prekeys |
| `X25519SharedSecret` | DH key agreement |
| `DeriveStorageKey` | Argon2id → local encryption key |
| `RecipientBlindID` | HMAC(pepper, route_key) → opaque routing index |
| `DeliveryTag` | Opaque 32-byte ACK token |
| `EncryptMessageAES256GCM` | nonce ‖ AEAD ‖ outer HMAC |
| `DecryptMessageAES256GCM` | Verify MAC, then decrypt |
| `PadRandom` | Pad to fixed bucket size |
| `RatchetSession` | Symmetric ratchet step |

### 6.4 Run the Python Reference (Optional)

```bash
cd e2ee-reference
python3 -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt

# Quick smoke test
python -c "from crypto_reference import generate_x25519_keypair; print(len(generate_x25519_keypair()[0]))"
```

### 6.5 Integrate E2EE into the Android App

Today `ChatRepository` sends messages through Firebase RTDB as plaintext. To migrate:

1. **Add E2EE message type** — store `content` as `base64(ciphertext)`. Keep private keys in Room, encrypted with an Argon2id-derived key from the user's password.

2. **Replace transport** — point `ChatRepository` at the E2EE API (`POST /msg` with blinded recipient). Keep Firebase Auth or migrate to OIDC later.

3. **Run the ratchet in Kotlin** — use `libsignal-android` JNI bindings (recommended for production) or wrap the Go/Rust implementation via JNI.

```kotlin
// Example: derive local storage key from user password
val salt = generateSalt()  // store alongside encrypted blob
val storageKey = Argon2id.hash(
    password = userPassword.toByteArray(),
    salt = salt,
    memory = 65536,
    iterations = 3
)
val encryptedKeyBundle = AesGcm.encrypt(storageKey, privateKeyBundle)
```

### 6.6 Message Envelope Structure

Every message on the wire and in the database looks like this:

| Field | Server sees | Purpose |
|-------|-------------|---------|
| `recipient_blind_id` | Opaque 16 bytes | Routing index — HMAC(pepper, route_key) |
| `delivery_tag` | Opaque 32 bytes | Lookup and ACK without revealing identity |
| `ciphertext` | Blob | nonce ‖ AEAD ciphertext ‖ outer HMAC |
| `encrypted_header` | Blob | Ratchet counters, message ID — never server-parsed |
| `padded_len` | Integer (1024/2048/4096) | Hides actual message length |
| `expires_at` | Timestamp | TTL — server deletes after this time |
| `idempotency_hash` | Hash | Prevents replay storage |

### 6.7 Key Session Flow

**Registration:**
```
Client                        Server                  DB
  | generate IK, SPK, OPKs      |                       |
  | sign SPK with IK             |                       |
  | POST /keys (public bundle)   | store public keys     |
  |<-----------------------------| 200 OK                |
```

**First message (X3DH):**
```
Alice                         Server                   Bob
  | fetch Bob's IK, SPK, OPK    |                       |
  | X3DH → session key SK       |                       |
  | encrypt message              |                       |
  | POST /msg (blind_recipient)  | store ciphertext      |
  |                              | silent push (opaque)  |
  |                              |---------------------->|
  |                              |                       | fetch by blind_id
  |                              |                       | decrypt locally
```

**Runtime (double ratchet):**
```
Alice                                        Bob
  | msg_n  encrypted with MK_n               |
  |----------------------------------------->|
  |                                           | verify AEAD + HMAC
  |                                           | advance chain key
  |                                           | reply with new DH ratchet output
  |<------------------------------------------|
```

### 6.8 Production E2EE Deployment (Free Stack)

| Item | Free / open-source option |
|------|--------------------------|
| TLS | **Let's Encrypt** via certbot |
| Secrets | **SOPS** + gitignored env → **Vault** later |
| Database | Self-hosted **PostgreSQL 16** |
| Cache / OTP prekeys | **Redis 7** with TLS + ACL |
| Push | **FCM** free tier — payload is silent and opaque |
| Monitoring | **Grafana Cloud** free tier or self-hosted |
| Backups | `pg_dump` encrypted → **Cloudflare R2** free tier |
| VPS | **Hetzner CAX11** (~€4/mo), **Oracle Free Tier**, or **Fly.io** free allowances |

### 6.9 Security Hardening for E2EE Server

HTTP security headers to set on your E2EE API:

```
Strict-Transport-Security: max-age=63072000; includeSubDomains; preload
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Referrer-Policy: no-referrer
Permissions-Policy: geolocation=(), microphone=(), camera=()
Cross-Origin-Opener-Policy: same-origin
Content-Security-Policy: default-src 'none'; frame-ancestors 'none'; base-uri 'none'
```

Firewall rules:
- Allow **443 only** from the internet to the edge
- Deny all DB and Redis ports from the internet
- Use **Tailscale** or SSH bastion for admin access
- Internal API ↔ PostgreSQL: TLS with client certificates, rotated weekly
- Redis: TLS + ACL + strong password, no plaintext

Strip `X-Forwarded-For` at the edge (set in `e2ee-reference/nginx/osanwall-e2ee.conf`) to avoid logging client IPs. Rate-limit by **HMAC(server_pepper, blinded_session_token)** instead.

### 6.10 Operational Maintenance

| Frequency | Task |
|-----------|------|
| Weekly | Rotate DB passwords, Redis ACL passwords, HMAC peppers (dual-epoch during rotation) |
| On rotation | Dual-write messages with epoch tag in encrypted header; server supports two epochs during migration window |
| On device compromise | User generates new IK, broadcasts key change message inside E2EE channel to contacts, re-encrypts local Room DB |
| On key loss | No server escrow. User restores from optional Argon2id-encrypted backup blob, or uses Shamir shares with trusted contacts |

> For full protocol specification including X3DH test vectors, threat model, zero-day key rotation procedure, and privacy-preserving personalisation — see [`E2EE_PROTOCOL.md`](E2EE_PROTOCOL.md). For production, use audited libraries (**libsignal** or **OpenMLS**) rather than extending the reference implementation alone.

---

## Step 7 — CI/CD (GitHub Actions)

Create `.github/workflows/android.yml`:

```yaml
name: Android CI
on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - uses: gradle/actions/setup-gradle@v3
      - name: Create local.properties
        run: |
          echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
          echo "SPOTIFY_CLIENT_ID=${{ secrets.SPOTIFY_CLIENT_ID }}" >> local.properties
          echo "SPOTIFY_CLIENT_SECRET=${{ secrets.SPOTIFY_CLIENT_SECRET }}" >> local.properties
          echo "TMDB_API_KEY=${{ secrets.TMDB_API_KEY }}" >> local.properties
          echo "GOOGLE_BOOKS_API_KEY=${{ secrets.GOOGLE_BOOKS_API_KEY }}" >> local.properties
          echo "CLOUDFLARE_WORKER_URL=${{ secrets.CLOUDFLARE_WORKER_URL }}" >> local.properties
      - name: Create google-services.json
        run: echo '${{ secrets.GOOGLE_SERVICES_JSON }}' > app/google-services.json
      - name: Run unit tests
        run: ./gradlew test
      - name: Build debug APK
        run: ./gradlew assembleDebug
      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: debug-apk
          path: app/build/outputs/apk/debug/*.apk
```

Add these secrets in GitHub → Repository Settings → Secrets and variables → Actions:

| Secret | Value |
|--------|-------|
| `SPOTIFY_CLIENT_ID` | Spotify client ID |
| `SPOTIFY_CLIENT_SECRET` | Spotify client secret |
| `TMDB_API_KEY` | TMDB bearer token |
| `GOOGLE_BOOKS_API_KEY` | Google Books API key |
| `CLOUDFLARE_WORKER_URL` | Your deployed worker URL |
| `GOOGLE_SERVICES_JSON` | Full contents of `google-services.json` |

---

## Project Structure

```
OsanWall/
├── app/
│   ├── src/main/java/com/osanwall/
│   │   ├── MainActivity.kt                  # Nav host, app entry
│   │   ├── OsanWallApplication.kt           # Hilt app, Firebase init
│   │   ├── data/
│   │   │   ├── api/
│   │   │   │   └── ApiServices.kt           # Retrofit interfaces
│   │   │   ├── model/
│   │   │   │   ├── Models.kt                # Domain models (User, Post, Chat)
│   │   │   │   └── Entities.kt              # Room cache entities
│   │   │   └── repository/
│   │   │       ├── AuthRepository.kt        # Firebase Auth
│   │   │       ├── PostRepository.kt        # Posts + Firestore paging
│   │   │       ├── ChatRepository.kt        # RTDB / E2EE messaging
│   │   │       ├── UserRepository.kt        # Profiles + follow
│   │   │       ├── MediaRepository.kt       # Spotify/TMDB/Books
│   │   │       ├── Daos.kt                  # Room DAOs
│   │   │       └── OsanWallDatabase.kt      # Room database
│   │   ├── di/
│   │   │   ├── AppModule.kt                 # Network + DB DI
│   │   │   └── FirebaseModule.kt            # Firebase DI
│   │   ├── ui/
│   │   │   ├── Theme.kt                     # Material3 themes
│   │   │   ├── Typography.kt                # Type scale
│   │   │   ├── Navigation.kt                # Screen sealed class
│   │   │   ├── AuthScreens.kt               # Login + Register
│   │   │   ├── home/                        # Feed + post cards
│   │   │   ├── chat/                        # E2EE messaging UI
│   │   │   ├── discover/                    # Search + trending
│   │   │   ├── profile/                     # Wall (top 4 picks) + follow
│   │   │   └── components/                  # Shared composables
│   │   └── utils/
│   │       ├── OsanWallMessagingService.kt  # FCM handler
│   │       └── SyncWorker.kt                # WorkManager cache sync
│   └── src/main/res/
│       ├── drawable/                        # Vector icons
│       ├── mipmap-*/                        # Launcher icons
│       ├── values/                          # Strings, colors, themes
│       └── xml/                             # Network config, backup rules
├── backend/
│   ├── worker/
│   │   ├── src/index.js                     # Cloudflare Worker
│   │   ├── wrangler.toml                    # Worker config
│   │   └── package.json
│   └── firebase/
│       ├── firestore.rules                  # Firestore security rules
│       ├── firestore.indexes.json           # Composite indexes
│       ├── storage.rules                    # Storage security rules
│       └── database.rules.json              # Realtime DB rules
├── e2ee-reference/
│   ├── go/                                  # Go: all crypto primitives + ratchet
│   ├── python/crypto_reference.py           # Python mirror (PyNaCl)
│   ├── nodejs/secure_push.js               # Silent push envelope
│   ├── nginx/osanwall-e2ee.conf            # TLS + security headers
│   └── schema.sql                           # PostgreSQL DDL (blinded schema)
├── docker-compose.yml                       # Local PostgreSQL + Redis
├── E2EE_PROTOCOL.md                         # Full cryptographic protocol spec
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/libs.versions.toml
├── firebase.json
├── local.properties.template
└── .gitignore
```

---

## Environment Variables Reference

### `local.properties` (Android)

| Key | Where to get |
|-----|-------------|
| `sdk.dir` | Your Android SDK path |
| `SPOTIFY_CLIENT_ID` | Spotify Developer Dashboard |
| `SPOTIFY_CLIENT_SECRET` | Spotify Developer Dashboard |
| `TMDB_API_KEY` | TMDB → API → Bearer Token (long `eyJ...` string) |
| `GOOGLE_BOOKS_API_KEY` | Google Cloud Console |
| `CLOUDFLARE_WORKER_URL` | Printed after `npm run deploy` |

### Worker Secrets (`wrangler secret put`)

| Secret | Where to get |
|--------|-------------|
| `SPOTIFY_CLIENT_ID` | Spotify Developer Dashboard |
| `SPOTIFY_CLIENT_SECRET` | Spotify Developer Dashboard |
| `TMDB_API_KEY` | TMDB Bearer Token |
| `GOOGLE_BOOKS_API_KEY` | Google Cloud Console |
| `FIREBASE_ADMIN_KEY` | Firebase → Service Accounts → base64-encoded JSON |

---

## Security Checklist

- [x] No hardcoded API keys (all via `local.properties` or Worker secrets)
- [x] Firestore rules: users can only write their own data
- [x] Firestore rules: post authors only can delete/update their posts
- [x] Chat rules: only participants can read/write a conversation
- [x] Storage rules: max 5MB, images only, owner-scoped paths
- [x] RTDB rules: messages scoped to chat participants
- [x] Worker rate limiting: 100 req/min default, 30/min for search
- [x] Worker input sanitisation: query length limits, type validation
- [x] HTTPS enforced via `network_security_config.xml`
- [x] No cleartext traffic
- [x] ProGuard/R8 minification on release builds
- [x] E2EE: AES-256-GCM + HMAC-SHA256 on all chat messages
- [x] E2EE: blinded routing IDs — server cannot link sender to recipient
- [x] E2EE: forward secrecy via double ratchet (old keys deleted after use)
- [x] E2EE: message padding to fixed size buckets
- [x] E2EE: timestamps bucketed to 1-minute intervals
- [x] E2EE: no message keys stored in the database
- [x] `.gitignore` excludes all secrets, keystores, and service account files

---

## Performance Targets

| Metric | Target | Implementation |
|--------|--------|----------------|
| Cold start | < 1.5s | Baseline profiles, splash screen |
| Frame render | < 16ms | Compose strong skipping, lazy loading |
| APK size | < 30MB | R8 shrinking, resource shrinking |
| Network payload | < 100KB/req | Pagination, Worker KV caching |
| Memory | < 150MB | Coil disk cache, Room TTL cleanup |

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt (Dagger) |
| Navigation | Navigation Compose |
| Networking | Retrofit + OkHttp + Kotlinx Serialization |
| Image loading | Coil 2 |
| Local DB | Room + Paging 3 |
| Auth | Firebase Auth |
| Database | Firestore + Firebase RTDB |
| Storage | Firebase Storage |
| Push | Firebase Cloud Messaging |
| Monitoring | Firebase Crashlytics + Analytics |
| Background | WorkManager |
| Backend | Cloudflare Workers (JS) |
| Cache | Cloudflare KV |
| E2EE DB | PostgreSQL 16 (blinded schema) |
| E2EE Cache | Redis 7 (OTP prekeys with TTL) |
| E2EE Crypto | X25519 · Ed25519 · AES-256-GCM · HKDF · Argon2id |
| Music | Spotify Web API |
| Movies | TMDB API |
| Books | Google Books API |

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `google-services.json not found` | Download from Firebase Console → place at `app/google-services.json` |
| Worker 502 on `/api/movies/trending` | Check `TMDB_API_KEY` is the bearer token (`eyJ...`), not the short v3 key. Run `wrangler secret list` to confirm it's set |
| Spotify search returns empty | Check `SPOTIFY_CLIENT_ID` / `SPOTIFY_CLIENT_SECRET` are correct. Worker auto-refreshes tokens |
| Google sign-in fails | Add SHA-1 fingerprint to Firebase Console. Run `./gradlew signingReport` to get it |
| Chat messages not real-time | Deploy RTDB rules: `firebase deploy --only database`. Ensure `setPersistenceEnabled(true)` is called before any DB reference |
| KV namespace binding error | Run `wrangler kv:namespace list` and update `wrangler.toml` with actual IDs |
| Docker fails to start | Ensure Docker Desktop is running. Check logs: `docker compose logs` |
| E2EE Go tests fail | Ensure Go 1.22+ is installed: `go version`. Run `go mod tidy` in `e2ee-reference/go/` |
| PostgreSQL connection refused | Docker stack not running. Run `docker compose up -d` and wait ~5 seconds |
| E2EE messages not decrypting | Clients must be on the same ratchet epoch. Check `encrypted_header` epoch tag matches current server pepper epoch |