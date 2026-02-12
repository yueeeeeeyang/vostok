package yueyang.vostok.exception;

import java.sql.SQLTimeoutException;
import java.sql.SQLException;

/**
 * SQL 异常转换器：根据 SQLState 映射更细粒度错误码。
 */
public final class VKExceptionTranslator {
    private VKExceptionTranslator() {
    }

    
    public static VKSqlException translate(String sql, SQLException e) {
        if (e instanceof SQLTimeoutException) {
            return new VKSqlException(VKErrorCode.SQL_TIMEOUT, "SQL timeout: " + sql, sql, e.getSQLState(), e.getErrorCode(), e);
        }
        String sqlState = e.getSQLState();
        VKErrorCode code = VKErrorCode.SQL_ERROR;
        if (sqlState != null) {
            if (sqlState.startsWith("23")) {
                code = VKErrorCode.SQL_CONSTRAINT;
            } else if (sqlState.startsWith("08")) {
                code = VKErrorCode.SQL_CONNECTION;
            } else if (sqlState.startsWith("42")) {
                code = VKErrorCode.SQL_SYNTAX;
            }
        }
        return new VKSqlException(code, "SQL error: " + sql, sql, sqlState, e.getErrorCode(), e);
    }
}
