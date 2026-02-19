package yueyang.vostok.cache.exception;

public class VKCacheException extends RuntimeException {
    private final VKCacheErrorCode code;

    public VKCacheException(VKCacheErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public VKCacheException(VKCacheErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public VKCacheErrorCode getCode() {
        return code;
    }
}
