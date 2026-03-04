# `@vostok/frontend` 组件使用说明（完整）

本文档覆盖当前库内全部组件，包含：

1. 组件用途
2. 最小可用示例
3. 全量 `props`（类型、默认值、说明）
4. 事件与插槽

## 1. 统一导入方式

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

## 2. `VkTable`

### 2.1 用途

通用数据表格组件，支持：

1. 模糊搜索
2. 高级搜索（输入框/选择器/时间范围）
3. 展示列调整
4. 列头排序
5. 自定义操作列按钮
6. 服务端分页

### 2.2 最小示例

```vue
<script setup lang="ts">
import { VkTable } from '@vostok/frontend/components';
import type { VkTableColumn, VkTableQuery, VkTableResult } from '@vostok/frontend';

interface UserRow {
  id: string;
  name: string;
}

const columns: VkTableColumn[] = [
  { key: 'id', title: 'ID' },
  { key: 'name', title: '姓名' }
];

async function fetcher(query: VkTableQuery): Promise<VkTableResult<UserRow>> {
  void query;
  return {
    items: [{ id: 'u1', name: '管理员' }],
    total: 1
  };
}
</script>

<template>
  <VkTable
    :columns="columns"
    :fetcher="fetcher"
    pagination-mode="server"
    row-key="id"
  />
</template>
```

### 2.3 Props

| 属性 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `columns` | `VkTableColumn[]` | 是 | - | 列定义 |
| `fetcher` | `VkTableFetcher<T>` | 是 | - | 数据请求函数，入参为 `VkTableQuery` |
| `paginationMode` | `'server' \| 'client'` | 是 | - | 分页模式（当前主要用于服务端分页） |
| `rowKey` | `string` | 是 | - | 行唯一键字段名 |
| `searchPlaceholder` | `string` | 否 | `'请输入关键词'` | 模糊搜索占位文案 |
| `advancedSearchFields` | `VkAdvancedSearchField[]` | 否 | `[]` | 高级搜索字段配置 |
| `customActionButtons` | `VkTableActionButton<T>[]` | 否 | `[]` | 操作列按钮配置 |
| `defaultPageSize` | `number` | 否 | `20` | 默认每页条数 |
| `showFuzzySearch` | `boolean` | 否 | `false` | 是否显示模糊搜索框 |
| `showAdvancedSearch` | `boolean` | 否 | `false` | 是否显示高级搜索按钮和面板 |
| `showColumnSetting` | `boolean` | 否 | `false` | 是否显示展示列设置 |
| `showColumnSort` | `boolean` | 否 | `false` | 是否启用列头排序 |
| `showCustomActions` | `boolean` | 否 | `false` | 是否展示“操作”列 |
| `toolbarActions` | `VkToolbarAction[]` | 否 | `[]` | 工具栏自定义按钮 |

### 2.4 相关类型说明

1. `VkTableColumn`
   - `key: string`
   - `title: string`
   - `width?: number`
   - `sortable?: boolean`
   - `visible?: boolean`
   - `render?: (row) => string | number`
2. `VkTableQuery`
   - `page: number`
   - `pageSize: number`
   - `keyword?: string`
   - `sortKey?: string`
   - `sortOrder?: 'ascend' | 'descend' | null`
   - `sorter?: string`（兼容格式：`${sortKey}:${sortOrder}`）
   - `advancedFilters?: Record<string, VkAdvancedFilterValue>`
3. `VkAdvancedSearchField`
   - `type?: 'input' | 'select' | 'datetime_range'`
   - `options?: VkAdvancedSearchOption[]`（仅 `select` 使用）

### 2.5 事件与插槽

1. 组件不对外暴露自定义事件（通过 `fetcher` 与按钮回调驱动）。
2. 组件不定义业务插槽。

## 3. `VkForm`

### 3.1 用途

基于 Schema 的表单组件，支持：

1. 创建/编辑/只读模式
2. 必填校验
3. 自定义规则校验

### 3.2 最小示例

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

### 3.3 Props

| 属性 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `schema` | `VkFormFieldSchema[]` | 是 | - | 表单字段定义 |
| `submitter` | `(values) => Promise<void>` | 是 | - | 提交函数 |
| `mode` | `'create' \| 'edit' \| 'view'` | 是 | - | 表单模式；`view` 下禁用输入并隐藏提交按钮 |
| `initialValues` | `Record<string, unknown>` | 否 | `{}` | 初始值 |
| `rules` | `VkFormRule[]` | 否 | `[]` | 自定义校验规则 |

### 3.4 相关类型说明

1. `VkFormFieldSchema`
   - `key: string`
   - `label: string`
   - `type: 'text' | 'number' | 'textarea'`
   - `placeholder?: string`
   - `required?: boolean`
2. `VkFormRule`
   - `key: string`
   - `validator: (value, formValues) => string | null`

### 3.5 事件与插槽

1. 组件不对外暴露自定义事件。
2. 组件不定义业务插槽。

## 4. `VkSearchBar`

### 4.1 用途

轻量检索条，按字段配置渲染多个输入框并统一触发查询。

### 4.2 最小示例

```vue
<script setup lang="ts">
import { VkSearchBar } from '@vostok/frontend/components';
import type { VkSearchField } from '@vostok/frontend';

const fields: VkSearchField[] = [
  { key: 'name', label: '姓名', placeholder: '请输入姓名' },
  { key: 'email', label: '邮箱', placeholder: '请输入邮箱' }
];

function onSearch(values: Record<string, string>): void {
  console.log(values);
}
</script>

<template>
  <VkSearchBar :fields="fields" @search="onSearch" />
</template>
```

### 4.3 Props

| 属性 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `fields` | `VkSearchField[]` | 是 | - | 查询字段配置 |

### 4.4 Events

| 事件名 | 参数类型 | 说明 |
| --- | --- | --- |
| `search` | `Record<string, string>` | 点击“查询”按钮触发，返回当前输入值 |

### 4.5 Slots

无。

## 5. `VkSelector`

### 5.1 用途

统一选择器组件，支持：

1. 单选/多选切换
2. 搜索过滤
3. 最近选择列表
4. 普通选项选择与树形结构选择
5. 动态选择框名称

### 5.2 最小示例

```vue
<script setup lang="ts">
import { ref } from 'vue';
import { VkSelector } from '@vostok/frontend/components';
import type {
  VkSelectorOption,
  VkSelectorTreeOption,
  VkSelectorValue
} from '@vostok/frontend';

const value = ref<VkSelectorValue>(null);

const options: VkSelectorOption[] = [
  { label: '研发中心', value: 'rd' },
  { label: '产品中心', value: 'pm' }
];

const treeOptions: VkSelectorTreeOption[] = [
  {
    label: '总部',
    key: 'root',
    children: [
      { label: '前端组', key: 'fe' },
      { label: '后端组', key: 'be' }
    ]
  }
];
</script>

<template>
  <VkSelector
    v-model="value"
    selector-name="部门"
    :options="options"
    :tree-options="treeOptions"
    option-type="flat"
    mode="single"
    :searchable="true"
    :show-recent="true"
  />
</template>
```

### 5.3 Props

| 属性 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `modelValue` | `VkSelectorValue` | 否 | `null` | 当前选中值 |
| `mode` | `'single' \| 'multiple'` | 否 | `'single'` | 单选或多选模式 |
| `optionType` | `'flat' \| 'tree'` | 否 | `'flat'` | 普通选项或树形选项 |
| `selectorName` | `string` | 否 | `'选项'` | 选择框名称，影响标签与占位文案 |
| `options` | `VkSelectorOption[]` | 否 | `[]` | 普通选项列表 |
| `treeOptions` | `VkSelectorTreeOption[]` | 否 | `[]` | 树形选项列表 |
| `searchable` | `boolean` | 否 | `true` | 是否启用搜索 |
| `clearable` | `boolean` | 否 | `true` | 是否显示清除按钮 |
| `disabled` | `boolean` | 否 | `false` | 是否禁用 |
| `showRecent` | `boolean` | 否 | `true` | 是否展示最近选择区 |
| `recentLimit` | `number` | 否 | `5` | 最近选择数量上限 |
| `recentTitle` | `string` | 否 | `'最近选择'` | 最近选择标题 |
| `recentStorageKey` | `string` | 否 | `'vk-selector-recent'` | 本地存储键名 |

### 5.4 Events

| 事件名 | 参数类型 | 说明 |
| --- | --- | --- |
| `update:modelValue` | `VkSelectorValue` | 选中值变化时触发 |
| `change` | `VkSelectorValue` | 与 `update:modelValue` 同步触发，便于业务监听 |
| `recent-select` | `VkSelectorRecentItem` | 点击最近选择项时触发 |

### 5.5 Slots

无。

## 6. `VkUpload`

### 6.1 用途

文件选择组件（当前版本只负责选择，不自动上传）。

### 6.2 最小示例

```vue
<template>
  <VkUpload />
</template>
```

### 6.3 Props / Events / Slots

1. 当前版本不暴露 `props`。
2. 当前版本不暴露自定义事件。
3. 当前版本不定义插槽。

## 7. `VkModalForm`

### 7.1 用途

弹窗表单容器占位组件（当前版本仅透传内容插槽）。

### 7.2 最小示例

```vue
<template>
  <VkModalForm>
    <div>这里放业务表单</div>
  </VkModalForm>
</template>
```

### 7.3 Props / Events / Slots

1. 当前版本不暴露 `props`。
2. 当前版本不暴露自定义事件。
3. 支持默认插槽（`default`）。

## 8. `VkDrawerForm`

### 8.1 用途

抽屉表单容器占位组件（当前版本仅透传内容插槽）。

### 8.2 最小示例

```vue
<template>
  <VkDrawerForm>
    <div>这里放业务表单</div>
  </VkDrawerForm>
</template>
```

### 8.3 Props / Events / Slots

1. 当前版本不暴露 `props`。
2. 当前版本不暴露自定义事件。
3. 支持默认插槽（`default`）。

## 9. `VkAdminLayout`

### 9.1 用途

后台通用布局组件，包含：

1. 顶部 Header（应用切换、全局搜索、消息、语言、主题、用户区）
2. 左侧菜单（支持按应用切换动态拉取）
3. 中央内容区（默认 `RouterView`）

### 9.2 最小示例

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
  { key: 'n1', title: '消息1', time: '刚刚', path: '/message-center' }
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
    :recommend-app-count="2"
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

### 9.3 Props

| 属性 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `title` | `string` | 否 | - | 顶部标题 |
| `menuFetcher` | `(app?: VkHeaderAppItem \| null) => Promise<unknown>` | 否 | - | 自定义菜单获取函数，优先级高于内置 API |
| `menuApiPath` | `string` | 否 | - | 内置菜单接口路径 |
| `menuApiQuery` | `Record<string, unknown>` | 否 | `{}` | 菜单查询参数 |
| `menuApiAppKey` | `string` | 否 | `'appKey'` | 菜单接口中应用标识字段名 |
| `menuFieldMap` | `VkMenuFieldMap` | 否 | - | 菜单字段映射 |
| `appFetcher` | `() => Promise<unknown>` | 否 | - | 自定义应用列表获取函数 |
| `appApiPath` | `string` | 否 | - | 内置应用接口路径 |
| `appApiQuery` | `Record<string, unknown>` | 否 | `{}` | 应用查询参数 |
| `appFieldMap` | `VkAppFieldMap` | 否 | - | 应用字段映射 |
| `recommendAppCount` | `number` | 否 | `2` | Header 外显推荐应用数量 |
| `defaultExpandedKeys` | `string[]` | 否 | `[]` | 默认展开菜单键 |
| `defaultSelectedKey` | `string` | 否 | - | 默认选中菜单键 |
| `showHeader` | `boolean` | 否 | `true` | 是否显示 Header |
| `headerHeight` | `number` | 否 | `64` | Header 高度（px） |
| `menuWidth` | `number` | 否 | `260` | 左侧菜单展开宽度（px） |
| `menuCollapsedWidth` | `number` | 否 | `64` | 左侧菜单折叠宽度（px） |
| `quickApps` | `VkHeaderAppItem[]` | 否 | - | 静态推荐应用兜底数据 |
| `allApps` | `VkHeaderAppItem[]` | 否 | - | 静态应用全量兜底数据 |
| `languageOptions` | `VkLanguageOption[]` | 否 | - | 语言选项 |
| `currentLanguage` | `string` | 否 | - | 当前语言 |
| `currentTheme` | `'light' \| 'dark'` | 否 | - | 当前主题 |
| `notificationCount` | `number` | 否 | `0` | 通知角标数量 |
| `recentNotifications` | `VkNotificationItem[]` | 否 | - | 最近消息（面板最多显示 5 条） |
| `messageCenterPath` | `string` | 否 | - | “进入消息中心”按钮路由 |
| `messageCenterButtonText` | `string` | 否 | - | 消息中心按钮文案 |
| `user` | `VkUserProfile` | 否 | - | 用户对象：`id/avatar/name/username` |
| `userMenus` | `VkUserMenuItem[]` | 否 | - | 用户下拉菜单 |
| `collapsed` | `boolean` | 否 | `false` | 侧栏折叠状态（受控） |
| `collapsible` | `boolean` | 否 | `true` | 是否显示折叠按钮 |

### 9.4 Events

| 事件名 | 参数类型 | 说明 |
| --- | --- | --- |
| `menu-click` | `VkMenuItem` | 点击菜单触发 |
| `menu-loaded` | `VkMenuItem[]` | 菜单加载成功触发 |
| `menu-load-error` | `unknown` | 菜单加载失败触发 |
| `update:collapsed` | `boolean` | 折叠状态变化触发 |
| `quick-app-click` | `VkHeaderAppItem` | 点击外显推荐应用触发 |
| `all-app-click` | `VkHeaderAppItem` | 点击全部应用面板内应用触发 |
| `global-search` | `string` | 回车触发全局搜索 |
| `notification-click` | `void` | 点击通知按钮触发 |
| `notification-item-click` | `VkNotificationItem` | 点击消息项触发 |
| `message-center-click` | `void` | 点击“进入消息中心”触发 |
| `language-change` | `string` | 切换语言触发 |
| `theme-change` | `'light' \| 'dark'` | 切换主题触发 |
| `user-menu-click` | `string` | 点击用户菜单项触发（返回菜单 key） |

### 9.5 Slots

| 插槽名 | 说明 |
| --- | --- |
| `default` | 覆盖内容区；未提供时内部使用 `RouterView` |

### 9.6 字段映射说明

1. 菜单映射 `VkMenuFieldMap`
   - `key/label/path/icon/disabled/children`
2. 应用映射 `VkAppFieldMap`
   - `key/label/icon/path/recommended`

## 10. 使用建议

1. 业务项目优先通过 `props` 传入业务数据，避免在组件内部固化业务语义。
2. 后端字段不统一时，优先用 `menuFieldMap` 与 `appFieldMap` 适配。
3. 统一通过 `@vostok/frontend` 导出的类型约束调用，减少文档与实现偏差。
