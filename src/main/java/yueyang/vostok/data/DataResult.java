package yueyang.vostok.data;

import yueyang.vostok.data.exception.VKExceptionTranslator;
import yueyang.vostok.data.type.VKTypeMapper;
import yueyang.vostok.util.VKAssert;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 原生 SQL 查询结果游标。
 *
 * <p>语义接近 JDBC {@link ResultSet}：通过 {@link #next()} 向前遍历，使用 {@code getXxx(...)} 读取列值。
 * 结果集为 forward-only，不支持随机定位。
 */
public final class DataResult implements AutoCloseable {
    @FunctionalInterface
    public interface CloseHandler {
        void onClose(Throwable error) throws SQLException;
    }

    private final String sql;
    private final ResultSet resultSet;
    private final CloseHandler closeHandler;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile boolean lastWasNull;

    public DataResult(String sql, ResultSet resultSet, CloseHandler closeHandler) {
        VKAssert.notBlank(sql, "SQL is blank");
        VKAssert.notNull(resultSet, "ResultSet is null");
        VKAssert.notNull(closeHandler, "CloseHandler is null");
        this.sql = sql;
        this.resultSet = resultSet;
        this.closeHandler = closeHandler;
    }

    /**
     * 移动到下一行；到达末尾时返回 false，并自动关闭底层资源。
     */
    public boolean next() {
        if (closed.get()) {
            return false;
        }
        try {
            boolean hasRow = resultSet.next();
            if (!hasRow) {
                close();
            }
            return hasRow;
        } catch (SQLException e) {
            throw fail(e);
        }
    }

    /**
     * 返回最近一次列读取是否为 SQL NULL。
     */
    public boolean wasNull() {
        return lastWasNull;
    }

    public Object getObject(int columnIndex) {
        return read(() -> resultSet.getObject(columnIndex));
    }

    public Object getObject(String columnLabel) {
        return read(() -> resultSet.getObject(columnLabel));
    }

    @SuppressWarnings("unchecked")
    public <T> T getObject(int columnIndex, Class<T> type) {
        VKAssert.notNull(type, "Target type is null");
        Object value = getObject(columnIndex);
        if (value == null) {
            return null;
        }
        Object mapped = VKTypeMapper.fromJdbc(value, wrapPrimitive(type));
        return (T) mapped;
    }

    @SuppressWarnings("unchecked")
    public <T> T getObject(String columnLabel, Class<T> type) {
        VKAssert.notNull(type, "Target type is null");
        Object value = getObject(columnLabel);
        if (value == null) {
            return null;
        }
        Object mapped = VKTypeMapper.fromJdbc(value, wrapPrimitive(type));
        return (T) mapped;
    }

    public String getString(int columnIndex) {
        return read(() -> resultSet.getString(columnIndex));
    }

    public String getString(String columnLabel) {
        return read(() -> resultSet.getString(columnLabel));
    }

    public int getInt(int columnIndex) {
        return read(() -> resultSet.getInt(columnIndex));
    }

    public int getInt(String columnLabel) {
        return read(() -> resultSet.getInt(columnLabel));
    }

    public long getLong(int columnIndex) {
        return read(() -> resultSet.getLong(columnIndex));
    }

    public long getLong(String columnLabel) {
        return read(() -> resultSet.getLong(columnLabel));
    }

    public double getDouble(int columnIndex) {
        return read(() -> resultSet.getDouble(columnIndex));
    }

    public double getDouble(String columnLabel) {
        return read(() -> resultSet.getDouble(columnLabel));
    }

    public boolean getBoolean(int columnIndex) {
        return read(() -> resultSet.getBoolean(columnIndex));
    }

    public boolean getBoolean(String columnLabel) {
        return read(() -> resultSet.getBoolean(columnLabel));
    }

    public BigDecimal getBigDecimal(int columnIndex) {
        return getObject(columnIndex, BigDecimal.class);
    }

    public BigDecimal getBigDecimal(String columnLabel) {
        return getObject(columnLabel, BigDecimal.class);
    }

    public Date getDate(int columnIndex) {
        return read(() -> resultSet.getDate(columnIndex));
    }

    public Date getDate(String columnLabel) {
        return read(() -> resultSet.getDate(columnLabel));
    }

    public Timestamp getTimestamp(int columnIndex) {
        return read(() -> resultSet.getTimestamp(columnIndex));
    }

    public Timestamp getTimestamp(String columnLabel) {
        return read(() -> resultSet.getTimestamp(columnLabel));
    }

    public LocalDate getLocalDate(int columnIndex) {
        return getObject(columnIndex, LocalDate.class);
    }

    public LocalDate getLocalDate(String columnLabel) {
        return getObject(columnLabel, LocalDate.class);
    }

    public LocalDateTime getLocalDateTime(int columnIndex) {
        return getObject(columnIndex, LocalDateTime.class);
    }

    public LocalDateTime getLocalDateTime(String columnLabel) {
        return getObject(columnLabel, LocalDateTime.class);
    }

    public int getColumnCount() {
        ensureReadable();
        try {
            return resultSet.getMetaData().getColumnCount();
        } catch (SQLException e) {
            throw fail(e);
        }
    }

    public String getColumnLabel(int columnIndex) {
        ensureReadable();
        try {
            String label = resultSet.getMetaData().getColumnLabel(columnIndex);
            if (label == null || label.isBlank()) {
                return resultSet.getMetaData().getColumnName(columnIndex);
            }
            return label;
        } catch (SQLException e) {
            throw fail(e);
        }
    }

    @Override
    public void close() {
        closeInternal(null, true);
    }

    private void ensureReadable() {
        if (closed.get()) {
            throw new IllegalStateException("DataResult already closed");
        }
    }

    private <T> T read(SqlSupplier<T> supplier) {
        ensureReadable();
        try {
            T value = supplier.get();
            lastWasNull = resultSet.wasNull();
            return value;
        } catch (SQLException e) {
            throw fail(e);
        }
    }

    private RuntimeException fail(SQLException e) {
        RuntimeException translated = VKExceptionTranslator.translate(sql, e);
        try {
            closeInternal(translated, false);
        } catch (RuntimeException closeEx) {
            translated.addSuppressed(closeEx);
        }
        return translated;
    }

    private void closeInternal(Throwable error, boolean throwOnCloseError) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            closeHandler.onClose(error);
        } catch (SQLException e) {
            if (throwOnCloseError) {
                throw VKExceptionTranslator.translate(sql, e);
            }
            if (error != null) {
                error.addSuppressed(e);
            }
        } catch (RuntimeException e) {
            if (throwOnCloseError) {
                throw e;
            }
            if (error != null) {
                error.addSuppressed(e);
            }
        }
    }

    private static Class<?> wrapPrimitive(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }
}
