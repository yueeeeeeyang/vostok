import { expect, test } from '@playwright/test';

test('vk admin layout loads menu and supports route navigation', async ({ page }) => {
  await page.goto('/');
  const sideMenu = page.locator('.n-layout-sider');

  await expect(page.getByRole('heading', { level: 1 })).toContainText('Vostok Frontend Playground');
  await expect(sideMenu.getByText('组件样例与使用方式', { exact: true })).toBeVisible();
  await expect(sideMenu.getByText('VkTable', { exact: true })).toBeVisible();
  await expect(sideMenu.getByText('VkForm', { exact: true })).toBeVisible();

  await sideMenu.getByText('VkForm', { exact: true }).click();
  await expect(page).toHaveURL(/\/components\/vk-form$/);
  await expect(page.getByRole('heading', { level: 2, name: 'VkForm 样例' })).toBeVisible();

  await sideMenu.getByText('VkAdminLayout', { exact: true }).click();
  await expect(page).toHaveURL(/\/components\/vk-admin-layout$/);
  await expect(page.getByRole('heading', { level: 2, name: 'VkAdminLayout 使用方式' })).toBeVisible();
});
