import { describe, expect, it } from 'vitest';
import { normalizeVkMenus } from '@pkg/components/vk-admin-layout/vk-menu-normalizer';
import type { VkMenuFieldMap } from '@pkg/components/vk-admin-layout/vk-admin-layout.types';

describe('vk menu normalizer contract', () => {
  it('normalizes default field names', () => {
    const menus = normalizeVkMenus([
      {
        key: 'dashboard',
        label: '仪表盘',
        path: '/dashboard',
        children: [{ key: 'users', label: '用户列表', path: '/users' }]
      }
    ]);

    expect(menus).toHaveLength(1);
    expect(menus[0].key).toBe('dashboard');
    expect(menus[0].children?.[0].path).toBe('/users');
  });

  it('normalizes custom field names by field map', () => {
    const fieldMap: VkMenuFieldMap = {
      key: 'menuId',
      label: 'menuName',
      path: 'routePath',
      children: 'subMenus'
    };
    const menus = normalizeVkMenus(
      {
        data: [
          {
            menuId: 'setting',
            menuName: '系统设置',
            routePath: '/settings',
            subMenus: [{ menuId: 'roles', menuName: '角色管理', routePath: '/settings/roles' }]
          }
        ]
      },
      fieldMap
    );

    expect(menus).toHaveLength(1);
    expect(menus[0].label).toBe('系统设置');
    expect(menus[0].children?.[0].key).toBe('roles');
    expect(menus[0].children?.[0].path).toBe('/settings/roles');
  });
});
