package yueyang.vostok.event;

/**
 * 死信处理器接口：当发布的事件没有任何匹配监听器时被调用。
 * 可通过 VostokEvent.onDeadLetter() 注册，用于监控未被消费的事件。
 */
@FunctionalInterface
public interface VKEventDeadLetterHandler {
    /**
     * 处理没有匹配监听器的事件（死信）。
     *
     * @param event 未被任何监听器处理的事件对象
     */
    void handle(Object event);
}
