import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { resolve } from 'node:path';

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@vostok/frontend': resolve(__dirname, '../packages/vostok.frontend/src/index.ts'),
      '@vostok/frontend/components': resolve(__dirname, '../packages/vostok.frontend/src/components/index.ts'),
      '@vostok/frontend/api': resolve(__dirname, '../packages/vostok.frontend/src/api/index.ts'),
      '@vostok/frontend/adapters': resolve(__dirname, '../packages/vostok.frontend/src/adapters/index.ts')
    }
  },
  server: {
    host: '127.0.0.1',
    port: 5175
  }
});
