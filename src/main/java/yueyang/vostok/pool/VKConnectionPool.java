package yueyang.vostok.pool;

import yueyang.vostok.config.DataSourceConfig;
import yueyang.vostok.util.VKAssert;
import yueyang.vostok.util.VKLog;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class VKConnectionPool {
    private final DataSourceConfig config;
    private final ArrayBlockingQueue<PooledEntry> idleQueue;
    private final AtomicInteger totalCount = new AtomicInteger(0);
    private final ScheduledExecutorService housekeeper;
    private final ConcurrentHashMap<Connection, ConnectionDefaults> defaults = new ConcurrentHashMap<>();
    private volatile String lastLeakStack;
    private volatile boolean closed;

    public VKConnectionPool(DataSourceConfig config) {
        this.config = config;
        this.idleQueue = new ArrayBlockingQueue<>(config.getMaxActive());
        if (config.isPreheatEnabled()) {
            preload();
        }
        this.housekeeper = startHousekeeper();
    }

    private void preload() {
        for (int i = 0; i < config.getMinIdle(); i++) {
            Connection conn = createConnection();
            if (conn != null) {
                idleQueue.offer(new PooledEntry(conn, System.currentTimeMillis()));
            }
        }
    }

    public Connection borrow() throws SQLException {
        VKAssert.isTrue(!closed, "Connection pool is closed");

        while (true) {
            PooledEntry entry = idleQueue.poll();
            if (entry != null) {
                if (isIdleExpired(entry)) {
                    closeSilently(entry.conn);
                    continue;
                }
                return validate(entry.conn);
            }

            if (totalCount.get() < config.getMaxActive()) {
                Connection created = createConnection();
                if (created != null) {
                    return created;
                }
                throw new SQLException("Failed to create connection");
            }

            try {
                entry = idleQueue.poll(config.getMaxWaitMs(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SQLException("Interrupted while waiting for a connection", e);
            }

            if (entry == null) {
                throw new SQLException("Timeout waiting for a connection");
            }

            if (isIdleExpired(entry)) {
                closeSilently(entry.conn);
                continue;
            }

            return validate(entry.conn);
        }
    }

    public void release(Connection conn, long checkoutAt) {
        release(conn, checkoutAt, null);
    }

    public void release(Connection conn, long checkoutAt, StackTraceElement[] checkoutStack) {
        if (conn == null) {
            return;
        }
        if (closed) {
            closeSilently(conn);
            return;
        }
        detectLeak(checkoutAt, checkoutStack);

        restoreDefaults(conn);

        if (config.isTestOnReturn()) {
            try {
                if (!validateConnection(conn)) {
                    closeSilently(conn);
                    return;
                }
            } catch (SQLException e) {
                closeSilently(conn);
                return;
            }
        }

        if (!idleQueue.offer(new PooledEntry(conn, System.currentTimeMillis()))) {
            closeSilently(conn);
        }
    }

    private Connection validate(Connection conn) throws SQLException {
        if (!config.isTestOnBorrow()) {
            return conn;
        }
        if (!validateConnection(conn)) {
            closeSilently(conn);
            Connection created = createConnection();
            if (created != null) {
                return created;
            }
            throw new SQLException("Failed to create connection");
        }
        return conn;
    }

    private boolean validateConnection(Connection conn) throws SQLException {
        if (conn == null || conn.isClosed()) {
            return false;
        }
        String validationQuery = config.getValidationQuery();
        if (validationQuery != null && !validationQuery.trim().isEmpty()) {
            try (var ps = conn.prepareStatement(validationQuery)) {
                int timeout = Math.max(1, config.getValidationTimeoutSec());
                ps.setQueryTimeout(timeout);
                try (var rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        }
        int timeout = Math.max(1, config.getValidationTimeoutSec());
        return conn.isValid(timeout);
    }

    private boolean isIdleExpired(PooledEntry entry) {
        long idleTimeoutMs = config.getIdleTimeoutMs();
        if (idleTimeoutMs <= 0) {
            return false;
        }
        return System.currentTimeMillis() - entry.lastUsedAt > idleTimeoutMs;
    }

    private void detectLeak(long checkoutAt) {
        detectLeak(checkoutAt, null);
    }

    private void detectLeak(long checkoutAt, StackTraceElement[] checkoutStack) {
        long leakMs = config.getLeakDetectMs();
        if (leakMs <= 0) {
            return;
        }
        long cost = System.currentTimeMillis() - checkoutAt;
        if (cost < leakMs) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Potential connection leak detected, costMs=").append(cost);
        if (checkoutStack != null && checkoutStack.length > 0) {
            for (StackTraceElement e : checkoutStack) {
                sb.append(" at ").append(e);
            }
        }
        lastLeakStack = sb.toString();
        VKLog.warn(lastLeakStack);
    }

    private Connection createConnection() {
        try {
            Connection conn = DriverManager.getConnection(config.getUrl(), config.getUsername(), config.getPassword());
            defaults.put(conn, captureDefaults(conn));
            totalCount.incrementAndGet();
            return conn;
        } catch (SQLException e) {
            VKLog.error("Failed to create connection", e);
            return null;
        }
    }

    public void close() {
        closed = true;
        if (housekeeper != null) {
            housekeeper.shutdownNow();
        }
        while (!idleQueue.isEmpty()) {
            PooledEntry entry = idleQueue.poll();
            if (entry != null) {
                closeSilently(entry.conn);
            }
        }
    }

    public DataSourceConfig getConfig() {
        return config;
    }

    public String getLastLeakStack() {
        return lastLeakStack;
    }

    public int getTotalCount() {
        return totalCount.get();
    }

    public int getIdleCount() {
        return idleQueue.size();
    }

    public int getActiveCount() {
        return Math.max(0, totalCount.get() - idleQueue.size());
    }

    private void closeSilently(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.close();
        } catch (SQLException ignore) {
            // ignore
        } finally {
            defaults.remove(conn);
            totalCount.decrementAndGet();
        }
    }

    private ScheduledExecutorService startHousekeeper() {
        long interval = config.getIdleValidationIntervalMs();
        if (interval <= 0) {
            return null;
        }
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "vk-pool-housekeeper");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::validateIdleConnections, interval, interval, TimeUnit.MILLISECONDS);
        return scheduler;
    }

    private void validateIdleConnections() {
        if (closed) {
            return;
        }
        int size = idleQueue.size();
        for (int i = 0; i < size; i++) {
            PooledEntry entry = idleQueue.poll();
            if (entry == null) {
                break;
            }
            if (isIdleExpired(entry)) {
                closeSilently(entry.conn);
                continue;
            }
            try {
                if (!validateConnection(entry.conn)) {
                    closeSilently(entry.conn);
                    continue;
                }
            } catch (SQLException e) {
                closeSilently(entry.conn);
                continue;
            }
            idleQueue.offer(new PooledEntry(entry.conn, System.currentTimeMillis()));
        }
        ensureMinIdle();
    }

    private void ensureMinIdle() {
        int minIdle = config.getMinIdle();
        if (minIdle <= 0) {
            return;
        }
        int need = minIdle - idleQueue.size();
        for (int i = 0; i < need; i++) {
            if (totalCount.get() >= config.getMaxActive()) {
                return;
            }
            Connection conn = createConnection();
            if (conn != null) {
                idleQueue.offer(new PooledEntry(conn, System.currentTimeMillis()));
            }
        }
    }

    private ConnectionDefaults captureDefaults(Connection conn) {
        try {
            boolean autoCommit = conn.getAutoCommit();
            boolean readOnly = conn.isReadOnly();
            int isolation = conn.getTransactionIsolation();
            return new ConnectionDefaults(autoCommit, readOnly, isolation);
        } catch (SQLException e) {
            return new ConnectionDefaults(true, false, Connection.TRANSACTION_READ_COMMITTED);
        }
    }

    private void restoreDefaults(Connection conn) {
        ConnectionDefaults defaults = this.defaults.get(conn);
        if (defaults == null) {
            return;
        }
        try {
            if (conn.getAutoCommit() != defaults.autoCommit) {
                conn.setAutoCommit(defaults.autoCommit);
            }
        } catch (SQLException ignore) {
            // ignore
        }
        try {
            if (conn.isReadOnly() != defaults.readOnly) {
                conn.setReadOnly(defaults.readOnly);
            }
        } catch (SQLException ignore) {
            // ignore
        }
        try {
            if (conn.getTransactionIsolation() != defaults.isolation) {
                conn.setTransactionIsolation(defaults.isolation);
            }
        } catch (SQLException ignore) {
            // ignore
        }
    }

    private static class PooledEntry {
        private final Connection conn;
        private final long lastUsedAt;

        private PooledEntry(Connection conn, long lastUsedAt) {
            this.conn = conn;
            this.lastUsedAt = lastUsedAt;
        }
    }

    private static class ConnectionDefaults {
        private final boolean autoCommit;
        private final boolean readOnly;
        private final int isolation;

        private ConnectionDefaults(boolean autoCommit, boolean readOnly, int isolation) {
            this.autoCommit = autoCommit;
            this.readOnly = readOnly;
            this.isolation = isolation;
        }
    }
}
