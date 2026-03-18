package yueyang.vostok.cluster.exception;

/** Cluster 模块统一运行时异常。 */
public class VKClusterException extends RuntimeException {
    private final VKClusterErrorCode errorCode;

    public VKClusterException(VKClusterErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public VKClusterException(VKClusterErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public VKClusterErrorCode getErrorCode() {
        return errorCode;
    }

    public String getCode() {
        return errorCode.getCode();
    }
}
