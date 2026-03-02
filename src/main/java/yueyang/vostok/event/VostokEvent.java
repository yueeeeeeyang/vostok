package yueyang.vostok.event;

import yueyang.vostok.event.core.VKEventRuntime;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * 事件模块公共 API 门面，所有方法均为静态，委托给 {@link VKEventRuntime} 单例。
 *
 * <p>通过 {@code Vostok.Event} 子类访问（{@code Vostok.Event.publish(…)}），
 * 也可直接使用 {@code VostokEvent.publish(…)}。
 */
public class VostokEvent {
    private static final VKEventRuntime RUNTIME = VKEventRuntime.getInstance();

    protected VostokEvent() {
    }

    // ---------------------------------------------------------------- 生命周期

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

    // ---------------------------------------------------------------- on()

    /** 注册同步监听器，默认 NORMAL 优先级 */
    public static <T> VKEventSubscription on(Class<T> eventType, VKEventListener<T> listener) {
        return RUNTIME.on(eventType, VKListenerMode.SYNC, listener);
    }

    /** 注册监听器，指定执行模式，默认 NORMAL 优先级 */
    public static <T> VKEventSubscription on(Class<T> eventType, VKListenerMode mode, VKEventListener<T> listener) {
        return RUNTIME.on(eventType, mode == null ? VKListenerMode.SYNC : mode, listener);
    }

    /** 注册同步监听器，指定优先级 */
    public static <T> VKEventSubscription on(Class<T> eventType, VKEventPriority priority, VKEventListener<T> listener) {
        return RUNTIME.on(eventType, priority, listener);
    }

    /** 注册监听器，指定模式和优先级 */
    public static <T> VKEventSubscription on(Class<T> eventType, VKListenerMode mode, VKEventPriority priority,
                                              VKEventListener<T> listener) {
        return RUNTIME.on(eventType, mode, priority, listener);
    }

    /**
     * 注册监听器（全参数版本）：指定模式、优先级和前置过滤器。
     *
     * @param filter 可选事件过滤器；为 null 表示不过滤；不满足时跳过（不消耗 once token）
     */
    public static <T> VKEventSubscription on(Class<T> eventType, VKListenerMode mode, VKEventPriority priority,
                                              Predicate<T> filter, VKEventListener<T> listener) {
        return RUNTIME.on(eventType, mode, priority, filter, listener);
    }

    // ---------------------------------------------------------------- once()

    /** 注册一次性同步监听器，触发一次后自动注销 */
    public static <T> VKEventSubscription once(Class<T> eventType, VKEventListener<T> listener) {
        return RUNTIME.once(eventType, listener);
    }

    /** 注册一次性监听器，指定执行模式，触发一次后自动注销 */
    public static <T> VKEventSubscription once(Class<T> eventType, VKListenerMode mode, VKEventListener<T> listener) {
        return RUNTIME.once(eventType, mode, listener);
    }

    // ---------------------------------------------------------------- off()

    public static void off(VKEventSubscription subscription) {
        RUNTIME.off(subscription);
    }

    public static void offAll(Class<?> eventType) {
        RUNTIME.offAll(eventType);
    }

    // ---------------------------------------------------------------- dead letter

    /**
     * 注册死信处理器：当发布的事件没有任何匹配监听器时被调用。
     * 全局唯一，后注册覆盖前注册。
     */
    public static void onDeadLetter(VKEventDeadLetterHandler handler) {
        RUNTIME.onDeadLetter(handler);
    }

    // ---------------------------------------------------------------- publish

    /** 同步发布事件，返回本次发布统计 */
    public static VKEventPublishResult publish(Object event) {
        return RUNTIME.publish(event);
    }

    /**
     * 发布事件并等待所有 ASYNC 监听器执行完成后才 complete 返回的 CompletableFuture。
     * 返回结果中 asyncFailed 统计所有异步监听器的执行失败数。
     */
    public static CompletableFuture<VKEventPublishResult> publishAsync(Object event) {
        return RUNTIME.publishAsync(event);
    }

    // ---------------------------------------------------------------- scan()

    /**
     * 扫描 bean 对象中所有带 {@link VKEventHandler} 注解的方法，自动注册为监听器。
     * 被注解方法必须恰好有 1 个参数，参数类型为监听的事件类型。
     *
     * @param bean 含 @VKEventHandler 方法的实例
     * @return 注册成功的订阅句柄列表
     */
    public static List<VKEventSubscription> scan(Object bean) {
        return RUNTIME.scan(bean);
    }
}
