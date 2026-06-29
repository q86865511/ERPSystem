import createClient, { type Middleware } from 'openapi-fetch';
import type { paths } from './schema';
import { clearAccessToken, getAccessToken, setAccessToken, toBearerHeader } from '../auth/credentials';

/**
 * Single typed API client for the whole app. Spec paths already include the `/api` prefix and the app is
 * served same-origin (Vite proxy in dev, nginx reverse proxy in the container), so the base URL is `/`.
 */
export const api = createClient<paths>({ baseUrl: '/' });

// 401/403 handlers are injected at runtime by AuthProvider so this module stays free of React/router deps.
type Handlers = { onUnauthorized: () => void; onForbidden: (detail?: string) => void };
let handlers: Handlers = { onUnauthorized: () => {}, onForbidden: () => {} };
export function setAuthErrorHandlers(next: Handlers): void {
  handlers = next;
}

// The token endpoints must never trigger the refresh-retry loop (a 401 from /login or /refresh is terminal).
const AUTH_PATHS = ['/api/auth/login', '/api/auth/refresh', '/api/auth/logout'];
const isAuthPath = (url: string): boolean => AUTH_PATHS.some((p) => url.includes(p));

// Single-flight refresh: many concurrent 401s share one POST /api/auth/refresh (the httpOnly refresh cookie
// is sent automatically, being same-origin). Resolves true when a fresh access token was stored.
let refreshing: Promise<boolean> | null = null;
function refreshAccessToken(): Promise<boolean> {
  refreshing ??= fetch('/api/auth/refresh', { method: 'POST' })
    .then(async (res) => {
      if (!res.ok) return false;
      const data = (await res.json()) as { accessToken?: string };
      if (data.accessToken) {
        setAccessToken(data.accessToken);
        return true;
      }
      return false;
    })
    .catch(() => false)
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}

// Clones captured in onRequest (before fetch consumes the body) so a 401 can replay the original request
// once with a refreshed token. Keyed by openapi-fetch's per-request id; always cleared in onResponse.
const replayClones = new Map<string, Request>();

const authMiddleware: Middleware = {
  onRequest({ request, id }) {
    const token = getAccessToken();
    if (token) {
      request.headers.set('Authorization', toBearerHeader(token));
    }
    if (!isAuthPath(request.url)) {
      replayClones.set(id, request.clone());
    }
    return request;
  },
  async onResponse({ request, response, id }) {
    const original = replayClones.get(id);
    replayClones.delete(id);

    if (response.status === 401 && !isAuthPath(request.url)) {
      const refreshed = original ? await refreshAccessToken() : false;
      if (refreshed && original) {
        original.headers.set('Authorization', toBearerHeader(getAccessToken() ?? ''));
        return fetch(original); // replay once with the new token
      }
      clearAccessToken();
      handlers.onUnauthorized();
    } else if (response.status === 403) {
      handlers.onForbidden();
    }
    return response;
  },
};

api.use(authMiddleware);
