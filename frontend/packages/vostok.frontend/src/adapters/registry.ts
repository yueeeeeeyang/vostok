import type { BackendAdapter, BackendProfileInput } from '../types/public';
import { RestDefaultAdapter } from './default-adapter';

const globalAdapters: BackendAdapter[] = [new RestDefaultAdapter()];

export function registerAdapter(adapter: BackendAdapter): void {
  globalAdapters.unshift(adapter);
}

export function resolveAdapter(profile: BackendProfileInput, localAdapters?: BackendAdapter[]): BackendAdapter {
  const adapters = [...(localAdapters ?? []), ...globalAdapters];
  return adapters.find((adapter) => adapter.match(profile)) ?? globalAdapters[globalAdapters.length - 1];
}

export function getRegisteredAdapters(): BackendAdapter[] {
  return [...globalAdapters];
}
