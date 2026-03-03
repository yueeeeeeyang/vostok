<template>
  <n-card class="pro-table">
    <n-space v-if="showToolbar" class="toolbar" justify="end" align="center" wrap>
      <n-button
        v-for="action in toolbarActions"
        :key="action.key"
        size="medium"
        @click="action.onClick"
      >
        {{ action.label }}
      </n-button>

      <n-input
        v-if="showFuzzySearch"
        class="search-input"
        size="medium"
        clearable
        :value="keyword"
        :placeholder="searchPlaceholder"
        @update:value="onKeywordChange"
        @clear="handleClearKeyword"
        @keyup.enter="handleInputEnter"
      >
        <template #prefix>
          <n-icon class="input-icon passive">
            <SearchOutline />
          </n-icon>
        </template>
        <template #suffix>
          <div class="input-suffix">
            <span
              v-if="hasKeyword"
              class="suffix-btn"
              title="回车搜索"
              @click="handleSearch"
            >
              <n-icon class="suffix-icon">
                <ArrowUndoOutline />
              </n-icon>
            </span>
            <span
              v-if="showAdvancedSearch && hasAdvancedFields"
              class="suffix-funnel-zone"
              :class="{ active: advancedExpanded }"
              title="高级搜索"
              @click="toggleAdvancedSearch"
            >
              <n-icon class="suffix-icon">
                <FunnelOutline />
              </n-icon>
            </span>
          </div>
        </template>
      </n-input>

      <n-popover v-if="showColumnSetting" trigger="click" placement="bottom-end">
        <template #trigger>
          <n-button size="medium" title="展示列">
            <n-icon class="input-icon">
              <GridOutline />
            </n-icon>
          </n-button>
        </template>
        <div class="column-setting">
          <n-checkbox-group
            :value="visibleColumnKeys"
            @update:value="onVisibleColumnsUpdate"
          >
            <n-space vertical size="small">
              <n-checkbox
                v-for="column in columns"
                :key="column.key"
                :value="column.key"
              >
                {{ column.title }}
              </n-checkbox>
            </n-space>
          </n-checkbox-group>
          <div class="column-setting-tip">至少保留一列</div>
        </div>
      </n-popover>
    </n-space>

    <div
      v-if="showAdvancedSearch && hasAdvancedFields && advancedExpanded"
      class="advanced-panel"
    >
      <n-space wrap align="end">
        <div
          v-for="field in advancedSearchFields"
          :key="field.key"
          class="advanced-item"
        >
          <div class="advanced-label">{{ field.label }}</div>
          <n-input
            v-if="resolveFieldType(field) === 'input'"
            :value="toInputFilterValue(advancedFilters[field.key])"
            :placeholder="field.placeholder ?? `请输入${field.label}`"
            clearable
            style="width: 220px"
            @update:value="(value) => setAdvancedFilter(field.key, value)"
          />
          <n-select
            v-else-if="resolveFieldType(field) === 'select'"
            :value="toSelectFilterValue(advancedFilters[field.key])"
            :options="toSelectOptions(field.options)"
            clearable
            filterable
            style="width: 220px"
            :placeholder="field.placeholder ?? `请选择${field.label}`"
            @update:value="(value) => setAdvancedFilter(field.key, value ?? null)"
          />
          <n-date-picker
            v-else
            type="datetimerange"
            clearable
            style="width: 300px"
            :value="toDateRangeFilterValue(advancedFilters[field.key])"
            @update:value="(value) => setAdvancedFilter(field.key, normalizeDateRange(value))"
          />
        </div>
      </n-space>
      <n-space justify="end" class="advanced-actions">
        <n-button @click="handleSearch">查询</n-button>
        <n-button @click="handleResetAdvanced">重置</n-button>
      </n-space>
    </div>

    <n-alert v-if="error" type="error" :show-icon="false">
      {{ error }}
    </n-alert>
    <n-spin v-else-if="loading">
      <div class="state">加载中...</div>
    </n-spin>
    <n-empty v-else-if="rows.length === 0" description="暂无数据" />
    <n-data-table
      v-else
      :columns="dataTableColumns"
      :data="rows"
      :row-key="resolveRowKey"
      :remote="paginationMode === 'server'"
      :pagination="pagination"
      @update:sorter="handleSorterUpdate"
    />
  </n-card>
</template>

<script setup lang="ts" generic="T extends Record<string, unknown>">
import { computed, h, onMounted, reactive, ref, watch } from 'vue';
import {
  NAlert,
  NButton,
  NCard,
  NCheckbox,
  NCheckboxGroup,
  NDatePicker,
  NDataTable,
  NEmpty,
  NIcon,
  NInput,
  NPopover,
  NSelect,
  NSpace,
  NSpin
} from 'naive-ui';
import type { DataTableColumns, PaginationProps, SelectOption } from 'naive-ui';
import {
  ArrowUndoOutline,
  FunnelOutline,
  GridOutline,
  SearchOutline
} from '@vicons/ionicons5';
import { useProTable } from './use-pro-table';
import type {
  ProAdvancedFilterValue,
  ProAdvancedSearchField,
  ProTableProps,
  ProTableQuery,
  ProTableSortOrder
} from './pro-table.types';

const props = withDefaults(defineProps<ProTableProps<T>>(), {
  searchPlaceholder: '请输入关键词',
  defaultPageSize: 20,
  toolbarActions: () => [],
  advancedSearchFields: () => [],
  customActionButtons: () => [],
  showFuzzySearch: false,
  showAdvancedSearch: false,
  showColumnSetting: false,
  showColumnSort: false,
  showCustomActions: false
});

const { loading, items, total, error, load } = useProTable<T>(props.fetcher);
const page = ref(1);
const pageSize = ref(props.defaultPageSize);
const keyword = ref('');
const advancedExpanded = ref(false);
const sortKey = ref('');
const sortOrder = ref<ProTableSortOrder>(null);
const visibleColumnKeys = ref<string[]>([]);
const advancedFilters = reactive<Record<string, ProAdvancedFilterValue>>({});

const rows = computed(() => items.value);
const columns = computed(() => props.columns);
const toolbarActions = computed(() => props.toolbarActions ?? []);
const advancedSearchFields = computed(() => props.advancedSearchFields ?? []);
const customActionButtons = computed(() => props.customActionButtons ?? []);
const searchPlaceholder = computed(() => props.searchPlaceholder);
const paginationMode = computed(() => props.paginationMode);
const showFuzzySearch = computed(() => props.showFuzzySearch);
const showAdvancedSearch = computed(() => props.showAdvancedSearch);
const showColumnSetting = computed(() => props.showColumnSetting);
const showColumnSort = computed(() => props.showColumnSort);
const showCustomActions = computed(() => props.showCustomActions);
const hasAdvancedFields = computed(() => advancedSearchFields.value.length > 0);
const hasKeyword = computed(() => keyword.value.trim() !== '');
// 工具栏按“自定义按钮 -> 搜索框 -> 展示列”顺序渲染，整体右对齐。
const showToolbar = computed(
  () =>
    toolbarActions.value.length > 0 ||
    showFuzzySearch.value ||
    (showAdvancedSearch.value && hasAdvancedFields.value) ||
    showColumnSetting.value
);

function resolveFieldType(field: ProAdvancedSearchField): 'input' | 'select' | 'datetime_range' {
  return field.type ?? 'input';
}

function getDefaultAdvancedValue(field: ProAdvancedSearchField): ProAdvancedFilterValue {
  return resolveFieldType(field) === 'datetime_range' ? null : '';
}

function toInputFilterValue(value: ProAdvancedFilterValue): string {
  if (typeof value === 'string') {
    return value;
  }
  if (typeof value === 'number') {
    return String(value);
  }
  return '';
}

function toSelectFilterValue(value: ProAdvancedFilterValue): string | number | null {
  if (typeof value === 'string') {
    return value.trim() === '' ? null : value;
  }
  if (typeof value === 'number') {
    return value;
  }
  return null;
}

function toSelectOptions(
  options: ProAdvancedSearchField['options'] | undefined
): SelectOption[] {
  return (options ?? []).map((item) => ({
    label: item.label,
    value: item.value
  }));
}

function toDateRangeFilterValue(value: ProAdvancedFilterValue): [number, number] | null {
  if (Array.isArray(value) && value.length === 2) {
    const [start, end] = value;
    if (typeof start === 'number' && typeof end === 'number') {
      return [start, end];
    }
  }
  return null;
}

function normalizeDateRange(value: number | [number, number] | null): [number, number] | null {
  if (Array.isArray(value) && value.length === 2) {
    return [value[0], value[1]];
  }
  return null;
}

function hasAdvancedFilterValue(value: ProAdvancedFilterValue): boolean {
  if (typeof value === 'string') {
    return value.trim() !== '';
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return true;
  }
  if (Array.isArray(value)) {
    if (value.length !== 2) {
      return false;
    }
    return value[0] !== null && value[1] !== null;
  }
  return false;
}

// 同步初始化高级搜索字段，字段变化时保留已有输入并剔除失效键。
watch(
  () => advancedSearchFields.value,
  (fields) => {
    const keys = new Set(fields.map((item) => item.key));
    fields.forEach((field) => {
      if (!(field.key in advancedFilters)) {
        advancedFilters[field.key] = getDefaultAdvancedValue(field);
      }
    });
    Object.keys(advancedFilters).forEach((key) => {
      if (!keys.has(key)) {
        delete advancedFilters[key];
      }
    });
  },
  { deep: true, immediate: true }
);

// 展示列默认遵循 columns.visible，且自动兼容动态列更新。
watch(
  () => columns.value,
  (nextColumns) => {
    const availableKeys = nextColumns.map((item) => item.key);
    const defaultKeys = nextColumns.filter((item) => item.visible !== false).map((item) => item.key);
    const kept = visibleColumnKeys.value.filter((key) => availableKeys.includes(key));
    visibleColumnKeys.value = kept.length > 0 ? kept : defaultKeys.length > 0 ? defaultKeys : availableKeys;
  },
  { deep: true, immediate: true }
);

const effectiveColumnKeys = computed(() => {
  if (!showColumnSetting.value) {
    return columns.value.map((column) => column.key);
  }
  return visibleColumnKeys.value;
});

const selectedAdvancedFilters = computed<Record<string, ProAdvancedFilterValue>>(() => {
  const result: Record<string, ProAdvancedFilterValue> = {};
  Object.entries(advancedFilters).forEach(([key, value]) => {
    if (!hasAdvancedFilterValue(value)) {
      return;
    }
    if (typeof value === 'string') {
      result[key] = value.trim();
    } else if (Array.isArray(value)) {
      result[key] = [value[0], value[1]];
    } else {
      result[key] = value;
    }
  });
  return result;
});

// 将库定义的列结构映射为 Naive UI DataTable 列结构。
const dataTableColumns = computed<DataTableColumns<T>>(() => {
  const tableColumns: DataTableColumns<T> = columns.value
    .filter((column) => effectiveColumnKeys.value.includes(column.key))
    .map((column) => ({
      key: column.key,
      title: column.title,
      width: column.width,
      sorter: showColumnSort.value && (column.sortable ?? true) ? 'default' : false,
      sortOrder: sortKey.value === column.key ? sortOrder.value ?? false : false,
      render: (row) => {
        if (column.render) {
          return column.render(row as Record<string, unknown>);
        }
        const value = (row as Record<string, unknown>)[column.key];
        return value === undefined || value === null ? '-' : String(value);
      }
    }));

  if (showCustomActions.value && customActionButtons.value.length > 0) {
    tableColumns.push({
      key: '__actions__',
      title: '操作',
      width: 220,
      render: (row) => {
        const currentRow = row as T;
        const buttons = customActionButtons.value.filter((button) =>
          button.show ? button.show(currentRow) : true
        );

        if (buttons.length === 0) {
          return '-';
        }

        return h(
          NSpace,
          { size: 'small' },
          {
            default: () =>
              buttons.map((button) =>
                h(
                  NButton,
                  {
                    size: 'small',
                    type: button.type ?? 'default',
                    disabled: button.disabled ? button.disabled(currentRow) : false,
                    onClick: () => button.onClick(currentRow)
                  },
                  { default: () => button.label }
                )
              )
          }
        );
      }
    });
  }

  return tableColumns;
});

const pagination = computed<PaginationProps | false>(() => {
  if (props.paginationMode !== 'server') {
    return false;
  }
  return {
    page: page.value,
    pageSize: pageSize.value,
    itemCount: total.value,
    showSizePicker: true,
    pageSizes: [10, 20, 50, 100],
    onUpdatePage: (nextPage: number) => {
      page.value = nextPage;
      void loadData();
    },
    onUpdatePageSize: (nextPageSize: number) => {
      pageSize.value = nextPageSize;
      page.value = 1;
      void loadData();
    }
  };
});

function setAdvancedFilter(key: string, value: ProAdvancedFilterValue): void {
  advancedFilters[key] = value;
}

function onKeywordChange(value: string): void {
  keyword.value = value;
}

// 汇总当前分页、搜索、排序条件，统一传入 fetcher。
function buildQuery(): ProTableQuery {
  return {
    page: page.value,
    pageSize: pageSize.value,
    keyword: showFuzzySearch.value ? keyword.value.trim() || undefined : undefined,
    sortKey: sortKey.value || undefined,
    sortOrder: sortOrder.value,
    sorter:
      sortKey.value && sortOrder.value
        ? `${sortKey.value}:${sortOrder.value}`
        : undefined,
    advancedFilters:
      showAdvancedSearch.value && Object.keys(selectedAdvancedFilters.value).length > 0
        ? selectedAdvancedFilters.value
        : undefined
  };
}

async function loadData(): Promise<void> {
  await load(buildQuery());
}

function handleSearch(): void {
  page.value = 1;
  void loadData();
}

function handleInputEnter(): void {
  if (!hasKeyword.value) {
    return;
  }
  handleSearch();
}

function handleClearKeyword(): void {
  keyword.value = '';
  page.value = 1;
  void loadData();
}

function handleResetAdvanced(): void {
  advancedSearchFields.value.forEach((field) => {
    advancedFilters[field.key] = getDefaultAdvancedValue(field);
  });
  page.value = 1;
  void loadData();
}

function toggleAdvancedSearch(): void {
  advancedExpanded.value = !advancedExpanded.value;
}

function onVisibleColumnsUpdate(keys: Array<string | number>): void {
  const normalizedKeys = keys.map((item) => String(item));
  if (normalizedKeys.length === 0) {
    return;
  }
  visibleColumnKeys.value = normalizedKeys;
}

function handleSorterUpdate(
  sorter:
    | { columnKey: string | number; order: 'ascend' | 'descend' | false }
    | Array<{ columnKey: string | number; order: 'ascend' | 'descend' | false }>
    | null
): void {
  const current = Array.isArray(sorter) ? sorter[0] : sorter;
  if (!current || current.order === false) {
    sortKey.value = '';
    sortOrder.value = null;
  } else {
    sortKey.value = String(current.columnKey);
    sortOrder.value = current.order;
  }
  page.value = 1;
  void loadData();
}

function resolveRowKey(row: T): string {
  const value = (row as Record<string, unknown>)[props.rowKey];
  return value === undefined || value === null ? JSON.stringify(row) : String(value);
}

onMounted(() => {
  void loadData();
});
</script>

<style scoped>
.pro-table {
  width: 100%;
}
.toolbar {
  margin-bottom: 12px;
}
.search-input {
  width: 220px;
}
.search-input :deep(.n-input-wrapper) {
  padding-right: 0;
}
.search-input :deep(.n-input__suffix) {
  height: 100%;
  padding-right: 0;
}
.advanced-panel {
  margin: 8px 0 12px;
  padding: 12px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: var(--n-border-radius, 6px);
}
.advanced-item {
  display: grid;
  gap: 4px;
}
.advanced-label {
  color: #4b5563;
  font-size: 12px;
}
.advanced-actions {
  margin-top: 10px;
}
.column-setting {
  min-width: 160px;
}
.column-setting-tip {
  margin-top: 8px;
  color: #6b7280;
  font-size: 12px;
}
.input-icon {
  color: #6b7280;
  font-size: 16px;
  line-height: 1;
}
.input-icon.passive {
  cursor: default;
}
.input-icon.active {
  color: #111827;
}
.suffix-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 999px;
  color: #9ca3af;
  cursor: pointer;
}
.suffix-btn:hover {
  background: #f3f4f6;
  color: #4b5563;
}
.suffix-btn.active {
  color: #4b5563;
}
.input-suffix {
  display: inline-flex;
  align-items: center;
  height: 100%;
  gap: 6px;
}
.suffix-funnel-zone {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 100%;
  margin-left: 2px;
  border-left: 1px solid #e5e7eb;
  color: #9ca3af;
  cursor: pointer;
}
.suffix-funnel-zone:hover {
  background: #f3f4f6;
  color: #4b5563;
}
.suffix-funnel-zone.active {
  background: #eef2f7;
  color: #4b5563;
}
.suffix-icon {
  font-size: 13px;
}
.state {
  padding: 12px 0;
}
</style>
