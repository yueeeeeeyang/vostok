# `@vostok/frontend` 接入说明

## 1. 安装

```bash
pnpm add @vostok/frontend vue vue-router pinia naive-ui
```

## 2. 插件初始化

```ts
import { createApp } from 'vue';
import App from './App.vue';
import { VostokFrontendPlugin } from '@vostok/frontend';

createApp(App)
  .use(VostokFrontendPlugin, {
    api: {
      profile: 'default',
      profiles: [{ name: 'default', baseURL: 'https://api.example.com', authScheme: 'bearer' }]
    },
    debug: {
      enableLogger: false
    }
  })
  .mount('#app');
```

## 3. 导入方式

```ts
import {
  VkAdminLayout,
  VkTable,
  VkForm,
  VkSearchBar,
  VkUpload,
  VkSelector,
  VkModalForm,
  VkDrawerForm
} from '@vostok/frontend/components';
```

## 4. API 能力导入

```ts
import { useApiClient } from '@vostok/frontend';
import { createApiClient } from '@vostok/frontend/api';
```

## 5. 组件快速示例

### 5.1 `VkAdminLayout`

```vue
<script setup lang="ts">
import { VkAdminLayout } from '@vostok/frontend/components';
import type {
  VkAppFieldMap,
  VkLanguageOption,
  VkMenuFieldMap,
  VkNotificationItem,
  VkUserMenuItem,
  VkUserProfile
} from '@vostok/frontend';

const menuFieldMap: VkMenuFieldMap = {
  key: 'menuId',
  label: 'menuName',
  path: 'routePath',
  children: 'subMenus'
};

const appFieldMap: VkAppFieldMap = {
  key: 'appId',
  label: 'appName',
  icon: 'iconName',
  path: 'routePath',
  recommended: 'isRecommended'
};

const user: VkUserProfile = {
  id: 'u-admin',
  avatar: '',
  name: '管理员',
  username: 'admin'
};

const userMenus: VkUserMenuItem[] = [
  { key: 'profile', label: '个人中心' },
  { key: 'logout', label: '退出登录' }
];

const languageOptions: VkLanguageOption[] = [
  { value: 'zh-CN', label: '简体中文' },
  { value: 'en-US', label: 'English' }
];

const recentNotifications: VkNotificationItem[] = [
  { key: 'n1', title: '系统通知', time: '刚刚', path: '/message-center' }
];
</script>

<template>
  <VkAdminLayout
    title="业务后台"
    menu-api-path="/menus"
    menu-api-app-key="appKey"
    :menu-field-map="menuFieldMap"
    app-api-path="/apps"
    :app-field-map="appFieldMap"
    :language-options="languageOptions"
    current-language="zh-CN"
    current-theme="light"
    :user="user"
    :user-menus="userMenus"
    :recent-notifications="recentNotifications"
    :notification-count="recentNotifications.length"
    message-center-path="/message-center"
    message-center-button-text="进入消息中心"
  />
</template>
```

### 5.2 `VkTable`

```vue
<script setup lang="ts">
import { VkTable } from '@vostok/frontend/components';
import type { VkTableColumn, VkTableQuery, VkTableResult } from '@vostok/frontend';

interface Row {
  id: string;
  name: string;
}

const columns: VkTableColumn[] = [
  { key: 'id', title: 'ID', sortable: true },
  { key: 'name', title: '姓名', sortable: true }
];

async function fetcher(query: VkTableQuery): Promise<VkTableResult<Row>> {
  void query;
  return { items: [], total: 0 };
}
</script>

<template>
  <VkTable
    :columns="columns"
    :fetcher="fetcher"
    pagination-mode="server"
    row-key="id"
    :show-fuzzy-search="true"
    :show-advanced-search="true"
    :show-column-setting="true"
    :show-column-sort="true"
    :show-custom-actions="true"
  />
</template>
```

### 5.3 `VkForm`

```vue
<script setup lang="ts">
import { VkForm } from '@vostok/frontend/components';
import type { VkFormFieldSchema } from '@vostok/frontend';

const schema: VkFormFieldSchema[] = [
  { key: 'name', label: '姓名', type: 'text', required: true },
  { key: 'age', label: '年龄', type: 'number' }
];

async function submitter(values: Record<string, unknown>): Promise<void> {
  console.log(values);
}
</script>

<template>
  <VkForm :schema="schema" mode="create" :submitter="submitter" />
</template>
```

### 5.4 `VkSearchBar`

```vue
<script setup lang="ts">
import { VkSearchBar } from '@vostok/frontend/components';

const fields = [
  { key: 'name', label: '姓名', placeholder: '请输入姓名' },
  { key: 'email', label: '邮箱', placeholder: '请输入邮箱' }
];

function handleSearch(values: Record<string, string>): void {
  console.log(values);
}
</script>

<template>
  <VkSearchBar :fields="fields" @search="handleSearch" />
</template>
```

### 5.5 `VkSelector`

```vue
<script setup lang="ts">
import { ref } from 'vue';
import { VkSelector } from '@vostok/frontend/components';
import type { VkSelectorOption, VkSelectorValue } from '@vostok/frontend';

const value = ref<VkSelectorValue>(null);
const options: VkSelectorOption[] = [
  { label: '研发中心', value: 'rd' },
  { label: '产品中心', value: 'pm' }
];
</script>

<template>
  <VkSelector
    v-model="value"
    selector-name="部门"
    :options="options"
    option-type="flat"
    mode="single"
    :searchable="true"
    :show-recent="true"
  />
</template>
```

### 5.6 `VkUpload`

```vue
<template>
  <VkUpload />
</template>
```

### 5.7 `VkModalForm` / `VkDrawerForm`

```vue
<template>
  <VkModalForm>
    <div>弹窗内容</div>
  </VkModalForm>

  <VkDrawerForm>
    <div>抽屉内容</div>
  </VkDrawerForm>
</template>
```

## 6. 完整属性与事件文档

组件全量 `props`、事件、插槽、类型说明请查看：

1. [component-spec.md](/Users/yueyang/develop/code/yueyang/vostok/frontend/docs/component-spec.md)
