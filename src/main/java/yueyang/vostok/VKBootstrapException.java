package yueyang.vostok;

public class VKBootstrapException extends RuntimeException {
    private final String stage;

    public VKBootstrapException(String stage, String message) {
        super(message);
        this.stage = stage;
    }

    public VKBootstrapException(String stage, String message, Throwable cause) {
        super(message, cause);
        this.stage = stage;
    }

    public String getStage() {
        return stage;
    }
}
