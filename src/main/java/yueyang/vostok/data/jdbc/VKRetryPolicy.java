package yueyang.vostok.data.jdbc;

import yueyang.vostok.data.VKDataConfig;

import java.sql.SQLRecoverableException;
import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.util.HashSet;
import java.util.Set;

/**
 * SQL 重试策略：基于 SQLState 前缀白名单 + 指数退避。
 */
public class VKRetryPolicy {
    private final boolean enabled;
    private final int maxRetries;
    private final long backoffBaseMs;
    private final long backoffMaxMs;
    private final Set<String> sqlStatePrefixes;

    public VKRetryPolicy(VKDataConfig config) {
        this.enabled = config.isRetryEnabled();
        this.maxRetries = Math.max(0, config.getMaxRetries());
        this.backoffBaseMs = Math.max(0, config.getRetryBackoffBaseMs());
        this.backoffMaxMs = Math.max(0, config.getRetryBackoffMaxMs());
        this.sqlStatePrefixes = new HashSet<>();
        if (config.getRetrySqlStatePrefixes() != null) {
            for (String p : config.getRetrySqlStatePrefixes()) {
                if (p != null && !p.isBlank()) {
                    sqlStatePrefixes.add(p.trim());
                }
            }
        }
    }

    
    public boolean isEnabled() {
        return enabled;
    }

    
    public int getMaxRetries() {
        return maxRetries;
    }

    
    public boolean shouldRetry(SQLException e) {
        if (!enabled) {
            return false;
        }
        if (e instanceof SQLTransientException || e instanceof SQLRecoverableException) {
            return true;
        }
        String state = e.getSQLState();
        if (state == null) {
            return false;
        }
        for (String prefix : sqlStatePrefixes) {
            if (state.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    
    public long backoffMs(int attempt) {
        if (attempt <= 0) {
            return 0;
        }
        long delay = backoffBaseMs * (1L << Math.min(20, attempt - 1));
        return Math.min(delay, backoffMaxMs);
    }
}
