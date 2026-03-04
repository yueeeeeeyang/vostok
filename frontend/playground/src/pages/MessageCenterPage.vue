<template>
  <n-card title="消息中心" size="small">
    <n-space vertical :size="12">
      <n-alert type="info" :show-icon="false">
        这里是消息中心示例页面，可替换为真实业务消息列表。
      </n-alert>
      <n-empty description="暂无更多消息" />
    </n-space>
  </n-card>
</template>

<script setup lang="ts">
import { NAlert, NCard, NEmpty, NSpace } from 'naive-ui';
</script>
