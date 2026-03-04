import { ref } from 'vue';
import type { VkTableFetcher, VkTableQuery, VkTableResult } from './vk-table.types';

export function useVkTable<T extends Record<string, unknown>>(fetcher: VkTableFetcher<T>) {
  const loading = ref(false);
  const items = ref<T[]>([]);
  const total = ref(0);
  const error = ref<string | null>(null);

  // 统一封装表格异步加载流程，组件层只负责传递查询条件。
  const load = async (query: VkTableQuery): Promise<void> => {
    loading.value = true;
    error.value = null;
    try {
      const result: VkTableResult<T> = await fetcher(query);
      items.value = result.items;
      total.value = result.total;
    } catch (e) {
      error.value = (e as Error).message;
    } finally {
      loading.value = false;
    }
  };

  return {
    loading,
    items,
    total,
    error,
    load
  };
}
