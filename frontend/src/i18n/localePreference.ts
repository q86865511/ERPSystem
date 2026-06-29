/**
 * Persists the user's manual locale override in localStorage (survives tab close, unlike the auth credentials
 * in `credentials.ts` which use sessionStorage). Mirrors that module's load/save + try-catch shape.
 */
import type { Locale } from './types';

const KEY = 'erp.locale';
const LOCALES: readonly Locale[] = ['en', 'zh-TW'];

export function loadLocale(): Locale | null {
  try {
    const raw = localStorage.getItem(KEY);
    return raw && (LOCALES as readonly string[]).includes(raw) ? (raw as Locale) : null;
  } catch {
    return null;
  }
}

export function saveLocale(locale: Locale): void {
  try {
    localStorage.setItem(KEY, locale);
  } catch {
    // Storage may be unavailable (private mode / quota); preference is best-effort.
  }
}
