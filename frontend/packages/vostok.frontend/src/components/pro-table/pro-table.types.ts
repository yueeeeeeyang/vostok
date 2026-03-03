export interface ProTableColumn {
  key: string;
  title: string;
  width?: number;
  // 是否允许该列参与列头排序。
  sortable?: boolean;
  // 配合展示列设置使用，false 表示初始默认不展示。
  visible?: boolean;
  render?: (row: Record<string, unknown>) => string | number;
}

export type ProTableSortOrder = 'ascend' | 'descend' | null;
export type ProAdvancedSearchFieldType = 'input' | 'select' | 'datetime_range';
export type ProAdvancedFilterValue =
  | string
  | number
  | boolean
  | null
  | undefined
  | [number, number];

export interface ProAdvancedSearchOption {
  label: string;
  value: string | number;
}

export interface ProTableQuery {
  page: number;
  pageSize: number;
  // 结构化排序字段，建议后端优先使用这两个字段。
  sortKey?: string;
  sortOrder?: ProTableSortOrder;
  // 兼容字段，格式 `${sortKey}:${sortOrder}`。
  sorter?: string;
  keyword?: string;
  // 高级搜索透传字段。
  advancedFilters?: Record<string, ProAdvancedFilterValue>;
}

export interface ProTableResult<T extends Record<string, unknown>> {
  items: T[];
  total: number;
}

export type ProTableFetcher<T extends Record<string, unknown>> = (
  query: ProTableQuery
) => Promise<ProTableResult<T>>;

export interface ProToolbarAction {
  key: string;
  label: string;
  onClick: () => void;
}

export interface ProAdvancedSearchField {
  key: string;
  label: string;
  type?: ProAdvancedSearchFieldType;
  placeholder?: string;
  options?: ProAdvancedSearchOption[];
}

export interface ProTableActionButton<T extends Record<string, unknown>> {
  key: string;
  label: string;
  type?: 'default' | 'primary' | 'info' | 'success' | 'warning' | 'error';
  show?: (row: T) => boolean;
  disabled?: (row: T) => boolean;
  onClick: (row: T) => void;
}

export interface ProTableProps<T extends Record<string, unknown>> {
  columns: ProTableColumn[];
  fetcher: ProTableFetcher<T>;
  paginationMode: 'server' | 'client';
  rowKey: string;
  searchPlaceholder?: string;
  advancedSearchFields?: ProAdvancedSearchField[];
  customActionButtons?: ProTableActionButton<T>[];
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
  toolbarActions?: ProToolbarAction[];
}
