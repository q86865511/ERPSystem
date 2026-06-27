/**
 * HTTP Basic credential storage. Held in sessionStorage (cleared when the tab closes, not shared across
 * tabs) so a refresh keeps the session. Basic auth requires resending the password on every request, so
 * it is stored here — a deliberate demo-grade trade-off; swapping to a token only changes this module and
 * the client's auth middleware, not the UI. See SecurityConfig.java for the deferred JWT note.
 */
const KEY = 'erp.auth';

export interface StoredCredentials {
  username: string;
  password: string;
  roles: string[];
}

export function loadCredentials(): StoredCredentials | null {
  const raw = sessionStorage.getItem(KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as StoredCredentials;
  } catch {
    return null;
  }
}

export function saveCredentials(credentials: StoredCredentials): void {
  sessionStorage.setItem(KEY, JSON.stringify(credentials));
}

export function clearCredentials(): void {
  sessionStorage.removeItem(KEY);
}

export function toBasicHeader(username: string, password: string): string {
  return `Basic ${btoa(`${username}:${password}`)}`;
}
