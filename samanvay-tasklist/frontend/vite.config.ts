import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// In development the Spring Boot backend runs on :8081; Vite proxies API calls to it so the
// session and CSRF cookies stay same-origin, exactly as in the packaged jar.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8081',
      '/actuator': 'http://localhost:8081',
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
});
