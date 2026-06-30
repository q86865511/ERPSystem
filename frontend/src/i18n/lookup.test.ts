import { describe, expect, it } from 'vitest';
import { lookup } from './lookup';
import { en } from './messages/en';

describe('i18n lookup', () => {
  it('resolves a dotted key to its leaf string', () => {
    expect(lookup(en, 'common.cancel')).toBe(en.common.cancel);
    expect(lookup(en, 'audit.event.LOGIN_SUCCESS')).toBe(en.audit.event.LOGIN_SUCCESS);
  });

  it('returns the key verbatim for a missing key (never throws)', () => {
    expect(lookup(en, 'no.such.key')).toBe('no.such.key');
  });

  it('returns the key when the path lands on an object, not a string leaf', () => {
    expect(lookup(en, 'common')).toBe('common');
  });

  it('substitutes {name} placeholders from vars', () => {
    expect(lookup(en, 'audit.total', { count: 5 })).toBe('5 records');
  });

  it('leaves a placeholder intact when its var is missing', () => {
    expect(lookup(en, 'audit.total', {})).toBe('{count} records');
  });
});
