import type {
  ApiError,
  ApiRequestInput,
  ApiResult,
  BackendAdapter,
  BackendProfileInput,
  NormalizedRequest
} from '../types/public';

// 兼容 Axios 风格错误对象（error.response.status）与通用错误对象（status/httpStatus）。
function getNestedStatus(value: Record<string, unknown>): number {
  const response = value.response as Record<string, unknown> | undefined;
  const statusFromResponse = Number(response?.status ?? 0);
  if (statusFromResponse > 0) {
    return statusFromResponse;
  }
  return Number((value.status ?? value.httpStatus ?? 0) as number);
}

// 从多种常见错误结构中抽取可读信息，保证对外 message 稳定。
function toErrorMessage(raw: unknown, fallback: string): string {
  const value = raw as Record<string, unknown>;
  return String(
    value?.message ??
      (value?.error as Record<string, unknown> | undefined)?.message ??
      (value?.response as Record<string, unknown> | undefined)?.statusText ??
      fallback
  );
}

// 优先处理 GraphQL errors[] 语义，按扩展错误码映射 auth/biz。
function normalizeGraphQLError(raw: unknown): ApiError | null {
  const value = raw as Record<string, unknown>;
  const response = value?.response as Record<string, unknown> | undefined;
  const responseData = response?.data as Record<string, unknown> | undefined;
  const errors = (value?.errors ?? responseData?.errors) as Array<Record<string, unknown>> | undefined;

  if (!Array.isArray(errors) || errors.length === 0) {
    return null;
  }

  const first = errors[0] ?? {};
  const extensions = (first.extensions as Record<string, unknown> | undefined) ?? {};
  const errorCode = String(extensions.code ?? 'GRAPHQL_ERROR');
  const message = String(first.message ?? 'GraphQL error');
  const status = getNestedStatus(value);

  const authCodes = new Set(['UNAUTHENTICATED', 'FORBIDDEN', 'UNAUTHORIZED']);
  const kind = authCodes.has(errorCode.toUpperCase()) ? 'auth' : 'biz';

  return {
    kind,
    code: errorCode,
    message,
    httpStatus: status > 0 ? status : undefined,
    requestId: (value?.requestId as string | undefined) ?? (responseData?.requestId as string | undefined),
    raw
  };
}

// 将不同后端或网络错误统一映射为标准 ApiError。
function normalizeError(raw: unknown): ApiError {
  const value = raw as Record<string, unknown>;
  const graphQLError = normalizeGraphQLError(raw);
  if (graphQLError) {
    return graphQLError;
  }

  const status = getNestedStatus(value);

  if (status === 401 || status === 403) {
    return {
      kind: 'auth',
      code: status,
      message: toErrorMessage(raw, 'Unauthorized'),
      httpStatus: status,
      requestId: value?.requestId as string | undefined,
      raw
    };
  }

  if (status === 429) {
    return {
      kind: 'rate_limit',
      code: status,
      message: toErrorMessage(raw, 'Too many requests'),
      httpStatus: status,
      requestId: value?.requestId as string | undefined,
      raw
    };
  }

  if (status >= 400) {
    return {
      kind: 'biz',
      code: (value?.code as string | number | undefined) ?? status,
      message: toErrorMessage(raw, 'Business error'),
      httpStatus: status,
      requestId: value?.requestId as string | undefined,
      raw
    };
  }

  if (String((raw as Error)?.message ?? '').toLowerCase().includes('timeout')) {
    return {
      kind: 'timeout',
      code: 'TIMEOUT',
      message: 'Request timeout',
      raw
    };
  }

  if (raw instanceof Error) {
    return {
      kind: 'network',
      code: 'NETWORK_ERROR',
      message: raw.message,
      raw
    };
  }

  return {
    kind: 'unknown',
    code: 'UNKNOWN_ERROR',
    message: 'Unknown error',
    raw
  };
}

export class RestDefaultAdapter implements BackendAdapter {
  name = 'rest-default';

  match(_profile: BackendProfileInput): boolean {
    return true;
  }

  buildRequest(profile: BackendProfileInput, input: ApiRequestInput): NormalizedRequest {
    return {
      url: `${profile.baseURL}${input.path}`,
      method: input.method,
      headers: {
        'Content-Type': 'application/json',
        ...(input.headers ?? {})
      },
      query: input.query,
      body: input.body
    };
  }

  // 兼容 data/result/payload 等常见成功包裹结构。
  normalizeSuccess<T>(raw: unknown): ApiResult<T> {
    const value = raw as Record<string, unknown>;
    const data =
      (value?.data as T | undefined) ??
      (value?.result as T | undefined) ??
      (value?.payload as T | undefined) ??
      (raw as T);

    return {
      ok: Boolean(value?.ok ?? value?.success ?? true),
      code: (value?.code as string | number | undefined) ?? 0,
      message: (value?.message as string | undefined) ?? 'OK',
      data,
      requestId: (value?.requestId as string | undefined) ?? (value?.traceId as string | undefined),
      raw
    };
  }

  normalizeError(raw: unknown): ApiError {
    return normalizeError(raw);
  }
}
