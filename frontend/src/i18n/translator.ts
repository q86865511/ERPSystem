import type { TranslationKey, TranslationVars } from './types';

/**
 * Bridge so non-React modules (e.g. `lib/notify.ts`) can translate with the active locale. The
 * `I18nProvider` registers its `t` here on every locale change. In components always prefer `useI18n().t`;
 * this is only for code that can't use hooks. Before the provider mounts it falls back to returning the key.
 */
type TranslateFn = (key: TranslationKey, vars?: TranslationVars) => string;

let active: TranslateFn = (key) => key;

export function setActiveTranslator(fn: TranslateFn): void {
  active = fn;
}

export function tGlobal(key: TranslationKey, vars?: TranslationVars): string {
  return active(key, vars);
}
