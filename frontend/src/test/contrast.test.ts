/// <reference types="vite/client" />
import { describe, expect, it } from 'vitest';
import { sidebarColors, theme } from '../theme';
import { treemapFill, treemapLabelColor } from '../components/charts/palette';
// Vite's `?raw` suffix imports the file as a plain string (no Node `fs`/`path` needed — this project has
// no @types/node dependency, and Vitest shares Vite's transform pipeline so this works under `vitest run`
// exactly like it would in app code). Editing index.css automatically flows into this test unmodified.
// The `vite/client` types triple-slash reference (shipped with the already-installed `vite` package)
// types the `?raw` import specifier so this doesn't need a new devDependency.
import indexCssSource from '../index.css?raw';

/**
 * Objectifies the design.md hard contrast constraints (§2.5, §2.4, §5, §9.3) so a future palette edit
 * fails CI instead of silently shipping an inaccessible color. Node environment only (no jsdom needed —
 * this is pure math + file parsing).
 *
 * Single-source rule: no hex is hand-copied a third time. Ramp/sidebar values come from `theme.ts`
 * (the same module the app imports); CSS custom properties come from parsing `index.css` itself via
 * regex, split into the light/dark `:root[data-mantine-color-scheme=...]` blocks — so editing a token in
 * index.css automatically flows into this test without a matching test edit.
 */

// ---------------------------------------------------------------------------
// WCAG 2.x relative luminance / contrast ratio (spec: https://www.w3.org/TR/WCAG21/#contrast-minimum)
// ---------------------------------------------------------------------------

function hexToRgb(hex: string): [number, number, number] {
  const clean = hex.replace('#', '');
  const full = clean.length === 3 ? clean.split('').map((c) => c + c).join('') : clean;
  const n = Number.parseInt(full, 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

function srgbChannelToLinear(c: number): number {
  const cs = c / 255;
  return cs <= 0.03928 ? cs / 12.92 : ((cs + 0.055) / 1.055) ** 2.4;
}

function relativeLuminance(rgb: [number, number, number]): number {
  const [rl, gl, bl] = rgb.map(srgbChannelToLinear) as [number, number, number];
  return 0.2126 * rl + 0.7152 * gl + 0.0722 * bl;
}

/** WCAG contrast ratio between two opaque hex colors, e.g. 21 for black-on-white. */
function contrastRatio(hexA: string, hexB: string): number {
  const lA = relativeLuminance(hexToRgb(hexA));
  const lB = relativeLuminance(hexToRgb(hexB));
  const [lighter, darker] = lA >= lB ? [lA, lB] : [lB, lA];
  return (lighter + 0.05) / (darker + 0.05);
}

/** Flattens a translucent color (e.g. an rgba() wash) onto an opaque background before measuring. */
function compositeOverBackground(fgHex: string, alpha: number, bgHex: string): string {
  const [fr, fg, fb] = hexToRgb(fgHex);
  const [br, bg, bb] = hexToRgb(bgHex);
  const mix = (f: number, b: number) => Math.round(f * alpha + b * (1 - alpha));
  const toHex = (v: number) => v.toString(16).padStart(2, '0');
  return `#${toHex(mix(fr, br))}${toHex(mix(fg, bg))}${toHex(mix(fb, bb))}`;
}

function parseRgba(value: string): { hex: string; alpha: number } {
  const m = value.match(/rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*(?:,\s*([\d.]+)\s*)?\)/i);
  if (!m) throw new Error(`Not an rgba()/rgb() value: ${value}`);
  const r = m[1]!;
  const g = m[2]!;
  const b = m[3]!;
  const a = m[4];
  const toHex = (v: string) => Math.round(Number(v)).toString(16).padStart(2, '0');
  return { hex: `#${toHex(r)}${toHex(g)}${toHex(b)}`, alpha: a === undefined ? 1 : Number(a) };
}

const AA_TEXT = 4.5;
const AA_NON_TEXT = 3;

function expectAA(label: string, fg: string, bg: string, threshold = AA_TEXT) {
  const ratio = contrastRatio(fg, bg);
  expect(ratio, `${label}: ${fg} vs ${bg} = ${ratio.toFixed(2)}:1 (need >= ${threshold}:1)`).toBeGreaterThanOrEqual(
    threshold,
  );
}

// ---------------------------------------------------------------------------
// index.css parsing — one pass per color-scheme block, regex-based (no CSS parser dependency).
// ---------------------------------------------------------------------------

const cssSource = indexCssSource;

/**
 * Extracts the `--token: value;` declarations from the block whose selector list contains
 * `[data-mantine-color-scheme='light']` or `'dark']`, TOLERATING the project's actual selector shape
 * (`:root, [data-mantine-color-scheme='light'] { ... }` and the dark equivalent with the extra
 * `:root[data-mantine-color-scheme='dark']` specificity-matching selector prepended).
 */
function parseCssVarsForScheme(scheme: 'light' | 'dark'): Record<string, string> {
  const blockRegex = new RegExp(
    `\\[data-mantine-color-scheme=['"]${scheme}['"]\\][^{]*\\{([^}]*)\\}`,
    'm',
  );
  const match = cssSource.match(blockRegex);
  if (!match?.[1]) throw new Error(`Could not find a ${scheme}-scheme block in index.css`);
  const body = match[1];
  const vars: Record<string, string> = {};
  const declRegex = /(--[a-z0-9-]+)\s*:\s*([^;]+);/gi;
  let m: RegExpExecArray | null;
  while ((m = declRegex.exec(body))) {
    vars[m[1]!] = m[2]!.trim();
  }
  return vars;
}

const lightVars = parseCssVarsForScheme('light');
const darkVars = parseCssVarsForScheme('dark');

function cssVar(vars: Record<string, string>, name: string): string {
  const v = vars[name];
  if (!v) throw new Error(`CSS var ${name} not found in parsed block`);
  return v;
}

// ---------------------------------------------------------------------------
// theme.ts ramp lookups (single source — no ramp hex re-typed here).
// ---------------------------------------------------------------------------

const grayRamp = theme.colors!.gray!;

// ---------------------------------------------------------------------------
// §2.5 semantic colors: text vs wash, text vs card, text vs body — light + dark.
// ---------------------------------------------------------------------------

describe('semantic colors (design.md §2.5)', () => {
  const schemes: Array<{ name: 'light' | 'dark'; vars: Record<string, string>; card: string; body: string }> = [
    { name: 'light', vars: lightVars, card: cssVar(lightVars, '--app-color-card'), body: cssVar(lightVars, '--mantine-color-body') },
    { name: 'dark', vars: darkVars, card: cssVar(darkVars, '--app-color-card'), body: cssVar(darkVars, '--mantine-color-body') },
  ];

  const pairsWithWash: Array<[string, string]> = [
    ['--erp-positive-text', '--erp-positive-bg'],
    ['--erp-negative-text', '--erp-negative-bg'],
  ];

  // warning has no dedicated wash token in §2.5 (only text) — validated against card/body only, per the
  // task brief's "warning 沒有 bg 變數就驗 vs card/body" instruction.
  const textOnlyTokens = ['--erp-warning-text'];

  for (const { name, vars, card, body } of schemes) {
    for (const [textVar, bgVar] of pairsWithWash) {
      it(`${textVar} vs ${bgVar} passes AA in ${name}`, () => {
        expectAA(`${name} ${textVar} vs wash`, cssVar(vars, textVar), cssVar(vars, bgVar));
      });
    }
    for (const textVar of [...pairsWithWash.map(([t]) => t), ...textOnlyTokens]) {
      it(`${textVar} vs card passes AA in ${name}`, () => {
        expectAA(`${name} ${textVar} vs card`, cssVar(vars, textVar), card);
      });
      it(`${textVar} vs body passes AA in ${name}`, () => {
        expectAA(`${name} ${textVar} vs body`, cssVar(vars, textVar), body);
      });
    }
  }
});

// ---------------------------------------------------------------------------
// Sidebar (design.md §2.4/§5/§8): fixed ink, never flips with color scheme. fg + fgInactive against both
// bg values; the active-item white text against both backgrounds.
// ---------------------------------------------------------------------------

describe('sidebar colors (design.md §2.4/§5)', () => {
  const backgrounds: Array<[string, string]> = [
    ['bgLight', sidebarColors.bgLight],
    ['bgDark', sidebarColors.bgDark],
  ];

  for (const [bgName, bg] of backgrounds) {
    it(`fg vs ${bgName} passes AA`, () => {
      expectAA(`sidebar fg vs ${bgName}`, sidebarColors.fg, bg);
    });
    it(`fgInactive vs ${bgName} passes AA`, () => {
      expectAA(`sidebar fgInactive vs ${bgName}`, sidebarColors.fgInactive, bg);
    });
    it(`active white label vs ${bgName} passes AA`, () => {
      expectAA(`sidebar active (#FFFFFF) vs ${bgName}`, '#FFFFFF', bg);
    });
  }

  // Phase B1 (design.md §5): the deep-ink NavLink states are washes over the sidebar bg, not opaque
  // colors, so the *rendered* text background is the wash composited over the bg — that's what must clear
  // AA. The wash + fg tokens are parsed from index.css (single source; they live in the light block only,
  // being scheme-independent), the two bg values come from theme.ts's sidebarColors, and the composite is
  // the same flatten helper the semantic-color tests use.
  const activeWash = parseRgba(cssVar(lightVars, '--app-sidebar-active-wash'));
  const activeFg = cssVar(lightVars, '--app-sidebar-active-fg'); // #ffffff
  const hoverWash = parseRgba(cssVar(lightVars, '--app-sidebar-hover-wash'));
  const hoverFg = cssVar(lightVars, '--app-sidebar-hover-fg');
  const activeBar = cssVar(lightVars, '--app-sidebar-active-bar');

  for (const [bgName, bg] of backgrounds) {
    it(`active label on active-wash over ${bgName} passes AA`, () => {
      const surface = compositeOverBackground(activeWash.hex, activeWash.alpha, bg);
      expectAA(`sidebar active fg on wash/${bgName}`, activeFg, surface);
    });
    it(`hover label on hover-wash over ${bgName} passes AA`, () => {
      const surface = compositeOverBackground(hoverWash.hex, hoverWash.alpha, bg);
      expectAA(`sidebar hover fg on wash/${bgName}`, hoverFg, surface);
    });
    // The 3px 青瓷 (ink-3) active left bar is a non-text UI affordance — 3:1 floor, not 4.5:1.
    it(`active left bar vs ${bgName} passes the 3:1 non-text floor`, () => {
      expectAA(`sidebar active bar vs ${bgName}`, activeBar, bg, AA_NON_TEXT);
    });
  }
});

// ---------------------------------------------------------------------------
// Table header (design.md §5): th text vs th background, both schemes.
// ---------------------------------------------------------------------------

describe('table header colors (design.md §5)', () => {
  it('th color vs th bg passes AA in light', () => {
    expectAA('light th', cssVar(lightVars, '--app-table-th-color'), cssVar(lightVars, '--app-table-th-bg'));
  });

  it('th color vs th bg passes AA in dark', () => {
    expectAA('dark th', cssVar(darkVars, '--app-table-th-color'), cssVar(darkVars, '--app-table-th-bg'));
  });
});

// ---------------------------------------------------------------------------
// Dimmed text (design.md §2.3/§9.3): vs card and vs body, both schemes.
// ---------------------------------------------------------------------------

describe('dimmed text (design.md §2.3/§9.3)', () => {
  // Light mode intentionally has NO --mantine-color-dimmed override in index.css. Mantine's own stock
  // default resolves dimmed to `var(--mantine-color-gray-6)` in light mode, which — now that `gray` is
  // remapped to paperGray — is paperGray-6 (#4a4d46, sourced from theme.ts, not re-typed here).
  //
  // Deviation from design.md §2.3/the task brief: the brief names paperGray-4 (#a6a69c, "dimmed 文字
  // (light)") and, as a fallback, paperGray-5 (#77786f, claimed "白底 4.54:1"). Measured: gray-4 is
  // 2.45:1 on the white card / 2.33:1 on the paper body, and gray-5 is 4.47:1 / 4.24:1 — BOTH fail the
  // >=4.5:1 hard floor this same spec section demands. Since Mantine's own default already lands on
  // gray-6 (8.60:1 / 8.17:1, comfortably AA) once the ramp is swapped, this test intentionally checks
  // gray-6 and leaves index.css with no override, rather than inventing a bespoke dimmed color the brief
  // never authorized. Flagged for design.md correction — see final report.
  const lightDimmed = grayRamp[6]!;
  const darkDimmed = cssVar(darkVars, '--mantine-color-dimmed');

  it('light dimmed vs card passes AA', () => {
    expectAA('light dimmed vs card', lightDimmed, cssVar(lightVars, '--app-color-card'));
  });

  it('light dimmed vs body passes AA', () => {
    expectAA('light dimmed vs body', lightDimmed, cssVar(lightVars, '--mantine-color-body'));
  });

  it('dark dimmed vs card passes AA', () => {
    expectAA('dark dimmed vs card', darkDimmed, cssVar(darkVars, '--app-color-card'));
  });

  it('dark dimmed vs body passes AA', () => {
    expectAA('dark dimmed vs body', darkDimmed, cssVar(darkVars, '--mantine-color-body'));
  });
});

// ---------------------------------------------------------------------------
// Chart series (design.md §2.6): non-text 3:1 floor against the card surface. §2.6's own text only
// mandates re-checking the DARK card ("dark 模式至少覆寫 --erp-chart-1... 其餘系列遷移時逐一檢查暗底對比,
// 不足者提亮一階") — so that's what's asserted here. Chart-1 is overridden in dark; the rest inherit the
// light-mode value in both schemes (no dark override authored, and all 7 clear 3:1 on the dark card).
//
// NOT asserted here (flagged for design.md, see final report): --erp-chart-3 (#c99235, 藤黃) measures
// only 2.75:1 against the LIGHT card (#ffffff) — below the same 3:1 floor — but §2.6 never asked for a
// light-mode check, only a dark-mode one, so this test doesn't invent a light-mode requirement (or a new
// hex) the spec didn't authorize.
// ---------------------------------------------------------------------------

describe('chart series vs dark card (design.md §2.6, non-text 3:1 floor)', () => {
  const seriesVars = Array.from({ length: 8 }, (_, i) => `--erp-chart-${i + 1}`);

  it.each(seriesVars)('%s vs dark card passes the 3:1 non-text floor', (name) => {
    const value = darkVars[name] ?? lightVars[name];
    if (!value) throw new Error(`Chart var ${name} not found in either scheme block`);
    expectAA(`dark ${name} vs card`, value, cssVar(darkVars, '--app-color-card'), AA_NON_TEXT);
  });
});

// ---------------------------------------------------------------------------
// Inventory heat-treemap tile labels (design.md §2.6): per-status label color vs its own tile fill,
// both single-sourced from palette.ts (no hex re-typed here) — must clear the 4.5:1 text floor.
// ---------------------------------------------------------------------------

describe('treemap tile label colors (design.md §2.6)', () => {
  const statuses = Object.keys(treemapFill) as Array<keyof typeof treemapFill>;

  it.each(statuses)('%s label vs %s tile fill passes AA', (status) => {
    expectAA(`treemap ${status} label vs fill`, treemapLabelColor[status], treemapFill[status]);
  });
});

// ---------------------------------------------------------------------------
// Sanity checks on the helper functions themselves (known WCAG reference values).
// ---------------------------------------------------------------------------

describe('contrast helper sanity checks', () => {
  it('matches the textbook AA boundary value (#767676 on white = 4.54:1)', () => {
    expect(contrastRatio('#767676', '#FFFFFF')).toBeCloseTo(4.54, 1);
  });

  it('black on white is the maximum 21:1', () => {
    expect(contrastRatio('#000000', '#FFFFFF')).toBeCloseTo(21, 0);
  });

  it('composites an rgba wash onto an opaque background before measuring', () => {
    const { hex, alpha } = parseRgba(sidebarColors.activeWash);
    const composited = compositeOverBackground(hex, alpha, sidebarColors.bgLight);
    // The composited swatch must land strictly between the wash color and the bg (a real blend), not
    // equal either endpoint — guards against alpha-parsing regressions silently no-op'ing the blend.
    expect(composited.toLowerCase()).not.toBe(sidebarColors.bgLight.toLowerCase());
    expect(composited.toLowerCase()).not.toBe(hex.toLowerCase());
  });
});
