import './styles/index.css';

export { VostokFrontendPlugin } from './plugin/install';
export { createVostokFrontend } from './plugin/create-vostok-frontend';
export { useApiClient } from './composables/use-api-client';

export * from './components';
export * from './api';
export * from './adapters';
export type { VostokFrontendOptions, ApiClient, ApiResult, ApiError, BackendAdapter } from './types/public';
export type { ProTableProps } from './components/pro-table/pro-table.types';
export type { ProFormProps } from './components/pro-form/pro-form.types';
