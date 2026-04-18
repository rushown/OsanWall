import { describe, it, expect, vi, beforeEach } from 'vitest';

function createMockEnv() {
  const store = new Map();
  return {
    CACHE: {
      get: vi.fn(async (key) => store.get(key) ?? null),
      put: vi.fn(async (key, value) => {
        store.set(key, value);
      }),
      list: vi.fn(async ({ prefix = '' } = {}) => ({
        keys: [...store.keys()].filter((k) => k.startsWith(prefix)).map((name) => ({ name })),
      })),
    },
    SPOTIFY_CLIENT_ID: 'test_client_id',
    SPOTIFY_CLIENT_SECRET: 'test_client_secret',
    TMDB_API_KEY: 'test_tmdb_key',
    GOOGLE_BOOKS_API_KEY: 'test_books_key',
    FIREBASE_ADMIN_KEY: '',
    CORS_ORIGIN: '*',
    JWT_SECRET: 'test-secret',
    SWEEP_TOKEN: 'sweep-token',
  };
}

function makeJwt(sub, secret = 'test-secret') {
  const header = base64Url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = base64Url(
    JSON.stringify({
      sub,
      exp: Math.floor(Date.now() / 1000) + 3600,
    })
  );
  const encoder = new TextEncoder();
  return crypto.subtle
    .importKey('raw', encoder.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign'])
    .then((key) => crypto.subtle.sign('HMAC', key, encoder.encode(`${header}.${payload}`)))
    .then((sig) => {
      const bytes = new Uint8Array(sig);
      let binary = '';
      for (let i = 0; i < bytes.length; i += 1) binary += String.fromCharCode(bytes[i]);
      const signature = btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
      return `${header}.${payload}.${signature}`;
    });
}

function base64Url(text) {
  return btoa(text).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

describe('OsanWall Worker', () => {
  let mockEnv;
  beforeEach(() => {
    mockEnv = createMockEnv();
  });

  it('health endpoint returns 200', async () => {
    const { default: worker } = await import('./index.js');
    const request = new Request('https://osanwall-api.workers.dev/health');
    const response = await worker.fetch(request, mockEnv, {});
    expect(response.status).toBe(200);
    const body = await response.json();
    expect(body.status).toBe('ok');
  });

  it('unknown route returns 404', async () => {
    const { default: worker } = await import('./index.js');
    const request = new Request('https://osanwall-api.workers.dev/api/unknown');
    const response = await worker.fetch(request, mockEnv, {});
    expect(response.status).toBe(404);
  });

  it('search rejects short queries', async () => {
    const { default: worker } = await import('./index.js');
    const request = new Request('https://osanwall-api.workers.dev/api/search', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'osanwall-web' },
      body: JSON.stringify({ query: 'a' }),
    });
    const response = await worker.fetch(request, mockEnv, {});
    expect(response.status).toBe(400);
  });

  it('search rejects missing content-type', async () => {
    const { default: worker } = await import('./index.js');
    const request = new Request('https://osanwall-api.workers.dev/api/search', {
      method: 'POST',
      body: JSON.stringify({ query: 'test' }),
    });
    const response = await worker.fetch(request, mockEnv, {});
    expect(response.status).toBe(415);
  });

  it('search rejects missing CSRF guard header', async () => {
    const { default: worker } = await import('./index.js');
    const request = new Request('https://osanwall-api.workers.dev/api/search', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query: 'hello' }),
    });
    const response = await worker.fetch(request, mockEnv, {});
    expect(response.status).toBe(403);
  });

  it('OPTIONS returns CORS headers', async () => {
    const { default: worker } = await import('./index.js');
    const request = new Request('https://osanwall-api.workers.dev/api/trending', {
      method: 'OPTIONS',
    });
    const response = await worker.fetch(request, mockEnv, {});
    expect(response.status).toBe(204);
    expect(response.headers.get('Access-Control-Allow-Origin')).toBe('*');
  });

  it('creates and lists motes', async () => {
    const { default: worker } = await import('./index.js');
    const token = await makeJwt('user_1');
    const createReq = new Request('https://osanwall-api.workers.dev/api/motes', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}`, 'X-Requested-With': 'osanwall-web' },
      body: JSON.stringify({
        authorId: 'user_1',
        text: 'hello osanwall',
        x: 100,
        y: 220,
        vibe: 'strange',
      }),
    });

    const createResp = await worker.fetch(createReq, mockEnv, {});
    expect(createResp.status).toBe(200);
    const createBody = await createResp.json();
    expect(createBody.mote.text).toBe('hello osanwall');

    const listReq = new Request('https://osanwall-api.workers.dev/api/motes?x=0&y=0&w=800&h=600');
    const listResp = await worker.fetch(listReq, mockEnv, {});
    expect(listResp.status).toBe(200);
  });

  it('rejects mote create without auth', async () => {
    const { default: worker } = await import('./index.js');
    const req = new Request('https://osanwall-api.workers.dev/api/motes', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'osanwall-web' },
      body: JSON.stringify({ authorId: 'user_1', text: 'x', x: 0, y: 0 }),
    });
    const resp = await worker.fetch(req, mockEnv, {});
    expect(resp.status).toBe(401);
  });

  it('runs authorized sweep endpoint', async () => {
    const { default: worker } = await import('./index.js');
    const req = new Request('https://osanwall-api.workers.dev/api/motes/sweep', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer sweep-token',
        'X-Requested-With': 'osanwall-web',
      },
      body: JSON.stringify({}),
    });
    const resp = await worker.fetch(req, mockEnv, {});
    expect(resp.status).toBe(200);
    const body = await resp.json();
    expect(body.ok).toBe(true);
  });
});
