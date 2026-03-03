import { inject } from 'vue';
import { API_CLIENT_KEY } from '../symbols';
import type { ApiClient } from '../types/public';

export function useApiClient(): ApiClient {
  // 强制要求先执行 app.use(VostokFrontendPlugin)，避免组件绕过统一数据层。
  const client = inject(API_CLIENT_KEY);
  if (!client) {
    throw new Error('ApiClient is not provided. Please call app.use(VostokFrontendPlugin, options).');
  }
  return client;
}
