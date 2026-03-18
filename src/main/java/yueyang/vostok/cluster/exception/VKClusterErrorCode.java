package yueyang.vostok.cluster.exception;

/** Cluster 模块错误码。 */
public enum VKClusterErrorCode {
    INVALID_ARGUMENT("CL-400", "Invalid argument"),
    CONFIG_ERROR("CL-402", "Cluster module configuration error"),
    STATE_ERROR("CL-403", "Cluster module state error"),
    NOT_FOUND("CL-404", "Cluster resource not found"),
    AUTH_ERROR("CL-451", "Cluster authentication failed"),
    IO_ERROR("CL-500", "Cluster IO error"),
    PROTOCOL_ERROR("CL-560", "Cluster protocol error"),
    LIMIT_EXCEEDED("CL-566", "Cluster limit exceeded"),
    BROADCAST_TIMEOUT("CL-568", "Cluster broadcast timeout");

    private final String code;
    private final String message;

    VKClusterErrorCode(String code, String message) {
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
