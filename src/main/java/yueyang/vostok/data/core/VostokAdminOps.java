package yueyang.vostok.data.core;

import yueyang.vostok.data.ds.VKDataSourceHolder;
import yueyang.vostok.data.ds.VKDataSourceRegistry;
import yueyang.vostok.data.meta.MetaRegistry;
import yueyang.vostok.data.pool.VKPoolMetrics;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理与诊断相关操作。
 */
final class VostokAdminOps {
    private VostokAdminOps() {
    }

    static List<VKPoolMetrics> poolMetrics() {
        VostokInternal.ensureInit();
        List<VKPoolMetrics> list = new ArrayList<>();
        for (VKDataSourceHolder holder : VKDataSourceRegistry.allHolders().values()) {
            list.add(new VKPoolMetrics(holder.getName(), holder.getDataSource().getTotalCount(),
                    holder.getDataSource().getActiveCount(), holder.getDataSource().getIdleCount()));
        }
        return list;
    }

    static String report() {
        VostokInternal.ensureInit();
        StringBuilder sb = new StringBuilder();
        sb.append("Vostok Report\n");
        sb.append("EntityCount: ").append(MetaRegistry.size()).append("\n");
        sb.append("MetaLastRefreshAt: ").append(MetaRegistry.getLastRefreshAt()).append("\n");
        for (VKPoolMetrics m : poolMetrics()) {
            sb.append("DataSource:").append(m.getName())
                    .append(" total=").append(m.getTotal())
                    .append(" active=").append(m.getActive())
                    .append(" idle=").append(m.getIdle())
                    .append("\n");
            VKDataSourceHolder holder = VKDataSourceRegistry.get(m.getName());
            var cache = MetaRegistry.getTemplateCache(holder.getName());
            sb.append("  SqlTemplateCacheSize: ").append(cache.size())
                    .append("/").append(cache.getMaxSize()).append("\n");
            String leak = holder.getDataSource().getLastLeakStack();
            if (leak != null && !leak.isBlank()) {
                sb.append("  LeakStack:\n").append(leak).append("\n");
            }
            sb.append(VostokInternal.buildSqlMetricsReport(m.getName()));
        }
        return sb.toString();
    }
}
