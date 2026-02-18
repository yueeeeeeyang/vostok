package yueyang.vostok.log;

public enum VKLogLevel {
    TRACE(0),
    DEBUG(1),
    INFO(2),
    WARN(3),
    ERROR(4);

    private final int weight;

    VKLogLevel(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }

    public boolean enabled(VKLogLevel threshold) {
        return this.weight >= threshold.weight;
    }
}
