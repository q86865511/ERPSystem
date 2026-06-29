import { en } from './messages/en';

/** Supported UI locales. */
export type Locale = 'en' | 'zh-TW';

/** The catalogue shape, derived from the English source of truth (`en.ts`). zh-TW.ts is constrained to this. */
export type Messages = typeof en;

/**
 * Every legal dotted key path to a string leaf, e.g. `'login.signIn'` or `'status.POSTED'`. `t()` only accepts
 * a `TranslationKey`, so a typo or a key removed from the catalogue is a compile error.
 */
type DottedKeys<T, Prefix extends string = ''> = {
  [K in keyof T & string]: T[K] extends string
    ? `${Prefix}${K}`
    : DottedKeys<T[K], `${Prefix}${K}.`>;
}[keyof T & string];

export type TranslationKey = DottedKeys<Messages>;

/** Variables substituted into `{name}` placeholders by `t()`. */
export type TranslationVars = Record<string, string | number>;
