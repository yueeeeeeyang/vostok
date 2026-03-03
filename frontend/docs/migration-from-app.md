# 迁移指南：应用形态 `frontend` -> 依赖库形态 `@vostok/frontend`

## 变化说明

1. `frontend` 根目录改为 workspace，不再作为可运行应用。
2. 可发布包固定在 `packages/vostok.frontend`。
3. 示例业务工程迁移至 `playground`。

## 迁移步骤

1. 将本地应用内导入替换为包导入：
   - `@/components/...` -> `@vostok/frontend/components`
   - 本地数据请求客户端 -> `useApiClient()`
2. 在业务应用入口通过插件完成初始化，并配置后端 profile。
3. 将页面路由等 app-only 代码迁回业务工程，不放在库内。

## 废弃项

- 不再维护旧包名 `vostok.frontend`。
