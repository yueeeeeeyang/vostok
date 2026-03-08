import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';

function getDemoBlock(page: Page, title: string) {
  return page.locator('.demo-block').filter({
    has: page.getByRole('heading', { level: 3, name: title })
  });
}

test.describe('VkLogin 演示页', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/components/vk-login');
    await expect(page.getByRole('heading', { level: 2, name: 'VkLogin 样例' })).toBeVisible();
  });

  test('三种布局模式渲染正确', async ({ page }) => {
    const centerBlock = getDemoBlock(page, '布局1：登录卡片居中（center）');
    const leftBlock = getDemoBlock(page, '布局2：左卡右图（left-card）');
    const rightBlock = getDemoBlock(page, '布局3：右卡左图（right-card）');

    // 居中布局不应出现侧图区域。
    await expect(centerBlock.locator('.vk-login__image-panel')).toHaveCount(0);

    // 左卡右图：卡片面板应位于侧图面板左侧。
    const leftCard = leftBlock.locator('.vk-login__card-panel');
    const leftImage = leftBlock.locator('.vk-login__image-panel');
    await expect(leftImage).toBeVisible();
    const leftCardBox = await leftCard.boundingBox();
    const leftImageBox = await leftImage.boundingBox();
    expect(leftCardBox).not.toBeNull();
    expect(leftImageBox).not.toBeNull();
    expect(leftCardBox!.x).toBeLessThan(leftImageBox!.x);

    // 右卡左图：卡片面板应位于侧图面板右侧。
    const rightCard = rightBlock.locator('.vk-login__card-panel');
    const rightImage = rightBlock.locator('.vk-login__image-panel');
    await expect(rightImage).toBeVisible();
    const rightCardBox = await rightCard.boundingBox();
    const rightImageBox = await rightImage.boundingBox();
    expect(rightCardBox).not.toBeNull();
    expect(rightImageBox).not.toBeNull();
    expect(rightCardBox!.x).toBeGreaterThan(rightImageBox!.x);
  });

  test('注册与记住密码开关生效', async ({ page }) => {
    const centerBlock = getDemoBlock(page, '布局1：登录卡片居中（center）');
    const leftBlock = getDemoBlock(page, '布局2：左卡右图（left-card）');
    const rightBlock = getDemoBlock(page, '布局3：右卡左图（right-card）');

    await expect(centerBlock.getByRole('button', { name: '立即注册' })).toBeVisible();
    await expect(centerBlock.getByText('记住登录状态', { exact: true })).toBeVisible();

    await expect(leftBlock.getByRole('button', { name: '立即注册' })).toHaveCount(0);
    await expect(leftBlock.getByText('7天内免登录', { exact: true })).toBeVisible();

    await expect(rightBlock.locator('.n-checkbox')).toHaveCount(0);
  });

  test('提交事件可输出完整表单值', async ({ page }) => {
    const centerBlock = getDemoBlock(page, '布局1：登录卡片居中（center）');
    await centerBlock.getByPlaceholder('请输入账号').fill('demo_user');
    await centerBlock.locator('input[type="password"]').fill('demo_password');
    await centerBlock.getByText('记住登录状态', { exact: true }).click();
    await centerBlock.getByRole('button', { name: '登录系统' }).click();

    const eventPanel = page.getByTestId('vk-login-event');
    await expect(eventPanel).toContainText('"source": "center"');
    await expect(eventPanel).toContainText('"event": "submit"');
    await expect(eventPanel).toContainText('"username": "demo_user"');
    await expect(eventPanel).toContainText('"password": "demo_password"');
    await expect(eventPanel).toContainText('"remember": true');
  });

  test('注册与忘记密码事件可触发', async ({ page }) => {
    const leftBlock = getDemoBlock(page, '布局2：左卡右图（left-card）');
    const rightBlock = getDemoBlock(page, '布局3：右卡左图（right-card）');
    const eventPanel = page.getByTestId('vk-login-event');

    await leftBlock.getByRole('button', { name: '忘记密码?' }).click();
    await expect(eventPanel).toContainText('"source": "left-card"');
    await expect(eventPanel).toContainText('"event": "forgot-password-click"');

    await rightBlock.getByRole('button', { name: '企业注册' }).click();
    await expect(eventPanel).toContainText('"source": "right-card"');
    await expect(eventPanel).toContainText('"event": "register-click"');
  });

  test('背景图为空时仍可渲染并提交', async ({ page }) => {
    const rightBlock = getDemoBlock(page, '布局3：右卡左图（right-card）');
    const rightRoot = rightBlock.locator('.vk-login');
    await expect(rightRoot).toBeVisible();

    const backgroundImage = await rightRoot.evaluate((node) => window.getComputedStyle(node).backgroundImage);
    expect(backgroundImage.includes('gradient')).toBeTruthy();

    await rightBlock.getByPlaceholder('请输入用户名').fill('empty_bg_user');
    await rightBlock.locator('input[type="password"]').fill('empty_bg_password');
    await rightBlock.getByRole('button', { name: '进入门户' }).click();

    const eventPanel = page.getByTestId('vk-login-event');
    await expect(eventPanel).toContainText('"source": "right-card"');
    await expect(eventPanel).toContainText('"event": "submit"');
    await expect(eventPanel).toContainText('"username": "empty_bg_user"');
  });
});
