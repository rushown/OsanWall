/**
 * OsanWall API Gateway — Cloudflare Worker
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

const MOTE_INDEX = {
  CELL_SIZE: 600,
  VIEWPORT_MULTIPLIER: 2,
  MAX_TEXT_LEN: 240,
  MAX_RETURNED_MOTES: 300,
};

const DECAY = {
  BASE_LAMBDA_PER_HOUR: 0.08,
  INTERACTION_BETA: 0.65,
  DENSITY_TARGET: 250,
  DENSITY_GAMMA: 1.25,
  AGE_A0_HOURS: 36,
  AGE_SIGMOID_SPAN_HOURS: 10,
  AGE_ETA: 0.7,
  NOVELTY_KAPPA: 0.8,
  MIN_VISIBLE_HOURS: 0.5,
  GHOST_THRESHOLD: 0.18,
  ARCHIVE_THRESHOLD: 0.05,
  SWEEP_INTERVAL_HOURS: 5 / 60, // 5 minutes
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
  async scheduled(_event, env, ctx) {
    ctx.waitUntil(runDecaySweep(env));
  },
};

async function handleRequest(request, env, ctx) {
  setRuntimeEnv(env);
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
      const requestedWith = request.headers.get('X-Requested-With') || '';
      if (pathname.startsWith('/api/') && requestedWith !== 'osanwall-web') {
        return jsonResponse({ error: 'Missing X-Requested-With guard' }, 403);
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
    if (apiPath === 'motes' && request.method === 'POST') {
      const auth = await requireAuth(request, env);
      if (auth.errorResponse) return auth.errorResponse;
      return handleCreateMote(request, env, auth.userId);
    }
    if (apiPath === 'motes' && request.method === 'GET') {
      return handleListMotes(url, env);
    }
    if (apiPath === 'motes/interact' && request.method === 'POST') {
      const auth = await requireAuth(request, env);
      if (auth.errorResponse) return auth.errorResponse;
      return handleMoteInteraction(request, env, auth.userId);
    }
    if (apiPath === 'motes/sweep' && request.method === 'POST') {
      return handleSweepRequest(request, env);
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
              notification: { channel_id: data.type === 'chat' ? 'osanwall_chat' : 'osanwall_social' },
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

// ─── Motes: Free KV Spatial/Decay Engine ──────────────────────────────────────
async function handleCreateMote(request, env, authenticatedUserId) {
  let body;
  try {
    body = await request.json();
  } catch {
    return jsonResponse({ error: 'Invalid JSON body' }, 400);
  }

  const payloadValidation = validateCreateMotePayload(body);
  if (!payloadValidation.ok) return jsonResponse({ error: payloadValidation.error }, 400);
  const { text, x, y, vibe } = payloadValidation.value;
  const authorId = getAuthorIdFromBody(body);
  if (authorId !== authenticatedUserId) {
    return jsonResponse({ error: 'authorId must match authenticated user' }, 403);
  }

  const id = body.id && isSafeId(body.id) ? String(body.id) : crypto.randomUUID();
  const now = Date.now();
  const cell = getCellForPoint(x, y);

  const mote = {
    id,
    authorId,
    text,
    x,
    y,
    vibe,
    alpha: 1,
    status: 'visible',
    createdAt: now,
    updatedAt: now,
    lastDecayAt: now,
    uniqueInteractions: 0,
    interactions: 0,
    lastInteractionAt: null,
  };

  await env.CACHE.put(`mote:${id}`, JSON.stringify(mote));
  await addMoteToCell(env.CACHE, cell, id);

  return jsonResponse({ mote });
}

async function handleListMotes(url, env) {
  const x = Number(url.searchParams.get('x') || 0);
  const y = Number(url.searchParams.get('y') || 0);
  const width = Math.max(1, Number(url.searchParams.get('w') || 1200));
  const height = Math.max(1, Number(url.searchParams.get('h') || 800));
  const limit = Math.min(
    MOTE_INDEX.MAX_RETURNED_MOTES,
    Math.max(1, Number(url.searchParams.get('limit') || 120))
  );

  const expanded = {
    minX: x - width * (MOTE_INDEX.VIEWPORT_MULTIPLIER - 1) / 2,
    maxX: x + width * (MOTE_INDEX.VIEWPORT_MULTIPLIER + 1) / 2,
    minY: y - height * (MOTE_INDEX.VIEWPORT_MULTIPLIER - 1) / 2,
    maxY: y + height * (MOTE_INDEX.VIEWPORT_MULTIPLIER + 1) / 2,
  };

  const cells = getCellsForBounds(expanded);
  const ids = new Set();
  await Promise.all(cells.map(async (cellKey) => {
    const raw = await env.CACHE.get(`cell:${cellKey}`);
    if (!raw) return;
    try {
      const arr = JSON.parse(raw);
      for (const id of arr) ids.add(id);
    } catch {
      // ignore malformed cell
    }
  }));

  const motes = [];
  for (const id of ids) {
    if (motes.length >= limit) break;
    const mote = await getMote(env.CACHE, id);
    if (!mote || mote.status === 'archived') continue;
    if (mote.x < expanded.minX || mote.x > expanded.maxX || mote.y < expanded.minY || mote.y > expanded.maxY) continue;
    motes.push(mote);
  }

  return jsonResponse({ motes, count: motes.length, bounds: expanded });
}

async function handleMoteInteraction(request, env, authenticatedUserId) {
  let body;
  try {
    body = await request.json();
  } catch {
    return jsonResponse({ error: 'Invalid JSON body' }, 400);
  }

  const payloadValidation = validateMoteInteractionPayload(body);
  if (!payloadValidation.ok) return jsonResponse({ error: payloadValidation.error }, 400);
  const { moteId, actorId } = payloadValidation.value;
  if (actorId !== authenticatedUserId) {
    return jsonResponse({ error: 'actorId must match authenticated user' }, 403);
  }

  const mote = await getMote(env.CACHE, moteId);
  if (!mote) return jsonResponse({ error: 'Mote not found' }, 404);

  const dedupeKey = `mote:ix:${moteId}:${actorId}`;
  const alreadySeen = await env.CACHE.get(dedupeKey);
  mote.interactions = (mote.interactions || 0) + 1;
  if (!alreadySeen) {
    mote.uniqueInteractions = (mote.uniqueInteractions || 0) + 1;
    await env.CACHE.put(dedupeKey, '1', { expirationTtl: 60 * 60 * 24 * 14 }); // 14d
  }
  mote.lastInteractionAt = Date.now();
  mote.updatedAt = Date.now();
  await env.CACHE.put(`mote:${mote.id}`, JSON.stringify(mote));

  return jsonResponse({
    success: true,
    moteId,
    interactions: mote.interactions,
    uniqueInteractions: mote.uniqueInteractions,
  });
}

async function handleSweepRequest(request, env) {
  if (env.SWEEP_TOKEN) {
    const auth = request.headers.get('Authorization') || '';
    if (auth !== `Bearer ${env.SWEEP_TOKEN}`) return jsonResponse({ error: 'Unauthorized' }, 401);
  }
  const report = await runDecaySweep(env);
  return jsonResponse(report);
}

async function requireAuth(request, env) {
  const authHeader = request.headers.get('Authorization') || '';
  if (!authHeader.startsWith('Bearer ')) {
    return { errorResponse: jsonResponse({ error: 'Missing bearer token' }, 401) };
  }
  const token = authHeader.slice('Bearer '.length).trim();
  if (!token) return { errorResponse: jsonResponse({ error: 'Missing token' }, 401) };

  try {
    const payload = await verifyJwt(token, env.JWT_SECRET);
    const userId = String(payload.sub || payload.user_id || '');
    if (!userId) return { errorResponse: jsonResponse({ error: 'Token missing subject' }, 401) };
    return { userId };
  } catch (err) {
    return { errorResponse: jsonResponse({ error: 'Invalid token' }, 401) };
  }
}

function getAuthorIdFromBody(body) {
  return String(body.authorId || body.author_id || '').trim();
}

function validateCreateMotePayload(body) {
  if (!body || typeof body !== 'object') return { ok: false, error: 'Body must be an object' };
  const text = String(body.text || '').trim();
  const x = Number(body.x);
  const y = Number(body.y);
  const vibe = String(body.vibe || 'unknown').trim().slice(0, 40);
  const authorId = getAuthorIdFromBody(body);
  if (!authorId || !isSafeId(authorId)) return { ok: false, error: 'authorId is required' };
  if (!text) return { ok: false, error: 'text is required' };
  if (text.length > MOTE_INDEX.MAX_TEXT_LEN) return { ok: false, error: `text max length is ${MOTE_INDEX.MAX_TEXT_LEN}` };
  if (!Number.isFinite(x) || !Number.isFinite(y)) return { ok: false, error: 'x and y must be numbers' };
  return { ok: true, value: { text, x, y, vibe } };
}

function validateMoteInteractionPayload(body) {
  if (!body || typeof body !== 'object') return { ok: false, error: 'Body must be an object' };
  const moteId = String(body.moteId || '').trim();
  const actorId = String(body.actorId || '').trim();
  if (!moteId || !isSafeId(moteId)) return { ok: false, error: 'moteId is required' };
  if (!actorId || !isSafeId(actorId)) return { ok: false, error: 'actorId is required' };
  return { ok: true, value: { moteId, actorId } };
}

function isSafeId(value) {
  return typeof value === 'string' && value.length >= 3 && value.length <= 128 && /^[a-zA-Z0-9:_-]+$/.test(value);
}

async function verifyJwt(jwt, secret) {
  const parts = jwt.split('.');
  if (parts.length !== 3) throw new Error('Malformed JWT');
  const [h, p, s] = parts;
  const header = JSON.parse(textFromBase64Url(h));
  const payload = JSON.parse(textFromBase64Url(p));
  if (payload.exp && Date.now() >= payload.exp * 1000) throw new Error('Expired JWT');
  if (header.alg !== 'HS256') return payload;
  if (!secret) return payload;

  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['verify']
  );
  const ok = await crypto.subtle.verify(
    'HMAC',
    key,
    fromBase64Url(s),
    new TextEncoder().encode(`${h}.${p}`)
  );
  if (!ok) throw new Error('Invalid JWT signature');
  return payload;
}

function textFromBase64Url(input) {
  return new TextDecoder().decode(fromBase64Url(input));
}

function fromBase64Url(input) {
  const normalized = input.replace(/-/g, '+').replace(/_/g, '/');
  const padded = normalized + '==='.slice((normalized.length + 3) % 4);
  const bin = atob(padded);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i += 1) out[i] = bin.charCodeAt(i);
  return out;
}

async function runDecaySweep(env) {
  const list = await env.CACHE.list({ prefix: 'mote:' });
  const motes = [];
  for (const k of list.keys) {
    if (!k.name.startsWith('mote:ix:')) {
      const raw = await env.CACHE.get(k.name);
      if (!raw) continue;
      try {
        motes.push(JSON.parse(raw));
      } catch {
        // ignore malformed entry
      }
    }
  }

  const activeMotes = motes.filter((m) => m.status !== 'archived');
  const density = activeMotes.length / DECAY.DENSITY_TARGET;
  const vibeCounts = {};
  for (const mote of activeMotes) {
    const key = mote.vibe || 'unknown';
    vibeCounts[key] = (vibeCounts[key] || 0) + 1;
  }
  const maxVibeCount = Math.max(1, ...Object.values(vibeCounts));

  let archived = 0;
  let ghosted = 0;
  let decayed = 0;

  for (const mote of activeMotes) {
    const updated = applyDecayStep(mote, {
      density,
      vibeCounts,
      maxVibeCount,
      now: Date.now(),
    });
    if (updated.alpha !== mote.alpha || updated.status !== mote.status) decayed += 1;
    if (updated.status === 'ghosted' && mote.status !== 'ghosted') ghosted += 1;
    if (updated.status === 'archived' && mote.status !== 'archived') {
      archived += 1;
      await removeMoteFromCell(env.CACHE, getCellForPoint(updated.x, updated.y), updated.id);
    }
    await env.CACHE.put(`mote:${updated.id}`, JSON.stringify(updated));
  }

  return {
    ok: true,
    scanned: motes.length,
    active: activeMotes.length,
    decayed,
    ghosted,
    archived,
    at: Date.now(),
  };
}

function applyDecayStep(mote, context) {
  const now = context.now;
  const ageHours = Math.max(0, (now - (mote.createdAt || now)) / 3600000);
  const sinceLastDecayHours = Math.max(
    DECAY.SWEEP_INTERVAL_HOURS,
    (now - (mote.lastDecayAt || now)) / 3600000
  );
  const uniqueInteractions = Math.max(0, Number(mote.uniqueInteractions || 0));

  if (ageHours < DECAY.MIN_VISIBLE_HOURS) {
    return { ...mote, lastDecayAt: now, updatedAt: now };
  }

  const densityBoost = 1 + DECAY.DENSITY_GAMMA * Math.max(0, context.density - 1) ** 2;
  const ageBoost = 1 + DECAY.AGE_ETA * sigmoid((ageHours - DECAY.AGE_A0_HOURS) / DECAY.AGE_SIGMOID_SPAN_HOURS);
  const vibeCount = context.vibeCounts[mote.vibe || 'unknown'] || 1;
  const novelty = 1 - Math.min(1, vibeCount / context.maxVibeCount);
  const noveltyBoost = 1 + DECAY.NOVELTY_KAPPA * (1 - novelty);
  const interactionDamping = 1 / (1 + DECAY.INTERACTION_BETA * Math.log1p(uniqueInteractions));

  const lambdaPerHour =
    DECAY.BASE_LAMBDA_PER_HOUR *
    densityBoost *
    ageBoost *
    noveltyBoost *
    interactionDamping;

  const nextAlpha = Math.max(0, Number(mote.alpha || 1) * Math.exp(-lambdaPerHour * sinceLastDecayHours));
  let nextStatus = mote.status || 'visible';
  if (nextAlpha < DECAY.ARCHIVE_THRESHOLD) nextStatus = 'archived';
  else if (nextAlpha < DECAY.GHOST_THRESHOLD) nextStatus = 'ghosted';
  else nextStatus = 'visible';

  return {
    ...mote,
    alpha: round4(nextAlpha),
    status: nextStatus,
    lastDecayAt: now,
    updatedAt: now,
  };
}

function sigmoid(x) {
  return 1 / (1 + Math.exp(-x));
}

function round4(n) {
  return Math.round(n * 10000) / 10000;
}

function getCellForPoint(x, y) {
  const cx = Math.floor(x / MOTE_INDEX.CELL_SIZE);
  const cy = Math.floor(y / MOTE_INDEX.CELL_SIZE);
  return `${cx}:${cy}`;
}

function getCellsForBounds(bounds) {
  const minCx = Math.floor(bounds.minX / MOTE_INDEX.CELL_SIZE);
  const maxCx = Math.floor(bounds.maxX / MOTE_INDEX.CELL_SIZE);
  const minCy = Math.floor(bounds.minY / MOTE_INDEX.CELL_SIZE);
  const maxCy = Math.floor(bounds.maxY / MOTE_INDEX.CELL_SIZE);
  const cells = [];
  for (let cx = minCx; cx <= maxCx; cx += 1) {
    for (let cy = minCy; cy <= maxCy; cy += 1) {
      cells.push(`${cx}:${cy}`);
    }
  }
  return cells;
}

async function addMoteToCell(kv, cellKey, moteId) {
  const key = `cell:${cellKey}`;
  const raw = await kv.get(key);
  const arr = raw ? safeJsonArray(raw) : [];
  if (!arr.includes(moteId)) arr.push(moteId);
  await kv.put(key, JSON.stringify(arr));
}

async function removeMoteFromCell(kv, cellKey, moteId) {
  const key = `cell:${cellKey}`;
  const raw = await kv.get(key);
  if (!raw) return;
  const arr = safeJsonArray(raw).filter((id) => id !== moteId);
  await kv.put(key, JSON.stringify(arr));
}

function safeJsonArray(raw) {
  try {
    const val = JSON.parse(raw);
    return Array.isArray(val) ? val : [];
  } catch {
    return [];
  }
}

async function getMote(kv, id) {
  const raw = await kv.get(`mote:${id}`);
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
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
  const env = getRuntimeEnv();
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'Content-Type': 'application/json',
      'Access-Control-Allow-Origin': (env && env.CORS_ORIGIN) ? env.CORS_ORIGIN : '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Requested-With',
      'X-Content-Type-Options': 'nosniff',
      'X-Frame-Options': 'DENY',
      'Referrer-Policy': 'strict-origin-when-cross-origin',
      'Content-Security-Policy': "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; connect-src 'self';",
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
      'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Requested-With',
      'Access-Control-Max-Age': '86400',
    },
  });
}

let runtimeEnv = null;
function setRuntimeEnv(env) {
  runtimeEnv = env || null;
}
function getRuntimeEnv() {
  return runtimeEnv || null;
}

function getRateLimitKey(pathname, ip) {
  // Group similar endpoints
  if (pathname.includes('/search')) return `search:${ip}`;
  if (pathname.includes('/notify')) return `notify:${ip}`;
  return `default:${ip}`;
}
