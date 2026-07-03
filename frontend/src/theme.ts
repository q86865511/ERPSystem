import {
  ActionIcon,
  Alert,
  AppShell,
  Badge,
  Button,
  Card,
  createTheme,
  Drawer,
  Input,
  Modal,
  NavLink,
  Paper,
  Table,
  Tabs,
  Tooltip,
  type MantineColorsTuple,
} from '@mantine/core';

// Ink Ledger (墨青帳房) design system — see frontend/design.md (single source of truth for every color,
// font and token below). Replaces the earlier Blue Enterprise system (`#2563EB` blue + cool slate gray):
// the accent is now a deep ink-teal ramp evoking ledger ink, the accent-adjacent "seal" ramp is reserved
// for the vermillion approval-stamp language, and the neutral ramp is a warm paper gray. Both light and
// dark derive from these ramps + the CSS-var surfaces in index.css — no second palette is authored.
//
// Naming note (design.md §9.1): the ramp export/key stays `brand` even though its *content* is now the
// `ink` ramp from §2.1 — every existing `brand-*` / `--mantine-color-brand-*` reference in the codebase
// keeps working unchanged. Only the hex values moved.
const brand: MantineColorsTuple = [
  '#EBF2F0', // 0  最淡水洗(hover wash、striped)
  '#D4E4E1', // 1
  '#B0CCC7', // 2
  '#8AB3AC', // 3  青瓷(sidebar active bar)
  '#67998F', // 4  dark-mode primary
  '#477F73', // 5
  '#2F6559', // 6
  '#1D5049', // 7  hover(light)
  '#123F3C', // 8  primary(light)── 錨點「墨青」
  '#0C2B29', // 9
];

// Point color ramp: 朱印(seal)red. Reserved for the seal-badge stamp language and its shared danger
// semantic (design.md §2.2) — never used decoratively elsewhere.
const seal: MantineColorsTuple = [
  '#FBEEEB', // 0
  '#F4D4CD', // 1
  '#E9B0A5', // 2
  '#DC8A7B', // 3
  '#D06853', // 4  dark-mode 朱印/danger
  '#C43D2F', // 5  朱印錨點(light)
  '#A93225', // 6  hover
  '#8C291E', // 7
  '#6F2018', // 8
  '#521711', // 9
];

const paperGray: MantineColorsTuple = [
  '#FAF9F6', // 0  宣紙白(light 頁面底)
  '#F2F0EA', // 1  表頭底、次級面
  '#E5E2D9', // 2  light 邊框
  '#CFCCC2', // 3
  '#A6A69C', // 4  dimmed 文字(light)
  '#77786F', // 5
  '#4A4D46', // 6
  '#343A36', // 7
  '#1C2321', // 8  主文字(light)/ dark 卡片參考
  '#121917', // 9  dark 頁面
];

// Fixed (scheme-independent) sidebar colors — design.md §2.4/§5. Single source: the AppShell/NavLink
// overrides (Phase B1) and contrast.test.ts both import this instead of re-typing the hex.
export const sidebarColors = {
  bgLight: '#123F3C',
  bgDark: '#0F332F',
  fg: '#DCE7E2',
  fgInactive: '#A9C2BA',
  activeWash: 'rgba(138,179,172,.16)',
};

export const theme = createTheme({
  primaryColor: 'brand',
  primaryShade: { light: 8, dark: 4 },
  autoContrast: true,

  colors: { brand, seal, gray: paperGray },

  defaultRadius: 'md',
  radius: { xs: '3px', sm: '6px', md: '8px', lg: '12px', xl: '16px' },
  spacing: { xs: '8px', sm: '12px', md: '16px', lg: '24px', xl: '32px' },

  // Lighter across the board than Blue Enterprise (design.md §4: "比現行更輕"). Cards use no shadow at
  // all (withBorder carries the layering); xs/sm exist only for hover/lift transitions; md is what the
  // one allowed elevated surface (Modal/Drawer) uses, and lg/xl stay under the old system's md.
  shadows: {
    xs: '0 1px 2px rgba(0, 0, 0, 0.05)',
    sm: '0 1px 3px rgba(0, 0, 0, 0.06)',
    md: '0 2px 6px rgba(0, 0, 0, 0.08)',
    lg: '0 4px 10px rgba(0, 0, 0, 0.10)',
    xl: '0 6px 16px rgba(0, 0, 0, 0.12)',
  },

  fontFamily:
    '"Plus Jakarta Sans", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, ' +
    '"Helvetica Neue", "PingFang TC", "Microsoft JhengHei", "Noto Sans TC", sans-serif',
  fontFamilyMonospace: '"JetBrains Mono", ui-monospace, "Courier New", monospace',

  // Serif ledger headings (design.md §3): only h1–h4 get the serif treatment; h5/h6 are explicitly
  // sans-serif and overridden back to the body stack in index.css (Mantine has no per-level fontFamily
  // hook on `headings`, so the two lower levels are handled there instead).
  headings: {
    fontFamily: "'Noto Serif TC', 'Source Serif 4', Georgia, serif",
    fontWeight: '700',
    sizes: {
      h1: { fontSize: '28px', fontWeight: '700', lineHeight: '1.35' },
      h2: { fontSize: '24px', fontWeight: '700', lineHeight: '1.35' },
      h3: { fontSize: '20px', fontWeight: '600', lineHeight: '1.35' },
      h4: { fontSize: '17px', fontWeight: '600', lineHeight: '1.35' },
      h5: { fontSize: '15px', fontWeight: '500', lineHeight: '1.35' },
      h6: { fontSize: '13px', fontWeight: '500', lineHeight: '1.35' },
    },
  },

  // Component layer (PR 2.2, re-skinned for Ink Ledger). Each override is authored with
  // `Component.extend({...})` so defaultProps / styles / vars stay typed under strict TS. Two rules keep
  // this conflict-free with the existing panels:
  //   1. We only set *safe defaults* and *inert visual tints* — inline props that panels already set
  //      (radius, padding, size, variant, color) win over defaultProps, so nothing is clobbered.
  //   2. Colors reference the brand/seal ramps or the app CSS vars (index.css) — no raw hex, so both
  //      light and dark derive from the one source.
  components: {
    // White (light) / ink-dark (dark) card; warm hairline border; radius lg; NO shadow (design.md §4:
    // cards carry no elevation — the border + surface tone ladder does the layering work instead).
    Card: Card.extend({
      defaultProps: { radius: 'lg', withBorder: true },
      styles: { root: { borderColor: 'var(--app-color-border)' } },
    }),
    // Form sections etc. — same warm border as Card; no forced radius (callers pick); no shadow.
    Paper: Paper.extend({
      styles: { root: { borderColor: 'var(--app-color-border)' } },
    }),
    // Hairline warm borders + paper-gray header + ink stripe/hover. We set only the *Color props (inert
    // unless `striped`/`highlightOnHover` are turned on inline), never the booleans — so detail/modal
    // tables that intentionally omit striping stay clean while list tables get the paper treatment.
    Table: Table.extend({
      defaultProps: {
        borderColor: 'var(--app-color-border)',
        stripedColor: 'var(--app-table-striped)',
        highlightOnHoverColor: 'var(--app-table-hover)',
      },
      styles: {
        th: {
          backgroundColor: 'var(--app-table-th-bg)',
          color: 'var(--app-table-th-color)',
          fontWeight: 500,
        },
      },
    }),
    // Active tab underline + label already follow the primary (brand/ink); make it explicit.
    Tabs: Tabs.extend({
      defaultProps: { color: 'brand' },
    }),
    // TODO(B1): 深墨青側欄——navbar 固定 ink、NavLink 深底配色
    // Active nav item: 3px ink left bar via inset box-shadow (no layout shift). The wash bg + label come
    // for free from the primary color on the default (light) variant.
    NavLink: NavLink.extend({
      styles: (_theme, props) => ({
        root: props.active ? { boxShadow: 'inset 3px 0 0 var(--mantine-color-brand-6)' } : {},
      }),
    }),
    // Centered dialog, radius lg, shadow md (the one elevated-surface exception in §4). Close button is
    // ink, not the seal accent (design.md §5) — ink-8 is unreadable on the dark card (~1.3:1), so it's
    // light-dark()'d to ink-4 in dark mode, which clears AA (4.77:1) there.
    Modal: Modal.extend({
      defaultProps: { centered: true, padding: 'lg', radius: 'lg', shadow: 'md' },
      styles: {
        close: { color: 'light-dark(var(--mantine-color-brand-8), var(--mantine-color-brand-4))' },
      },
    }),
    // Right-slide detail drawer, radius lg, shadow md, ink close button (see Modal above).
    Drawer: Drawer.extend({
      defaultProps: { position: 'right', padding: 'lg', radius: 'lg', shadow: 'md' },
      styles: {
        close: { color: 'light-dark(var(--mantine-color-brand-8), var(--mantine-color-brand-4))' },
      },
    }),
    // One override skins every text/select/number/date input (they all render <Input> internally). Only
    // the resting border is set here — the focus border already follows the primary (brand/ink).
    // `--input-bd` isn't in the typed CSS-var union, so it goes through `styles.wrapper` (CSSProperties
    // allows custom properties), not the typed `vars` resolver.
    Input: Input.extend({
      styles: { wrapper: { '--input-bd': 'var(--app-color-border)' } },
    }),
    // Filled buttons are already brand (primary, ink). Anchor the radius; leave variant to each call site
    // (panels use subtle/default/light strategically).
    Button: Button.extend({
      defaultProps: { radius: 'md' },
    }),
    // Tertiary by default; brand-on-hover comes from the primary. Inline variant/color (e.g. the
    // seal delete icons) still win.
    ActionIcon: ActionIcon.extend({
      defaultProps: { variant: 'subtle' },
    }),
    Badge: Badge.extend({
      defaultProps: { variant: 'light', radius: 'sm' },
    }),
    Alert: Alert.extend({
      defaultProps: { variant: 'light', radius: 'md' },
    }),
    // No Tooltip used yet; this is theme config (not dead code) that activates once 2.4+ adds icon tooltips.
    Tooltip: Tooltip.extend({
      defaultProps: { radius: 'sm', color: 'dark' },
    }),
    // TODO(B1): 深墨青側欄——navbar 固定 ink、NavLink 深底配色
    // Warm header surface + off-white navbar (both scheme-aware via the surface CSS vars). Navbar does
    // NOT yet use the fixed sidebarColors ink — that structural change belongs to Phase B1.
    AppShell: AppShell.extend({
      styles: {
        header: { backgroundColor: 'var(--app-color-card)' },
        navbar: { backgroundColor: 'var(--mantine-color-body)' },
      },
    }),
  },
});
