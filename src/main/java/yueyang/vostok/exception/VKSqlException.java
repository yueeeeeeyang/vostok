package yueyang.vostok.exception;

/**
 * SQL 异常，携带 SQL 与数据库错误细节。
 */
public class VKSqlException extends VKException {
    private final String sql;
    private final String sqlState;
    private final int vendorCode;

    public VKSqlException(VKErrorCode code, String message, String sql, String sqlState, int vendorCode, Throwable cause) {
        super(code, message, cause);
        this.sql = sql;
        this.sqlState = sqlState;
        this.vendorCode = vendorCode;
    }

    
    public String getSql() {
        return sql;
    }

    
    public String getSqlState() {
        return sqlState;
    }

    
    public int getVendorCode() {
        return vendorCode;
    }
}
