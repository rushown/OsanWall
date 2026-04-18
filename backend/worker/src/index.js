/**
 * MeroWall API Gateway — Cloudflare Worker
 * Handles: rate limiting, CORS, Spotify token caching,
 * TMDB/Books proxying, trending aggregation, push notifications.
 */

// ─── Constants ────────────────────────────────────────────────────────────────
const CACHE_TTL = {
  SPOTIFY_TOKEN: 3500,       // seconds (token expires in 3600)
  TRENDING_MOVIES: 3600,     // 1 hour
  TRENDING_SONGS: 1800,      // 30 min
  SEARCH_RESULTS: 300,       // 5 min
  RECOMMENDATIONS: 600,      // 10 min
};

const RATE_LIMITS = {
  DEFAULT: { requests: 100, window: 60 },   // 100 req/min
  SEARCH: { requests: 30, window: 60 },     // 30 req/min for search
  AUTH: { requests: 10, window: 60 },       // 10 req/min for auth
};

// ─── Router ────────────────────────────────────────────────────────────────────
export default {
  async fetch(request, env, ctx) {
    try {
      return await handleRequest(request, env, ctx);
    } catch (err) {
      console.error('Unhandled error:', err);
      return jsonResponse({ error: 'Internal server error' }, 500);
    }
  },
};

async function handleRequest(request, env, ctx) {
  const url = new URL(request.url);
  const { pathname } = url;

  // ── Request size guard ──────────────────────────────────────────────────────
  const contentLength = parseInt(request.headers.get('Content-Length') || '0');
  if (contentLength > 64 * 1024) { // 64KB max body
    return jsonResponse({ error: 'Request body too large' }, 413);
  }

  // ── CORS preflight ──────────────────────────────────────────────────────────
  if (request.method === 'OPTIONS') {
    return corsPreflightResponse(env);
  }

  // ── Rate limiting ────────────────────────────────────────────────────────────
  const ip = request.headers.get('CF-Connecting-IP') || 'unknown';
  const rateLimitKey = getRateLimitKey(pathname, ip);
  const limitConfig = pathname.includes('/search') ? RATE_LIMITS.SEARCH : RATE_LIMITS.DEFAULT;

  const rateLimitResult = await checkRateLimit(env.CACHE, rateLimitKey, limitConfig);
  if (!rateLimitResult.allowed) {
    return jsonResponse(
      { error: 'Rate limit exceeded', retryAfter: rateLimitResult.retryAfter },
      429,
      { 'Retry-After': String(rateLimitResult.retryAfter) }
    );
  }

  // ── Health check ─────────────────────────────────────────────────────────────
  if (pathname === '/health' || pathname === '/') {
    return jsonResponse({ status: 'ok', version: '1.0.0', timestamp: Date.now() });
  }

  // ── API Routes ───────────────────────────────────────────────────────────────
  if (pathname.startsWith('/api/')) {
    const apiPath = pathname.slice(5); // strip /api/

    // Validate content type for POST
    if (request.method === 'POST') {
      const ct = request.headers.get('Content-Type') || '';
      if (!ct.includes('application/json')) {
        return jsonResponse({ error: 'Content-Type must be application/json' }, 415);
      }
    }

    // Route dispatcher
    if (apiPath === 'trending' && request.method === 'GET') {
      return handleTrending(request, env, url);
    }
    if (apiPath === 'search' && request.method === 'POST') {
      return handleSearch(request, env);
    }
    if (apiPath === 'recommend' && request.method === 'GET') {
      return handleRecommend(request, env, url);
    }
    if (apiPath === 'notify' && request.method === 'POST') {
      return handleNotify(request, env);
    }
    if (apiPath === 'spotify/token' && request.method === 'GET') {
      return handleSpotifyToken(env);
    }
    if (apiPath === 'movies/trending' && request.method === 'GET') {
      return handleMoviesTrending(env, url);
    }
    if (apiPath === 'movies/search' && request.method === 'GET') {
      return handleMoviesSearch(env, url);
    }
    if (apiPath === 'songs/search' && request.method === 'GET') {
      return handleSongsSearch(env, url);
    }
    if (apiPath === 'books/search' && request.method === 'GET') {
      return handleBooksSearch(env, url);
    }

    return jsonResponse({ error: 'Not found', path: pathname }, 404);
  }

  return jsonResponse({ error: 'Not found' }, 404);
}

// ─── Handler: Spotify Token ───────────────────────────────────────────────────
async function handleSpotifyToken(env) {
  const cacheKey = 'spotify:access_token';

  // Check KV cache first
  const cached = await env.CACHE.get(cacheKey, { type: 'json' });
  if (cached) {
    return jsonResponse({ access_token: cached.token, source: 'cache' });
  }

  // Fetch fresh token
  const credentials = btoa(`${env.SPOTIFY_CLIENT_ID}:${env.SPOTIFY_CLIENT_SECRET}`);
  const resp = await fetch('https://accounts.spotify.com/api/token', {
    method: 'POST',
    headers: {
      'Authorization': `Basic ${credentials}`,
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: 'grant_type=client_credentials',
  });

  if (!resp.ok) {
    const err = await resp.text();
    console.error('Spotify token error:', err);
    return jsonResponse({ error: 'Failed to get Spotify token' }, 502);
  }

  const data = await resp.json();

  // Cache the token
  await env.CACHE.put(cacheKey, JSON.stringify({ token: data.access_token }), {
    expirationTtl: CACHE_TTL.SPOTIFY_TOKEN,
  });

  return jsonResponse({ access_token: data.access_token, source: 'fresh' });
}

// ─── Handler: Songs Search ────────────────────────────────────────────────────
async function handleSongsSearch(env, url) {
  const query = url.searchParams.get('q');
  const limit = Math.min(parseInt(url.searchParams.get('limit') || '20'), 50);

  if (!query || query.trim().length < 2) {
    return jsonResponse({ error: 'Query must be at least 2 characters' }, 400);
  }

  const sanitized = encodeURIComponent(query.trim().slice(0, 100));
  const cacheKey = `songs:search:${sanitized}:${limit}`;

  const cached = await env.CACHE.get(cacheKey, { type: 'json' });
  if (cached) return jsonResponse({ ...cached, source: 'cache' });

  // Get Spotify token
  const tokenResp = await handleSpotifyToken(env);
  const tokenData = await tokenResp.json();
  const token = tokenData.access_token;

  if (!token) {
    return jsonResponse({ error: 'Spotify unavailable' }, 502);
  }

  const spotifyResp = await fetch(
    `https://api.spotify.com/v1/search?q=${sanitized}&type=track&limit=${limit}&market=US`,
    { headers: { Authorization: `Bearer ${token}` } }
  );

  if (!spotifyResp.ok) {
    return jsonResponse({ error: 'Spotify search failed', status: spotifyResp.status }, 502);
  }

  const spotifyData = await spotifyResp.json();
  const tracks = (spotifyData.tracks?.items || []).map(normalizeTrack);
  const result = { tracks, total: spotifyData.tracks?.total || 0 };

  await env.CACHE.put(cacheKey, JSON.stringify(result), { expirationTtl: CACHE_TTL.SEARCH_RESULTS });
  return jsonResponse({ ...result, source: 'fresh' });
}

// ─── Handler: Movies Trending ─────────────────────────────────────────────────
async function handleMoviesTrending(env, url) {
  const page = Math.max(1, parseInt(url.searchParams.get('page') || '1'));
  const cacheKey = `movies:trending:${page}`;

  const cached = await env.CACHE.get(cacheKey, { type: 'json' });
  if (cached) return jsonResponse({ ...cached, source: 'cache' });

  const resp = await fetch(
    `https://api.themoviedb.org/3/trending/movie/week?language=en-US&page=${page}`,
    { headers: { Authorization: `Bearer ${env.TMDB_API_KEY}`, Accept: 'application/json' } }
  );

  if (!resp.ok) {
    return jsonResponse({ error: 'TMDB unavailable', status: resp.status }, 502);
  }

  const data = await resp.json();
  const movies = (data.results || []).map(normalizeMovie);
  const result = { movies, total_pages: data.total_pages, page: data.page };

  await env.CACHE.put(cacheKey, JSON.stringify(result), { expirationTtl: CACHE_TTL.TRENDING_MOVIES });
  return jsonResponse({ ...result, source: 'fresh' });
}

// ─── Handler: Movies Search ───────────────────────────────────────────────────
async function handleMoviesSearch(env, url) {
  const query = url.searchParams.get('q');
  const page = Math.max(1, parseInt(url.searchParams.get('page') || '1'));

  if (!query || query.trim().length < 2) {
    return jsonResponse({ error: 'Query required' }, 400);
  }

  const sanitized = encodeURIComponent(query.trim().slice(0, 100));
  const cacheKey = `movies:search:${sanitized}:${page}`;

  const cached = await env.CACHE.get(cacheKey, { type: 'json' });
  if (cached) return jsonResponse({ ...cached, source: 'cache' });

  const resp = await fetch(
    `https://api.themoviedb.org/3/search/movie?query=${sanitized}&language=en-US&page=${page}`,
    { headers: { Authorization: `Bearer ${env.TMDB_API_KEY}`, Accept: 'application/json' } }
  );

  if (!resp.ok) {
    return jsonResponse({ error: 'TMDB search failed' }, 502);
  }

  const data = await resp.json();
  const movies = (data.results || []).map(normalizeMovie);
  const result = { movies, total_pages: data.total_pages, page: data.page };

  await env.CACHE.put(cacheKey, JSON.stringify(result), { expirationTtl: CACHE_TTL.SEARCH_RESULTS });
  return jsonResponse({ ...result, source: 'fresh' });
}

// ─── Handler: Books Search ────────────────────────────────────────────────────
async function handleBooksSearch(env, url) {
  const query = url.searchParams.get('q');
  const maxResults = Math.min(parseInt(url.searchParams.get('limit') || '20'), 40);

  if (!query || query.trim().length < 2) {
    return jsonResponse({ error: 'Query required' }, 400);
  }

  const sanitized = encodeURIComponent(query.trim().slice(0, 100));
  const cacheKey = `books:search:${sanitized}:${maxResults}`;

  const cached = await env.CACHE.get(cacheKey, { type: 'json' });
  if (cached) return jsonResponse({ ...cached, source: 'cache' });

  const resp = await fetch(
    `https://www.googleapis.com/books/v1/volumes?q=${sanitized}&maxResults=${maxResults}&key=${env.GOOGLE_BOOKS_API_KEY}`
  );

  if (!resp.ok) {
    return jsonResponse({ error: 'Books search failed' }, 502);
  }

  const data = await resp.json();
  const books = (data.items || []).map(normalizeBook);
  const result = { books, total: data.totalItems || 0 };

  await env.CACHE.put(cacheKey, JSON.stringify(result), { expirationTtl: CACHE_TTL.SEARCH_RESULTS });
  return jsonResponse({ ...result, source: 'fresh' });
}

// ─── Handler: Trending (Aggregated) ──────────────────────────────────────────
async function handleTrending(request, env, url) {
  const type = url.searchParams.get('type') || 'all'; // all | movies | songs | books
  const cacheKey = `trending:${type}`;

  const cached = await env.CACHE.get(cacheKey, { type: 'json' });
  if (cached) return jsonResponse({ ...cached, source: 'cache' });

  const result = {};

  if (type === 'all' || type === 'movies') {
    try {
      const r = await fetch(
        'https://api.themoviedb.org/3/trending/movie/week?language=en-US',
        { headers: { Authorization: `Bearer ${env.TMDB_API_KEY}`, Accept: 'application/json' } }
      );
      const d = await r.json();
      result.movies = (d.results || []).slice(0, 10).map(normalizeMovie);
    } catch (e) {
      result.movies = [];
    }
  }

  if (type === 'all' || type === 'songs') {
    try {
      const tokenResp = await handleSpotifyToken(env);
      const tokenData = await tokenResp.json();
      const r = await fetch(
        'https://api.spotify.com/v1/search?q=year:2024&type=track&market=US&limit=10',
        { headers: { Authorization: `Bearer ${tokenData.access_token}` } }
      );
      const d = await r.json();
      result.songs = (d.tracks?.items || []).slice(0, 10).map(normalizeTrack);
    } catch (e) {
      result.songs = [];
    }
  }

  await env.CACHE.put(cacheKey, JSON.stringify(result), { expirationTtl: CACHE_TTL.TRENDING_MOVIES });
  return jsonResponse({ ...result, source: 'fresh' });
}

// ─── Handler: Unified Search ──────────────────────────────────────────────────
async function handleSearch(request, env) {
  let body;
  try {
    body = await request.json();
  } catch {
    return jsonResponse({ error: 'Invalid JSON body' }, 400);
  }

  const { query, types = ['movies', 'songs', 'books'] } = body;

  if (!query || typeof query !== 'string' || query.trim().length < 2) {
    return jsonResponse({ error: 'query must be a string of at least 2 characters' }, 400);
  }

  const sanitizedQuery = query.trim().slice(0, 100);
  const results = {};

  await Promise.allSettled([
    types.includes('movies') && (async () => {
      try {
        const r = await fetch(
          `https://api.themoviedb.org/3/search/movie?query=${encodeURIComponent(sanitizedQuery)}&language=en-US`,
          { headers: { Authorization: `Bearer ${env.TMDB_API_KEY}`, Accept: 'application/json' } }
        );
        const d = await r.json();
        results.movies = (d.results || []).slice(0, 5).map(normalizeMovie);
      } catch { results.movies = []; }
    })(),
    types.includes('songs') && (async () => {
      try {
        const tokenResp = await handleSpotifyToken(env);
        const tokenData = await tokenResp.json();
        const r = await fetch(
          `https://api.spotify.com/v1/search?q=${encodeURIComponent(sanitizedQuery)}&type=track&limit=5`,
          { headers: { Authorization: `Bearer ${tokenData.access_token}` } }
        );
        const d = await r.json();
        results.songs = (d.tracks?.items || []).slice(0, 5).map(normalizeTrack);
      } catch { results.songs = []; }
    })(),
    types.includes('books') && (async () => {
      try {
        const r = await fetch(
          `https://www.googleapis.com/books/v1/volumes?q=${encodeURIComponent(sanitizedQuery)}&maxResults=5&key=${env.GOOGLE_BOOKS_API_KEY}`
        );
        const d = await r.json();
        results.books = (d.items || []).slice(0, 5).map(normalizeBook);
      } catch { results.books = []; }
    })(),
  ].filter(Boolean));

  return jsonResponse({ query: sanitizedQuery, results });
}

// ─── Handler: Recommend ───────────────────────────────────────────────────────
async function handleRecommend(request, env, url) {
  const genre = url.searchParams.get('genre') || 'pop';
  const type = url.searchParams.get('type') || 'songs';
  const cacheKey = `recommend:${type}:${genre}`;

  const cached = await env.CACHE.get(cacheKey, { type: 'json' });
  if (cached) return jsonResponse({ ...cached, source: 'cache' });

  let result = {};

  if (type === 'songs') {
    try {
      const tokenResp = await handleSpotifyToken(env);
      const { access_token } = await tokenResp.json();
      const r = await fetch(
        `https://api.spotify.com/v1/search?q=genre:${encodeURIComponent(genre)}&type=track&limit=10&market=US`,
        { headers: { Authorization: `Bearer ${access_token}` } }
      );
      const d = await r.json();
      result.songs = (d.tracks?.items || []).map(normalizeTrack);
    } catch { result.songs = []; }
  }

  if (type === 'movies') {
    try {
      const r = await fetch(
        `https://api.themoviedb.org/3/discover/movie?with_genres=${genre}&sort_by=popularity.desc&language=en-US`,
        { headers: { Authorization: `Bearer ${env.TMDB_API_KEY}`, Accept: 'application/json' } }
      );
      const d = await r.json();
      result.movies = (d.results || []).slice(0, 10).map(normalizeMovie);
    } catch { result.movies = []; }
  }

  await env.CACHE.put(cacheKey, JSON.stringify(result), { expirationTtl: CACHE_TTL.RECOMMENDATIONS });
  return jsonResponse({ ...result, source: 'fresh' });
}

// ─── Handler: Push Notify ─────────────────────────────────────────────────────
async function handleNotify(request, env) {
  let body;
  try {
    body = await request.json();
  } catch {
    return jsonResponse({ error: 'Invalid JSON body' }, 400);
  }

  const { token, title, body: msgBody, data = {} } = body;

  if (!token || !title) {
    return jsonResponse({ error: 'token and title are required' }, 400);
  }

  // Validate FCM token format (should be 140-200 chars, alphanumeric + : - _)
  if (typeof token !== 'string' || token.length < 100 || token.length > 512 || !/^[a-zA-Z0-9:_-]+$/.test(token)) {
    return jsonResponse({ error: 'Invalid FCM token format' }, 400);
  }

  // Sanitize notification content
  const safeTitle = String(title).slice(0, 100);
  const safeBody = msgBody ? String(msgBody).slice(0, 500) : '';

  // Get Firebase service account key from env (base64 encoded)
  if (!env.FIREBASE_ADMIN_KEY) {
    return jsonResponse({ error: 'Push notifications not configured' }, 503);
  }

  try {
    const serviceAccount = JSON.parse(atob(env.FIREBASE_ADMIN_KEY));
    const accessToken = await getFirebaseAccessToken(serviceAccount);

    const fcmResp = await fetch(
      `https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id}/messages:send`,
      {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${accessToken}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          message: {
            token,
            notification: { title: safeTitle, body: safeBody },
            data: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, String(v)])),
            android: {
              priority: 'HIGH',
              notification: { channel_id: data.type === 'chat' ? 'merowall_chat' : 'merowall_social' },
            },
          },
        }),
      }
    );

    if (!fcmResp.ok) {
      const err = await fcmResp.json();
      return jsonResponse({ error: 'FCM send failed', details: err }, 502);
    }

    return jsonResponse({ success: true });
  } catch (err) {
    console.error('Notify error:', err);
    return jsonResponse({ error: 'Failed to send notification' }, 500);
  }
}

// ─── Firebase JWT Helper ──────────────────────────────────────────────────────
async function getFirebaseAccessToken(serviceAccount) {
  const now = Math.floor(Date.now() / 1000);
  const payload = {
    iss: serviceAccount.client_email,
    sub: serviceAccount.client_email,
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
  };

  const header = { alg: 'RS256', typ: 'JWT' };
  const headerB64 = btoa(JSON.stringify(header)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  const payloadB64 = btoa(JSON.stringify(payload)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  const signingInput = `${headerB64}.${payloadB64}`;

  // Import RSA private key
  const pemKey = serviceAccount.private_key
    .replace(/-----BEGIN PRIVATE KEY-----/, '')
    .replace(/-----END PRIVATE KEY-----/, '')
    .replace(/\n/g, '');

  const keyBuffer = Uint8Array.from(atob(pemKey), c => c.charCodeAt(0));
  const cryptoKey = await crypto.subtle.importKey(
    'pkcs8', keyBuffer.buffer,
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false, ['sign']
  );

  const signBuffer = new TextEncoder().encode(signingInput);
  const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', cryptoKey, signBuffer);
  const sigB64 = btoa(String.fromCharCode(...new Uint8Array(signature))).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  const jwt = `${signingInput}.${sigB64}`;

  const tokenResp = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`,
  });

  const tokenData = await tokenResp.json();
  return tokenData.access_token;
}

// ─── Rate Limiter (KV-based) ──────────────────────────────────────────────────
async function checkRateLimit(kv, key, { requests, window }) {
  const now = Math.floor(Date.now() / 1000);
  const windowKey = `rl:${key}:${Math.floor(now / window)}`;

  let count = 0;
  try {
    const stored = await kv.get(windowKey);
    count = stored ? parseInt(stored) : 0;
  } catch { /* KV read failure → allow */ return { allowed: true }; }

  if (count >= requests) {
    const windowEnd = (Math.floor(now / window) + 1) * window;
    return { allowed: false, retryAfter: windowEnd - now };
  }

  try {
    await kv.put(windowKey, String(count + 1), { expirationTtl: window * 2 });
  } catch { /* KV write failure → allow */ }

  return { allowed: true, remaining: requests - count - 1 };
}

// ─── Normalizers ─────────────────────────────────────────────────────────────
function normalizeTrack(t) {
  return {
    id: t.id,
    title: t.name,
    artist: t.artists?.[0]?.name || '',
    album: t.album?.name || '',
    albumArtUrl: t.album?.images?.[0]?.url || '',
    previewUrl: t.preview_url || '',
    spotifyUrl: t.external_urls?.spotify || '',
    durationMs: t.duration_ms || 0,
    popularity: t.popularity || 0,
  };
}

function normalizeMovie(m) {
  return {
    id: String(m.id),
    title: m.title,
    overview: m.overview || '',
    posterUrl: m.poster_path ? `https://image.tmdb.org/t/p/w500${m.poster_path}` : '',
    backdropUrl: m.backdrop_path ? `https://image.tmdb.org/t/p/w780${m.backdrop_path}` : '',
    releaseYear: (m.release_date || '').slice(0, 4),
    rating: Math.round((m.vote_average || 0) * 10) / 10,
    genreIds: m.genre_ids || [],
    popularity: m.popularity || 0,
  };
}

function normalizeBook(item) {
  const info = item.volumeInfo || {};
  return {
    id: item.id,
    title: info.title || '',
    author: info.authors?.[0] || 'Unknown',
    coverUrl: (info.imageLinks?.thumbnail || '').replace('http://', 'https://'),
    description: (info.description || '').slice(0, 500),
    publishedYear: (info.publishedDate || '').slice(0, 4),
    pageCount: info.pageCount || 0,
    categories: info.categories || [],
  };
}

// ─── Helpers ──────────────────────────────────────────────────────────────────
function jsonResponse(data, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'Content-Type': 'application/json',
      'Access-Control-Allow-Origin': (env && env.CORS_ORIGIN) ? env.CORS_ORIGIN : '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization',
      'X-Content-Type-Options': 'nosniff',
      'X-Frame-Options': 'DENY',
      'Cache-Control': status === 200 ? 'public, max-age=60' : 'no-store',
      ...extraHeaders,
    },
  });
}

function corsPreflightResponse(env) {
  return new Response(null, {
    status: 204,
    headers: {
      'Access-Control-Allow-Origin': (env && env.CORS_ORIGIN) ? env.CORS_ORIGIN : '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization',
      'Access-Control-Max-Age': '86400',
    },
  });
}

function getRateLimitKey(pathname, ip) {
  // Group similar endpoints
  if (pathname.includes('/search')) return `search:${ip}`;
  if (pathname.includes('/notify')) return `notify:${ip}`;
  return `default:${ip}`;
}
