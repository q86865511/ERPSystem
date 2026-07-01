import { describe, expect, it } from 'vitest';
import { renderWithProviders } from '../test/test-utils';
import { StatusBadge } from './StatusBadge';

/**
 * Mantine emits the badge color as inline CSS variables on the root (e.g.
 * `--badge-color: var(--mantine-color-brand-light-color)`), so we can assert the *mapped Mantine
 * color* directly from the style string — independent of the active locale's label text and of which
 * color scheme is active (the var reference is the same in light and dark; only the CSS resolves per
 * scheme at runtime). That makes these checks the durable guard for the status→color map and a
 * cheap dark-mode parity guard the sandbox can run (real rendering is verified on the Oracle/Docker demo).
 */
function badgeStyle(status: string, colorScheme: 'light' | 'dark'): string {
  const { container } = renderWithProviders(<StatusBadge status={status} />, { colorScheme });
  return container.querySelector('.mantine-Badge-root')?.getAttribute('style') ?? '';
}

const REPRESENTATIVE = ['DRAFT', 'CONFIRMED', 'RELEASED', 'IN_PROGRESS', 'CANCELLED'] as const;

describe('StatusBadge', () => {
  it('renders nothing when status is undefined', () => {
    const { container } = renderWithProviders(<StatusBadge status={undefined} />);
    expect(container.querySelector('.mantine-Badge-root')).toBeNull();
  });

  // The "active / in-our-hands" primary states map to the brand color (blue enterprise theme).
  it.each(['CONFIRMED', 'IN_PROGRESS', 'RELEASED'])('maps active state %s to brand', (status) => {
    const style = badgeStyle(status, 'light');
    expect(style).toContain('brand');
    expect(style).not.toContain('terracotta');
  });

  it('keeps semantic colors for non-primary states', () => {
    expect(badgeStyle('CANCELLED', 'light')).toContain('red');
    expect(badgeStyle('PAID', 'light')).toContain('teal');
    expect(badgeStyle('DRAFT', 'light')).toContain('gray');
  });

  // Dark-mode parity (spec §7.2 / §11): each representative status renders crash-free and identically
  // under both schemes — a divergence (or a hand-written scheme-specific color) would fail here.
  it.each(REPRESENTATIVE)('renders %s identically in light and dark', (status) => {
    expect(badgeStyle(status, 'light')).toBe(badgeStyle(status, 'dark'));
  });

  // Snapshot the badge element itself (not the container, whose first child is Mantine's injected global
  // <style>) under both schemes (spec §11) — encodes the brand color token so a remap regression or a
  // structural change to the badge fails the snapshot.
  it.each(REPRESENTATIVE)('matches snapshot for %s in light', (status) => {
    const { container } = renderWithProviders(<StatusBadge status={status} />, { colorScheme: 'light' });
    expect(container.querySelector('.mantine-Badge-root')).toMatchSnapshot();
  });

  it.each(REPRESENTATIVE)('matches snapshot for %s in dark', (status) => {
    const { container } = renderWithProviders(<StatusBadge status={status} />, { colorScheme: 'dark' });
    expect(container.querySelector('.mantine-Badge-root')).toMatchSnapshot();
  });
});
