export type ProFormFieldType = 'text' | 'number' | 'textarea';

export interface ProFormFieldSchema {
  key: string;
  label: string;
  type: ProFormFieldType;
  placeholder?: string;
  required?: boolean;
}

export interface ProFormRule {
  key: string;
  validator: (value: unknown, formValues: Record<string, unknown>) => string | null;
}

export interface ProFormProps {
  schema: ProFormFieldSchema[];
  initialValues?: Record<string, unknown>;
  submitter: (values: Record<string, unknown>) => Promise<void>;
  mode: 'create' | 'edit' | 'view';
  rules?: ProFormRule[];
}
