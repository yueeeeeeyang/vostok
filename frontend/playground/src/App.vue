<template>
  <main class="page">
    <h1>Vostok Frontend Playground</h1>
    <p class="desc">该页面演示 <code>@vostok/frontend</code> 当前导出的全部组件。</p>

    <section class="demo-section">
      <h2>ProSearchBar</h2>
      <ProSearchBar :fields="searchFields" @search="onSearch" />
      <p class="hint">当前检索条件：{{ JSON.stringify(searchValues) }}</p>
    </section>

    <section class="demo-section">
      <h2>ProTable</h2>
      <ProTable
        :key="tableKey"
        :columns="columns"
        :fetcher="loadUsers"
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
    </section>

    <section class="demo-section">
      <h2>ProForm</h2>
      <ProForm
        :schema="formSchema"
        :initial-values="selectedUser"
        :submitter="saveUser"
        mode="edit"
      />
    </section>

    <section class="demo-section">
      <h2>ProUpload</h2>
      <ProUpload />
    </section>

    <section class="demo-section">
      <h2>ProModalForm</h2>
      <ProModalForm>
        <div class="container-box">
          <p class="hint">当前组件为占位实现，这里演示其容器插槽能力。</p>
          <ProForm
            :schema="miniSchema"
            :initial-values="{ name: 'Modal User' }"
            :submitter="submitMiniForm"
            mode="create"
          />
        </div>
      </ProModalForm>
    </section>

    <section class="demo-section">
      <h2>ProDrawerForm</h2>
      <ProDrawerForm>
        <div class="container-box">
          <p class="hint">当前组件为占位实现，这里演示其容器插槽能力。</p>
          <ProForm
            :schema="miniSchema"
            :initial-values="{ name: 'Drawer User' }"
            :submitter="submitMiniForm"
            mode="create"
          />
        </div>
      </ProDrawerForm>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import {
  ProDrawerForm,
  ProForm,
  ProModalForm,
  ProSearchBar,
  ProTable,
  ProUpload,
  useApiClient
} from '@vostok/frontend';
import type {
  ProAdvancedSearchField,
  ProFormFieldSchema,
  ProSearchField,
  ProTableActionButton,
  ProTableColumn,
  ProTableQuery,
  ProTableResult,
  ProToolbarAction
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
const selectedUser = ref<Record<string, unknown>>({
  id: 'u1',
  name: 'Alice',
  email: 'alice@example.com',
  status: 'active',
  createdAt: '2026-03-01 10:00:00'
});
const searchValues = ref<Record<string, string>>({});
const tableKey = ref(0);

const columns: ProTableColumn[] = [
  { key: 'id', title: 'ID' },
  { key: 'name', title: 'Name' },
  { key: 'email', title: 'Email' },
  { key: 'status', title: 'Status' },
  { key: 'createdAt', title: 'Created At' }
];

const formSchema: ProFormFieldSchema[] = [
  { key: 'name', label: 'Name', type: 'text', required: true },
  { key: 'email', label: 'Email', type: 'text', required: true },
  { key: 'status', label: 'Status', type: 'text', required: true }
];

const miniSchema: ProFormFieldSchema[] = [
  { key: 'name', label: '名称', type: 'text', required: true }
];

const searchFields: ProSearchField[] = [
  { key: 'keyword', label: '关键词', placeholder: '输入姓名或邮箱' },
  { key: 'status', label: '状态', placeholder: 'active / inactive' }
];

const advancedSearchFields: ProAdvancedSearchField[] = [
  { key: 'email', label: '邮箱', type: 'input', placeholder: '按邮箱模糊匹配' },
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

const customActionButtons: ProTableActionButton<UserItem>[] = [
  {
    key: 'view',
    label: '查看',
    onClick: (row) => {
      selectedUser.value = row;
    }
  },
  {
    key: 'activate',
    label: '设为激活',
    show: (row) => row.status !== 'active',
    onClick: (row) => {
      selectedUser.value = { ...row, status: 'active' };
    }
  },
  {
    key: 'disable',
    label: '禁用',
    show: (row) => row.status !== 'inactive',
    onClick: (row) => {
      selectedUser.value = { ...row, status: 'inactive' };
    }
  }
];

const toolbarActions: ProToolbarAction[] = [
  {
    key: 'action1',
    label: 'action1',
    onClick: () => {
      selectedUser.value = { ...selectedUser.value, action: 'action1' };
    }
  },
  {
    key: 'action2',
    label: 'action2',
    onClick: () => {
      selectedUser.value = { ...selectedUser.value, action: 'action2' };
    }
  }
];

function onSearch(values: Record<string, string>): void {
  searchValues.value = values;
  tableKey.value += 1;
}

async function loadUsers(query: ProTableQuery): Promise<ProTableResult<UserItem>> {
  const res = await api.query<{ items: UserItem[]; total: number }>('/users', {
    page: query.page,
    pageSize: query.pageSize,
    keyword: query.keyword || searchValues.value.keyword,
    sortKey: query.sortKey,
    sortOrder: query.sortOrder,
    ...query.advancedFilters
  });
  const first = res.data.items[0];
  if (first) {
    selectedUser.value = first;
  }
  return { items: res.data.items, total: res.data.total };
}

async function saveUser(values: Record<string, unknown>): Promise<void> {
  selectedUser.value = values;
}

async function submitMiniForm(_values: Record<string, unknown>): Promise<void> {
  return Promise.resolve();
}

onMounted(async () => {
  const detail = await api.query<UserItem>('/users/u1');
  selectedUser.value = detail.data;
});
</script>

<style scoped>
.page {
  max-width: 1000px;
  margin: 24px auto;
  padding: 0 16px 40px;
}
h1 {
  margin: 0 0 8px;
}
.desc {
  margin: 0 0 16px;
  color: #4b5563;
}
.demo-section {
  margin-top: 20px;
}
.hint {
  margin: 8px 0 0;
  color: #6b7280;
  font-size: 13px;
}
.container-box {
  border: 1px dashed #d1d5db;
  border-radius: 8px;
  padding: 12px;
}
</style>
