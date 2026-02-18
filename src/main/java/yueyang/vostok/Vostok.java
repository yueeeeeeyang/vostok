package yueyang.vostok;

import yueyang.vostok.data.VostokData;
import yueyang.vostok.file.VostokFile;
import yueyang.vostok.log.VostokLog;
import yueyang.vostok.web.VostokWeb;

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
     * Log entry.
     */
    public static final class Log extends VostokLog {
        private Log() {
        }
    }
}
