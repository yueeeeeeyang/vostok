package yueyang.vostok.config.exception;

public enum VKConfigErrorCode {
    INVALID_ARGUMENT("CK-400", "Invalid argument"),
    VALIDATION_ERROR("CK-420", "Configuration validation error"),
    CONFIG_ERROR("CK-402", "Configuration error"),
    KEY_NOT_FOUND("CK-404", "Config key not found"),
    IO_ERROR("CK-500", "Config IO error"),
    PARSE_ERROR("CK-510", "Config parse error");

    private final String code;
    private final String message;

    VKConfigErrorCode(String code, String message) {
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
