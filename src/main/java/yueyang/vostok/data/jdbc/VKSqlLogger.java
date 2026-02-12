package yueyang.vostok.data.jdbc;

import yueyang.vostok.data.VKDataConfig;
import yueyang.vostok.util.VKLog;

import java.util.Arrays;

public final class VKSqlLogger {
    private final boolean logSql;
    private final boolean logParams;
    private final long slowSqlMs;

    public VKSqlLogger(VKDataConfig config) {
        this.logSql = config.isLogSql();
        this.logParams = config.isLogParams();
        this.slowSqlMs = config.getSlowSqlMs();
    }

    public void logSql(String sql, Object[] params) {
        if (!logSql) {
            return;
        }
        if (logParams) {
            VKLog.info(formatSql(sql, params, true));
        } else {
            VKLog.info(formatSql(sql, params, false));
        }
    }

    public void logSlow(String sql, Object[] params, long costMs) {
        if (slowSqlMs <= 0 || costMs < slowSqlMs) {
            return;
        }
        if (logParams) {
            VKLog.warn(formatSlow(sql, params, costMs, true));
        } else {
            VKLog.warn(formatSlow(sql, params, costMs, false));
        }
    }

    public static String formatSql(String sql, Object[] params, boolean withParams) {
        if (withParams) {
            return "SQL: " + sql + " | params=" + Arrays.toString(params);
        }
        return "SQL: " + sql;
    }

    public static String formatSlow(String sql, Object[] params, long costMs, boolean withParams) {
        if (withParams) {
            return "SLOW SQL: " + sql + " | costMs=" + costMs + " | params=" + Arrays.toString(params);
        }
        return "SLOW SQL: " + sql + " | costMs=" + costMs;
    }

    public boolean isLogEnabled() {
        return logSql;
    }

    public boolean isSlowEnabled() {
        return slowSqlMs > 0;
    }
}
