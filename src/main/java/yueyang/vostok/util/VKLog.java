package yueyang.vostok.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLoggerFactory;

public final class VKLog {
    private static final Logger LOG = LoggerFactory.getLogger("Vostok");
    private static final boolean FALLBACK_STDOUT = isNoOpLogger();

    private VKLog() {
    }

    public static void info(String msg) {
        if (FALLBACK_STDOUT) {
            System.out.println("[INFO] " + msg);
            return;
        }
        LOG.info(msg);
    }

    public static void warn(String msg) {
        if (FALLBACK_STDOUT) {
            System.out.println("[WARN] " + msg);
            return;
        }
        LOG.warn(msg);
    }

    public static void error(String msg, Throwable t) {
        if (FALLBACK_STDOUT) {
            System.err.println("[ERROR] " + msg);
            if (t != null) {
                t.printStackTrace(System.err);
            }
            return;
        }
        LOG.error(msg, t);
    }

    private static boolean isNoOpLogger() {
        try {
            return LoggerFactory.getILoggerFactory() instanceof NOPLoggerFactory;
        } catch (Throwable t) {
            return true;
        }
    }
}
