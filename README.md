# OsanWall 🌌

A social platform for sharing your cultural identity — songs, movies, books, and thoughts.
Built with Kotlin + Jetpack Compose (Android) and Cloudflare Workers + Firebase (backend).

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                     Android App                          │
│  Jetpack Compose · Hilt DI · Room · Paging3 · Coil      │
└───────────────────┬─────────────────────────────────────┘
                    │ HTTPS
          ┌─────────▼──────────┐
          │  Cloudflare Worker  │  ← Rate limiting, caching, API gateway
          │   (osanwall-api)    │
          └──┬──────┬──────┬───┘
             │      │      │
    ┌────────▼─┐ ┌──▼───┐ ┌▼──────────┐
    │ Spotify  │ │ TMDB │ │ G. Books  │
    │   API    │ │  API │ │   API     │
    └──────────┘ └──────┘ └───────────┘
                    │
          ┌─────────▼──────────┐
          │      Firebase       │
          │  Auth · Firestore   │
          │  RTDB · Storage     │
          │  FCM · Crashlytics  │
          └────────────────────┘
```

---

## Quick Start

### Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Android Studio | Hedgehog+ | [Download](https://developer.android.com/studio) |
| JDK | 17+ | bundled with Android Studio |
| Node.js | 18+ | [Download](https://nodejs.org) |
| Wrangler CLI | 3.x | `npm i -g wrangler` |
| Firebase CLI | latest | `npm i -g firebase-tools` |

---

## Step 1 — Firebase Setup

### 1.1 Create Firebase Project

1. Go to [https://console.firebase.google.com](https://console.firebase.google.com)
2. Click **Add project** → name it `OsanWall`
3. Enable Google Analytics (optional but recommended)

### 1.2 Enable Services

In the Firebase Console, enable each of these:

| Service | Path |
|---------|------|
| **Authentication** | Build → Authentication → Sign-in method → Enable: Email/Password, Google |
| **Firestore** | Build → Firestore Database → Create database → Start in **production mode** |
| **Realtime Database** | Build → Realtime Database → Create database → Start in **locked mode** |
| **Storage** | Build → Storage → Get started → Production mode |
| **Cloud Messaging** | Automatically enabled |
| **Crashlytics** | Release & Monitor → Crashlytics → Enable |

### 1.3 Add Android App

1. Project Settings → Add app → Android
2. Package name: `com.osanwall`
3. App nickname: `OsanWall`
4. Download **`google-services.json`**
5. Replace `app/google-services.json` with your downloaded file

### 1.4 Deploy Firebase Rules

```bash
# Login to Firebase
firebase login

# Initialize in project root (select Firestore, Database, Storage)
firebase init

# Deploy all rules
firebase deploy --only firestore:rules,storage,database

# Deploy Firestore indexes
firebase deploy --only firestore:indexes
```

### 1.5 Firestore Indexes

The file `backend/firebase/firestore.indexes.json` contains required composite indexes.
They are deployed automatically with `firebase deploy --only firestore:indexes`.

### 1.6 Get Firebase Admin SDK Key (for Worker push notifications)

1. Firebase Console → Project Settings → Service Accounts
2. Click **Generate new private key** → Download JSON
3. Base64 encode it:
   ```bash
   base64 -i serviceAccountKey.json | tr -d '\n'
   ```
4. Save the output — you'll use it as `FIREBASE_ADMIN_KEY` in the Worker.

---

## Step 2 — API Keys

### 2.1 Spotify

1. Go to [https://developer.spotify.com/dashboard](https://developer.spotify.com/dashboard)
2. Create app → set Redirect URI to `https://osanwall.app/callback`
3. Copy **Client ID** and **Client Secret**

### 2.2 TMDB (The Movie Database)

1. Register at [https://www.themoviedb.org/signup](https://www.themoviedb.org/signup)
2. Settings → API → Create (Developer) → Get your **API Read Access Token** (Bearer)
3. This is a long JWT string starting with `eyJ...`

### 2.3 Google Books

1. Go to [https://console.cloud.google.com](https://console.cloud.google.com)
2. Create project → Enable **Books API**
3. Credentials → Create API Key → Copy it
4. Restrict the key to Books API + your Android app's SHA-1

---

## Step 3 — Cloudflare Worker (Backend)

### 3.1 Create Cloudflare Account & Login

```bash
wrangler login
```

### 3.2 Create KV Namespace

```bash
cd backend/worker

# Create KV namespace for caching
wrangler kv:namespace create "CACHE"
# → outputs: id = "abc123..."

wrangler kv:namespace create "CACHE" --preview
# → outputs: preview_id = "xyz789..."
```

Update `wrangler.toml` with your KV namespace IDs:
```toml
[[kv_namespaces]]
binding = "CACHE"
id = "YOUR_KV_ID_HERE"
preview_id = "YOUR_KV_PREVIEW_ID_HERE"
```

### 3.3 Set Worker Secrets

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
# paste your base64-encoded Firebase service account JSON
```

### 3.4 Install Dependencies & Deploy

```bash
cd backend/worker
npm install

# Test locally first
npm run dev
# → Worker runs at http://localhost:8787

# Test endpoints
curl http://localhost:8787/health
curl http://localhost:8787/api/movies/trending
curl "http://localhost:8787/api/songs/search?q=radiohead"

# Deploy to production
npm run deploy
# → Deployed to https://osanwall-api.YOUR-SUBDOMAIN.workers.dev
```

### 3.5 Worker API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/health` | Health check |
| GET | `/api/spotify/token` | Get Spotify access token (cached) |
| GET | `/api/songs/search?q=query` | Search songs via Spotify |
| GET | `/api/movies/trending` | Trending movies from TMDB |
| GET | `/api/movies/search?q=query` | Search movies via TMDB |
| GET | `/api/books/search?q=query` | Search books via Google Books |
| GET | `/api/trending?type=all` | Aggregated trending (movies + songs) |
| POST | `/api/search` | Unified search across all types |
| GET | `/api/recommend?type=songs&genre=pop` | Recommendations |
| POST | `/api/notify` | Send FCM push notification |

### 3.6 Custom Domain (Optional)

In Cloudflare Dashboard → Workers & Pages → your worker → Triggers → Custom Domains:
Add `api.osanwall.app` (requires your domain on Cloudflare).

---

## Step 4 — Android App Setup

### 4.1 Configure API Keys

Copy `local.properties.template` to `local.properties`:
```bash
cp local.properties.template local.properties
```

Edit `local.properties`:
```properties
sdk.dir=/path/to/your/android/sdk

SPOTIFY_CLIENT_ID=your_spotify_client_id
SPOTIFY_CLIENT_SECRET=your_spotify_client_secret
TMDB_API_KEY=your_tmdb_bearer_token
GOOGLE_BOOKS_API_KEY=your_google_books_key
CLOUDFLARE_WORKER_URL=https://osanwall-api.your-subdomain.workers.dev/
```

> ⚠️ **Never commit `local.properties`** — it's in `.gitignore`.

### 4.2 Build & Run

```bash
# Open project in Android Studio
# File → Open → select OsanWall folder

# Or build from CLI:
./gradlew assembleDebug

# Install on connected device:
./gradlew installDebug

# Run tests:
./gradlew test

# Build release APK:
./gradlew assembleRelease
```

### 4.3 Signing for Release

Create `keystore.jks`:
```bash
keytool -genkey -v -keystore keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias osanwall
```

Add to `local.properties`:
```properties
KEYSTORE_PATH=../keystore.jks
KEYSTORE_PASSWORD=your_keystore_password
KEY_ALIAS=osanwall
KEY_PASSWORD=your_key_password
```

---

## Step 5 — Google Sign-In Setup

1. Firebase Console → Authentication → Sign-in method → Google → Enable
2. Copy the **Web client ID** (OAuth 2.0)
3. Add to your Android app in Firebase Console:
   - Get SHA-1: `./gradlew signingReport`
   - Add SHA-1 to Firebase project settings
4. Re-download `google-services.json`

---

## Project Structure

```
MeroWall/
├── app/
│   ├── src/main/java/com/merowall/
│   │   ├── MainActivity.kt              # Nav host, app entry
│   │   ├── MeroWallApplication.kt       # Hilt app, Firebase init
│   │   ├── data/
│   │   │   ├── api/
│   │   │   │   └── ApiServices.kt       # Retrofit interfaces (Spotify, TMDB, Books)
│   │   │   ├── model/
│   │   │   │   ├── Models.kt            # Domain models (User, Post, Chat, etc.)
│   │   │   │   └── Entities.kt          # Room cache entities
│   │   │   └── repository/
│   │   │       ├── AuthRepository.kt    # Firebase Auth
│   │   │       ├── PostRepository.kt    # Posts + Firestore paging
│   │   │       ├── ChatRepository.kt    # RTDB real-time messaging
│   │   │       ├── UserRepository.kt    # Profiles + follow
│   │   │       ├── MediaRepository.kt   # Spotify/TMDB/Books
│   │   │       ├── Daos.kt              # Room DAOs
│   │   │       └── MeroWallDatabase.kt  # Room database
│   │   ├── di/
│   │   │   ├── AppModule.kt             # Network + DB DI
│   │   │   └── FirebaseModule.kt        # Firebase DI
│   │   ├── ui/
│   │   │   ├── Theme.kt                 # Material3 dark/light themes
│   │   │   ├── Typography.kt            # Manrope + Inter type scale
│   │   │   ├── Navigation.kt            # Screen sealed class
│   │   │   ├── AuthScreens.kt           # Login + Register + AuthViewModel
│   │   │   ├── home/
│   │   │   │   ├── HomeScreen.kt        # Feed, post cards
│   │   │   │   └── HomeViewModel.kt
│   │   │   ├── chat/
│   │   │   │   ├── ChatScreens.kt       # Chat list + ChatViewModel
│   │   │   │   └── ChatDetailScreen.kt  # Real-time messaging UI
│   │   │   ├── discover/
│   │   │   │   └── DiscoverScreen.kt    # Search + trending + DiscoverViewModel
│   │   │   ├── profile/
│   │   │   │   └── ProfileScreen.kt     # Profile + ProfileViewModel
│   │   │   └── components/
│   │   │       └── Components.kt        # Shared composables
│   │   └── utils/
│   │       ├── MeroWallMessagingService.kt  # FCM handler
│   │       └── SyncWorker.kt               # WorkManager cache sync
│   └── src/main/res/
│       ├── drawable/                    # Vector icons
│       ├── mipmap-*/                    # Launcher icons
│       ├── values/                      # Strings, colors, themes
│       └── xml/                         # Network config, backup rules
├── backend/
│   ├── worker/
│   │   ├── src/index.js                 # Full Cloudflare Worker
│   │   ├── wrangler.toml                # Worker config
│   │   └── package.json
│   └── firebase/
│       ├── firestore.rules              # Firestore security rules
│       ├── firestore.indexes.json       # Composite indexes
│       ├── storage.rules                # Storage security rules
│       └── database.rules.json          # Realtime DB rules
├── build.gradle.kts                     # Root build
├── settings.gradle.kts
├── gradle/
│   ├── libs.versions.toml               # Version catalog
│   └── wrapper/gradle-wrapper.properties
├── gradle.properties                    # Build config + API key placeholders
├── firebase.json                        # Firebase CLI deploy config
├── local.properties.template            # Template for local secrets
└── .gitignore
```

---

## Environment Variables Reference

### Android `local.properties`

| Key | Where to get |
|-----|-------------|
| `sdk.dir` | Your Android SDK path |
| `SPOTIFY_CLIENT_ID` | Spotify Developer Dashboard |
| `SPOTIFY_CLIENT_SECRET` | Spotify Developer Dashboard |
| `TMDB_API_KEY` | TMDB → API → Bearer Token |
| `GOOGLE_BOOKS_API_KEY` | Google Cloud Console |
| `CLOUDFLARE_WORKER_URL` | After deploying worker |

### Cloudflare Worker Secrets (`wrangler secret put`)

| Secret | Where to get |
|--------|-------------|
| `SPOTIFY_CLIENT_ID` | Spotify Developer Dashboard |
| `SPOTIFY_CLIENT_SECRET` | Spotify Developer Dashboard |
| `TMDB_API_KEY` | TMDB Bearer Token |
| `GOOGLE_BOOKS_API_KEY` | Google Cloud Console |
| `FIREBASE_ADMIN_KEY` | Firebase → Service Accounts → base64 JSON |

---

## Security Checklist

- [x] No hardcoded API keys (all via `local.properties` or Worker secrets)
- [x] Firestore rules: users can only write their own data
- [x] Firestore rules: post authors only can delete/update
- [x] Chat rules: only participants can read/write
- [x] Storage rules: max 5MB, images only, owner-scoped
- [x] RTDB rules: messages scoped to chat participants
- [x] Worker rate limiting: 100 req/min default, 30/min for search
- [x] Worker input sanitization: query length limits, type validation
- [x] HTTPS enforced (network_security_config.xml)
- [x] No cleartext traffic
- [x] ProGuard/R8 minification on release
- [x] `.gitignore` excludes all secrets

---

## Performance Targets

| Metric | Target | Implementation |
|--------|--------|----------------|
| Cold start | < 1.5s | Baseline profiles, splash screen |
| Frame render | < 16ms | Compose strong skipping, lazy loading |
| APK size | < 30MB | R8 shrinking, resource shrinking |
| Network payload | < 100KB/req | Pagination, Worker caching |
| Memory | < 150MB | Coil disk cache, Room TTL cleanup |

---

## Troubleshooting

### Build fails: `google-services.json not found`
→ Download from Firebase Console and place at `app/google-services.json`

### Worker returns 502 on `/api/movies/trending`
→ Check `TMDB_API_KEY` secret: `wrangler secret list`
→ Verify it's the Bearer token (not the v3 key)

### Spotify search returns empty
→ Tokens expire. The worker auto-refreshes. Check `SPOTIFY_CLIENT_ID` / `SPOTIFY_CLIENT_SECRET`.

### Firebase Auth Google sign-in fails
→ Add your app's SHA-1 fingerprint to Firebase Console → Project Settings → Your app
→ Run: `./gradlew signingReport` to get SHA-1

### Chat messages not appearing in real-time
→ Check RTDB rules are deployed: `firebase deploy --only database`
→ Ensure `setPersistenceEnabled(true)` is called before any DB reference

### KV namespace binding error on Worker deploy
→ Update `wrangler.toml` with your actual KV IDs from `wrangler kv:namespace list`

---

## CI/CD (GitHub Actions)

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
      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: debug-apk
          path: app/build/outputs/apk/debug/*.apk
```

Add these GitHub repository secrets:
- `SPOTIFY_CLIENT_ID`, `SPOTIFY_CLIENT_SECRET`
- `TMDB_API_KEY`, `GOOGLE_BOOKS_API_KEY`
- `CLOUDFLARE_WORKER_URL`
- `GOOGLE_SERVICES_JSON` (entire content of google-services.json)

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
| Music | Spotify Web API |
| Movies | TMDB API |
| Books | Google Books API |
