import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { clearAccessToken, getAccessToken, setAccessToken } from '../auth/credentials';
import { api, setAuthErrorHandlers } from './client';

const PROTECTED = '/api/reporting/trial-balance';
// Two node-vs-browser adjustments, neither of which touches the middleware/refresh logic under test:
//  - baseUrl: openapi-fetch builds the request with `new Request()`; node's undici rejects a relative URL
//    (the browser would resolve it), so override the client's '/' base with an absolute one per request.
//  - fetch: openapi-fetch captures globalThis.fetch at client-creation time, before our stub exists, so the
//    initial request needs the mock passed in. (The refresh + replay call the live global fetch, which the
//    vi.stubGlobal below covers.)
const BASE = 'http://localhost';

let refreshCalls = 0;
let protectedCalls = 0;
let refreshOk = true;

function json(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

function reqInfo(input: RequestInfo | URL, init?: RequestInit): { url: string; headers: Headers } {
  if (input instanceof Request) return { url: input.url, headers: input.headers };
  return { url: String(input), headers: new Headers(init?.headers) };
}

// One fetch mock for everything openapi-fetch + client.ts touch: the protected GET (401 until a refreshed
// Bearer is present), the single-flight refresh, and terminal auth-path 401s.
const mockFetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
  const { url, headers } = reqInfo(input, init);
  if (url.includes('/api/auth/refresh')) {
    refreshCalls++;
    return refreshOk ? json({ accessToken: 'new-token' }, 200) : json({ status: 401 }, 401);
  }
  if (url.includes('/api/auth/login')) {
    return json({ status: 401, detail: 'bad creds' }, 401);
  }
  protectedCalls++;
  return headers.get('authorization') === 'Bearer new-token'
    ? json({ ok: true }, 200)
    : json({ status: 401 }, 401);
});

describe('api client — single-flight 401 → refresh → replay', () => {
  beforeEach(() => {
    refreshCalls = 0;
    protectedCalls = 0;
    refreshOk = true;
    clearAccessToken();
    setAuthErrorHandlers({ onUnauthorized: () => {}, onForbidden: () => {} });
    vi.stubGlobal('fetch', mockFetch);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shares ONE refresh across concurrent 401s and replays each request once', async () => {
    const results = await Promise.all([
      api.GET(PROTECTED, { baseUrl: BASE, fetch: mockFetch }),
      api.GET(PROTECTED, { baseUrl: BASE, fetch: mockFetch }),
      api.GET(PROTECTED, { baseUrl: BASE, fetch: mockFetch }),
    ]);

    expect(refreshCalls).toBe(1); // single-flight: 3 concurrent 401s -> exactly one refresh
    expect(protectedCalls).toBe(6); // 3 first attempts (401) + 3 replays (200)
    expect(getAccessToken()).toBe('new-token');
    for (const r of results) {
      expect(r.error).toBeUndefined();
      expect(r.data).toEqual({ ok: true });
    }
  });

  it('clears the token and fires onUnauthorized when the refresh itself fails', async () => {
    refreshOk = false;
    const onUnauthorized = vi.fn();
    setAuthErrorHandlers({ onUnauthorized, onForbidden: () => {} });

    const r = await api.GET(PROTECTED, { baseUrl: BASE, fetch: mockFetch });

    expect(refreshCalls).toBe(1);
    expect(onUnauthorized).toHaveBeenCalledOnce();
    expect(getAccessToken()).toBeNull();
    expect(r.error).toBeDefined();
  });

  it('does NOT refresh on a 401 from an auth path (terminal)', async () => {
    const r = await api.POST('/api/auth/login', {
      baseUrl: BASE,
      fetch: mockFetch,
      body: { username: 'x', password: 'y' },
    });

    expect(refreshCalls).toBe(0);
    expect(r.error).toBeDefined();
  });

  it('attaches the current bearer token to outgoing requests', async () => {
    setAccessToken('new-token'); // already valid -> first attempt succeeds, no refresh
    const r = await api.GET(PROTECTED, { baseUrl: BASE, fetch: mockFetch });

    expect(refreshCalls).toBe(0);
    expect(protectedCalls).toBe(1);
    expect(r.data).toEqual({ ok: true });
  });
});
