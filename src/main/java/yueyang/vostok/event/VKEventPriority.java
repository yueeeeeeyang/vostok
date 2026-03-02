package yueyang.vostok.event;

/**
 * 事件监听器优先级枚举。
 * value 值越小，优先级越高，publish 时执行顺序越靠前。
 * 同优先级的监听器按注册 ID 升序执行（先注册先执行）。
 */
public enum VKEventPriority {
    HIGHEST(1),
    HIGH(2),
    NORMAL(3),
    LOW(4),
    LOWEST(5);

    /** 优先级数值，越小越优先 */
    private final int value;

    VKEventPriority(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
