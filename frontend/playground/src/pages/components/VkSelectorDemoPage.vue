<template>
  <main class="page">
    <h2>VkSelector 样例</h2>
    <p class="desc">支持单选/多选、搜索、最近选择、普通选项与树形结构选项。</p>

    <n-space vertical :size="16">
      <n-card title="普通选项 - 单选" size="small">
        <VkSelector
          v-model="flatSingleValue"
          selector-name="部门"
          :options="flatOptions"
          option-type="flat"
          mode="single"
          :show-recent="true"
          recent-storage-key="vk-selector-demo-flat-single"
        />
      </n-card>

      <n-card title="普通选项 - 多选" size="small">
        <VkSelector
          v-model="flatMultiValue"
          selector-name="成员"
          :options="memberOptions"
          option-type="flat"
          mode="multiple"
          :show-recent="true"
          recent-storage-key="vk-selector-demo-flat-multi"
        />
      </n-card>

      <n-card title="树形选项 - 多选" size="small">
        <VkSelector
          v-model="treeMultiValue"
          selector-name="组织节点"
          :tree-options="treeOptions"
          option-type="tree"
          mode="multiple"
          :show-recent="true"
          recent-storage-key="vk-selector-demo-tree-multi"
        />
      </n-card>
    </n-space>

    <section class="result">
      <h3>当前值</h3>
      <pre>{{ payload }}</pre>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { NCard, NSpace } from 'naive-ui';
import { VkSelector } from '@vostok/frontend';
import type { VkSelectorOption, VkSelectorTreeOption, VkSelectorValue } from '@vostok/frontend';

const flatSingleValue = ref<VkSelectorValue>(null);
const flatMultiValue = ref<VkSelectorValue>([]);
const treeMultiValue = ref<VkSelectorValue>([]);

const flatOptions: VkSelectorOption[] = [
  { label: '研发中心', value: 'rd' },
  { label: '产品中心', value: 'pm' },
  { label: '运营中心', value: 'ops' },
  { label: '市场中心', value: 'mkt' }
];

const memberOptions: VkSelectorOption[] = [
  { label: '张三', value: 'u1' },
  { label: '李四', value: 'u2' },
  { label: '王五', value: 'u3' },
  { label: '赵六', value: 'u4' },
  { label: '孙七', value: 'u5' }
];

const treeOptions: VkSelectorTreeOption[] = [
  {
    label: '总部',
    key: 'root',
    children: [
      {
        label: '研发部',
        key: 'dev',
        children: [
          { label: '前端组', key: 'dev-fe' },
          { label: '后端组', key: 'dev-be' }
        ]
      },
      {
        label: '运营部',
        key: 'ops',
        children: [
          { label: '活动组', key: 'ops-campaign' },
          { label: '内容组', key: 'ops-content' }
        ]
      }
    ]
  }
];

const payload = computed(() =>
  JSON.stringify(
    {
      flatSingleValue: flatSingleValue.value,
      flatMultiValue: flatMultiValue.value,
      treeMultiValue: treeMultiValue.value
    },
    null,
    2
  )
);
</script>

<style scoped>
.page {
  max-width: 980px;
  margin: 0 auto;
}

.desc {
  color: #4b5563;
}

.result {
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
