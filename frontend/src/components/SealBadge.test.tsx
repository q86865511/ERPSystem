import { describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../test/test-utils';
import { SealBadge } from './SealBadge';
import { StatusBadge } from './StatusBadge';

/**
 * SealBadge is the Ink Ledger signature element (design.md §6). These tests pin the four shape branches,
 * that the i18n-resolved label is rendered verbatim, and the stamp-drop animation contract: it fires only
 * when a *mounted* badge transitions into the approved stamp (never on first render), and reduced-motion is
 * handled purely in CSS (the component never branches on matchMedia — see the reduced-motion test).
 *
 * The stamp / ink / pending / draft branches are tagged with `data-seal-variant`, so we assert on that
 * rather than on the hashed CSS-module class names.
 */
function seal(status: string, variant: 'stamp' | 'ink' | 'pending' | 'draft', label: string, colorScheme?: 'light' | 'dark') {
  return renderWithProviders(<SealBadge status={status} variant={variant} label={label} />, { colorScheme });
}

describe('SealBadge shapes', () => {
  it('renders 朱印 (vermillion stamp) for the approved variant', () => {
    const { container } = seal('APPROVED', 'stamp', '已核准');
    const el = container.querySelector('[data-seal-variant="stamp"]');
    expect(el).not.toBeNull();
    expect(el?.textContent).toBe('已核准');
  });

  it('renders 墨印 (ink stamp) for the posted/closed variant', () => {
    const { container } = seal('POSTED', 'ink', '已過帳');
    const el = container.querySelector('[data-seal-variant="ink"]');
    expect(el).not.toBeNull();
    expect(el?.textContent).toBe('已過帳');
  });

  it('renders the 待審 grey chip for the pending variant', () => {
    const { container } = seal('PENDING', 'pending', '待審');
    const el = container.querySelector('[data-seal-variant="pending"]');
    expect(el).not.toBeNull();
    // "not stamped yet": not a circular stamp, so no stamp element is present.
    expect(container.querySelector('[data-seal-variant="stamp"]')).toBeNull();
  });

  it('renders the draft variant as a plain grey light Badge (subtle 灰, design.md §6)', () => {
    const { container } = seal('DRAFT', 'draft', '草稿');
    const el = container.querySelector('[data-seal-variant="draft"]');
    expect(el).not.toBeNull();
    // Draft keeps the existing gray light Badge look — so it is a Mantine Badge, not a hand-rolled chip.
    expect(el?.classList.contains('mantine-Badge-root')).toBe(true);
    expect(container.querySelector('.mantine-Badge-root')?.getAttribute('style')).toContain('gray');
  });

  it('renders the label passed in verbatim (i18n is resolved by StatusBadge, not here)', () => {
    // English label fits or not is a layout concern; the component must render exactly what it is given.
    const { container } = seal('APPROVED', 'stamp', 'Approved');
    expect(container.querySelector('[data-seal-variant="stamp"]')?.textContent).toBe('Approved');
  });
});

describe('SealBadge 朱印 outline: circle for short CJK, rounded-rect otherwise (design.md §6)', () => {
  it('uses the circular stamp for a ≤3-char CJK label (已核准)', () => {
    const { container } = seal('APPROVED', 'stamp', '已核准');
    expect(container.querySelector('[data-seal-variant="stamp"]')?.getAttribute('data-seal-shape')).toBe('circle');
  });

  it('uses the rounded-rect stamp for an English label that a circle can not hold (Approved)', () => {
    const { container } = seal('APPROVED', 'stamp', 'Approved');
    expect(container.querySelector('[data-seal-variant="stamp"]')?.getAttribute('data-seal-shape')).toBe('rect');
  });

  it('uses the rounded-rect stamp for a CJK label longer than 3 chars', () => {
    const { container } = seal('APPROVED', 'stamp', '已核准通過');
    expect(container.querySelector('[data-seal-variant="stamp"]')?.getAttribute('data-seal-shape')).toBe('rect');
  });

  it('picks the outline the same way when the label comes from StatusBadge under each locale', async () => {
    // Full path: StatusBadge resolves the i18n label, so zh-TW → 已核准 (circle) and en → Approved (rect).
    // I18nProvider reads the persisted locale (saveLocale) at mount, so we set it before each render.
    const { saveLocale } = await import('../i18n/localePreference');
    try {
      saveLocale('zh-TW');
      const zh = renderWithProviders(<StatusBadge status="APPROVED" />);
      expect(zh.container.querySelector('[data-seal-variant="stamp"]')?.getAttribute('data-seal-shape')).toBe(
        'circle',
      );
      expect(zh.container.querySelector('[data-seal-variant="stamp"]')?.textContent).toBe('已核准');
      zh.unmount();

      saveLocale('en');
      const en = renderWithProviders(<StatusBadge status="APPROVED" />);
      expect(en.container.querySelector('[data-seal-variant="stamp"]')?.getAttribute('data-seal-shape')).toBe(
        'rect',
      );
      expect(en.container.querySelector('[data-seal-variant="stamp"]')?.textContent).toBe('Approved');
      en.unmount();
    } finally {
      localStorage.removeItem('erp.locale'); // don't leak a locale preference into other tests
    }
  });
});

describe('SealBadge size (design.md §6): sm compact for lists, md full for detail areas', () => {
  it('defaults to the compact (sm) size when no size prop is given', () => {
    const { container } = renderWithProviders(<SealBadge status="APPROVED" variant="stamp" label="已核准" />);
    expect(container.querySelector('[data-seal-variant="stamp"]')?.getAttribute('data-seal-size')).toBe('sm');
  });

  it('applies the md size for the detail-area path', () => {
    const { container } = renderWithProviders(
      <SealBadge status="APPROVED" variant="stamp" label="已核准" size="md" />,
    );
    expect(container.querySelector('[data-seal-variant="stamp"]')?.getAttribute('data-seal-size')).toBe('md');
  });

  it('carries the size through every stamp-language shape (ink / pending)', () => {
    const ink = renderWithProviders(<SealBadge status="POSTED" variant="ink" label="已過帳" size="md" />);
    const pending = renderWithProviders(<SealBadge status="PENDING" variant="pending" label="待審" size="sm" />);
    expect(ink.container.querySelector('[data-seal-variant="ink"]')?.getAttribute('data-seal-size')).toBe('md');
    expect(pending.container.querySelector('[data-seal-variant="pending"]')?.getAttribute('data-seal-size')).toBe(
      'sm',
    );
  });

  it('StatusBadge forwards size="md" from a detail-area call site to the seal', () => {
    const { container } = renderWithProviders(<StatusBadge status="APPROVED" size="md" />);
    expect(container.querySelector('[data-seal-variant="stamp"]')?.getAttribute('data-seal-size')).toBe('md');
  });
});

describe('SealBadge stamp-drop animation', () => {
  // The hashed CSS-module class name for `.stampDrop` — resolved once via a known transition so the
  // assertions below don't hard-code the hash.
  function stampDropClass(): string {
    const { container, rerender } = renderWithProviders(
      <SealBadge status="SUBMITTED" variant="pending" label="待審" />,
    );
    rerender(<SealBadge status="APPROVED" variant="stamp" label="已核准" />);
    const el = container.querySelector('[data-seal-variant="stamp"]');
    const dropClass = Array.from(el?.classList ?? []).find((c) => c.toLowerCase().includes('stampdrop'));
    expect(dropClass).toBeDefined();
    return dropClass as string;
  }

  it('does NOT animate on first render of an already-approved badge (list initial load is silent)', () => {
    const { container } = renderWithProviders(
      <SealBadge status="APPROVED" variant="stamp" label="已核准" />,
    );
    const el = container.querySelector('[data-seal-variant="stamp"]');
    const hasDrop = Array.from(el?.classList ?? []).some((c) => c.toLowerCase().includes('stampdrop'));
    expect(hasDrop).toBe(false);
  });

  it('DOES animate when a mounted badge transitions into the approved stamp', () => {
    // Establishing this also proves the drop class resolves — the helper asserts it is present.
    expect(stampDropClass()).toBeTruthy();
  });

  it('does not animate when the badge was already the approved stamp (no state change)', () => {
    const { container, rerender } = renderWithProviders(
      <SealBadge status="APPROVED" variant="stamp" label="已核准" />,
    );
    // Re-render with the same status — a parent re-render must not re-trigger the drop.
    rerender(<SealBadge status="APPROVED" variant="stamp" label="已核准" />);
    const el = container.querySelector('[data-seal-variant="stamp"]');
    const hasDrop = Array.from(el?.classList ?? []).some((c) => c.toLowerCase().includes('stampdrop'));
    expect(hasDrop).toBe(false);
  });

  it('handles reduced-motion in CSS, not JS — the emitted class is identical regardless of matchMedia', () => {
    // §7 mandates reduced-motion be handled via a CSS @media query, never JS detection. Proof: flipping
    // matchMedia to report reduced-motion does not change the class the component emits on transition.
    const original = window.matchMedia;
    try {
      window.matchMedia = ((query: string) => ({
        matches: query.includes('prefers-reduced-motion'),
        media: query,
        onchange: null,
        addListener() {},
        removeListener() {},
        addEventListener() {},
        removeEventListener() {},
        dispatchEvent: () => false,
      })) as unknown as typeof window.matchMedia;

      const { container, rerender } = renderWithProviders(
        <SealBadge status="SUBMITTED" variant="pending" label="待審" />,
      );
      rerender(<SealBadge status="APPROVED" variant="stamp" label="已核准" />);
      const el = container.querySelector('[data-seal-variant="stamp"]');
      const hasDrop = Array.from(el?.classList ?? []).some((c) => c.toLowerCase().includes('stampdrop'));
      // The class is STILL added under reduced-motion (CSS neutralizes the animation, not JS).
      expect(hasDrop).toBe(true);
    } finally {
      window.matchMedia = original;
    }
    vi.restoreAllMocks();
  });
});
