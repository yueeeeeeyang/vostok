# 组件规范

首批组件统一基于 Naive UI 构建，保证风格一致与主题可扩展。

## ProTable

- 支持服务端与本地两种分页模式。
- 通过 `fetcher` 与类型化列定义驱动渲染。
- 内置统一状态：`loading`、`empty`、`error`。
- 支持以下能力并可通过开关独立控制：
  - 模糊搜索框（`showFuzzySearch`）
  - 高级搜索展开区（`showAdvancedSearch` + `advancedSearchFields`）
  - 展示列调整（`showColumnSetting`）
  - 列头排序（`showColumnSort`）
  - 自定义操作按钮（`showCustomActions` + `customActionButtons`）

### ProTable 关键参数

- `columns`：列定义，支持 `sortable`、`visible`、`render`。
- `fetcher`：数据加载函数，接收统一查询参数 `ProTableQuery`。
- `paginationMode`：`server | client`。
- `rowKey`：行唯一键。
- `searchPlaceholder`：模糊搜索框占位文案。
- `toolbarActions`：工具栏自定义按钮。
- `advancedSearchFields`：高级搜索字段配置，支持：
  - `type: 'input'` 输入框
  - `type: 'select'` 选择器（配合 `options`）
  - `type: 'datetime_range'` 日期时间范围
- `customActionButtons`：操作列按钮配置。
- `showFuzzySearch / showAdvancedSearch / showColumnSetting / showColumnSort / showCustomActions`：功能开关。

### ProTable 工具栏布局建议

- 推荐顺序：`自定义按钮(action1/action2)` -> `搜索框` -> `展示列图标`。
- 搜索框左侧展示搜索图标，右侧内联 `x 重置图标`、`回车图标`、`高级搜索图标`。
- 推荐使用中性色按钮，避免在工具栏使用过多彩色 `type`。

### ProTable 查询参数说明

- `keyword`：模糊搜索关键词。
- `sortKey`：排序字段。
- `sortOrder`：排序方向（`ascend | descend | null`）。
- `sorter`：兼容字段，格式为 `${sortKey}:${sortOrder}`。
- `advancedFilters`：高级搜索键值对。
  - 输入框：`string`
  - 选择器：`string | number`
  - 日期时间范围：`[number, number]`（时间戳）

## ProForm

- 基于 schema 动态渲染表单项。
- 支持规则驱动的同步校验。
- 支持三种模式：`create`、`edit`、`view`。
