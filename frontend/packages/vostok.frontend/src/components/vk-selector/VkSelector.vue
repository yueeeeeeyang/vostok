<template>
  <div class="vk-selector">
    <div v-if="selectorName" class="vk-selector__label">{{ selectorName }}</div>

    <n-select
      v-if="optionType === 'flat'"
      :value="flatValue"
      :options="flatOptionsForUI"
      :multiple="isMultiple"
      :filterable="searchable"
      :clearable="clearable"
      :disabled="disabled"
      :placeholder="placeholder"
      @update:value="handleFlatValueChange"
    />

    <n-tree-select
      v-else
      :value="treeValue"
      :options="treeOptionsForUI"
      :multiple="isMultiple"
      :filterable="searchable"
      :clearable="clearable"
      :disabled="disabled"
      :placeholder="placeholder"
      @update:value="handleTreeValueChange"
    />

    <div v-if="showRecent && recentItems.length > 0" class="vk-selector__recent">
      <div class="vk-selector__recent-title">{{ recentTitle }}</div>
      <n-space :size="8" wrap>
        <n-tag
          v-for="item in recentItems"
          :key="String(item.value)"
          round
          checkable
          @click="handleRecentSelect(item)"
        >
          {{ item.label }}
        </n-tag>
      </n-space>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { NSpace, NSelect, NTag, NTreeSelect } from 'naive-ui';
import type { SelectOption, TreeSelectOption } from 'naive-ui';
import type {
  VkSelectorMode,
  VkSelectorOption,
  VkSelectorPrimitive,
  VkSelectorProps,
  VkSelectorRecentItem,
  VkSelectorTreeOption,
  VkSelectorValue
} from './vk-selector.types';

const props = withDefaults(defineProps<VkSelectorProps>(), {
  modelValue: null,
  mode: 'single',
  optionType: 'flat',
  selectorName: '选项',
  options: () => [],
  treeOptions: () => [],
  searchable: true,
  clearable: true,
  disabled: false,
  showRecent: true,
  recentLimit: 5,
  recentTitle: '最近选择',
  recentStorageKey: 'vk-selector-recent'
});

const emit = defineEmits<{
  (event: 'update:modelValue', value: VkSelectorValue): void;
  (event: 'change', value: VkSelectorValue): void;
  (event: 'recent-select', item: VkSelectorRecentItem): void;
}>();

const innerValue = ref<VkSelectorValue>(normalizeValue(props.modelValue, props.mode));
const recentItems = ref<VkSelectorRecentItem[]>([]);

const isMultiple = computed(() => props.mode === 'multiple');
const optionType = computed(() => props.optionType);
const selectorName = computed(() => props.selectorName);
const options = computed(() => props.options);
const treeOptions = computed(() => props.treeOptions);
const searchable = computed(() => props.searchable);
const clearable = computed(() => props.clearable);
const disabled = computed(() => props.disabled);
const showRecent = computed(() => props.showRecent);
const recentTitle = computed(() => props.recentTitle);
const placeholder = computed(() => `请选择${selectorName.value}`);

const flatOptionMap = computed<Map<VkSelectorPrimitive, string>>(() => {
  const map = new Map<VkSelectorPrimitive, string>();
  options.value.forEach((item) => {
    map.set(item.value, item.label);
  });
  return map;
});

const treeOptionMap = computed<Map<VkSelectorPrimitive, string>>(() => {
  const map = new Map<VkSelectorPrimitive, string>();
  const walk = (items: VkSelectorTreeOption[]): void => {
    items.forEach((item) => {
      map.set(item.key, item.label);
      if (item.children?.length) {
        walk(item.children);
      }
    });
  };
  walk(treeOptions.value);
  return map;
});

const currentOptionMap = computed(() =>
  optionType.value === 'tree' ? treeOptionMap.value : flatOptionMap.value
);

const flatOptionsForUI = computed<SelectOption[]>(() =>
  options.value.map((item) => ({
    label: item.label,
    value: item.value,
    disabled: item.disabled ?? false
  }))
);

const treeOptionsForUI = computed<TreeSelectOption[]>(() => {
  const walk = (items: VkSelectorTreeOption[]): TreeSelectOption[] =>
    items.map((item) => ({
      label: item.label,
      key: item.key,
      disabled: item.disabled ?? false,
      children: item.children?.length ? walk(item.children) : undefined
    }));
  return walk(treeOptions.value);
});

const flatValue = computed(() => innerValue.value);
const treeValue = computed(() => {
  if (Array.isArray(innerValue.value)) {
    return innerValue.value;
  }
  return innerValue.value === null ? undefined : innerValue.value;
});

watch(
  () => props.modelValue,
  (value) => {
    innerValue.value = normalizeValue(value, props.mode);
  },
  { immediate: true }
);

watch(
  () => props.mode,
  (mode) => {
    innerValue.value = normalizeValue(innerValue.value, mode);
  }
);

watch(
  () => [props.options, props.treeOptions, props.optionType, props.recentStorageKey],
  () => {
    recentItems.value = loadRecentItems();
  },
  { immediate: true, deep: true }
);

function normalizeValue(value: VkSelectorValue, mode: VkSelectorMode): VkSelectorValue {
  if (mode === 'multiple') {
    if (Array.isArray(value)) {
      return value;
    }
    if (value === null || value === undefined || value === '') {
      return [];
    }
    return [value];
  }

  if (Array.isArray(value)) {
    return value[0] ?? null;
  }
  if (value === undefined) {
    return null;
  }
  return value;
}

function resolveValues(value: VkSelectorValue): VkSelectorPrimitive[] {
  if (Array.isArray(value)) {
    return value;
  }
  if (value === null || value === undefined || value === '') {
    return [];
  }
  return [value];
}

function handleFlatValueChange(value: unknown): void {
  const normalized = normalizeValue(value as VkSelectorValue, props.mode);
  onValueChange(normalized);
}

function handleTreeValueChange(value: unknown): void {
  const normalized = normalizeValue(value as VkSelectorValue, props.mode);
  onValueChange(normalized);
}

function onValueChange(value: VkSelectorValue): void {
  innerValue.value = value;
  // 记录最近选择，便于高频选项回选。
  pushRecentByValue(value);
  emit('update:modelValue', value);
  emit('change', value);
}

function pushRecentByValue(value: VkSelectorValue): void {
  const values = resolveValues(value);
  if (values.length === 0) {
    return;
  }

  const next = [...recentItems.value];
  values.forEach((current) => {
    const label = currentOptionMap.value.get(current) ?? String(current);
    const index = next.findIndex((item) => item.value === current);
    if (index >= 0) {
      next.splice(index, 1);
    }
    next.unshift({ label, value: current });
  });

  recentItems.value = next.slice(0, Math.max(props.recentLimit, 1));
  saveRecentItems(recentItems.value);
}

function handleRecentSelect(item: VkSelectorRecentItem): void {
  emit('recent-select', item);

  if (isMultiple.value) {
    const current = resolveValues(innerValue.value);
    if (!current.includes(item.value)) {
      const next = [...current, item.value];
      onValueChange(next);
      return;
    }
    onValueChange(current);
    return;
  }

  onValueChange(item.value);
}

function loadRecentItems(): VkSelectorRecentItem[] {
  if (typeof window === 'undefined') {
    return [];
  }
  try {
    const raw = window.localStorage.getItem(props.recentStorageKey);
    if (!raw) {
      return [];
    }
    const parsed = JSON.parse(raw) as VkSelectorRecentItem[];
    if (!Array.isArray(parsed)) {
      return [];
    }
    const filtered = parsed
      .filter(
        (item) =>
          item &&
          (typeof item.value === 'string' || typeof item.value === 'number') &&
          typeof item.label === 'string'
      )
      .slice(0, Math.max(props.recentLimit, 1));
    // 若选项池中仍存在该值，则使用实时标签，避免标签变更后展示旧文案。
    return filtered.map((item) => ({
      ...item,
      label: currentOptionMap.value.get(item.value) ?? item.label
    }));
  } catch {
    return [];
  }
}

function saveRecentItems(items: VkSelectorRecentItem[]): void {
  if (typeof window === 'undefined') {
    return;
  }
  try {
    window.localStorage.setItem(props.recentStorageKey, JSON.stringify(items));
  } catch {
    // 本地存储失败时不阻断选择流程。
  }
}
</script>

<style scoped>
.vk-selector {
  width: 100%;
  display: grid;
  gap: 8px;
}

.vk-selector__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--n-text-color, #111827);
}

.vk-selector__recent {
  display: grid;
  gap: 6px;
}

.vk-selector__recent-title {
  font-size: 12px;
  color: var(--n-text-color-3, #6b7280);
}
</style>
