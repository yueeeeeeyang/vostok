import type { App, Plugin } from 'vue';
import { createApiClient } from '../api/client';
import { API_CLIENT_KEY, FRONTEND_OPTIONS_KEY } from '../symbols';
import type { VostokFrontendOptions } from '../types/public';
import { registerAdapter } from '../adapters/registry';
import { componentRegistry } from '../components';

export function installVostokFrontend(app: App, options: VostokFrontendOptions): void {
  // 先注册外部传入的自定义 adapter，确保其匹配优先级高于默认 adapter。
  for (const adapter of options.api.adapters ?? []) {
    registerAdapter(adapter);
  }

  const apiClient = createApiClient({
    profile: options.api.profile,
    profiles: options.api.profiles,
    adapters: options.api.adapters,
    requester: options.api.requester,
    tokenGetter: options.auth?.tokenGetter,
    onAuthFailed: options.auth?.onAuthFailed,
    enableLogger: options.debug?.enableLogger
  });

  // 通过 provide/inject 暴露统一 API 客户端与运行配置。
  app.provide(API_CLIENT_KEY, apiClient);
  app.provide(FRONTEND_OPTIONS_KEY, options);

  // 批量全局注册组件，业务项目可直接在模板中使用。
  Object.entries(componentRegistry).forEach(([name, component]) => {
    app.component(name, component);
  });
}

export const VostokFrontendPlugin: Plugin = {
  install(app: App, options: VostokFrontendOptions) {
    installVostokFrontend(app, options);
  }
};
