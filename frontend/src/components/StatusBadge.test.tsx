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

// Statuses that stay on the plain light-Badge path (i.e. NOT routed to SealBadge). DRAFT is deliberately
// included: it flows through SealBadge's `draft` branch, which still renders a gray light Badge, so its
// `.mantine-Badge-root` + gray-token assertions and snapshot remain valid.
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

  // --- SealBadge routing (design.md §6) — the split is internal to StatusBadge; panels are unchanged. ---
  describe('routes stamp-language statuses to SealBadge', () => {
    function render(status: string) {
      return renderWithProviders(<StatusBadge status={status} />, { colorScheme: 'light' });
    }

    it('APPROVED renders the 朱印 circular stamp, not a light Badge', () => {
      const { container } = render('APPROVED');
      expect(container.querySelector('[data-seal-variant="stamp"]')).not.toBeNull();
      // The stamp is a bespoke <span>, not a Mantine Badge.
      expect(container.querySelector('.mantine-Badge-root')).toBeNull();
    });

    it.each(['POSTED', 'CLOSED'])('%s renders the 墨印 ink stamp', (status) => {
      const { container } = render(status);
      expect(container.querySelector('[data-seal-variant="ink"]')).not.toBeNull();
      expect(container.querySelector('.mantine-Badge-root')).toBeNull();
    });

    it.each(['PENDING', 'SUBMITTED'])('%s renders the 待審 grey chip (no stamp)', (status) => {
      const { container } = render(status);
      expect(container.querySelector('[data-seal-variant="pending"]')).not.toBeNull();
      expect(container.querySelector('[data-seal-variant="stamp"]')).toBeNull();
    });

    it('DRAFT routes through the draft branch but keeps the gray light Badge look', () => {
      const { container } = render('DRAFT');
      const el = container.querySelector('[data-seal-variant="draft"]');
      expect(el).not.toBeNull();
      expect(el?.classList.contains('mantine-Badge-root')).toBe(true);
    });

    // Regression guard: statuses that merely *look* stamp-adjacent must NOT be hijacked by the seal router.
    it.each(['CONFIRMED', 'RELEASED', 'IN_PROGRESS', 'PAID', 'CANCELLED', 'DONE'])(
      '%s stays on the plain light Badge path',
      (status) => {
        const { container } = render(status);
        expect(container.querySelector('[data-seal-variant]')).toBeNull();
        expect(container.querySelector('.mantine-Badge-root')).not.toBeNull();
      },
    );
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
