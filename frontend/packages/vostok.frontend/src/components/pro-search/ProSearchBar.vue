<template>
  <n-space class="pro-search" wrap>
    <n-input
      v-for="field in fields"
      :key="field.key"
      :placeholder="field.placeholder ?? field.label"
      :value="values[field.key] ?? ''"
      clearable
      @update:value="(value) => (values[field.key] = value)"
    />
    <n-button type="primary" @click="emitSearch">查询</n-button>
  </n-space>
</template>

<script setup lang="ts">
import { reactive } from 'vue';
import { NButton, NInput, NSpace } from 'naive-ui';
import type { ProSearchField } from './pro-search.types';

defineProps<{ fields: ProSearchField[] }>();
const emit = defineEmits<{ search: [Record<string, string>] }>();

const values = reactive<Record<string, string>>({});

function emitSearch(): void {
  emit('search', { ...values });
}
</script>

<style scoped>
.pro-search {
  width: 100%;
}
</style>
