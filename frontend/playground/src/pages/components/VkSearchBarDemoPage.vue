<template>
  <main class="page">
    <h2>VkSearchBar 样例</h2>
    <p class="desc">用于快速构建轻量级检索区。</p>

    <VkSearchBar :fields="fields" @search="handleSearch" />
    <p class="result">当前检索参数：{{ JSON.stringify(searchValues) }}</p>

    <section class="usage">
      <h3>使用方式</h3>
      <pre><code>import { VkSearchBar } from '@vostok/frontend';

&lt;VkSearchBar :fields="fields" @search="onSearch" /&gt;</code></pre>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { VkSearchBar } from '@vostok/frontend';
import type { VkSearchField } from '@vostok/frontend';

const fields: VkSearchField[] = [
  { key: 'keyword', label: '关键词', placeholder: '输入关键字' },
  { key: 'status', label: '状态', placeholder: 'active / inactive' }
];

const searchValues = ref<Record<string, string>>({});

function handleSearch(values: Record<string, string>): void {
  searchValues.value = values;
}
</script>

<style scoped>
.page {
  max-width: 920px;
  margin: 0 auto;
}

.desc {
  color: #4b5563;
}

.result {
  margin-top: 12px;
  color: #374151;
}

.usage {
  margin-top: 16px;
}

pre {
  margin: 0;
  padding: 12px;
  border-radius: 8px;
  background: #111827;
  color: #e5e7eb;
  overflow: auto;
}
</style>
