package yueyang.vostok.tx;

import yueyang.vostok.config.VKTxIsolation;
import yueyang.vostok.config.VKTxPropagation;
import yueyang.vostok.pool.VKDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayDeque;
import java.util.Deque;

public final class VKTransactionManager {
    private static final ThreadLocal<Context> CTX = new ThreadLocal<>();

    private VKTransactionManager() {
    }

    public static void beginRequired(VKDataSource dataSource, VKTxIsolation isolation, boolean readOnly, VKTxPropagation propagation, boolean savepointEnabled) {
        Context ctx = getContext();
        switch (propagation) {
            case SUPPORTS:
                if (ctx.conn != null) {
                    ctx.depth++;
                } else {
                    ctx.nonTxDepth++;
                }
                return;
            case NOT_SUPPORTED:
                suspendIfNeeded(ctx);
                ctx.nonTxDepth++;
                return;
            case MANDATORY:
                if (ctx.conn == null) {
                    throw new yueyang.vostok.exception.VKTxException("No existing transaction for MANDATORY");
                }
                ctx.depth++;
                return;
            case NEVER:
                if (ctx.conn != null) {
                    throw new yueyang.vostok.exception.VKTxException("Existing transaction found for NEVER");
                }
                ctx.nonTxDepth++;
                return;
            case REQUIRED:
            default:
                if (ctx.conn == null) {
                    ctx.conn = openConn(dataSource, isolation, readOnly);
                    ctx.savepointEnabled = savepointEnabled;
                    ctx.txStartAt = System.currentTimeMillis();
                    ctx.txTimeoutMs = dataSource.getConfig().getTxTimeoutMs();
                }
                if (ctx.conn != null && ctx.depth > 0 && ctx.savepointEnabled) {
                    createSavepoint(ctx);
                }
                ctx.depth++;
        }
    }

    public static void beginRequiresNew(VKDataSource dataSource, VKTxIsolation isolation, boolean readOnly, boolean savepointEnabled) {
        Context ctx = getContext();
        suspendIfNeeded(ctx);
        ctx.conn = openConn(dataSource, isolation, readOnly);
        ctx.depth = 1;
        ctx.savepointEnabled = savepointEnabled;
        ctx.txStartAt = System.currentTimeMillis();
        ctx.txTimeoutMs = dataSource.getConfig().getTxTimeoutMs();
    }

    public static void commit() {
        Context ctx = CTX.get();
        if (ctx == null) {
            return;
        }
        if (ctx.conn == null) {
            if (ctx.nonTxDepth > 0) {
                ctx.nonTxDepth--;
                resumeIfNeeded(ctx);
            }
            return;
        }
        if (ctx.depth > 1) {
            if (ctx.rollbackOnly) {
                ctx.depth--;
                return;
            }
            if (ctx.savepointEnabled) {
                releaseSavepointIfAny(ctx);
            }
            ctx.depth--;
            return;
        }
        if (ctx.rollbackOnly) {
            rollback();
            return;
        }
        try {
            checkTimeout(ctx);
            ctx.conn.commit();
        } catch (RuntimeException e) {
            rollback();
            throw e;
        } catch (SQLException e) {
            rollback();
            throw new yueyang.vostok.exception.VKTxException("Failed to commit", e);
        } finally {
            if (ctx.conn != null) {
                cleanup(ctx);
            }
        }
    }

    public static void rollback() {
        Context ctx = CTX.get();
        if (ctx == null) {
            return;
        }
        if (ctx.conn == null) {
            if (ctx.nonTxDepth > 0) {
                ctx.nonTxDepth--;
                resumeIfNeeded(ctx);
            }
            return;
        }
        try {
            ctx.conn.rollback();
        } catch (SQLException e) {
            throw new yueyang.vostok.exception.VKTxException("Failed to rollback", e);
        } finally {
            cleanup(ctx);
        }
    }

    public static void rollbackCurrent() {
        Context ctx = CTX.get();
        if (ctx == null) {
            return;
        }
        if (ctx.conn == null) {
            if (ctx.nonTxDepth > 0) {
                ctx.nonTxDepth--;
                resumeIfNeeded(ctx);
            }
            return;
        }
        if (ctx.depth > 1) {
            if (ctx.savepointEnabled && !ctx.savepoints.isEmpty()) {
                Savepoint sp = ctx.savepoints.pop();
                try {
                    ctx.conn.rollback(sp);
                    ctx.conn.releaseSavepoint(sp);
                } catch (SQLException e) {
                    throw new yueyang.vostok.exception.VKTxException("Failed to rollback to savepoint", e);
                } finally {
                    ctx.depth--;
                }
                return;
            }
            ctx.rollbackOnly = true;
            ctx.depth--;
            return;
        }
        rollback();
    }

    public static void setRollbackOnly() {
        Context ctx = CTX.get();
        if (ctx != null) {
            ctx.rollbackOnly = true;
        }
    }

    public static boolean inTransaction() {
        Context ctx = CTX.get();
        return ctx != null && ctx.conn != null;
    }

    public static Connection getConnection() {
        Context ctx = CTX.get();
        if (ctx == null || ctx.conn == null) {
            throw new yueyang.vostok.exception.VKTxException("No active transaction");
        }
        return ctx.conn;
    }

    public static long remainingTimeoutMs() {
        Context ctx = CTX.get();
        if (ctx == null || ctx.conn == null) {
            return 0L;
        }
        long timeout = ctx.txTimeoutMs;
        if (timeout <= 0) {
            return 0L;
        }
        long elapsed = System.currentTimeMillis() - ctx.txStartAt;
        long remain = timeout - elapsed;
        return Math.max(0L, remain);
    }

    private static Context getContext() {
        Context ctx = CTX.get();
        if (ctx == null) {
            ctx = new Context();
            CTX.set(ctx);
        }
        return ctx;
    }

    private static void suspendIfNeeded(Context ctx) {
        if (ctx.conn == null) {
            return;
        }
        ctx.stack.push(new TxState(ctx.conn, ctx.depth, ctx.rollbackOnly, ctx.savepointEnabled, ctx.savepoints,
                ctx.originalIsolation, ctx.originalReadOnly, ctx.originalAutoCommit));
        ctx.conn = null;
        ctx.depth = 0;
        ctx.rollbackOnly = false;
        ctx.savepointEnabled = false;
        ctx.savepoints = new ArrayDeque<>();
    }

    private static void resumeIfNeeded(Context ctx) {
        if (ctx.conn != null || ctx.nonTxDepth > 0) {
            return;
        }
        if (ctx.stack.isEmpty()) {
            return;
        }
        TxState state = ctx.stack.pop();
        ctx.conn = state.conn;
        ctx.depth = state.depth;
        ctx.rollbackOnly = state.rollbackOnly;
        ctx.savepointEnabled = state.savepointEnabled;
        ctx.savepoints = state.savepoints == null ? new ArrayDeque<>() : state.savepoints;
        ctx.originalIsolation = state.originalIsolation;
        ctx.originalReadOnly = state.originalReadOnly;
        ctx.originalAutoCommit = state.originalAutoCommit;
    }

    private static Connection openConn(VKDataSource dataSource, VKTxIsolation isolation, boolean readOnly) {
        try {
            Connection conn = dataSource.getConnection();
            Context ctx = getContext();
            ctx.originalAutoCommit = conn.getAutoCommit();
            ctx.originalReadOnly = conn.isReadOnly();
            ctx.originalIsolation = conn.getTransactionIsolation();
            conn.setAutoCommit(false);
            conn.setReadOnly(readOnly);
            if (isolation != null && isolation != VKTxIsolation.DEFAULT) {
                conn.setTransactionIsolation(isolation.getLevel());
            }
            return conn;
        } catch (SQLException e) {
            throw new yueyang.vostok.exception.VKTxException("Failed to open transaction connection", e);
        }
    }

    private static void cleanup(Context ctx) {
        try {
            if (ctx.conn != null) {
                try {
                    if (ctx.conn.getTransactionIsolation() != ctx.originalIsolation) {
                        ctx.conn.setTransactionIsolation(ctx.originalIsolation);
                    }
                } catch (SQLException ignore) {
                    // ignore
                }
                try {
                    if (ctx.conn.isReadOnly() != ctx.originalReadOnly) {
                        ctx.conn.setReadOnly(ctx.originalReadOnly);
                    }
                } catch (SQLException ignore) {
                    // ignore
                }
                try {
                    if (ctx.conn.getAutoCommit() != ctx.originalAutoCommit) {
                        ctx.conn.setAutoCommit(ctx.originalAutoCommit);
                    }
                } catch (SQLException ignore) {
                    // ignore
                }
                ctx.conn.close();
            }
        } catch (SQLException e) {
            throw new yueyang.vostok.exception.VKTxException("Failed to close connection", e);
        } finally {
            ctx.conn = null;
            ctx.depth = 0;
            ctx.rollbackOnly = false;
            ctx.savepointEnabled = false;
            ctx.savepoints.clear();
            ctx.txStartAt = 0L;
            ctx.txTimeoutMs = 0L;
            resumeIfNeeded(ctx);
        }
    }

    private static void checkTimeout(Context ctx) {
        long timeout = ctx.txTimeoutMs;
        if (timeout <= 0) {
            return;
        }
        long elapsed = System.currentTimeMillis() - ctx.txStartAt;
        if (elapsed > timeout) {
            ctx.rollbackOnly = true;
            throw new yueyang.vostok.exception.VKTxException("Transaction timeout");
        }
    }

    private static void createSavepoint(Context ctx) {
        try {
            Savepoint sp = ctx.conn.setSavepoint("VK_SP_" + ctx.depth + "_" + System.nanoTime());
            ctx.savepoints.push(sp);
        } catch (SQLException e) {
            throw new yueyang.vostok.exception.VKTxException("Failed to create savepoint", e);
        }
    }

    private static void releaseSavepointIfAny(Context ctx) {
        if (!ctx.savepointEnabled || ctx.savepoints.isEmpty()) {
            return;
        }
        Savepoint sp = ctx.savepoints.pop();
        try {
            ctx.conn.releaseSavepoint(sp);
        } catch (SQLException ignore) {
            // ignore
        }
    }

    private static class Context {
        private Connection conn;
        private int depth;
        private boolean rollbackOnly;
        private int nonTxDepth;
        private boolean savepointEnabled;
        private Deque<Savepoint> savepoints = new ArrayDeque<>();
        private long txStartAt;
        private long txTimeoutMs;
        private int originalIsolation;
        private boolean originalReadOnly;
        private boolean originalAutoCommit = true;
        private final Deque<TxState> stack = new ArrayDeque<>();
    }

    private static class TxState {
        private final Connection conn;
        private final int depth;
        private final boolean rollbackOnly;
        private final boolean savepointEnabled;
        private final Deque<Savepoint> savepoints;
        private final int originalIsolation;
        private final boolean originalReadOnly;
        private final boolean originalAutoCommit;

        private TxState(Connection conn, int depth, boolean rollbackOnly, boolean savepointEnabled, Deque<Savepoint> savepoints,
                        int originalIsolation, boolean originalReadOnly, boolean originalAutoCommit) {
            this.conn = conn;
            this.depth = depth;
            this.rollbackOnly = rollbackOnly;
            this.savepointEnabled = savepointEnabled;
            this.savepoints = savepoints;
            this.originalIsolation = originalIsolation;
            this.originalReadOnly = originalReadOnly;
            this.originalAutoCommit = originalAutoCommit;
        }
    }
}
