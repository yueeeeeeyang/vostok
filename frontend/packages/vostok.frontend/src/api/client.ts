import axios from 'axios';
import { resolveAdapter } from '../adapters/registry';
import type {
  ApiClient,
  ApiError,
  ApiRequester,
  ApiRequestInput,
  ApiResult,
  BackendProfileInput,
  CreateApiClientOptions,
  NormalizedRequest
} from '../types/public';

// 默认请求器基于 axios，实现统一超时与可选调试日志。
function buildDefaultRequester(enableLogger?: boolean): ApiRequester {
  return async (request: NormalizedRequest): Promise<unknown> => {
    if (enableLogger) {
      console.info('[vostok/frontend] request', request);
    }
    const response = await axios.request({
      url: request.url,
      method: request.method,
      headers: request.headers,
      params: request.query,
      data: request.body,
      timeout: 15000
    });
    return response.data;
  };
}

// 从配置中选择目标后端 profile，未命中直接抛错阻断错误请求。
function resolveProfile(name: string, profiles: BackendProfileInput[]): BackendProfileInput {
  const profile = profiles.find((item) => item.name === name);
  if (!profile) {
    throw new Error(`Backend profile not found: ${name}`);
  }
  return profile;
}

export function createApiClient(options: CreateApiClientOptions): ApiClient {
  const requester = options.requester ?? buildDefaultRequester(options.enableLogger);

  // 统一请求入口：鉴权头注入 -> adapter 组装 -> transport 请求 -> 结果归一化。
  const request = async <T>(input: ApiRequestInput, profileName = options.profile): Promise<ApiResult<T>> => {
    const profile = resolveProfile(profileName, options.profiles);
    const adapter = resolveAdapter(profile, options.adapters);
    const token = options.tokenGetter?.() ?? null;

    const normalized = adapter.buildRequest(profile, {
      ...input,
      headers: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(input.headers ?? {})
      }
    });

    try {
      const raw = await requester(normalized);
      return adapter.normalizeSuccess<T>(raw);
    } catch (error) {
      // 鉴权失败统一走回调，让业务方集中处理跳转登录等行为。
      const normalizedError = adapter.normalizeError(error);
      if (normalizedError.kind === 'auth') {
        options.onAuthFailed?.(normalizedError);
      }
      throw normalizedError;
    }
  };

  return {
    request,
    query<T>(path: string, query?: Record<string, unknown>, profileName?: string): Promise<ApiResult<T>> {
      return request<T>({ method: 'GET', path, query }, profileName);
    },
    mutate<T>(method: 'POST' | 'PUT' | 'PATCH' | 'DELETE', path: string, body?: unknown, profileName?: string): Promise<ApiResult<T>> {
      return request<T>({ method, path, body }, profileName);
    },
    subscribeSse(path: string, onMessage: (event: MessageEvent) => void, profileName?: string): EventSource {
      const profile = resolveProfile(profileName ?? options.profile, options.profiles);
      const source = new EventSource(`${profile.baseURL}${path}`);
      source.onmessage = onMessage;
      return source;
    },
    connectWs(path: string, onMessage: (event: MessageEvent) => void, profileName?: string): WebSocket {
      const profile = resolveProfile(profileName ?? options.profile, options.profiles);
      const wsUrl = `${profile.baseURL}${path}`.replace(/^http/, 'ws');
      const ws = new WebSocket(wsUrl);
      ws.onmessage = onMessage;
      return ws;
    }
  };
}

export function isApiError(error: unknown): error is ApiError {
  return Boolean((error as ApiError)?.kind && (error as ApiError)?.message);
}
