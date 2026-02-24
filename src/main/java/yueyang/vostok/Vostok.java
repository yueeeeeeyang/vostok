package yueyang.vostok;

import yueyang.vostok.data.VostokData;
import yueyang.vostok.cache.VostokCache;
import yueyang.vostok.config.VostokConfig;
import yueyang.vostok.event.VostokEvent;
import yueyang.vostok.file.VostokFile;
import yueyang.vostok.log.VostokLog;
import yueyang.vostok.security.VostokSecurity;
import yueyang.vostok.web.VostokWeb;
import yueyang.vostok.http.VostokHttp;
import yueyang.vostok.util.VostokUtil;
import yueyang.vostok.ai.VostokAI;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Vostok 统一入口。
 */
public final class Vostok {
    private static final Object INIT_LOCK = new Object();

    private Vostok() {
    }

    public static void init(Consumer<VKInitConfig.Builder> customizer) {
        if (customizer == null) {
            throw new VKBootstrapException("init", "VKInitConfig customizer is null");
        }
        VKInitConfig.Builder builder = VKInitConfig.builder();
        customizer.accept(builder);
        init(builder.build());
    }

    public static void init(VKInitConfig config) {
        if (config == null) {
            throw new VKBootstrapException("init", "VKInitConfig is null");
        }
        String[] dataPackages = config.getDataPackages();
        if (config.getDataConfig() == null && dataPackages.length > 0) {
            throw new VKBootstrapException("init", "dataPackages is set but dataConfig is null");
        }

        synchronized (INIT_LOCK) {
            List<Runnable> rollback = new ArrayList<>();
            try {
                if (config.getConfigOptions() != null) {
                    boolean already = Config.started();
                    Config.init(config.getConfigOptions());
                    if (!already) {
                        rollback.add(Config::close);
                    }
                }

                if (config.getLogConfig() != null) {
                    boolean already = Log.initialized();
                    Log.init(config.getLogConfig());
                    if (!already) {
                        rollback.add(Log::close);
                    }
                }

                if (config.getSecurityConfig() != null) {
                    boolean already = Security.started();
                    Security.init(config.getSecurityConfig());
                    if (!already) {
                        rollback.add(Security::close);
                    }
                }

                if (config.getDataConfig() != null) {
                    boolean already = Data.started();
                    Data.init(config.getDataConfig(), dataPackages);
                    if (!already) {
                        rollback.add(Data::close);
                    }
                }

                if (config.getCacheConfig() != null) {
                    boolean already = Cache.started();
                    Cache.init(config.getCacheConfig());
                    if (!already) {
                        rollback.add(Cache::close);
                    }
                }

                if (config.getEventConfig() != null) {
                    boolean already = Event.started();
                    Event.init(config.getEventConfig());
                    if (!already) {
                        rollback.add(Event::close);
                    }
                }

                if (config.getHttpConfig() != null) {
                    boolean already = Http.started();
                    Http.init(config.getHttpConfig());
                    if (!already) {
                        rollback.add(Http::close);
                    }
                }

                if (config.getFileConfig() != null) {
                    boolean already = File.started();
                    File.init(config.getFileConfig());
                    if (!already) {
                        rollback.add(File::close);
                    }
                }

                if (config.getAiConfig() != null) {
                    boolean already = AI.started();
                    AI.init(config.getAiConfig());
                    if (!already) {
                        rollback.add(AI::close);
                    }
                }

                boolean needWeb = config.getWebConfig() != null
                        || config.getWebSetup() != null
                        || config.isWebStart();
                if (needWeb) {
                    boolean already = Web.started();
                    var web = config.getWebConfig() == null ? Web.init(8080) : Web.init(config.getWebConfig());
                    if (config.getWebSetup() != null) {
                        config.getWebSetup().accept(web);
                    }
                    if (config.isWebStart()) {
                        Web.start();
                    }
                    if (!already) {
                        rollback.add(() -> {
                            try {
                                Web.stop();
                            } catch (Exception ignore) {
                            }
                        });
                    }
                }
            } catch (RuntimeException e) {
                reverseRun(rollback);
                throw new VKBootstrapException("init", "Vostok init failed", e);
            }
        }
    }

    public static void close() {
        synchronized (INIT_LOCK) {
            List<Runnable> actions = List.of(
                    () -> {
                        try {
                            Web.stop();
                        } catch (Exception ignore) {
                        }
                    },
                    () -> {
                        try {
                            AI.close();
                        } catch (Exception ignore) {
                        }
                    },
                    () -> {
                        try {
                            File.close();
                        } catch (Exception ignore) {
                        }
                    },
                    () -> {
                        try {
                            Http.close();
                        } catch (Exception ignore) {
                        }
                    },
                    () -> {
                        try {
                            Event.close();
                        } catch (Exception ignore) {
                        }
                    },
                    () -> {
                        try {
                            Cache.close();
                        } catch (Exception ignore) {
                        }
                    },
                    () -> {
                        try {
                            Data.close();
                        } catch (Exception ignore) {
                        }
                    },
                    () -> {
                        try {
                            Security.close();
                        } catch (Exception ignore) {
                        }
                    },
                    () -> {
                        try {
                            Log.close();
                        } catch (Exception ignore) {
                        }
                    },
                    () -> {
                        try {
                            Config.close();
                        } catch (Exception ignore) {
                        }
                    }
            );
            reverseRun(actions);
        }
    }

    private static void reverseRun(List<Runnable> actions) {
        for (int i = actions.size() - 1; i >= 0; i--) {
            try {
                actions.get(i).run();
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * 数据入口（统一对外 API）。
     */
    public static final class Data extends VostokData {
        private Data() {
        }
    }

    /**
     * Cache entry (redis/memory and extensible providers).
     */
    public static final class Cache extends VostokCache {
        private Cache() {
        }
    }

    /**
     * Web 入口（统一对外 API）。
     */
    public static final class Web extends VostokWeb {
        private Web() {
        }
    }

    /**
     * File entry (local text file by default, extensible to OSS/object storage).
     */
    public static final class File extends VostokFile {
        private File() {
        }
    }

    /**
     * Config entry.
     */
    public static final class Config extends VostokConfig {
        private Config() {
        }
    }

    /**
     * Log entry.
     */
    public static final class Log extends VostokLog {
        private Log() {
        }
    }

    /**
     * Security entry.
     */
    public static final class Security extends VostokSecurity {
        private Security() {
        }
    }

    /**
     * Event entry.
     */
    public static final class Event extends VostokEvent {
        private Event() {
        }
    }

    /**
     * Http entry.
     */
    public static final class Http extends VostokHttp {
        private Http() {
        }
    }

    /**
     * Util entry.
     */
    public static final class Util extends VostokUtil {
        private Util() {
        }
    }

    /**
     * AI entry.
     */
    public static final class AI extends VostokAI {
        private AI() {
        }
    }
}
