# Screenshot tooling

Regenerates `docs/screenshots/*.png` from a **local production build** with **no backend** — used to keep
the marketing screenshots in sync with the current UI (e.g. after a theme change).

## How it works

1. `capture-fixtures.mjs` logs into the deployed demo once and snapshots every `/api/**` GET the screenshot
   pages read into `fixtures/api-fixtures.json` (committed; the JWT is scrubbed). Real demo data, so the
   shots match what a visitor sees at the live site.
2. `shoot-screenshots.mjs` serves `../dist` over a loopback static server, drives Chromium with Playwright,
   and fulfils every `/api/**` request from those fixtures (auth is faked via a canned `/api/auth/refresh`).
   Theme is set through the emulated `prefers-color-scheme` (MantineProvider uses `defaultColorScheme="auto"`);
   locale + onboarding state are seeded in `localStorage` before boot.

Neither script is part of CI or the build gate.

## Usage

```bash
# one-off / when the demo data should be refreshed (needs network to the demo):
node scripts/capture-fixtures.mjs        # or: npm run capture:fixtures

# whenever the UI changes enough to re-shoot:
npm run build
npm run shoot                            # -> ../docs/screenshots/*.png
```

Env overrides for capture: `CAPTURE_BASE` (default `https://erp.terrychou.com`), `CAPTURE_USER`,
`CAPTURE_PASS` (default `admin`/`admin`).

If `shoot` logs `UNMATCHED /api endpoints`, add those paths to `GET_PATHS` in `capture-fixtures.mjs`,
re-capture, and re-shoot.
