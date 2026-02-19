package yueyang.vostok.event;

import yueyang.vostok.event.core.VKEventRuntime;

public class VostokEvent {
    private static final VKEventRuntime RUNTIME = VKEventRuntime.getInstance();

    protected VostokEvent() {
    }

    public static void init() {
        RUNTIME.init(new VKEventConfig());
    }

    public static void init(VKEventConfig config) {
        RUNTIME.init(config);
    }

    public static void reinit(VKEventConfig config) {
        RUNTIME.reinit(config);
    }

    public static boolean started() {
        return RUNTIME.started();
    }

    public static VKEventConfig config() {
        return RUNTIME.config();
    }

    public static void close() {
        RUNTIME.close();
    }

    public static <T> VKEventSubscription on(Class<T> eventType, VKEventListener<T> listener) {
        return RUNTIME.on(eventType, VKListenerMode.SYNC, listener);
    }

    public static <T> VKEventSubscription on(Class<T> eventType, VKListenerMode mode, VKEventListener<T> listener) {
        return RUNTIME.on(eventType, mode == null ? VKListenerMode.SYNC : mode, listener);
    }

    public static void off(VKEventSubscription subscription) {
        RUNTIME.off(subscription);
    }

    public static void offAll(Class<?> eventType) {
        RUNTIME.offAll(eventType);
    }

    public static VKEventPublishResult publish(Object event) {
        return RUNTIME.publish(event);
    }
}
