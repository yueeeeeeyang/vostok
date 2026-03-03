# 适配器规范（`@vostok/frontend`）

## 接口定义：`BackendAdapter`

```ts
interface BackendAdapter {
  name: string;
  match(profile: BackendProfileInput): boolean;
  buildRequest(profile: BackendProfileInput, input: ApiRequestInput): NormalizedRequest;
  normalizeSuccess<T>(raw: unknown): ApiResult<T>;
  normalizeError(raw: unknown): ApiError;
}
```

## 职责

1. 根据 profile 判断当前 adapter 是否可处理该后端。
2. 将通用请求结构转换为目标后端可接受的请求结构。
3. 将异构成功返回统一为 `ApiResult<T>`。
4. 将后端错误统一映射为 `ApiError`。

## 规则

1. Adapter 必须保持无状态。
2. `normalizeSuccess` 需保留 `raw` 原始数据便于排障。
3. `normalizeError` 必须稳定输出 `kind`。
4. 分页字段存在差异时，必须在 adapter 层完成映射。
