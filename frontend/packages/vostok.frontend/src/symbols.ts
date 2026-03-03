import type { InjectionKey } from 'vue';
import type { ApiClient, VostokFrontendOptions } from './types/public';

export const API_CLIENT_KEY: InjectionKey<ApiClient> = Symbol('vostok_frontend_api_client');
export const FRONTEND_OPTIONS_KEY: InjectionKey<VostokFrontendOptions> = Symbol('vostok_frontend_options');
