/// <reference types="vitest" />
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// In dev, Vite proxies the same two paths nginx proxies in the server setup,
// so the browser only ever talks to one origin and no CORS is needed.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '');
  const kdApiUrl = env.KD_API_URL_DEV || 'http://localhost:8080';
  const postgrestUrl = env.POSTGREST_URL_DEV || 'http://localhost:3001';

  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/knowledgeDrop': { target: kdApiUrl, changeOrigin: true },
        '/db': {
          target: postgrestUrl,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/db/, ''),
        },
      },
    },
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
    },
  };
});
