<template>
  <main class="page">
    <h2>VkLogin 样例</h2>
    <p class="desc">
      支持背景图片、三种布局模式、注册与记住密码开关，登录提交通过事件回调输出表单值。
    </p>

    <n-space vertical :size="18">
      <section class="demo-block">
        <h3>布局1：登录卡片居中（center）</h3>
        <VkLogin
          card-title="Vostok 管理后台"
          layout-mode="center"
          background-image-url="https://picsum.photos/id/1058/1600/900"
          username-label="账号"
          username-placeholder="请输入账号"
          password-label="密码"
          password-placeholder="请输入密码"
          login-button-text="登录系统"
          :show-register="true"
          register-text="立即注册"
          :show-remember="true"
          remember-text="记住登录状态"
          :show-forgot-password="true"
          forgot-password-text="找回密码"
          :initial-value="{ username: 'admin' }"
          @submit="(payload) => handleSubmit('center', payload)"
          @register-click="recordAction('center', 'register-click')"
          @forgot-password-click="recordAction('center', 'forgot-password-click')"
        >
          <template #footer>
            <n-text depth="3">登录即代表同意《用户协议》和《隐私政策》</n-text>
          </template>
        </VkLogin>
      </section>

      <section class="demo-block">
        <h3>布局2：左卡右图（left-card）</h3>
        <VkLogin
          card-title="运营管理系统"
          layout-mode="left-card"
          background-image-url="https://picsum.photos/id/1005/1600/900"
          side-image-url="https://picsum.photos/id/1025/1000/1000"
          side-image-alt="运营插图"
          username-label="手机号"
          username-placeholder="请输入手机号"
          password-label="登录密码"
          password-placeholder="请输入登录密码"
          login-button-text="立即登录"
          :show-register="false"
          :show-remember="true"
          remember-text="7天内免登录"
          :show-forgot-password="true"
          forgot-password-text="忘记密码?"
          :initial-value="{ username: '13800138000' }"
          @submit="(payload) => handleSubmit('left-card', payload)"
          @forgot-password-click="recordAction('left-card', 'forgot-password-click')"
        />
      </section>

      <section class="demo-block">
        <h3>布局3：右卡左图（right-card）</h3>
        <VkLogin
          card-title="企业门户登录"
          layout-mode="right-card"
          background-image-url=""
          side-image-url="https://picsum.photos/id/1035/1000/1000"
          side-image-alt="品牌侧图"
          username-label="用户名"
          username-placeholder="请输入用户名"
          password-label="访问密码"
          password-placeholder="请输入访问密码"
          login-button-text="进入门户"
          :show-register="true"
          register-text="企业注册"
          :show-remember="false"
          :show-forgot-password="false"
          @submit="(payload) => handleSubmit('right-card', payload)"
          @register-click="recordAction('right-card', 'register-click')"
        />
      </section>
    </n-space>

    <section class="result-panel">
      <h3>事件回调输出</h3>
      <pre data-testid="vk-login-event">{{ eventLogText }}</pre>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { NText, NSpace } from 'naive-ui';
import { VkLogin } from '@vostok/frontend';
import type { VkLoginFormValue } from '@vostok/frontend';

const eventLog = ref<Record<string, unknown> | null>(null);

function handleSubmit(source: string, payload: VkLoginFormValue): void {
  eventLog.value = {
    source,
    event: 'submit',
    payload
  };
}

function recordAction(source: string, event: 'register-click' | 'forgot-password-click'): void {
  eventLog.value = {
    source,
    event
  };
}

const eventLogText = computed(() =>
  JSON.stringify(eventLog.value ?? { tip: '请在上方任一登录框中触发操作' }, null, 2)
);
</script>

<style scoped>
.page {
  max-width: 1200px;
  margin: 0 auto;
}

.desc {
  color: #4b5563;
}

.demo-block {
  display: grid;
  gap: 8px;
}

.demo-block :deep(.vk-login) {
  min-height: 460px;
}

.result-panel {
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
