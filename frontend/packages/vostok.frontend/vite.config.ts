import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import dts from 'vite-plugin-dts';
import { resolve } from 'node:path';

export default defineConfig({
  plugins: [
    vue(),
    dts({
      tsconfigPath: './tsconfig.json',
      include: ['src'],
      insertTypesEntry: false,
      rollupTypes: false
    })
  ],
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    cssCodeSplit: false,
    lib: {
      entry: resolve(__dirname, 'src/index.ts'),
      formats: ['es', 'cjs'],
      fileName: (format) => (format === 'es' ? 'index.mjs' : 'index.cjs')
    },
    rollupOptions: {
      external: ['vue', 'vue-router', 'pinia', 'naive-ui', 'axios'],
      input: {
        index: resolve(__dirname, 'src/index.ts'),
        components: resolve(__dirname, 'src/components/index.ts'),
        api: resolve(__dirname, 'src/api/index.ts'),
        adapters: resolve(__dirname, 'src/adapters/index.ts')
      },
      output: [
        {
          format: 'es',
          entryFileNames: (chunk) => (chunk.name === 'index' ? 'index.mjs' : `${chunk.name}/index.mjs`),
          assetFileNames: (assetInfo) => {
            if ((assetInfo.name ?? '').includes('.css')) {
              return 'style.css';
            }
            return 'assets/[name]-[hash][extname]';
          }
        },
        {
          format: 'cjs',
          exports: 'named',
          entryFileNames: (chunk) => (chunk.name === 'index' ? 'index.cjs' : `${chunk.name}/index.cjs`),
          assetFileNames: (assetInfo) => {
            if ((assetInfo.name ?? '').includes('.css')) {
              return 'style.css';
            }
            return 'assets/[name]-[hash][extname]';
          }
        }
      ]
    }
  }
});
