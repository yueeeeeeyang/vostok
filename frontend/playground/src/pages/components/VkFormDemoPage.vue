<template>
  <main class="page">
    <h2>VkForm 样例</h2>
    <p class="desc">通过 Schema 动态渲染表单，并统一提交逻辑。</p>

    <VkForm
      :schema="schema"
      :initial-values="initialValues"
      :submitter="submitForm"
      mode="edit"
    />

    <p class="result">最近一次提交：{{ JSON.stringify(submitResult) }}</p>

    <section class="usage">
      <h3>使用方式</h3>
      <pre><code>import { VkForm } from '@vostok/frontend';

&lt;VkForm
  :schema="schema"
  :initial-values="initialValues"
  :submitter="submitter"
  mode="edit"
/&gt;</code></pre>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { VkForm } from '@vostok/frontend';
import type { VkFormFieldSchema } from '@vostok/frontend';

const schema: VkFormFieldSchema[] = [
  { key: 'name', label: '姓名', type: 'text', required: true },
  { key: 'email', label: '邮箱', type: 'text', required: true },
  { key: 'status', label: '状态', type: 'text', required: true }
];

const initialValues = {
  name: 'Alice',
  email: 'alice@example.com',
  status: 'active'
};

const submitResult = ref<Record<string, unknown>>(initialValues);

async function submitForm(values: Record<string, unknown>): Promise<void> {
  submitResult.value = values;
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
