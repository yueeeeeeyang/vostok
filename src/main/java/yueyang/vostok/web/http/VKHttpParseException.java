package yueyang.vostok.web.http;

public final class VKHttpParseException extends RuntimeException {
    private final int status;

    public VKHttpParseException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
