<template>
  <section class="page">
    <h2 class="title">用户列表</h2>
    <VkTable
      :columns="columns"
      :fetcher="loadUsers"
      pagination-mode="server"
      row-key="id"
      :custom-action-buttons="customActionButtons"
      :show-fuzzy-search="true"
      :show-advanced-search="false"
      :show-column-setting="true"
      :show-column-sort="true"
      :show-custom-actions="true"
    />
  </section>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';
import { VkTable, useApiClient } from '@vostok/frontend';
import type {
  VkTableActionButton,
  VkTableColumn,
  VkTableQuery,
  VkTableResult
} from '@vostok/frontend';

interface UserRow {
  [key: string]: string;
  id: string;
  name: string;
  email: string;
  status: string;
  createdAt: string;
}

const api = useApiClient();
const router = useRouter();

const columns: VkTableColumn[] = [
  { key: 'id', title: '用户ID', sortable: true },
  { key: 'name', title: '姓名', sortable: true },
  { key: 'email', title: '邮箱', sortable: true },
  { key: 'status', title: '状态', sortable: true },
  { key: 'createdAt', title: '创建时间', sortable: true }
];

const customActionButtons: VkTableActionButton<UserRow>[] = [
  {
    key: 'open-detail',
    label: '查看详情',
    onClick: (row) => {
      void router.push(`/users/${row.id}`);
    }
  }
];

async function loadUsers(query: VkTableQuery): Promise<VkTableResult<UserRow>> {
  const result = await api.query<{ items: UserRow[]; total: number }>('/users', {
    page: query.page,
    pageSize: query.pageSize,
    keyword: query.keyword,
    sortKey: query.sortKey,
    sortOrder: query.sortOrder
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

.title {
  margin: 0 0 12px;
}
</style>
