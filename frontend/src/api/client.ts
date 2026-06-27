import createClient, { type Middleware } from 'openapi-fetch';
import type { paths } from './schema';
import { loadCredentials, toBasicHeader } from '../auth/credentials';

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

const authMiddleware: Middleware = {
  onRequest({ request }) {
    const creds = loadCredentials();
    if (creds) {
      request.headers.set('Authorization', toBasicHeader(creds.username, creds.password));
    }
    return request;
  },
  onResponse({ response }) {
    if (response.status === 401) {
      handlers.onUnauthorized();
    } else if (response.status === 403) {
      handlers.onForbidden();
    }
    return response;
  },
};

api.use(authMiddleware);
