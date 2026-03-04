<template>
  <main class="page">
    <h2>VkDrawerForm 样例</h2>
    <p class="desc">用于演示抽屉表单容器的插槽使用方式。</p>

    <VkDrawerForm>
      <div class="box">
        <VkForm
          :schema="schema"
          :initial-values="{ name: 'Drawer User' }"
          :submitter="submitter"
          mode="create"
        />
      </div>
    </VkDrawerForm>

    <section class="usage">
      <h3>使用方式</h3>
      <pre><code>import { VkDrawerForm, VkForm } from '@vostok/frontend';

&lt;VkDrawerForm&gt;
  &lt;VkForm :schema="schema" :submitter="submitter" /&gt;
&lt;/VkDrawerForm&gt;</code></pre>
    </section>
  </main>
</template>

<script setup lang="ts">
import { VkDrawerForm, VkForm } from '@vostok/frontend';
import type { VkFormFieldSchema } from '@vostok/frontend';

const schema: VkFormFieldSchema[] = [
  { key: 'name', label: '名称', type: 'text', required: true }
];

async function submitter(_values: Record<string, unknown>): Promise<void> {
  return Promise.resolve();
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

.box {
  border: 1px dashed #d1d5db;
  border-radius: 8px;
  padding: 12px;
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
