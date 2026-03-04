<template>
  <main class="page">
    <h2>VkUpload 样例</h2>
    <p class="desc">用于演示统一上传组件能力。</p>

    <VkUpload />

    <section class="usage">
      <h3>使用方式</h3>
      <pre><code>import { VkUpload } from '@vostok/frontend';

&lt;VkUpload /&gt;</code></pre>
    </section>
  </main>
</template>

<script setup lang="ts">
import { VkUpload } from '@vostok/frontend';
</script>

<style scoped>
.page {
  max-width: 920px;
  margin: 0 auto;
}

.desc {
  color: #4b5563;
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
