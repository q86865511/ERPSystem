import { DatesProvider } from '@mantine/dates';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-tw';
import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { detectLocale } from './detectLocale';
import { lookup } from './lookup';
import { loadLocale, saveLocale } from './localePreference';
import { en } from './messages/en';
import { zhTW } from './messages/zh-TW';
import { setActiveTranslator } from './translator';
import type { Locale, Messages, TranslationKey, TranslationVars } from './types';

export interface I18nContextValue {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  t: (key: TranslationKey, vars?: TranslationVars) => string;
}

export const I18nContext = createContext<I18nContextValue | null>(null);

const DICTIONARIES: Record<Locale, Messages> = { en, 'zh-TW': zhTW };
// dayjs ships `en` by default; `zh-tw` is imported above. Drives DateInput month/weekday names.
const DAYJS_LOCALE: Record<Locale, string> = { en: 'en', 'zh-TW': 'zh-tw' };

export function I18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(() => loadLocale() ?? detectLocale());

  const setLocale = useCallback((next: Locale) => {
    saveLocale(next);
    setLocaleState(next);
  }, []);

  const t = useCallback(
    (key: TranslationKey, vars?: TranslationVars) => lookup(DICTIONARIES[locale], key, vars),
    [locale],
  );

  // Keep <html lang>, dayjs, and the non-React translator bridge in sync with the active locale.
  useEffect(() => {
    document.documentElement.lang = locale;
    dayjs.locale(DAYJS_LOCALE[locale]);
    setActiveTranslator(t);
  }, [locale, t]);

  const value = useMemo<I18nContextValue>(() => ({ locale, setLocale, t }), [locale, setLocale, t]);

  return (
    <I18nContext.Provider value={value}>
      <DatesProvider settings={{ locale: DAYJS_LOCALE[locale] }}>{children}</DatesProvider>
    </I18nContext.Provider>
  );
}
