import { describe, expect, it } from 'vitest';
import { renderWithProviders, screen } from '../../test/test-utils';
import { KpiTile, parseDeltaPct } from './KpiTile';

describe('parseDeltaPct', () => {
  it('parses numeric strings, including negatives', () => {
    expect(parseDeltaPct('12.5')).toBe(12.5);
    expect(parseDeltaPct('-91.7')).toBe(-91.7);
    expect(parseDeltaPct('0')).toBe(0);
  });

  it('returns undefined for null, undefined, blank and non-numeric input', () => {
    expect(parseDeltaPct(undefined)).toBeUndefined();
    expect(parseDeltaPct(null)).toBeUndefined();
    expect(parseDeltaPct('')).toBeUndefined();
    expect(parseDeltaPct('   ')).toBeUndefined();
    expect(parseDeltaPct('abc')).toBeUndefined();
  });
});

describe('KpiTile', () => {
  it('renders label and money value, no delta chip when delta is omitted', () => {
    renderWithProviders(<KpiTile label="Revenue" value="1234.5" />);
    expect(screen.getByText('Revenue')).toBeInTheDocument();
    expect(screen.getByText('1,234.50')).toBeInTheDocument();
    expect(screen.queryByText(/%/)).not.toBeInTheDocument();
  });

  it('shows an up arrow and positive-token color for a positive delta', () => {
    const { container } = renderWithProviders(<KpiTile label="Revenue" value="100" delta={12.5} />);
    expect(screen.getByText('12.5%')).toBeInTheDocument();
    const chip = screen.getByText('12.5%').closest('div');
    expect(chip?.getAttribute('style')).toContain('var(--erp-positive-text)');
    expect(chip?.getAttribute('style')).toContain('var(--erp-positive-bg)');
    expect(container.querySelector('svg')).toBeInTheDocument(); // arrow icon
  });

  it('shows a down arrow and negative-token color for a negative delta', () => {
    renderWithProviders(<KpiTile label="Revenue" value="100" delta={-8} />);
    expect(screen.getByText('8.0%')).toBeInTheDocument();
    const chip = screen.getByText('8.0%').closest('div');
    expect(chip?.getAttribute('style')).toContain('var(--erp-negative-text)');
    expect(chip?.getAttribute('style')).toContain('var(--erp-negative-bg)');
  });

  it('inverts the sentiment when invertDelta is set (e.g. receivables going down is good)', () => {
    renderWithProviders(<KpiTile label="Receivables" value="100" delta={-8} invertDelta />);
    const chip = screen.getByText('8.0%').closest('div');
    expect(chip?.getAttribute('style')).toContain('var(--erp-positive-text)');
  });

  it('does not render a delta chip when delta is not a finite number', () => {
    renderWithProviders(<KpiTile label="Revenue" value="100" delta={Number(undefined)} />);
    expect(screen.queryByText(/%/)).not.toBeInTheDocument();
  });

  it('shows the deltaLabel caption next to the chip when provided', () => {
    renderWithProviders(<KpiTile label="Revenue" value="100" delta={12.5} deltaLabel="vs same period last month" />);
    expect(screen.getByText('vs same period last month')).toBeInTheDocument();
  });

  it('does not show a deltaLabel caption when delta is absent', () => {
    renderWithProviders(<KpiTile label="Revenue" value="100" deltaLabel="vs same period last month" />);
    expect(screen.queryByText('vs same period last month')).not.toBeInTheDocument();
  });
});
