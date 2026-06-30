import type { Messages, TranslationVars } from './types';

/**
 * Walk a dotted key into the catalogue and substitute `{name}` placeholders. Unknown keys return the key
 * itself (never throws); a missing variable leaves its `{name}` placeholder intact. Pure + dependency-free
 * so it is unit-testable in isolation; {@link I18nProvider} and {@link tGlobal} both route through it.
 */
export function lookup(dict: Messages, key: string, vars?: TranslationVars): string {
  const raw = key.split('.').reduce<unknown>((acc, part) => {
    if (acc && typeof acc === 'object') return (acc as Record<string, unknown>)[part];
    return undefined;
  }, dict);
  if (typeof raw !== 'string') return key;
  if (!vars) return raw;
  return raw.replace(/\{(\w+)\}/g, (_, name: string) => String(vars[name] ?? `{${name}}`));
}
