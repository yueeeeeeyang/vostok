package yueyang.vostok.file.exception;

public enum VKFileErrorCode {
    INVALID_ARGUMENT("FK-400", "Invalid argument"),
    NOT_INITIALIZED("FK-401", "File module not initialized"),
    CONFIG_ERROR("FK-402", "File module configuration error"),
    STATE_ERROR("FK-403", "File module state error"),
    PATH_ERROR("FK-410", "Path error"),
    IO_ERROR("FK-500", "File IO error"),
    NOT_FOUND("FK-404", "Path not found"),
    SECURITY_ERROR("FK-520", "File security error"),
    UNSUPPORTED("FK-530", "Unsupported file operation"),
    ZIP_BOMB_RISK("FK-540", "Zip bomb risk");

    private final String code;
    private final String message;

    VKFileErrorCode(String code, String message) {
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
