import { createTheme, type MantineColorsTuple } from '@mantine/core';

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
});
