package yueyang.vostok.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 事件处理器注解，标记在方法上，配合 VostokEvent.scan() 自动注册监听器。
 * 被注解的方法必须恰好有一个参数，该参数类型即为监听的事件类型。
 *
 * <p>示例：
 * <pre>{@code
 * public class MyHandler {
 *     @VKEventHandler(priority = VKEventPriority.HIGH)
 *     public void onUserCreated(UserCreatedEvent e) { ... }
 *
 *     @VKEventHandler(mode = VKListenerMode.ASYNC, once = true)
 *     public void onFirstOrder(OrderPaidEvent e) { ... }
 * }
 * List<VKEventSubscription> subs = VostokEvent.scan(new MyHandler());
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface VKEventHandler {

    /**
     * 监听器执行模式，默认 SYNC（同步，在 publish 调用线程上执行）。
     */
    VKListenerMode mode() default VKListenerMode.SYNC;

    /**
     * 监听器优先级，默认 NORMAL。优先级高的监听器在同一次 publish 中先执行。
     */
    VKEventPriority priority() default VKEventPriority.NORMAL;

    /**
     * 是否为一次性监听器，触发一次后自动注销，默认 false。
     * CAS 保证并发 publish 场景下仍只触发一次。
     */
    boolean once() default false;
}
