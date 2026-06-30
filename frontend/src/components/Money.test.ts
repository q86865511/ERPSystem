import { describe, expect, it } from 'vitest';
import { formatMoney, sumMoney } from './Money';

describe('formatMoney', () => {
  it('renders an em-dash for null/undefined/empty', () => {
    expect(formatMoney(null)).toBe('—');
    expect(formatMoney(undefined)).toBe('—');
    expect(formatMoney('')).toBe('—');
  });

  it('pads to two decimals and groups thousands', () => {
    expect(formatMoney('0')).toBe('0.00');
    expect(formatMoney('12')).toBe('12.00');
    expect(formatMoney('1234.5')).toBe('1,234.50');
    expect(formatMoney('1000000')).toBe('1,000,000.00');
    expect(formatMoney(1234.5)).toBe('1,234.50');
  });

  it('truncates (does not round) beyond two decimals', () => {
    expect(formatMoney('1234.567')).toBe('1,234.56');
    expect(formatMoney('0.999')).toBe('0.99');
  });

  it('keeps the sign for negatives', () => {
    expect(formatMoney('-1234.567')).toBe('-1,234.56');
    expect(formatMoney('-0.5')).toBe('-0.50');
  });
});

describe('sumMoney', () => {
  it('sums at scale 4 exactly (BigInt, never float)', () => {
    expect(sumMoney(['1.00', '2.50'])).toBe('3.5000');
    expect(sumMoney(['0.0001', '0.0002'])).toBe('0.0003');
    expect(sumMoney(['10000.1234', '0.8766'])).toBe('10001.0000');
  });

  it('handles negatives, zero and mixed signs', () => {
    expect(sumMoney(['-1.5', '1.5'])).toBe('0.0000');
    expect(sumMoney(['-2.0001', '0.0001'])).toBe('-2.0000');
  });

  it('skips empty/undefined entries and totals nothing to zero', () => {
    expect(sumMoney([])).toBe('0.0000');
    expect(sumMoney([undefined, '', '2'])).toBe('2.0000');
  });

  it('avoids float drift that 0.1 + 0.2 would cause', () => {
    expect(sumMoney(['0.1', '0.2'])).toBe('0.3000');
  });
});
