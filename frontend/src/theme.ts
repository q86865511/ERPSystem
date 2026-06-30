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

// Warm Terracotta design system. The accent is a terracotta ramp; the neutral gray ramp is warm (not the
// stock cool gray) so every surface, border, and dimmed text picks up the warmth. Both light and dark
// derive from these two ramps + the CSS-var surfaces in index.css — no second palette is authored.
const terracotta: MantineColorsTuple = [
  '#FDF8F5', // 0 / 50  lightest wash
  '#F9F0EB', // 1 / 100
  '#F0DFCC', // 2 / 200
  '#E8C6A5', // 3 / 300
  '#E0AD7E', // 4 / 400
  '#D4935D', // 5 / 500  (dark-mode primary)
  '#C0532E', // 6 / 600  primary accent (light)
  '#A64729', // 7 / 700  hover
  '#8C3C23', // 8 / 800
  '#71301E', // 9 / 900
];

const warmGray: MantineColorsTuple = [
  '#FCFAF8', // 0
  '#F5EDE8', // 1
  '#E8DED7', // 2  light border
  '#D9C8B8', // 3
  '#9E8976', // 4  dimmed text
  '#6B5E52', // 5
  '#4A423A', // 6  primary text (light)
  '#3A3430', // 7  dark border
  '#2A2420', // 8  dark card
  '#1A1410', // 9  dark page
];

export const theme = createTheme({
  primaryColor: 'terracotta',
  primaryShade: { light: 6, dark: 5 },
  autoContrast: true,

  colors: { terracotta, gray: warmGray },

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
  //   2. Colors reference the terracotta ramp / warm-surface CSS vars (index.css) — no raw hex, so both
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
    // Hairline warm borders + terracotta-tinted header + warm stripe/hover. We set only the *Color props
    // (inert unless `striped`/`highlightOnHover` are turned on inline), never the booleans — so detail /
    // modal tables that intentionally omit striping stay clean while list tables get the warm treatment.
    Table: Table.extend({
      defaultProps: {
        borderColor: 'var(--app-color-border)',
        stripedColor: 'var(--mantine-color-terracotta-0)',
        highlightOnHoverColor: 'var(--mantine-color-terracotta-1)',
      },
      styles: {
        th: { backgroundColor: 'var(--mantine-color-terracotta-0)', fontWeight: 500 },
      },
    }),
    // Active tab underline + label already follow the primary (terracotta); make it explicit.
    Tabs: Tabs.extend({
      defaultProps: { color: 'terracotta' },
    }),
    // Active nav item: 3px terracotta left bar via inset box-shadow (no layout shift). The warm wash bg +
    // terracotta label come for free from the primary color on the default (light) variant.
    NavLink: NavLink.extend({
      styles: (_theme, props) => ({
        root: props.active ? { boxShadow: 'inset 3px 0 0 var(--mantine-color-terracotta-6)' } : {},
      }),
    }),
    // Warm centered dialog, radius lg, terracotta close button. Inline `size` is preserved.
    Modal: Modal.extend({
      defaultProps: { centered: true, padding: 'lg', radius: 'lg' },
      styles: { close: { color: 'var(--mantine-color-terracotta-6)' } },
    }),
    // Right-slide detail drawer, radius lg, terracotta close button.
    Drawer: Drawer.extend({
      defaultProps: { position: 'right', padding: 'lg', radius: 'lg' },
      styles: { close: { color: 'var(--mantine-color-terracotta-6)' } },
    }),
    // One override skins every text/select/number/date input (they all render <Input> internally). Only
    // the resting border is warmed here — the focus border already follows the primary (terracotta).
    // `--input-bd` isn't in the typed CSS-var union, so it goes through `styles.wrapper` (CSSProperties
    // allows custom properties), not the typed `vars` resolver.
    Input: Input.extend({
      styles: { wrapper: { '--input-bd': 'var(--app-color-border)' } },
    }),
    // Filled buttons are already terracotta (primary). Anchor the radius; leave variant to each call site
    // (panels use subtle/default/light strategically).
    Button: Button.extend({
      defaultProps: { radius: 'md' },
    }),
    // Tertiary by default; terracotta-on-hover comes from the primary. Inline variant/color (e.g. the
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
