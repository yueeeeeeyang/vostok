<template>
  <n-card class="pro-form-card">
    <n-form :model="formValues" label-placement="top">
      <n-form-item
        v-for="field in schema"
        :key="field.key"
        :label="field.label"
        :path="field.key"
        :validation-status="fieldErrors[field.key] ? 'error' : undefined"
        :feedback="fieldErrors[field.key]"
      >
        <n-input
          v-if="field.type === 'text' || field.type === 'textarea'"
          :type="field.type === 'textarea' ? 'textarea' : 'text'"
          :value="toInputValue(formValues[field.key])"
          :placeholder="field.placeholder"
          :disabled="isView"
          @update:value="(value) => setFieldValue(field.key, value)"
        />
        <n-input-number
          v-else
          :value="toNumberValue(formValues[field.key])"
          :disabled="isView"
          clearable
          style="width: 100%"
          @update:value="(value) => setFieldValue(field.key, value)"
        />
      </n-form-item>

      <n-space v-if="mode !== 'view'" justify="end">
        <n-button type="primary" :loading="submitting" @click="onSubmit">
          {{ submitting ? '保存中...' : '提交' }}
        </n-button>
      </n-space>
    </n-form>
  </n-card>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { NButton, NCard, NForm, NFormItem, NInput, NInputNumber, NSpace } from 'naive-ui';
import { toDefaultValues } from './form-schema';
import type { ProFormProps } from './pro-form.types';

const props = withDefaults(defineProps<ProFormProps>(), {
  initialValues: () => ({}),
  rules: () => []
});

const formValues = reactive<Record<string, unknown>>({
  ...toDefaultValues(props.schema),
  ...props.initialValues
});
const fieldErrors = reactive<Record<string, string>>({});
const submitting = ref(false);

const isView = computed(() => props.mode === 'view');
const schema = computed(() => props.schema);
const mode = computed(() => props.mode);

// 初始值变化时重建表单模型，保证弹窗/抽屉复用场景数据同步。
watch(
  () => props.initialValues,
  (value) => {
    Object.assign(formValues, toDefaultValues(props.schema), value ?? {});
  },
  { deep: true }
);

function setFieldValue(key: string, value: unknown): void {
  formValues[key] = value;
}

function toInputValue(value: unknown): string {
  return value === undefined || value === null ? '' : String(value);
}

function toNumberValue(value: unknown): number | null {
  if (typeof value === 'number') {
    return value;
  }
  if (typeof value === 'string' && value.trim() !== '' && !Number.isNaN(Number(value))) {
    return Number(value);
  }
  return null;
}

function validate(): boolean {
  Object.keys(fieldErrors).forEach((key) => {
    delete fieldErrors[key];
  });

  for (const field of props.schema) {
    const value = formValues[field.key];
    if (field.required && String(value ?? '').trim() === '') {
      fieldErrors[field.key] = `${field.label} is required`;
    }
  }

  for (const rule of props.rules ?? []) {
    const message = rule.validator(formValues[rule.key], formValues);
    if (message) {
      fieldErrors[rule.key] = message;
    }
  }

  return Object.keys(fieldErrors).length === 0;
}

async function onSubmit(): Promise<void> {
  if (!validate()) {
    return;
  }
  submitting.value = true;
  try {
    await props.submitter({ ...formValues });
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
.pro-form-card {
  width: 100%;
}
</style>
