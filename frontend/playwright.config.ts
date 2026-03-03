import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests/e2e',
  use: {
    baseURL: 'http://127.0.0.1:5175'
  },
  webServer: {
    command: 'pnpm --filter playground dev --host 127.0.0.1 --port 5175',
    url: 'http://127.0.0.1:5175',
    reuseExistingServer: true,
    timeout: 120000
  }
});
