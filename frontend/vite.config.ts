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
});
