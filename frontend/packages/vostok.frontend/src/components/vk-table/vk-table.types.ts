export interface VkTableColumn {
  key: string;
  title: string;
  width?: number;
  // 是否允许该列参与列头排序。
  sortable?: boolean;
  // 配合展示列设置使用，false 表示初始默认不展示。
  visible?: boolean;
  render?: (row: Record<string, unknown>) => string | number;
}

export type VkTableSortOrder = 'ascend' | 'descend' | null;
export type VkAdvancedSearchFieldType = 'input' | 'select' | 'datetime_range';
export type VkAdvancedFilterValue =
  | string
  | number
  | boolean
  | null
  | undefined
  | [number, number];

export interface VkAdvancedSearchOption {
  label: string;
  value: string | number;
}

export interface VkTableQuery {
  page: number;
  pageSize: number;
  // 结构化排序字段，建议后端优先使用这两个字段。
  sortKey?: string;
  sortOrder?: VkTableSortOrder;
  // 兼容字段，格式 `${sortKey}:${sortOrder}`。
  sorter?: string;
  keyword?: string;
  // 高级搜索透传字段。
  advancedFilters?: Record<string, VkAdvancedFilterValue>;
}

export interface VkTableResult<T extends Record<string, unknown>> {
  items: T[];
  total: number;
}

export type VkTableFetcher<T extends Record<string, unknown>> = (
  query: VkTableQuery
) => Promise<VkTableResult<T>>;

export interface VkToolbarAction {
  key: string;
  label: string;
  onClick: () => void;
}

export interface VkAdvancedSearchField {
  key: string;
  label: string;
  type?: VkAdvancedSearchFieldType;
  placeholder?: string;
  options?: VkAdvancedSearchOption[];
}

export interface VkTableActionButton<T extends Record<string, unknown>> {
  key: string;
  label: string;
  type?: 'default' | 'primary' | 'info' | 'success' | 'warning' | 'error';
  show?: (row: T) => boolean;
  disabled?: (row: T) => boolean;
  onClick: (row: T) => void;
}

export interface VkTableProps<T extends Record<string, unknown>> {
  columns: VkTableColumn[];
  fetcher: VkTableFetcher<T>;
  paginationMode: 'server' | 'client';
  rowKey: string;
  searchPlaceholder?: string;
  advancedSearchFields?: VkAdvancedSearchField[];
  customActionButtons?: VkTableActionButton<T>[];
  defaultPageSize?: number;
  // 功能开关：模糊搜索。
  showFuzzySearch?: boolean;
  // 功能开关：高级搜索面板。
  showAdvancedSearch?: boolean;
  // 功能开关：展示列调整。
  showColumnSetting?: boolean;
  // 功能开关：列头排序。
  showColumnSort?: boolean;
  // 功能开关：自定义操作按钮列。
  showCustomActions?: boolean;
  toolbarActions?: VkToolbarAction[];
}
