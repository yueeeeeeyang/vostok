import './styles/index.css';

export { VostokFrontendPlugin } from './plugin/install';
export { createVostokFrontend } from './plugin/create-vostok-frontend';
export { useApiClient } from './composables/use-api-client';

export * from './components';
export * from './api';
export * from './adapters';
export type { VostokFrontendOptions, ApiClient, ApiResult, ApiError, BackendAdapter } from './types/public';
export type { VkTableProps } from './components/vk-table/vk-table.types';
export type { VkFormProps } from './components/vk-form/vk-form.types';
export type { VkSelectorProps } from './components/vk-selector/vk-selector.types';
export type {
  VkAdminLayoutProps,
  VkAppFieldMap,
  VkMenuFieldMap,
  VkMenuItem,
  VkNotificationItem,
  VkUserProfile,
  VkThemeMode
} from './components/vk-admin-layout/vk-admin-layout.types';
