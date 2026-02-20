package yueyang.vostok.data.core;

import yueyang.vostok.data.ds.VKDataSourceRegistry;
import yueyang.vostok.util.scan.VKScanner;
import yueyang.vostok.data.sql.VKSqlWhitelist;

/**
 * Vostok 运行期状态（内部使用）。
 */
final class VostokRuntime {
    static final Object LOCK = new Object();
    static volatile boolean initialized = false;
    static final ThreadLocal<String> DS_CONTEXT = new ThreadLocal<>();
    static volatile String[] initPackages = new String[0];
    static volatile VKScanner.EntityScanner SCANNER = VKScanner::scan;

    static {
        VKSqlWhitelist.setDataSourceNameSupplier(() -> {
            String name = DS_CONTEXT.get();
            if (name == null || name.isBlank()) {
                return VKDataSourceRegistry.getDefaultName();
            }
            return name;
        });
    }

    private VostokRuntime() {
    }
}
