import { test, expect } from '@playwright/test';

test('playground loads table and form', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByText('Vostok Frontend Playground')).toBeVisible();
  await expect(page.getByRole('heading', { level: 2, name: 'VkTable 样例' })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'Alice', exact: true })).toBeVisible();
});
