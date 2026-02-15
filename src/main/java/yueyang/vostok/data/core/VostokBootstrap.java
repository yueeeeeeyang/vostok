package yueyang.vostok.data.core;

import yueyang.vostok.data.VKDataConfig;
import yueyang.vostok.data.ddl.VKDdlValidator;
import yueyang.vostok.data.ds.VKDataSourceRegistry;
import yueyang.vostok.data.meta.MetaRegistry;
import yueyang.vostok.data.plugin.VKInterceptor;
import yueyang.vostok.data.plugin.VKInterceptorRegistry;
import yueyang.vostok.common.scan.VKScanner;
import yueyang.vostok.data.sql.VKSqlWhitelist;
import yueyang.vostok.util.VKAssert;
import yueyang.vostok.util.VKLog;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Supplier;

/**
 * 初始化与运行期管理。
 */
public final class VostokBootstrap {
    private VostokBootstrap() {
    }

    public static void init(VKDataConfig config, String... basePackages) {
        if (VostokRuntime.initialized) {
            return;
        }
        synchronized (VostokRuntime.LOCK) {
            if (VostokRuntime.initialized) {
                return;
            }
            VostokInternal.validateConfig(config);

            try {
                Class.forName(config.getDriver());
            } catch (ClassNotFoundException e) {
                throw new yueyang.vostok.data.exception.VKConfigException("JDBC driver not found: " + config.getDriver());
            }

            try {
                VKDataSourceRegistry.register("default", config);
                VKDataSourceRegistry.setDefaultName("default");
                VostokRuntime.initPackages = basePackages == null ? new String[0] : basePackages.clone();

                if (basePackages == null || basePackages.length == 0) {
                    VKLog.warn("No basePackages provided, scanning full classpath.");
                }

                Set<Class<?>> classes = VostokRuntime.SCANNER.scan(basePackages);
                MetaRegistry.refreshAll(classes, VKDataSourceRegistry.all().entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getConfig())));

                if (config.isAutoCreateTable()) {
                    autoCreateTables();
                }
                if (config.isValidateDdl()) {
                    VKDdlValidator.validate(VostokInternal.currentHolder().getDataSource(), MetaRegistry.all(), config.getDdlSchema());
                }

                VKLog.info("Vostok initialized. Entity count: " + MetaRegistry.size());
                VostokRuntime.initialized = true;
            } catch (RuntimeException e) {
                VKDataSourceRegistry.clear();
                VostokRuntime.initialized = false;
                throw e;
            }
        }
    }

    public static void registerDataSource(String name, VKDataConfig config) {
        VostokInternal.ensureInit();
        VostokInternal.validateConfig(config);
        VKDataSourceRegistry.register(name, config);
        MetaRegistry.registerDataSource(name, config);
        if (config.isAutoCreateTable()) {
            VKDdlValidator.createMissingTables(VKDataSourceRegistry.get(name).getDataSource(), MetaRegistry.all(), config.getDdlSchema(), config);
        }
        if (config.isValidateDdl()) {
            VKDdlValidator.validate(VKDataSourceRegistry.get(name).getDataSource(), MetaRegistry.all(), config.getDdlSchema());
        }
    }

    public static void refreshMeta() {
        refreshMeta(VostokRuntime.initPackages);
    }

    public static void refreshMeta(String... basePackages) {
        VostokInternal.ensureInit();
        Set<Class<?>> classes = VostokRuntime.SCANNER.scan(basePackages);
        MetaRegistry.refreshAll(classes, VKDataSourceRegistry.all().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getConfig())));
        if (VostokInternal.currentConfig().isAutoCreateTable()) {
            autoCreateTables();
        }
        if (VostokInternal.currentConfig().isValidateDdl()) {
            VKDdlValidator.validate(VostokInternal.currentHolder().getDataSource(), MetaRegistry.all(), VostokInternal.currentConfig().getDdlSchema());
        }
    }

    private static void autoCreateTables() {
        for (Map.Entry<String, yueyang.vostok.data.ds.VKDataSourceHolder> entry : VKDataSourceRegistry.allHolders().entrySet()) {
            var holder = entry.getValue();
            var config = holder.getConfig();
            VKDdlValidator.createMissingTables(holder.getDataSource(), MetaRegistry.all(), config.getDdlSchema(), config);
        }
    }

    public static void setScanner(VKScanner.EntityScanner scanner) {
        VKAssert.notNull(scanner, "EntityScanner is null");
        VostokRuntime.SCANNER = scanner;
    }

    public static void withDataSource(String name, Runnable action) {
        String prev = VostokRuntime.DS_CONTEXT.get();
        VostokRuntime.DS_CONTEXT.set(name);
        try {
            action.run();
        } finally {
            if (prev == null) {
                VostokRuntime.DS_CONTEXT.remove();
            } else {
                VostokRuntime.DS_CONTEXT.set(prev);
            }
        }
    }

    public static <T> T withDataSource(String name, Supplier<T> supplier) {
        String prev = VostokRuntime.DS_CONTEXT.get();
        VostokRuntime.DS_CONTEXT.set(name);
        try {
            return supplier.get();
        } finally {
            if (prev == null) {
                VostokRuntime.DS_CONTEXT.remove();
            } else {
                VostokRuntime.DS_CONTEXT.set(prev);
            }
        }
    }

    public static void registerInterceptor(VKInterceptor interceptor) {
        VKInterceptorRegistry.register(interceptor);
    }

    public static void registerRawSql(String... sqls) {
        VKSqlWhitelist.registerRaw(sqls);
    }

    public static void registerRawSql(String dataSourceName, String[] sqls) {
        VKSqlWhitelist.registerRaw(dataSourceName, sqls);
    }

    public static void registerSubquery(String... sqls) {
        VKSqlWhitelist.registerSubquery(sqls);
    }

    public static void registerSubquery(String dataSourceName, String[] sqls) {
        VKSqlWhitelist.registerSubquery(dataSourceName, sqls);
    }

    public static void clearInterceptors() {
        VKInterceptorRegistry.clear();
    }

    public static void close() {
        synchronized (VostokRuntime.LOCK) {
            VKDataSourceRegistry.clear();
            MetaRegistry.clear();
            VostokRuntime.initialized = false;
            VostokRuntime.DS_CONTEXT.remove();
            VostokRuntime.initPackages = new String[0];
        }
    }
}
