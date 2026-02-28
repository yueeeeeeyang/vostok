package yueyang.vostok.game.core.lifecycle;

/**
 * 房间生命周期内置原因码。
 * 说明：drainRoom 支持自定义原因文本，这里只定义系统策略使用的标准原因。
 */
public enum VKGameLifecycleReason {
    MANUAL("manual"),
    IDLE_TIMEOUT("idle_timeout"),
    EMPTY_TIMEOUT("empty_timeout"),
    MAX_LIFETIME("max_lifetime"),
    DRAIN_TIMEOUT("drain_timeout"),
    LOGIC_ERROR("logic_error");

    private final String code;

    VKGameLifecycleReason(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static String normalize(String reason) {
        if (reason == null || reason.isBlank()) {
            return MANUAL.code;
        }
        return reason.trim();
    }

    public static VKGameLifecycleReason fromCode(String reason) {
        if (reason == null || reason.isBlank()) {
            return MANUAL;
        }
        for (VKGameLifecycleReason value : values()) {
            if (value.code.equals(reason)) {
                return value;
            }
        }
        return MANUAL;
    }
}
