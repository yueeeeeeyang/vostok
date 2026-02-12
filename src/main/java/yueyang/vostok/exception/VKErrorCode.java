package yueyang.vostok.exception;

public enum VKErrorCode {
    INVALID_ARGUMENT("DK-400", "Invalid argument"),
    NOT_INITIALIZED("DK-401", "Vostok not initialized"),
    CONFIG_ERROR("DK-402", "Configuration error"),
    META_ERROR("DK-410", "Entity metadata error"),
    SQL_ERROR("DK-500", "SQL execution error"),
    SQL_TIMEOUT("DK-501", "SQL timeout"),
    SQL_CONSTRAINT("DK-502", "SQL constraint violation"),
    SQL_SYNTAX("DK-503", "SQL syntax error"),
    SQL_CONNECTION("DK-504", "SQL connection error"),
    SCAN_ERROR("DK-510", "Class scan error"),
    POOL_ERROR("DK-520", "Connection pool error"),
    TX_ERROR("DK-530", "Transaction error"),
    CACHE_ERROR("DK-540", "Cache error");

    private final String code;
    private final String message;

    VKErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
