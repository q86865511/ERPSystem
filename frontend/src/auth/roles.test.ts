import { describe, expect, it } from 'vitest';
import { ROLES, WRITE_ACTION_ROLE, canDo, hasRole, type WriteAction } from './roles';

describe('hasRole', () => {
  it('checks membership in the caller role set', () => {
    expect(hasRole(['ADMIN'], 'ADMIN')).toBe(true);
    expect(hasRole(['ACCOUNTANT', 'SALES'], 'SALES')).toBe(true);
    expect(hasRole(['SALES'], 'ADMIN')).toBe(false);
    expect(hasRole([], 'ADMIN')).toBe(false);
  });
});

describe('canDo — the full write-action matrix (mirror of SecurityConfig)', () => {
  const entries = Object.entries(WRITE_ACTION_ROLE) as Array<[WriteAction, (typeof ROLES)[number]]>;

  it.each(entries)('%s is allowed for its required role and denied for a wrong role', (action, required) => {
    expect(canDo([required], action)).toBe(true);
    const wrongRole = ROLES.filter((r) => r !== required)[0];
    expect(wrongRole).toBeDefined();
    expect(canDo([wrongRole as (typeof ROLES)[number]], action)).toBe(false);
    expect(canDo([], action)).toBe(false);
  });

  it('the guest (no roles) can do nothing', () => {
    for (const [action] of entries) {
      expect(canDo([], action)).toBe(false);
    }
  });
});
