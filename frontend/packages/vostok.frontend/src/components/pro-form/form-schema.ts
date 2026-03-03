import type { ProFormFieldSchema } from './pro-form.types';

export function toDefaultValues(schema: ProFormFieldSchema[]): Record<string, unknown> {
  return schema.reduce<Record<string, unknown>>((acc, field) => {
    acc[field.key] = '';
    return acc;
  }, {});
}
