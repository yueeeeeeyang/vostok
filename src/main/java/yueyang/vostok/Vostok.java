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

/**
 * Vostok 统一入口。
 */
public final class Vostok {
    private Vostok() {
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
}
