export type VkFormFieldType = 'text' | 'number' | 'textarea';

export interface VkFormFieldSchema {
  key: string;
  label: string;
  type: VkFormFieldType;
  placeholder?: string;
  required?: boolean;
}

export interface VkFormRule {
  key: string;
  validator: (value: unknown, formValues: Record<string, unknown>) => string | null;
}

export interface VkFormProps {
  schema: VkFormFieldSchema[];
  initialValues?: Record<string, unknown>;
  submitter: (values: Record<string, unknown>) => Promise<void>;
  mode: 'create' | 'edit' | 'view';
  rules?: VkFormRule[];
}
