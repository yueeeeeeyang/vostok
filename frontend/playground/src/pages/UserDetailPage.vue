<template>
  <section class="page">
    <h2 class="title">用户详情</h2>
    <n-card v-if="loading" title="加载中">
      <n-spin size="small" />
    </n-card>
    <n-card v-else-if="errorMessage" title="加载失败">
      <p class="error">{{ errorMessage }}</p>
    </n-card>
    <n-card v-else title="用户信息">
      <n-descriptions label-placement="left" :column="1">
        <n-descriptions-item label="ID">{{ detail?.id ?? '-' }}</n-descriptions-item>
        <n-descriptions-item label="姓名">{{ detail?.name ?? '-' }}</n-descriptions-item>
        <n-descriptions-item label="邮箱">{{ detail?.email ?? '-' }}</n-descriptions-item>
        <n-descriptions-item label="状态">{{ detail?.status ?? '-' }}</n-descriptions-item>
        <n-descriptions-item label="创建时间">{{ detail?.createdAt ?? '-' }}</n-descriptions-item>
      </n-descriptions>
    </n-card>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { NCard, NDescriptions, NDescriptionsItem, NSpin } from 'naive-ui';
import { useApiClient } from '@vostok/frontend';
import { isApiError } from '@vostok/frontend/api';

interface UserDetail {
  id: string;
  name: string;
  email: string;
  status: string;
  createdAt: string;
}

const route = useRoute();
const api = useApiClient();

const loading = ref(false);
const detail = ref<UserDetail | null>(null);
const errorMessage = ref('');

async function loadDetail(id: string): Promise<void> {
  loading.value = true;
  errorMessage.value = '';
  try {
    const response = await api.query<UserDetail>(`/users/${id}`);
    detail.value = response.data;
  } catch (error) {
    // 演示页面统一展示标准化错误信息。
    errorMessage.value = isApiError(error) ? error.message : '未知异常';
  } finally {
    loading.value = false;
  }
}

watch(
  () => route.params.id,
  (id) => {
    if (typeof id === 'string' && id) {
      void loadDetail(id);
    }
  }
);

onMounted(() => {
  const id = route.params.id;
  if (typeof id === 'string' && id) {
    void loadDetail(id);
  }
});
</script>

<style scoped>
.page {
  max-width: 860px;
  margin: 0 auto;
}

.title {
  margin: 0 0 12px;
}

.error {
  color: #dc2626;
}
</style>
