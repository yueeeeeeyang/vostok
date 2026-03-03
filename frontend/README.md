# frontend 工作区

用于维护 Vue 依赖库 `@vostok/frontend` 的 Monorepo 工作区。

## 目录说明

- `packages/vostok.frontend`：可发布的库包
- `playground`：示例业务工程（消费库能力）
- `tests`：契约测试与 e2e 测试

## 常用命令

```bash
pnpm install
pnpm build:lib
pnpm dev:playground
pnpm type-check
pnpm test
pnpm test:e2e
```
