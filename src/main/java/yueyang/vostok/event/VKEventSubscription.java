package yueyang.vostok.event;

public final class VKEventSubscription {
    private final long id;
    private final Class<?> eventType;
    private final VKListenerMode mode;

    public VKEventSubscription(long id, Class<?> eventType, VKListenerMode mode) {
        this.id = id;
        this.eventType = eventType;
        this.mode = mode;
    }

    public long getId() {
        return id;
    }

    public Class<?> getEventType() {
        return eventType;
    }

    public VKListenerMode getMode() {
        return mode;
    }
}
