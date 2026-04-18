import { describe, it, expect, vi, beforeEach } from 'vitest';

// Mock env
const mockEnv = {
  CACHE: {
    get: vi.fn().mockResolvedValue(null),
    put: vi.fn().mockResolvedValue(undefined),
  },
  SPOTIFY_CLIENT_ID: 'test_client_id',
  SPOTIFY_CLIENT_SECRET: 'test_client_secret',
  TMDB_API_KEY: 'test_tmdb_key',
  GOOGLE_BOOKS_API_KEY: 'test_books_key',
  FIREBASE_ADMIN_KEY: '',
};

describe('OsanWall Worker', () => {
  it('health endpoint returns 200', async () => {
    const { default: worker } = await import('./src/index.js');
    const request = new Request('https://osanwall-api.workers.dev/health');
    const response = await worker.fetch(request, mockEnv, {});
    expect(response.status).toBe(200);
    const body = await response.json();
    expect(body.status).toBe('ok');
  });

  it('unknown route returns 404', async () => {
    const { default: worker } = await import('./src/index.js');
    const request = new Request('https://osanwall-api.workers.dev/api/unknown');
    const response = await worker.fetch(request, mockEnv, {});
    expect(response.status).toBe(404);
  });

  it('search rejects short queries', async () => {
    const { default: worker } = await import('./src/index.js');
    const request = new Request('https://osanwall-api.workers.dev/api/search', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query: 'a' }),
    });
    const response = await worker.fetch(request, mockEnv, {});
    expect(response.status).toBe(400);
  });

  it('search rejects missing content-type', async () => {
    const { default: worker } = await import('./src/index.js');
    const request = new Request('https://osanwall-api.workers.dev/api/search', {
      method: 'POST',
      body: JSON.stringify({ query: 'test' }),
    });
    const response = await worker.fetch(request, mockEnv, {});
    expect(response.status).toBe(415);
  });

  it('OPTIONS returns CORS headers', async () => {
    const { default: worker } = await import('./src/index.js');
    const request = new Request('https://osanwall-api.workers.dev/api/trending', {
      method: 'OPTIONS',
    });
    const response = await worker.fetch(request, mockEnv, {});
    expect(response.status).toBe(204);
    expect(response.headers.get('Access-Control-Allow-Origin')).toBe('*');
  });
});
