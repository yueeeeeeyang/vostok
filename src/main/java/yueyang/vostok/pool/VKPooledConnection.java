package yueyang.vostok.pool;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class VKPooledConnection implements InvocationHandler {
    private final Connection target;
    private final VKConnectionPool pool;
    private final long checkoutAt;
    private final StackTraceElement[] checkoutStack;
    private final int statementCacheSize;
    private final Map<String, PreparedStatement> statementCache;
    private volatile boolean returned;

    private VKPooledConnection(Connection target, VKConnectionPool pool, long checkoutAt, int statementCacheSize) {
        this.target = target;
        this.pool = pool;
        this.checkoutAt = checkoutAt;
        this.checkoutStack = new Exception().getStackTrace();
        this.statementCacheSize = Math.max(0, statementCacheSize);
        if (this.statementCacheSize > 0) {
            this.statementCache = new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, PreparedStatement> eldest) {
                    if (size() > VKPooledConnection.this.statementCacheSize) {
                        closeSilently(eldest.getValue());
                        return true;
                    }
                    return false;
                }
            };
        } else {
            this.statementCache = new LinkedHashMap<>();
        }
    }

    public static Connection wrap(Connection target, VKConnectionPool pool) {
        return wrap(target, pool, 0);
    }

    public static Connection wrap(Connection target, VKConnectionPool pool, int statementCacheSize) {
        return (Connection) Proxy.newProxyInstance(
                VKPooledConnection.class.getClassLoader(),
                new Class[]{Connection.class},
                new VKPooledConnection(target, pool, System.currentTimeMillis(), statementCacheSize)
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        if ("close".equals(name)) {
            if (!returned) {
                returned = true;
                closeStatementCache();
                pool.release(target, checkoutAt, checkoutStack);
            }
            return null;
        }
        if ("prepareStatement".equals(name) && args != null && args.length >= 1 && args[0] instanceof String) {
            PreparedStatement cached = getOrCreatePreparedStatement(method, args);
            if (cached != null) {
                return cached;
            }
        }

        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    private PreparedStatement getOrCreatePreparedStatement(Method method, Object[] args) throws SQLException {
        if (statementCacheSize <= 0) {
            return null;
        }
        String sql = String.valueOf(args[0]);
        String key = buildKey(method, args, sql);
        synchronized (statementCache) {
            PreparedStatement ps = statementCache.get(key);
            if (ps != null) {
                if (ps.isClosed()) {
                    statementCache.remove(key);
                } else {
                    ps.clearParameters();
                    return ps;
                }
            }
        }
        PreparedStatement created;
        try {
            created = (PreparedStatement) method.invoke(target, args);
        } catch (InvocationTargetException | IllegalAccessException e) {
            Throwable cause = e instanceof InvocationTargetException ? ((InvocationTargetException) e).getTargetException() : e;
            if (cause instanceof SQLException) {
                throw (SQLException) cause;
            }
            throw new SQLException("Failed to create PreparedStatement", cause);
        }
        synchronized (statementCache) {
            statementCache.put(key, created);
        }
        return created;
    }

    private String buildKey(Method method, Object[] args, String sql) {
        if (args.length == 1) {
            return sql;
        }
        if (args.length == 2 && args[1] instanceof Integer) {
            return sql + "|gen=" + args[1];
        }
        return method.getName() + ":" + sql + ":" + args.length;
    }

    private void closeSilently(PreparedStatement ps) {
        if (ps == null) {
            return;
        }
        try {
            ps.close();
        } catch (SQLException ignore) {
            // ignore
        }
    }

    private void closeStatementCache() {
        if (statementCacheSize <= 0) {
            return;
        }
        synchronized (statementCache) {
            for (PreparedStatement ps : statementCache.values()) {
                closeSilently(ps);
            }
            statementCache.clear();
        }
    }
}
