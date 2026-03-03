import { test, expect } from '@playwright/test';

test('playground page title visible', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Vostok Frontend Playground');
});
