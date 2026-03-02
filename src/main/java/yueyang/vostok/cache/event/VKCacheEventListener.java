package yueyang.vostok.cache.event;

/**
 * 缓存事件监听器函数式接口。
 * <p>
 * 使用方式：
 * <pre>{@code
 * new VKCacheConfig()
 *     .eventListener(event -> log.info("{} key={}", event.type(), event.key()));
 * }</pre>
 * <p>
 * 监听器在 {@link yueyang.vostok.cache.core.VKCacheRuntime} 内部<b>同步</b>调用，
 * 实现应尽量轻量，避免阻塞缓存操作主路径。
 * 监听器抛出的异常会被 try/catch 隔离，不会影响缓存业务逻辑。
 */
@FunctionalInterface
public interface VKCacheEventListener {
    /**
     * 处理缓存事件。
     *
     * @param event 事件对象，包含缓存名称、事件类型、key 等信息
     */
    void onEvent(VKCacheEvent event);
}
