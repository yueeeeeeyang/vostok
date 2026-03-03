# 前端库架构

## 工作区形态

- `packages/vostok.frontend`：可发布的前端库包
- `playground`：消费库能力的演示工程
- `tests`：根级契约测试与 e2e 测试

## 分层设计

1. `plugin`：Vue 插件入口与依赖注入。
2. `api`：协议无关的 API 客户端（REST/SSE/WS）。
3. `adapters`：后端返回结构归一化策略层。
4. `components`：可复用高级组件层。
5. `styles`：库级公共样式入口。

## 约束

- 不直接依赖任何具体后端实现细节。
- 后端差异必须在 adapter 层消化，业务层只使用统一结果模型。
