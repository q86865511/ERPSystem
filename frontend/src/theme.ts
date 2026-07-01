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

// Blue Enterprise design system. The accent is a corporate blue ramp; the neutral gray ramp is a cool
// slate so every surface, border, and dimmed text reads crisp and business-like. Both light and dark
// derive from these two ramps + the CSS-var surfaces in index.css — no second palette is authored.
const brand: MantineColorsTuple = [
  '#ECF3FE', // 0 / 50  lightest wash
  '#D6E6FD', // 1 / 100
  '#ABC9FA', // 2 / 200
  '#7CAAF5', // 3 / 300
  '#558FEF', // 4 / 400
  '#3B7EE8', // 5 / 500  (dark-mode primary)
  '#2563EB', // 6 / 600  primary accent (light)
  '#1D4FD0', // 7 / 700  hover
  '#1A44B0', // 8 / 800
  '#16357F', // 9 / 900
];

const coolGray: MantineColorsTuple = [
  '#F8FAFC', // 0
  '#F1F5F9', // 1
  '#E2E8F0', // 2  light border
  '#CBD5E1', // 3
  '#94A3B8', // 4  dimmed text
  '#64748B', // 5
  '#334155', // 6  primary text (light)
  '#293548', // 7  dark border
  '#1B2436', // 8  dark card
  '#0F172A', // 9  dark page
];

export const theme = createTheme({
  primaryColor: 'brand',
  primaryShade: { light: 6, dark: 5 },
  autoContrast: true,

  colors: { brand, gray: coolGray },

  defaultRadius: 'md',
  radius: { xs: '4px', sm: '8px', md: '10px', lg: '14px', xl: '16px' },
  spacing: { xs: '8px', sm: '12px', md: '16px', lg: '24px', xl: '32px' },

  shadows: {
    xs: '0 1px 3px rgba(0, 0, 0, 0.08)',
    sm: '0 2px 4px rgba(0, 0, 0, 0.10)',
    md: '0 4px 8px rgba(0, 0, 0, 0.12)',
    lg: '0 8px 16px rgba(0, 0, 0, 0.15)',
    xl: '0 16px 32px rgba(0, 0, 0, 0.20)',
  },

  fontFamily:
    '"Plus Jakarta Sans", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, ' +
    '"Helvetica Neue", "PingFang TC", "Microsoft JhengHei", "Noto Sans TC", sans-serif',
  fontFamilyMonospace: '"JetBrains Mono", ui-monospace, "Courier New", monospace',

  headings: {
    fontWeight: '600',
    sizes: {
      h1: { fontSize: '32px', fontWeight: '700', lineHeight: '1.3' },
      h2: { fontSize: '28px', fontWeight: '700', lineHeight: '1.35' },
      h3: { fontSize: '22px', fontWeight: '600', lineHeight: '1.4' },
      h4: { fontSize: '18px', fontWeight: '600', lineHeight: '1.45' },
      h5: { fontSize: '16px', fontWeight: '500', lineHeight: '1.5' },
      h6: { fontSize: '14px', fontWeight: '500', lineHeight: '1.5' },
    },
  },

  // Component layer (PR 2.2). Each override is authored with `Component.extend({...})` so defaultProps /
  // styles / vars stay typed under strict TS. Two rules keep this conflict-free with the existing panels:
  //   1. We only set *safe defaults* and *inert visual tints* — inline props that panels already set
  //      (radius, padding, size, variant, color) win over defaultProps, so nothing is clobbered. Those
  //      panels get hand-polished in PRs 2.4–2.8.
  //   2. Colors reference the brand ramp / warm-surface CSS vars (index.css) — no raw hex, so both
  //      light and dark derive from the one source.
  components: {
    // White (light) / warm-dark (dark) card; warm hairline border; soft shadow; radius lg for new cards.
    Card: Card.extend({
      defaultProps: { radius: 'lg', shadow: 'xs', withBorder: true },
      styles: { root: { borderColor: 'var(--app-color-border)' } },
    }),
    // Form sections etc. — same warm border as Card; no forced radius (callers pick).
    Paper: Paper.extend({
      defaultProps: { shadow: 'xs' },
      styles: { root: { borderColor: 'var(--app-color-border)' } },
    }),
    // Hairline warm borders + brand-tinted header + warm stripe/hover. We set only the *Color props
    // (inert unless `striped`/`highlightOnHover` are turned on inline), never the booleans — so detail /
    // modal tables that intentionally omit striping stay clean while list tables get the warm treatment.
    Table: Table.extend({
      defaultProps: {
        borderColor: 'var(--app-color-border)',
        // Scheme-AWARE warm tints. `brand-light`/`-light-hover` resolve to soft brand beige in
        // light and warm-dark tones in dark. (The earlier `brand-0`/`-1` were fixed-light ramp
        // indices — near-white hex that was invisible on white in light and glaring white on dark.)
        stripedColor: 'var(--mantine-color-brand-light)',
        highlightOnHoverColor: 'var(--mantine-color-brand-light-hover)',
      },
      styles: {
        th: {
          backgroundColor: 'var(--mantine-color-brand-light-hover)',
          color: 'var(--mantine-color-brand-light-color)',
          fontWeight: 500,
        },
      },
    }),
    // Active tab underline + label already follow the primary (brand); make it explicit.
    Tabs: Tabs.extend({
      defaultProps: { color: 'brand' },
    }),
    // Active nav item: 3px brand left bar via inset box-shadow (no layout shift). The warm wash bg +
    // brand label come for free from the primary color on the default (light) variant.
    NavLink: NavLink.extend({
      styles: (_theme, props) => ({
        root: props.active ? { boxShadow: 'inset 3px 0 0 var(--mantine-color-brand-6)' } : {},
      }),
    }),
    // Warm centered dialog, radius lg, brand close button. Inline `size` is preserved.
    Modal: Modal.extend({
      defaultProps: { centered: true, padding: 'lg', radius: 'lg' },
      styles: { close: { color: 'var(--mantine-color-brand-6)' } },
    }),
    // Right-slide detail drawer, radius lg, brand close button.
    Drawer: Drawer.extend({
      defaultProps: { position: 'right', padding: 'lg', radius: 'lg' },
      styles: { close: { color: 'var(--mantine-color-brand-6)' } },
    }),
    // One override skins every text/select/number/date input (they all render <Input> internally). Only
    // the resting border is warmed here — the focus border already follows the primary (brand).
    // `--input-bd` isn't in the typed CSS-var union, so it goes through `styles.wrapper` (CSSProperties
    // allows custom properties), not the typed `vars` resolver.
    Input: Input.extend({
      styles: { wrapper: { '--input-bd': 'var(--app-color-border)' } },
    }),
    // Filled buttons are already brand (primary). Anchor the radius; leave variant to each call site
    // (panels use subtle/default/light strategically).
    Button: Button.extend({
      defaultProps: { radius: 'md' },
    }),
    // Tertiary by default; brand-on-hover comes from the primary. Inline variant/color (e.g. the
    // red delete icons) still win.
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
    // Warm header surface + warm off-white navbar (both scheme-aware via the surface CSS vars).
    AppShell: AppShell.extend({
      styles: {
        header: { backgroundColor: 'var(--app-color-card)' },
        navbar: { backgroundColor: 'var(--mantine-color-body)' },
      },
    }),
  },
});
