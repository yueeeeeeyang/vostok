# @vostok/frontend

一个基于 Vue 3 的前端依赖库，提供：

- 可复用高级组件（如 `VkTable`、`VkForm`）
- 统一 API 客户端能力（`createApiClient`、`useApiClient`）
- 面向异构后端返回的适配器机制

## 快速接入

```ts
import { createApp } from 'vue';
import App from './App.vue';
import { VostokFrontendPlugin } from '@vostok/frontend';

createApp(App).use(VostokFrontendPlugin, {
  api: {
    profile: 'default',
    profiles: [{ name: 'default', baseURL: 'https://api.example.com' }]
  }
});
```

## 文档

1. 使用说明：`frontend/docs/library-usage.md`
2. 组件完整属性文档：`frontend/docs/component-spec.md`
