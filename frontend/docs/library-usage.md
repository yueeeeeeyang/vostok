# `@vostok/frontend` 使用说明

## 安装

```bash
pnpm add @vostok/frontend vue vue-router pinia naive-ui
```

## 初始化

```ts
import { createApp } from 'vue';
import App from './App.vue';
import { VostokFrontendPlugin } from '@vostok/frontend';

createApp(App)
  .use(VostokFrontendPlugin, {
    api: {
      profile: 'default',
      profiles: [{ name: 'default', baseURL: 'https://api.example.com', authScheme: 'bearer' }]
    }
  })
  .mount('#app');
```

## 导入组件

```ts
import { ProTable, ProForm } from '@vostok/frontend/components';
```

## ProTable 使用示例（含全部开关）

```ts
import type {
  ProAdvancedSearchField,
  ProTableActionButton,
  ProTableColumn,
  ProTableQuery,
  ProTableResult,
  ProToolbarAction
} from '@vostok/frontend';

interface UserRow {
  id: string;
  name: string;
  email: string;
  status: string;
}

const columns: ProTableColumn[] = [
  { key: 'id', title: 'ID', sortable: true },
  { key: 'name', title: '姓名', sortable: true },
  { key: 'email', title: '邮箱', sortable: true },
  { key: 'status', title: '状态', sortable: true }
];

const advancedSearchFields: ProAdvancedSearchField[] = [
  { key: 'email', label: '邮箱', type: 'input', placeholder: '按邮箱筛选' },
  {
    key: 'status',
    label: '状态',
    type: 'select',
    options: [
      { label: '激活', value: 'active' },
      { label: '未激活', value: 'inactive' }
    ]
  },
  { key: 'createdAtRange', label: '创建时间', type: 'datetime_range' }
];

const customActionButtons: ProTableActionButton<UserRow>[] = [
  { key: 'view', label: '查看', onClick: (row) => console.log(row) }
];

const toolbarActions: ProToolbarAction[] = [
  { key: 'action1', label: 'action1', onClick: () => console.log('action1') },
  { key: 'action2', label: 'action2', onClick: () => console.log('action2') }
];

async function fetchUsers(query: ProTableQuery): Promise<ProTableResult<UserRow>> {
  // query 内会包含 keyword、advancedFilters、sortKey、sortOrder 等参数
  // createdAtRange 类型为 [number, number]，表示时间戳区间
  return { items: [], total: 0 };
}
```

```vue
<ProTable
  :columns="columns"
  :fetcher="fetchUsers"
  pagination-mode="server"
  row-key="id"
  :toolbar-actions="toolbarActions"
  :advanced-search-fields="advancedSearchFields"
  :custom-action-buttons="customActionButtons"
  :show-fuzzy-search="true"
  :show-advanced-search="true"
  :show-column-setting="true"
  :show-column-sort="true"
  :show-custom-actions="true"
/>
```

## 导入 API 能力

```ts
import { useApiClient } from '@vostok/frontend';
import { createApiClient } from '@vostok/frontend/api';
```
