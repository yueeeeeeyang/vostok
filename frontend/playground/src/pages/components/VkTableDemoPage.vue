<template>
  <main class="page">
    <h2>VkTable 样例</h2>
    <p class="desc">支持服务端分页、模糊搜索、高级搜索、列设置、排序与自定义操作。</p>

    <VkTable
      :columns="columns"
      :fetcher="fetchUsers"
      pagination-mode="server"
      row-key="id"
      :advanced-search-fields="advancedSearchFields"
      :toolbar-actions="toolbarActions"
      :custom-action-buttons="customActionButtons"
      :show-fuzzy-search="true"
      :show-advanced-search="true"
      :show-column-setting="true"
      :show-column-sort="true"
      :show-custom-actions="true"
    />

    <section class="usage">
      <h3>使用方式</h3>
      <pre><code>import { VkTable } from '@vostok/frontend';

&lt;VkTable
  :columns="columns"
  :fetcher="fetcher"
  pagination-mode="server"
  row-key="id"
/&gt;</code></pre>
    </section>
  </main>
</template>

<script setup lang="ts">
import { VkTable, useApiClient } from '@vostok/frontend';
import type {
  VkAdvancedSearchField,
  VkTableActionButton,
  VkTableColumn,
  VkTableQuery,
  VkTableResult,
  VkToolbarAction
} from '@vostok/frontend';

interface UserItem {
  [key: string]: string;
  id: string;
  name: string;
  email: string;
  status: string;
  createdAt: string;
}

const api = useApiClient();

const columns: VkTableColumn[] = [
  { key: 'id', title: 'ID', sortable: true },
  { key: 'name', title: '姓名', sortable: true },
  { key: 'email', title: '邮箱', sortable: true },
  { key: 'status', title: '状态', sortable: true },
  { key: 'createdAt', title: '创建时间', sortable: true }
];

const advancedSearchFields: VkAdvancedSearchField[] = [
  { key: 'email', label: '邮箱', type: 'input', placeholder: '输入邮箱关键字' },
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

const toolbarActions: VkToolbarAction[] = [
  { key: 'action1', label: 'action1', onClick: () => undefined },
  { key: 'action2', label: 'action2', onClick: () => undefined }
];

const customActionButtons: VkTableActionButton<UserItem>[] = [
  { key: 'view', label: '查看', onClick: () => undefined }
];

async function fetchUsers(query: VkTableQuery): Promise<VkTableResult<UserItem>> {
  // 演示页面统一通过 ApiClient 走后端接口，保持真实接入方式。
  const result = await api.query<{ items: UserItem[]; total: number }>('/users', {
    page: query.page,
    pageSize: query.pageSize,
    keyword: query.keyword,
    sortKey: query.sortKey,
    sortOrder: query.sortOrder,
    ...query.advancedFilters
  });
  return {
    items: result.data.items,
    total: result.data.total
  };
}
</script>

<style scoped>
.page {
  max-width: 1200px;
  margin: 0 auto;
}

.desc {
  color: #4b5563;
}

.usage {
  margin-top: 16px;
}

pre {
  margin: 0;
  padding: 12px;
  border-radius: 8px;
  background: #111827;
  color: #e5e7eb;
  overflow: auto;
}
</style>
