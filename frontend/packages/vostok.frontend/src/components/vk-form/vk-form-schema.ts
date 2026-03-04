import type { VkFormFieldSchema } from './vk-form.types';

export function toDefaultValues(schema: VkFormFieldSchema[]): Record<string, unknown> {
  return schema.reduce<Record<string, unknown>>((acc, field) => {
    acc[field.key] = '';
    return acc;
  }, {});
}
