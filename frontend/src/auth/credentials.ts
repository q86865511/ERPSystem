/**
 * Access-token store. The access token lives only in memory (this module variable) — never in storage — so
 * an XSS payload can't lift it from localStorage. The refresh token is an httpOnly cookie the browser sends
 * automatically to /api/auth; JS can't read it. A page reload loses the in-memory token; AuthProvider
 * restores the session via a silent POST /api/auth/refresh on startup. Swapping the token transport only
 * touches this module and the client's auth middleware, not the UI.
 */
let accessToken: string | null = null;

export function getAccessToken(): string | null {
  return accessToken;
}

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function clearAccessToken(): void {
  accessToken = null;
}

export function toBearerHeader(token: string): string {
  return `Bearer ${token}`;
}
