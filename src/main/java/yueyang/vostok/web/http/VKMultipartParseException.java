package yueyang.vostok.web.http;

public final class VKMultipartParseException extends RuntimeException {
    private final int status;

    public VKMultipartParseException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
