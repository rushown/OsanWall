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
  };
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
      headers: { 'Content-Type': 'application/json' },
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
    const createReq = new Request('https://osanwall-api.workers.dev/api/motes', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
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
});
