<div align="center">

<br/>

```
 ██████╗ ███████╗ █████╗ ███╗   ██╗██╗    ██╗ █████╗ ██╗     ██╗
██╔═══██╗██╔════╝██╔══██╗████╗  ██║██║    ██║██╔══██╗██║     ██║
██║   ██║███████╗███████║██╔██╗ ██║██║ █╗ ██║███████║██║     ██║
██║   ██║╚════██║██╔══██║██║╚██╗██║██║███╗██║██╔══██║██║     ██║
╚██████╔╝███████║██║  ██║██║ ╚████║╚███╔███╔╝██   ██║███████╗███████╗
 ╚═════╝ ╚══════╝╚═╝  ╚═╝╚═╝  ╚═══╝ ╚══╝╚══╝ ╚═╝  ╚═╝╚══════╝╚══════╝
```

**Your culture. Your taste. Your wall.**

*Share the books, films, and music that define you — with people who actually get it.*

<br/>

[![Android](https://img.shields.io/badge/Android-Kotlin%202.0-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Cloudflare Workers](https://img.shields.io/badge/Cloudflare-Workers-F38020?style=flat-square&logo=cloudflare&logoColor=white)](https://workers.cloudflare.com)
[![Firebase](https://img.shields.io/badge/Firebase-FFCA28?style=flat-square&logo=firebase&logoColor=black)](https://firebase.google.com)
[![E2EE](https://img.shields.io/badge/Chat-E2E%20Encrypted-00C853?style=flat-square&logo=signal&logoColor=white)](#security--e2ee-chat)
[![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)](LICENSE)

<br/>

</div>

---

## What is OsanWall?

OsanWall is a **cultural identity platform** for Android — a place to share your niche taste in books, films, music, and ideas, and find others who share it.

Unlike general social media, OsanWall is built around **taste** and **depth**. Instead of chasing likes, you build a wall that tells people exactly who you are: your top 4 films, the albums you can't stop playing, the books that changed your thinking. Then you talk to people who get it — in **end-to-end encrypted chats** that not even the server can read.

### Core Features

**🧱 Your Wall**
Build a profile that actually says something. Pin your top 4 books, films, and songs. Write about why they matter. Your wall is a snapshot of your cultural identity, not a follower count.

**📚 Niche Discussions**
Post thoughts on specific topics — a deep cut album, a forgotten film, a philosophy book — and find people who are genuinely interested in the same things. Not everything, just the right things.

**🔍 Discover**
Search across Spotify, TMDB, and Google Books in one place. See what's trending in your niches. Get recommendations based on what you've already added to your wall.

**💬 End-to-End Encrypted Chat**
All private messages use a Signal-style double ratchet protocol (X3DH + AES-256-GCM). The server stores only ciphertext and blinded routing IDs — it cannot read your conversations, period.

**🌐 Privacy-First Architecture**
No plaintext metadata. Blinded sender/recipient IDs. Message padding to hide length. Timestamps bucketed to the minute. Even a fully compromised server learns almost nothing about who is talking to whom.

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
          └────────┬───────────┘
                   │
          ┌────────▼───────────┐
          │   E2EE Backend      │  ← PostgreSQL + Redis (optional)
          │  blinded IDs · KDF  │    self-hosted privacy layer
          └────────────────────┘
```

---

## Security & E2EE Chat

OsanWall's chat is designed so that **a compromised server reveals nothing**.

| Layer | What's used | What it protects |
|-------|-------------|-----------------|
| Key agreement | X3DH (X25519 + Ed25519) | Async session setup without both users online |
| Ratchet | Double ratchet (Signal spec) | Forward secrecy + break-in recovery |
| Encryption | AES-256-GCM + HMAC-SHA256 | Message confidentiality + integrity |
| Routing | Blinded recipient IDs (HMAC + pepper) | Server can't correlate sender ↔ recipient |
| Padding | Fixed-size buckets {1024, 2048, 4096 B} | Hides message length class |
| Timestamps | Rounded to 1-minute buckets | Prevents timing correlation |
| Passwords | Argon2id | Key wrapping for local storage + optional backup |

**Threat model summary:**
- ✅ Malicious server → cannot decrypt messages
- ✅ Database stolen → ciphertext + blinded fields only, no keys
- ✅ TLS MITM → replays fail, ratchet invalidates stale material
- ✅ Compromised device → only that device's messages affected

See [`E2EE_PROTOCOL.md`](E2EE_PROTOCOL.md) for full protocol specification, threat model, and key rotation procedure.

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
| Push | Firebase Cloud Messaging (silent, opaque payloads) |
| Monitoring | Firebase Crashlytics + Analytics |
| Background | WorkManager |
| Backend | Cloudflare Workers (JS) |
| Cache | Cloudflare KV |
| E2EE DB | PostgreSQL 16 (blinded schema) |
| E2EE Cache | Redis 7 (OTP prekeys, TTL) |
| Music | Spotify Web API |
| Movies | TMDB API |
| Books | Google Books API |

---

## Project Structure

```
OsanWall/
├── app/src/main/java/com/osanwall/
│   ├── data/
│   │   ├── api/          # Retrofit interfaces (Spotify, TMDB, Books)
│   │   ├── model/        # Domain models + Room entities
│   │   └── repository/   # Auth, Post, Chat, User, Media repos
│   ├── di/               # Hilt modules
│   ├── ui/
│   │   ├── home/         # Feed + post cards
│   │   ├── chat/         # E2EE messaging UI
│   │   ├── discover/     # Search + trending
│   │   ├── profile/      # Wall (top 4 books/films/songs) + follow
│   │   └── components/   # Shared composables
│   └── utils/            # FCM handler, WorkManager sync
├── backend/
│   ├── worker/           # Cloudflare Worker (JS)
│   └── firebase/         # Firestore/RTDB/Storage rules + indexes
├── e2ee-reference/
│   ├── go/               # Go: X25519, Ed25519, HKDF, AES-GCM, ratchet
│   ├── python/           # Python mirror (PyNaCl)
│   ├── nodejs/           # Silent push envelope
│   ├── nginx/            # TLS + security headers config
│   └── schema.sql        # PostgreSQL DDL (blinded indexes, TTL, audit)
├── docker-compose.yml    # Local PostgreSQL + Redis for E2EE dev
└── E2EE_PROTOCOL.md      # Full cryptographic protocol specification
```

---

## Quick Start

See **[SETUP.md](SETUP.md)** for the complete step-by-step guide.

**TL;DR:**

```bash
git clone https://github.com/your-username/OsanWall.git
cd OsanWall

# 1. Add your google-services.json from Firebase Console
cp your-downloaded-google-services.json app/google-services.json

# 2. Configure secrets
cp local.properties.template local.properties
# → edit local.properties with your API keys

# 3. Deploy Worker backend
cd backend/worker && npm install && npm run deploy

# 4. Build & run
./gradlew installDebug
```

---

## Worker API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/health` | Health check |
| GET | `/api/songs/search?q=` | Search songs via Spotify |
| GET | `/api/movies/trending` | Trending movies (TMDB) |
| GET | `/api/movies/search?q=` | Search movies (TMDB) |
| GET | `/api/books/search?q=` | Search books (Google Books) |
| GET | `/api/trending?type=all` | Aggregated trending content |
| POST | `/api/search` | Unified search across all types |
| GET | `/api/recommend?type=songs&genre=` | Personalised recommendations |
| POST | `/api/notify` | Send FCM push notification |

Rate limits: 100 req/min default · 30 req/min for search endpoints

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

## Security Checklist

- [x] No hardcoded API keys (all via `local.properties` or Worker secrets)
- [x] Firestore rules: users can only write their own data
- [x] Chat rules: only conversation participants can read/write
- [x] Storage rules: max 5MB, images only, owner-scoped
- [x] Worker rate limiting + input sanitisation
- [x] HTTPS enforced (`network_security_config.xml`)
- [x] No cleartext traffic
- [x] ProGuard/R8 minification on release
- [x] E2EE: AES-256-GCM + HMAC-SHA256 on all chat messages
- [x] E2EE: blinded routing IDs — server cannot correlate conversations
- [x] E2EE: forward secrecy via double ratchet
- [x] `.gitignore` excludes all secrets and keystores

---

## Contributing

1. Fork the repo
2. Create a feature branch: `git checkout -b feat/your-feature`
3. Follow the existing architecture (Hilt DI, repository pattern, Compose)
4. For E2EE changes: read `E2EE_PROTOCOL.md` first and use audited primitives
5. Open a PR with a clear description

---

## License

MIT © OsanWall

---

<div align="center">

*Built for people with taste.*

</div>