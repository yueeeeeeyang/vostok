<template>
  <div class="pro-upload">
    <n-upload
      :default-upload="false"
      :show-file-list="false"
      :on-before-upload="onBeforeUpload"
    >
      <n-button>选择文件</n-button>
    </n-upload>
    <div v-if="fileName">已选择：{{ fileName }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { NButton, NUpload } from 'naive-ui';
import type { UploadFileInfo } from 'naive-ui';

const fileName = ref('');

// 阻止自动上传，仅复用 Naive UI 的文件选择交互。
function onBeforeUpload(data: { file: UploadFileInfo; fileList: UploadFileInfo[] }): boolean {
  void data.fileList;
  fileName.value = data.file.name;
  return false;
}
</script>

<style scoped>
.pro-upload {
  display: grid;
  gap: 8px;
}
</style>
