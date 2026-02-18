package yueyang.vostok.data.pool;

import yueyang.vostok.data.VKDataConfig;
import yueyang.vostok.util.VKAssert;
import yueyang.vostok.util.VKLog;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class VKConnectionPool {
    private final VKDataConfig config;
    private final ConcurrentLinkedQueue<PooledEntry> idleQueue;
    private final AtomicInteger idleCount = new AtomicInteger(0);
    private final AtomicInteger localIdleCount = new AtomicInteger(0);
    private final AtomicInteger totalCount = new AtomicInteger(0);
    private final ScheduledExecutorService housekeeper;
    private final ConcurrentHashMap<Connection, ConnectionDefaults> defaults = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Connection, ConnectionState> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Connection, Thread> borrowedOwners = new ConcurrentHashMap<>();
    private final ThreadLocal<PooledEntry> localCache = new ThreadLocal<>();
    private final ConcurrentHashMap<Thread, PooledEntry> localCacheMap = new ConcurrentHashMap<>();
    private final Semaphore permits;
    private final boolean localCacheEnabled;
    private volatile String lastLeakStack;
    private volatile boolean closed;

    public VKConnectionPool(VKDataConfig config) {
        this.config = config;
        this.idleQueue = new ConcurrentLinkedQueue<>();
        this.permits = new Semaphore(config.getMaxActive());
        this.localCacheEnabled = config.getIdleTimeoutMs() <= 0 && config.getIdleValidationIntervalMs() <= 0;
        if (config.isPreheatEnabled()) {
            preload();
        }
        this.housekeeper = startHousekeeper();
    }

    private void preload() {
        for (int i = 0; i < config.getMinIdle(); i++) {
            Connection conn = createConnection();
            if (conn != null) {
                if (idleQueue.offer(new PooledEntry(conn, System.currentTimeMillis()))) {
                    idleCount.incrementAndGet();
                }
            }
        }
    }

    public Connection borrow() throws SQLException {
        VKAssert.isTrue(!closed, "Connection pool is closed");
        long timeoutMs = config.getMaxWaitMs();
        long deadline = timeoutMs > 0 ? System.currentTimeMillis() + timeoutMs : Long.MAX_VALUE;
        boolean acquired = false;
        while (true) {
            long waitMs = timeoutMs > 0 ? Math.max(0L, deadline - System.currentTimeMillis()) : 0L;
            try {
                if (timeoutMs > 0) {
                    acquired = permits.tryAcquire(waitMs, TimeUnit.MILLISECONDS);
                } else {
                    permits.acquire();
                    acquired = true;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SQLException("Interrupted while waiting for a connection", e);
            }
            if (!acquired) {
                throw new SQLException("Timeout waiting for a connection");
            }

            PooledEntry entry;
            if (localCacheEnabled) {
                entry = localCache.get();
                if (entry != null) {
                    localCache.remove();
                    if (localCacheMap.remove(Thread.currentThread(), entry)) {
                        localIdleCount.decrementAndGet();
                    }
                    if (isIdleExpired(entry)) {
                        closeSilently(entry.conn);
                        continue;
                    }
                    try {
                        return markBorrowed(validate(entry.conn));
                    } catch (SQLException e) {
                        permits.release();
                        throw e;
                    }
                }
            }

            entry = idleQueue.poll();
            if (entry != null) {
                idleCount.decrementAndGet();
                if (isIdleExpired(entry)) {
                    closeSilently(entry.conn);
                    continue;
                }
                try {
                        return markBorrowed(validate(entry.conn));
                } catch (SQLException e) {
                    permits.release();
                    throw e;
                }
            }

            if (totalCount.get() < config.getMaxActive()) {
                Connection created = createConnection();
                if (created != null) {
                    try {
                        return markBorrowed(validate(created));
                    } catch (SQLException e) {
                        permits.release();
                        throw e;
                    }
                }
                permits.release();
                throw new SQLException("Failed to create connection");
            }

            permits.release();
            // no idle and at max, retry until timeout
            if (timeoutMs > 0 && System.currentTimeMillis() >= deadline) {
                throw new SQLException("Timeout waiting for a connection");
            }
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
            borrowedOwners.remove(conn);
            closeSilently(conn);
            permits.release();
            return;
        }
        borrowedOwners.remove(conn);
        detectLeak(checkoutAt, checkoutStack);

        restoreDefaults(conn);

        if (config.isTestOnReturn()) {
            try {
                if (!validateConnection(conn)) {
                    closeSilently(conn);
                    permits.release();
                    return;
                }
            } catch (SQLException e) {
                closeSilently(conn);
                permits.release();
                return;
            }
        }
        PooledEntry entry = new PooledEntry(conn, System.currentTimeMillis());
        if (!localCacheEnabled || permits.getQueueLength() > 0) {
            if (idleQueue.offer(entry)) {
                idleCount.incrementAndGet();
            } else {
                closeSilently(conn);
            }
        } else {
            PooledEntry cached = localCache.get();
            if (cached != null) {
                localCache.remove();
                if (localCacheMap.remove(Thread.currentThread(), cached)) {
                    localIdleCount.decrementAndGet();
                }
                if (idleQueue.offer(cached)) {
                    idleCount.incrementAndGet();
                } else {
                    closeSilently(cached.conn);
                }
            }
            localCache.set(entry);
            localCacheMap.put(Thread.currentThread(), entry);
            localIdleCount.incrementAndGet();
        }
        permits.release();
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

    private Connection markBorrowed(Connection conn) throws SQLException {
        Thread prev = borrowedOwners.putIfAbsent(conn, Thread.currentThread());
        if (prev != null && prev != Thread.currentThread()) {
            throw new SQLException("Connection borrowed concurrently: " + conn);
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
            states.put(conn, new ConnectionState());
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
        while (true) {
            PooledEntry entry = idleQueue.poll();
            if (entry == null) {
                break;
            }
            idleCount.decrementAndGet();
            closeSilently(entry.conn);
        }
        for (PooledEntry entry : localCacheMap.values()) {
            closeSilently(entry.conn);
        }
        localCacheMap.clear();
        localIdleCount.set(0);
    }

    public VKDataConfig getConfig() {
        return config;
    }

    public String getLastLeakStack() {
        return lastLeakStack;
    }

    public int getTotalCount() {
        return totalCount.get();
    }

    public int getIdleCount() {
        return idleCount.get() + (localCacheEnabled ? localIdleCount.get() : 0);
    }

    public int getActiveCount() {
        int active = config.getMaxActive() - permits.availablePermits();
        return Math.max(0, active);
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
            states.remove(conn);
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
        int size = idleCount.get();
        for (int i = 0; i < size; i++) {
            PooledEntry entry = idleQueue.poll();
            if (entry == null) {
                break;
            }
            idleCount.decrementAndGet();
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
            if (idleQueue.offer(new PooledEntry(entry.conn, System.currentTimeMillis()))) {
                idleCount.incrementAndGet();
            }
        }
        ensureMinIdle();
    }

    private void ensureMinIdle() {
        int minIdle = config.getMinIdle();
        if (minIdle <= 0) {
            return;
        }
        int need = minIdle - idleCount.get();
        for (int i = 0; i < need; i++) {
            if (totalCount.get() >= config.getMaxActive()) {
                return;
            }
            Connection conn = createConnection();
            if (conn != null) {
                if (idleQueue.offer(new PooledEntry(conn, System.currentTimeMillis()))) {
                    idleCount.incrementAndGet();
                }
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
        ConnectionState state = states.get(conn);
        if (state == null) {
            state = new ConnectionState(true, true, true);
        }
        try {
            if (state.autoCommitDirty) {
                conn.setAutoCommit(defaults.autoCommit);
            }
        } catch (SQLException ignore) {
            // ignore
        }
        try {
            if (state.readOnlyDirty) {
                conn.setReadOnly(defaults.readOnly);
            }
        } catch (SQLException ignore) {
            // ignore
        }
        try {
            if (state.isolationDirty) {
                conn.setTransactionIsolation(defaults.isolation);
            }
        } catch (SQLException ignore) {
            // ignore
        }
        if (state != null) {
            state.reset();
        }
    }

    void markAutoCommitDirty(Connection conn) {
        ConnectionState state = states.get(conn);
        if (state != null) {
            state.autoCommitDirty = true;
        }
    }

    void markReadOnlyDirty(Connection conn) {
        ConnectionState state = states.get(conn);
        if (state != null) {
            state.readOnlyDirty = true;
        }
    }

    void markIsolationDirty(Connection conn) {
        ConnectionState state = states.get(conn);
        if (state != null) {
            state.isolationDirty = true;
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

    private static class ConnectionState {
        private volatile boolean autoCommitDirty;
        private volatile boolean readOnlyDirty;
        private volatile boolean isolationDirty;

        private ConnectionState() {
            this(false, false, false);
        }

        private ConnectionState(boolean autoCommitDirty, boolean readOnlyDirty, boolean isolationDirty) {
            this.autoCommitDirty = autoCommitDirty;
            this.readOnlyDirty = readOnlyDirty;
            this.isolationDirty = isolationDirty;
        }

        private void reset() {
            autoCommitDirty = false;
            readOnlyDirty = false;
            isolationDirty = false;
        }
    }
}
