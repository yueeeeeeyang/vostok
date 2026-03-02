package yueyang.vostok.event;

/**
 * 事件订阅句柄，由 on()/once() 返回，持有订阅元数据并支持主动取消订阅。
 *
 * <p>通过 cancel() 可直接注销该监听器，等效于调用 VostokEvent.off(subscription)。
 * cancelHook 由 runtime 在注册时注入，调用时原子地从 listeners 中移除对应 slot。
 */
public final class VKEventSubscription {
    private final long id;
    private final Class<?> eventType;
    private final VKListenerMode mode;
    /** 监听器优先级，决定在同一 publish 中的执行顺序 */
    private final VKEventPriority priority;
    /** 注销回调，由 runtime 注入；调用时从 listeners 中原子删除该订阅 */
    private final Runnable cancelHook;

    public VKEventSubscription(long id, Class<?> eventType, VKListenerMode mode,
                                VKEventPriority priority, Runnable cancelHook) {
        this.id = id;
        this.eventType = eventType;
        this.mode = mode;
        this.priority = priority;
        this.cancelHook = cancelHook;
    }

    /**
     * 取消该订阅，调用后监听器将不再接收任何后续事件。
     * 线程安全：底层通过 listeners.compute() 原子操作完成移除。
     */
    public void cancel() {
        if (cancelHook != null) {
            cancelHook.run();
        }
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

    public VKEventPriority getPriority() {
        return priority;
    }
}
