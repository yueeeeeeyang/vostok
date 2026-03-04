package yueyang.vostok.office.exception;

/** Office 模块错误码定义。 */
public enum VKOfficeErrorCode {
    INVALID_ARGUMENT("OF-400", "Invalid argument"),
    CONFIG_ERROR("OF-402", "Office module configuration error"),
    STATE_ERROR("OF-403", "Office module state error"),
    NOT_FOUND("OF-404", "Office resource not found"),
    IO_ERROR("OF-500", "Office IO error"),
    UNSUPPORTED_FORMAT("OF-530", "Unsupported office format"),
    PARSE_ERROR("OF-564", "Office parse error"),
    WRITE_ERROR("OF-565", "Office write error"),
    LIMIT_EXCEEDED("OF-566", "Office limit exceeded"),
    SECURITY_ERROR("OF-567", "Office security error");

    private final String code;
    private final String message;

    VKOfficeErrorCode(String code, String message) {
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
