import { createApp } from 'vue';
import App from './App.vue';
import { VostokFrontendPlugin } from '@vostok/frontend';
import type { ApiRequester, NormalizedRequest } from '@vostok/frontend';
import { router } from './router';

const mockUsers = [
  { id: 'u1', name: 'Alice', email: 'alice@example.com', status: 'active', createdAt: '2026-03-01 10:00:00' },
  { id: 'u2', name: 'Bob', email: 'bob@example.com', status: 'inactive', createdAt: '2026-03-02 09:20:00' },
  { id: 'u3', name: 'Cindy', email: 'cindy@example.com', status: 'active', createdAt: '2026-03-02 13:45:00' },
  { id: 'u4', name: 'David', email: 'david@example.com', status: 'inactive', createdAt: '2026-03-03 11:10:00' },
  { id: 'u5', name: 'Elena', email: 'elena@example.com', status: 'active', createdAt: '2026-03-03 16:30:00' }
];

const mockRequester: ApiRequester = async (request: NormalizedRequest) => {
  const url = new URL(request.url);
  if (url.pathname === '/menus') {
    const query = (request.query ?? {}) as Record<string, unknown>;
    const appKey = String(query.appKey ?? 'components');

    const componentMenus = [
      { menuId: 'vk-table', menuName: 'VkTable', iconName: 'layers', routePath: '/components/vk-table' },
      { menuId: 'vk-form', menuName: 'VkForm', iconName: 'layers', routePath: '/components/vk-form' },
      { menuId: 'vk-search-bar', menuName: 'VkSearchBar', iconName: 'layers', routePath: '/components/vk-search-bar' },
      { menuId: 'vk-upload', menuName: 'VkUpload', iconName: 'layers', routePath: '/components/vk-upload' },
      { menuId: 'vk-selector', menuName: 'VkSelector', iconName: 'layers', routePath: '/components/vk-selector' },
      { menuId: 'vk-modal-form', menuName: 'VkModalForm', iconName: 'layers', routePath: '/components/vk-modal-form' },
      { menuId: 'vk-drawer-form', menuName: 'VkDrawerForm', iconName: 'layers', routePath: '/components/vk-drawer-form' },
      { menuId: 'vk-admin-layout', menuName: 'VkAdminLayout', iconName: 'layers', routePath: '/components/vk-admin-layout' }
    ];

    const pageMenus = [
      { menuId: 'message-center', menuName: '消息中心', iconName: 'layers', routePath: '/message-center' },
      { menuId: 'workbench', menuName: '工作台', iconName: 'layers', routePath: '/pages/workbench' }
    ];

    return {
      code: 0,
      message: 'ok',
      data: appKey === 'pages' ? pageMenus : componentMenus
    };
  }

  if (url.pathname === '/apps') {
    return {
      code: 0,
      message: 'ok',
      data: [
        { appId: 'components', appName: '组件', iconName: 'layers', routePath: '/components/vk-table', isRecommended: true },
        { appId: 'pages', appName: '页面', iconName: 'apps', routePath: '/message-center', isRecommended: true }
      ]
    };
  }

  if (url.pathname === '/users') {
    const query = (request.query ?? {}) as Record<string, unknown>;
    const page = Number(query.page ?? 1);
    const pageSize = Number(query.pageSize ?? 20);
    const keyword = String(query.keyword ?? '').trim().toLowerCase();
    const email = String(query.email ?? '').trim().toLowerCase();
    const status = String(query.status ?? '').trim().toLowerCase();
    const createdAtRange = query.createdAtRange as [number, number] | null | undefined;
    const sortKey = String(query.sortKey ?? '').trim();
    const sortOrder = String(query.sortOrder ?? '').trim();

    let filtered = [...mockUsers];

    if (keyword) {
      filtered = filtered.filter((item) =>
        `${item.name} ${item.email}`.toLowerCase().includes(keyword)
      );
    }
    if (email) {
      filtered = filtered.filter((item) => item.email.toLowerCase().includes(email));
    }
    if (status) {
      filtered = filtered.filter((item) => item.status.toLowerCase() === status);
    }
    if (Array.isArray(createdAtRange) && createdAtRange.length === 2) {
      const [start, end] = createdAtRange;
      filtered = filtered.filter((item) => {
        const current = new Date(item.createdAt.replace(' ', 'T')).getTime();
        return current >= start && current <= end;
      });
    }

    if (sortKey && (sortOrder === 'ascend' || sortOrder === 'descend')) {
      filtered.sort((a, b) => {
        const left = String((a as Record<string, unknown>)[sortKey] ?? '');
        const right = String((b as Record<string, unknown>)[sortKey] ?? '');
        return sortOrder === 'ascend' ? left.localeCompare(right) : right.localeCompare(left);
      });
    }

    const start = (page - 1) * pageSize;
    const end = start + pageSize;

    return {
      code: 0,
      message: 'ok',
      data: {
        items: filtered.slice(start, end),
        total: filtered.length
      }
    };
  }

  if (url.pathname.startsWith('/users/')) {
    const id = url.pathname.split('/').at(-1) ?? 'u1';
    const current = mockUsers.find((item) => item.id === id);
    return {
      code: 0,
      message: 'ok',
      data:
        current ??
        { id, name: `User-${id}`, email: `${id}@example.com`, status: 'active', createdAt: '2026-03-03 00:00:00' }
    };
  }

  return {
    code: 0,
    message: 'ok',
    data: {}
  };
};

createApp(App)
  .use(router)
  .use(VostokFrontendPlugin, {
    api: {
      profile: 'playground',
      profiles: [{ name: 'playground', baseURL: 'http://playground.local', authScheme: 'none' }],
      requester: mockRequester
    },
    debug: {
      enableLogger: true
    }
  })
  .mount('#app');
