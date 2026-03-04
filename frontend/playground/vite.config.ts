import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { resolve } from 'node:path';

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: [
      {
        find: '@vostok/frontend/components',
        replacement: resolve(__dirname, '../packages/vostok.frontend/src/components/index.ts')
      },
      {
        find: '@vostok/frontend/api',
        replacement: resolve(__dirname, '../packages/vostok.frontend/src/api/index.ts')
      },
      {
        find: '@vostok/frontend/adapters',
        replacement: resolve(__dirname, '../packages/vostok.frontend/src/adapters/index.ts')
      },
      {
        find: '@vostok/frontend',
        replacement: resolve(__dirname, '../packages/vostok.frontend/src/index.ts')
      }
    ]
  },
  server: {
    host: '127.0.0.1',
    port: 5175
  }
});
