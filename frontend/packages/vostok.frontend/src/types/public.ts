export type ApiErrorKind =
  | 'network'
  | 'auth'
  | 'biz'
  | 'rate_limit'
  | 'timeout'
  | 'unknown';

export interface ApiResult<T> {
  ok: boolean;
  code: string | number;
  message: string;
  data: T;
  requestId?: string;
  raw?: unknown;
}

export interface ApiError {
  kind: ApiErrorKind;
  code: string | number;
  message: string;
  httpStatus?: number;
  requestId?: string;
  raw?: unknown;
}

export interface BackendProfileInput {
  name: string;
  baseURL: string;
  authScheme?: 'bearer' | 'none';
}

export interface ApiRequestInput {
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  path: string;
  query?: Record<string, unknown>;
  body?: unknown;
  headers?: Record<string, string>;
}

export interface NormalizedRequest {
  url: string;
  method: string;
  headers: Record<string, string>;
  query?: Record<string, unknown>;
  body?: unknown;
}

export interface BackendAdapter {
  name: string;
  match(profile: BackendProfileInput): boolean;
  buildRequest(profile: BackendProfileInput, input: ApiRequestInput): NormalizedRequest;
  normalizeSuccess<T>(raw: unknown): ApiResult<T>;
  normalizeError(raw: unknown): ApiError;
}

export type ApiRequester = (request: NormalizedRequest) => Promise<unknown>;

export interface ApiClient {
  request<T>(request: ApiRequestInput, profileName?: string): Promise<ApiResult<T>>;
  query<T>(path: string, query?: Record<string, unknown>, profileName?: string): Promise<ApiResult<T>>;
  mutate<T>(method: 'POST' | 'PUT' | 'PATCH' | 'DELETE', path: string, body?: unknown, profileName?: string): Promise<ApiResult<T>>;
  subscribeSse(path: string, onMessage: (event: MessageEvent) => void, profileName?: string): EventSource;
  connectWs(path: string, onMessage: (event: MessageEvent) => void, profileName?: string): WebSocket;
}

export interface CreateApiClientOptions {
  profile: string;
  profiles: BackendProfileInput[];
  adapters?: BackendAdapter[];
  tokenGetter?: () => string | null;
  onAuthFailed?: (error: ApiError) => void;
  enableLogger?: boolean;
  requester?: ApiRequester;
}

export interface VostokFrontendOptions {
  api: {
    profile: string;
    profiles: BackendProfileInput[];
    adapters?: BackendAdapter[];
    requester?: ApiRequester;
  };
  auth?: {
    tokenGetter?: () => string | null;
    onAuthFailed?: (error: ApiError) => void;
  };
  ui?: {
    themeOverrides?: Record<string, unknown>;
    locale?: string;
  };
  debug?: {
    enableLogger?: boolean;
  };
}
