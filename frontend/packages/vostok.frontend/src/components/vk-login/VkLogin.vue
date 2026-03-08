<template>
  <div class="vk-login" :style="rootStyle">
    <div
      class="vk-login__split"
      :class="{
        'vk-login__split--center': layoutMode === 'center',
        'vk-login__split--right': layoutMode === 'right-card'
      }"
    >
      <div class="vk-login__card-panel">
        <n-card class="vk-login__card" :bordered="false">
          <div class="vk-login__card-header">
            <n-text class="vk-login__title" strong>{{ cardTitleDisplay }}</n-text>
            <slot name="title-extra" />
          </div>

          <n-form :model="formState" label-placement="top">
            <n-form-item
              path="username"
              :label="usernameLabelDisplay"
              :validation-status="fieldErrors.username ? 'error' : undefined"
              :feedback="fieldErrors.username"
            >
              <n-input
                :value="formState.username"
                :placeholder="usernamePlaceholderDisplay"
                :disabled="disabledDisplay"
                clearable
                @update:value="(value) => updateField('username', value)"
              />
            </n-form-item>

            <n-form-item
              path="password"
              :label="passwordLabelDisplay"
              :validation-status="fieldErrors.password ? 'error' : undefined"
              :feedback="fieldErrors.password"
            >
              <n-input
                type="password"
                show-password-on="click"
                :value="formState.password"
                :placeholder="passwordPlaceholderDisplay"
                :disabled="disabledDisplay"
                @update:value="(value) => updateField('password', value)"
              />
            </n-form-item>

            <n-space class="vk-login__options-row" justify="space-between" align="center">
              <n-checkbox
                v-if="showRememberDisplay"
                :checked="formState.remember"
                :disabled="disabledDisplay"
                @update:checked="(value) => updateField('remember', value)"
              >
                {{ rememberTextDisplay }}
              </n-checkbox>
              <div class="vk-login__actions-inline">
                <n-button
                  v-if="showForgotPasswordDisplay"
                  text
                  type="primary"
                  @click="emit('forgot-password-click')"
                >
                  {{ forgotPasswordTextDisplay }}
                </n-button>
                <n-button
                  v-if="showRegisterDisplay"
                  text
                  type="primary"
                  @click="emit('register-click')"
                >
                  {{ registerTextDisplay }}
                </n-button>
              </div>
            </n-space>

            <n-button
              block
              type="primary"
              class="vk-login__submit-btn"
              :loading="loadingDisplay"
              :disabled="disabledDisplay"
              @click="handleSubmit"
            >
              {{ loginButtonTextDisplay }}
            </n-button>
          </n-form>

          <div v-if="$slots.footer" class="vk-login__footer">
            <slot name="footer" />
          </div>
        </n-card>
      </div>

      <div v-if="layoutMode !== 'center'" class="vk-login__image-panel">
        <n-image
          v-if="sideImageUrlDisplay"
          class="vk-login__image"
          object-fit="cover"
          :src="sideImageUrlDisplay"
          :alt="sideImageAltDisplay"
          preview-disabled
        />
        <div v-else class="vk-login__image-placeholder">
          <n-text depth="3">请传入 `sideImageUrl` 配置侧图</n-text>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, watch } from 'vue';
import {
  NButton,
  NCard,
  NCheckbox,
  NForm,
  NFormItem,
  NImage,
  NInput,
  NSpace,
  NText
} from 'naive-ui';
import type { VkLoginFormValue, VkLoginProps } from './vk-login.types';

const props = withDefaults(defineProps<VkLoginProps>(), {
  layoutMode: 'center',
  showRegister: false,
  showRemember: false,
  showForgotPassword: false,
  loading: false,
  disabled: false,
  initialValue: () => ({})
});

const emit = defineEmits<{
  (event: 'submit', payload: VkLoginFormValue): void;
  (event: 'register-click'): void;
  (event: 'forgot-password-click'): void;
}>();

const formState = reactive<VkLoginFormValue>({
  username: '',
  password: '',
  remember: false
});

const fieldErrors = reactive<Record<'username' | 'password', string>>({
  username: '',
  password: ''
});

const layoutMode = computed(() => props.layoutMode);
const loadingDisplay = computed(() => props.loading);
const disabledDisplay = computed(() => props.disabled || props.loading);
const showRegisterDisplay = computed(() => props.showRegister);
const showRememberDisplay = computed(() => props.showRemember);
const showForgotPasswordDisplay = computed(() => props.showForgotPassword);

const cardTitleDisplay = computed(() => props.cardTitle ?? '欢迎登录');
const usernameLabelDisplay = computed(() => props.usernameLabel ?? '用户名');
const usernamePlaceholderDisplay = computed(() => props.usernamePlaceholder ?? '请输入用户名');
const passwordLabelDisplay = computed(() => props.passwordLabel ?? '密码');
const passwordPlaceholderDisplay = computed(() => props.passwordPlaceholder ?? '请输入密码');
const loginButtonTextDisplay = computed(() => props.loginButtonText ?? '登录');
const registerTextDisplay = computed(() => props.registerText ?? '注册账号');
const rememberTextDisplay = computed(() => props.rememberText ?? '记住密码');
const forgotPasswordTextDisplay = computed(() => props.forgotPasswordText ?? '忘记密码');
const sideImageUrlDisplay = computed(() => props.sideImageUrl ?? '');
const sideImageAltDisplay = computed(() => props.sideImageAlt ?? '登录侧图');

const rootStyle = computed(() => {
  const hasImage = Boolean(props.backgroundImageUrl?.trim());
  return {
    backgroundImage: hasImage
      ? `url("${props.backgroundImageUrl}")`
      : 'linear-gradient(135deg, #f7fafc 0%, #edf2f7 100%)',
    backgroundSize: 'cover',
    backgroundPosition: 'center',
    backgroundRepeat: 'no-repeat'
  };
});

watch(
  () => props.initialValue,
  (value) => {
    // 外部初始值变化时同步刷新表单，保证弹窗复用场景可正确回填。
    formState.username = value?.username ?? '';
    formState.password = value?.password ?? '';
    formState.remember = Boolean(value?.remember);
  },
  { immediate: true, deep: true }
);

function updateField(field: keyof VkLoginFormValue, value: string | boolean): void {
  formState[field] = value as never;
  if (field === 'username' || field === 'password') {
    fieldErrors[field] = '';
  }
}

function validate(): boolean {
  fieldErrors.username = formState.username.trim() === '' ? '请输入用户名' : '';
  fieldErrors.password = formState.password.trim() === '' ? '请输入密码' : '';
  return fieldErrors.username === '' && fieldErrors.password === '';
}

function handleSubmit(): void {
  if (disabledDisplay.value) {
    return;
  }
  // 登录提交前先进行本地必填校验，失败时不触发 submit 事件。
  if (!validate()) {
    return;
  }
  emit('submit', {
    username: formState.username.trim(),
    password: formState.password,
    remember: showRememberDisplay.value ? formState.remember : false
  });
}
</script>

<style scoped>
.vk-login {
  width: 100%;
  min-height: 560px;
  border-radius: 12px;
  overflow: hidden;
}

.vk-login__split {
  min-height: 560px;
  display: flex;
  align-items: stretch;
}

.vk-login__split--center {
  justify-content: center;
  align-items: center;
}

.vk-login__split--right {
  flex-direction: row-reverse;
}

.vk-login__card-panel {
  flex: 1;
  min-width: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
}

.vk-login__split--center .vk-login__card-panel {
  max-width: 520px;
}

.vk-login__card {
  width: 100%;
  max-width: 420px;
  border-radius: 12px;
  box-shadow: 0 12px 36px rgba(15, 23, 42, 0.14);
}

.vk-login__card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.vk-login__title {
  font-size: 20px;
}

.vk-login__options-row {
  margin-bottom: 12px;
}

.vk-login__actions-inline {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.vk-login__submit-btn {
  margin-top: 4px;
}

.vk-login__footer {
  margin-top: 12px;
}

.vk-login__image-panel {
  flex: 1;
  min-height: 560px;
}

.vk-login__image {
  width: 100%;
  height: 100%;
}

.vk-login__image :deep(img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.vk-login__image-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e2e8f0 0%, #cbd5e1 100%);
}

@media (max-width: 960px) {
  .vk-login__split,
  .vk-login__split--right {
    flex-direction: column;
  }

  .vk-login__image-panel {
    min-height: 240px;
  }
}
</style>
