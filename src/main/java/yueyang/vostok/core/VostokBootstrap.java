package yueyang.vostok.core;

import yueyang.vostok.config.DataSourceConfig;
import yueyang.vostok.ddl.VKDdlValidator;
import yueyang.vostok.ds.VKDataSourceRegistry;
import yueyang.vostok.meta.MetaRegistry;
import yueyang.vostok.plugin.VKInterceptor;
import yueyang.vostok.plugin.VKInterceptorRegistry;
import yueyang.vostok.scan.ClassScanner;
import yueyang.vostok.sql.VKSqlWhitelist;
import yueyang.vostok.util.VKAssert;
import yueyang.vostok.util.VKLog;

import java.util.Set;
import java.util.function.Supplier;

/**
 * 初始化与运行期管理。
 */
final class VostokBootstrap {
    private VostokBootstrap() {
    }

    static void init(DataSourceConfig config, String... basePackages) {
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
                throw new yueyang.vostok.exception.VKConfigException("JDBC driver not found: " + config.getDriver());
            }

            try {
                VKDataSourceRegistry.register("default", config);
                VKDataSourceRegistry.setDefaultName("default");
                VostokRuntime.initPackages = basePackages == null ? new String[0] : basePackages.clone();

                if (basePackages == null || basePackages.length == 0) {
                    VKLog.warn("No basePackages provided, scanning full classpath.");
                }

                Set<Class<?>> classes = VostokRuntime.SCANNER.scan(basePackages);
                for (Class<?> clazz : classes) {
                    MetaRegistry.register(clazz);
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

    static void registerDataSource(String name, DataSourceConfig config) {
        VostokInternal.ensureInit();
        VostokInternal.validateConfig(config);
        VKDataSourceRegistry.register(name, config);
        if (config.isValidateDdl()) {
            VKDdlValidator.validate(VKDataSourceRegistry.get(name).getDataSource(), MetaRegistry.all(), config.getDdlSchema());
        }
    }

    static void refreshMeta() {
        refreshMeta(VostokRuntime.initPackages);
    }

    static void refreshMeta(String... basePackages) {
        VostokInternal.ensureInit();
        Set<Class<?>> classes = VostokRuntime.SCANNER.scan(basePackages);
        MetaRegistry.refreshAll(classes);
        VostokInternal.clearTemplateCaches();
        if (VostokInternal.currentConfig().isValidateDdl()) {
            VKDdlValidator.validate(VostokInternal.currentHolder().getDataSource(), MetaRegistry.all(), VostokInternal.currentConfig().getDdlSchema());
        }
    }

    static void setScanner(ClassScanner.EntityScanner scanner) {
        VKAssert.notNull(scanner, "EntityScanner is null");
        VostokRuntime.SCANNER = scanner;
    }

    static void withDataSource(String name, Runnable action) {
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

    static <T> T withDataSource(String name, Supplier<T> supplier) {
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

    static void registerInterceptor(VKInterceptor interceptor) {
        VKInterceptorRegistry.register(interceptor);
    }

    static void registerRawSql(String... sqls) {
        VKSqlWhitelist.registerRaw(sqls);
    }

    static void registerRawSql(String dataSourceName, String[] sqls) {
        VKSqlWhitelist.registerRaw(dataSourceName, sqls);
    }

    static void registerSubquery(String... sqls) {
        VKSqlWhitelist.registerSubquery(sqls);
    }

    static void registerSubquery(String dataSourceName, String[] sqls) {
        VKSqlWhitelist.registerSubquery(dataSourceName, sqls);
    }

    static void clearInterceptors() {
        VKInterceptorRegistry.clear();
    }

    static void close() {
        synchronized (VostokRuntime.LOCK) {
            VKDataSourceRegistry.clear();
            VostokRuntime.initialized = false;
            VostokRuntime.DS_CONTEXT.remove();
            VostokRuntime.initPackages = new String[0];
        }
    }
}
