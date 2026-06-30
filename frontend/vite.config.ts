/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// Dev server proxies API + OpenAPI paths to the Spring Boot backend on :8080, mirroring the nginx
// reverse proxy used in the containerised demo so the SPA always talks to a same-origin "/api".
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/v3/api-docs': 'http://localhost:8080',
      '/swagger-ui': 'http://localhost:8080',
    },
  },
  build: {
    rollupOptions: {
      output: {
        // Pin vendor code into stable chunks so an app change doesn't bust the (cached) framework code.
        // Rolldown (Vite 8) wants the function form of manualChunks, not Rollup's object map.
        manualChunks(id: string) {
          const path = id.replace(/\\/g, '/');
          if (!path.includes('/node_modules/')) return undefined;
          if (path.includes('/@mantine/')) return 'vendor-mantine';
          if (path.includes('/@tanstack/')) return 'vendor-query';
          if (/\/(react|react-dom|react-router|react-router-dom|scheduler)\//.test(path)) {
            return 'vendor-react';
          }
          return undefined;
        },
      },
    },
  },
  // Vitest: jsdom component/unit tests run without a server (works in the sandbox). The setup file
  // installs the jsdom shims Mantine needs and resets module singletons between tests.
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: true,
    // Only our Vitest tests; the Playwright specs under e2e/ (*.spec.ts) are run by `npm run e2e`.
    include: ['src/**/*.test.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      reportsDirectory: './coverage',
      // Report-only (no thresholds): generated on demand, not a gate.
      exclude: [
        'src/api/schema.d.ts',
        'src/main.tsx',
        'src/test/**',
        '**/*.test.{ts,tsx}',
        '**/*.d.ts',
        '*.config.*',
      ],
    },
  },
});
