import { defineConfig } from 'vitest/config';
import { fileURLToPath, URL } from 'node:url';

export default defineConfig({
  resolve: {
    alias: {
      '@pkg': fileURLToPath(new URL('./packages/vostok.frontend/src', import.meta.url))
    }
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['tests/contract/**/*.spec.ts']
  }
});
