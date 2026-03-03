import { test, expect } from '@playwright/test';

test('playground loads table and form', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByText('Vostok Frontend Playground')).toBeVisible();
  await expect(page.getByText('Alice')).toBeVisible();
});
