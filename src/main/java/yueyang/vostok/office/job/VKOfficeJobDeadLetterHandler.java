package yueyang.vostok.office.job;

/**
 * 任务通知死信处理器。
 *
 * <p>当一次状态通知没有任何匹配监听器时触发。</p>
 */
@FunctionalInterface
public interface VKOfficeJobDeadLetterHandler {
    void onDeadLetter(VKOfficeJobNotification notification);
}
