import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';
import { setAuthErrorHandlers } from '../api/client';
import { clearAccessToken } from '../auth/credentials';

// --- jsdom shims Mantine 9 relies on (jsdom provides none of these; every MantineProvider mount throws
// without them — the classic green-local / red-CI trap). Plain assignments so they persist for the whole
// file run regardless of vi.restoreAllMocks / vi.unstubAllGlobals in individual tests. ---
window.matchMedia = ((query: string) => ({
  matches: false,
  media: query,
  onchange: null,
  addListener() {},
  removeListener() {},
  addEventListener() {},
  removeEventListener() {},
  dispatchEvent: () => false,
})) as unknown as typeof window.matchMedia;

class ResizeObserverStub {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}
globalThis.ResizeObserver = ResizeObserverStub as unknown as typeof globalThis.ResizeObserver;

if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = () => {};
}

// Reset cross-test state: the access token and the API client's 401/403 handlers are module singletons, so a
// test that sets them must not leak into the next.
afterEach(() => {
  cleanup();
  clearAccessToken();
  setAuthErrorHandlers({ onUnauthorized: () => {}, onForbidden: () => {} });
});
