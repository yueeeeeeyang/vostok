<template>
  <n-card title="工作台" size="small">
    <n-space vertical :size="12">
      <n-alert type="success" :show-icon="false">
        这是页面应用下的工作台示例页面。
      </n-alert>
      <n-empty description="暂无待办事项" />
    </n-space>
  </n-card>
</template>

<script setup lang="ts">
import { NAlert, NCard, NEmpty, NSpace } from 'naive-ui';
</script>
