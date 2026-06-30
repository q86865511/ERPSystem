import { describe, expect, it } from 'vitest';
import { errorMessage } from './notify';

// Note: with no I18nProvider mounted, tGlobal falls back to returning the key — so the fallback branch
// resolves to the literal 'common.requestFailed', which is exactly what we assert.
describe('errorMessage', () => {
  it('passes a plain string through', () => {
    expect(errorMessage('boom')).toBe('boom');
  });

  it('prefers RFC9457 detail, then title, then message', () => {
    expect(errorMessage({ detail: 'D', title: 'T', message: 'M' })).toBe('D');
    expect(errorMessage({ title: 'T', message: 'M' })).toBe('T');
    expect(errorMessage({ message: 'M' })).toBe('M');
  });

  it('falls back to the requestFailed key for empty objects / non-objects', () => {
    expect(errorMessage({})).toBe('common.requestFailed');
    expect(errorMessage(null)).toBe('common.requestFailed');
    expect(errorMessage(42)).toBe('common.requestFailed');
  });
});
