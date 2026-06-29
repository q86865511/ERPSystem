import type { Locale } from './types';

/** First-visit default: any `zh-*` browser language → Traditional Chinese, everything else → English. */
export function detectLocale(): Locale {
  const lang = typeof navigator !== 'undefined' ? navigator.language : 'en';
  return lang.toLowerCase().startsWith('zh') ? 'zh-TW' : 'en';
}
