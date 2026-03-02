package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.data.exception.VKArgumentException;
import yueyang.vostok.event.VKEventConfig;
import yueyang.vostok.event.VKEventDeadLetterHandler;
import yueyang.vostok.event.VKEventHandler;
import yueyang.vostok.event.VKEventListenerErrorStrategy;
import yueyang.vostok.event.VKEventPriority;
import yueyang.vostok.event.VKEventPublishResult;
import yueyang.vostok.event.VKEventRejectionPolicy;
import yueyang.vostok.event.VKEventSubscription;
import yueyang.vostok.event.VKListenerMode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class VostokEventTest {
    @AfterEach
    void tearDown() {
        Vostok.Event.close();
    }

    @Test
    void testSyncListenersRunInRegisterOrder() {
        List<String> logs = new ArrayList<>();
        Vostok.Event.init();
        Vostok.Event.on(UserCreatedEvent.class, e -> logs.add("l1:" + e.userId));
        Vostok.Event.on(UserCreatedEvent.class, e -> logs.add("l2:" + e.userId));

        VKEventPublishResult r = Vostok.Event.publish(new UserCreatedEvent(1L));

        assertEquals(2, r.getMatchedListeners());
        assertEquals(2, r.getSyncExecuted());
        assertEquals(0, r.getSyncFailed());
        assertEquals(0, r.getAsyncSubmitted());
        assertEquals(List.of("l1:1", "l2:1"), logs);
    }

    @Test
    void testSinglePublishCanDriveSyncAndAsyncListeners() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        List<String> logs = new CopyOnWriteArrayList<>();
        Vostok.Event.init(new VKEventConfig()
                .asyncCoreThreads(1)
                .asyncMaxThreads(1)
                .asyncQueueCapacity(8)
                .rejectionPolicy(VKEventRejectionPolicy.CALLER_RUNS));

        Vostok.Event.on(OrderPaidEvent.class, e -> logs.add("sync:" + e.orderNo));
        Vostok.Event.on(OrderPaidEvent.class, VKListenerMode.ASYNC, e -> {
            logs.add("async:" + e.orderNo);
            done.countDown();
        });

        VKEventPublishResult r = Vostok.Event.publish(new OrderPaidEvent("ORD-1"));

        assertEquals(2, r.getMatchedListeners());
        assertEquals(1, r.getSyncExecuted());
        assertEquals(1, r.getAsyncSubmitted());
        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertTrue(logs.contains("sync:ORD-1"));
        assertTrue(logs.contains("async:ORD-1"));
    }

    @Test
    void testOffSubscription() {
        AtomicInteger count = new AtomicInteger();
        Vostok.Event.init();
        VKEventSubscription sub = Vostok.Event.on(UserCreatedEvent.class, e -> count.incrementAndGet());
        Vostok.Event.off(sub);

        VKEventPublishResult r = Vostok.Event.publish(new UserCreatedEvent(2L));

        assertEquals(0, count.get());
        assertEquals(0, r.getMatchedListeners());
    }

    @Test
    void testOffAll() {
        AtomicInteger count = new AtomicInteger();
        Vostok.Event.init();
        Vostok.Event.on(UserCreatedEvent.class, e -> count.incrementAndGet());
        Vostok.Event.on(UserCreatedEvent.class, e -> count.incrementAndGet());
        Vostok.Event.offAll(UserCreatedEvent.class);

        VKEventPublishResult r = Vostok.Event.publish(new UserCreatedEvent(3L));

        assertEquals(0, count.get());
        assertEquals(0, r.getMatchedListeners());
    }

    @Test
    void testContinueStrategyForSyncListenerError() {
        List<String> logs = new ArrayList<>();
        Vostok.Event.init(new VKEventConfig()
                .listenerErrorStrategy(VKEventListenerErrorStrategy.CONTINUE));
        Vostok.Event.on(UserCreatedEvent.class, e -> {
            throw new IllegalStateException("boom");
        });
        Vostok.Event.on(UserCreatedEvent.class, e -> logs.add("next"));

        VKEventPublishResult r = Vostok.Event.publish(new UserCreatedEvent(4L));

        assertEquals(2, r.getMatchedListeners());
        assertEquals(1, r.getSyncExecuted());
        assertEquals(1, r.getSyncFailed());
        assertEquals(List.of("next"), logs);
    }

    @Test
    void testFailFastStrategyForSyncListenerError() {
        Vostok.Event.init(new VKEventConfig()
                .listenerErrorStrategy(VKEventListenerErrorStrategy.FAIL_FAST));
        Vostok.Event.on(UserCreatedEvent.class, e -> {
            throw new IllegalStateException("boom-fast");
        });
        Vostok.Event.on(UserCreatedEvent.class, e -> fail("should not run"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> Vostok.Event.publish(new UserCreatedEvent(5L)));
        assertTrue(ex.getMessage().contains("boom-fast"));
    }

    @Test
    void testAsyncRejectionCountWhenAbortPolicy() throws Exception {
        CountDownLatch blocker = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        Vostok.Event.init(new VKEventConfig()
                .asyncCoreThreads(1)
                .asyncMaxThreads(1)
                .asyncQueueCapacity(1)
                .rejectionPolicy(VKEventRejectionPolicy.ABORT));

        Vostok.Event.on(OrderPaidEvent.class, VKListenerMode.ASYNC, e -> {
            started.countDown();
            blocker.await(2, TimeUnit.SECONDS);
        });

        assertEquals(1, Vostok.Event.publish(new OrderPaidEvent("A")).getAsyncSubmitted());
        assertTrue(started.await(1, TimeUnit.SECONDS));
        assertEquals(1, Vostok.Event.publish(new OrderPaidEvent("B")).getAsyncSubmitted());
        VKEventPublishResult r3 = Vostok.Event.publish(new OrderPaidEvent("C"));
        blocker.countDown();

        assertEquals(1, r3.getMatchedListeners());
        assertEquals(0, r3.getAsyncSubmitted());
        assertEquals(1, r3.getAsyncRejected());
    }

    @Test
    void testPublishToSuperTypeListener() {
        AtomicInteger count = new AtomicInteger();
        Vostok.Event.init();
        Vostok.Event.on(BaseEvent.class, e -> count.incrementAndGet());

        VKEventPublishResult r = Vostok.Event.publish(new UserCreatedEvent(6L));

        assertEquals(1, count.get());
        assertEquals(1, r.getMatchedListeners());
    }

    @Test
    void testPublishNullEventThrows() {
        Vostok.Event.init();
        assertThrows(VKArgumentException.class, () -> Vostok.Event.publish(null));
    }

    @Test
    void testInitIdempotentAndReinitEffective() {
        Vostok.Event.init(new VKEventConfig().asyncQueueCapacity(3));
        assertEquals(3, Vostok.Event.config().getAsyncQueueCapacity());

        Vostok.Event.init(new VKEventConfig().asyncQueueCapacity(99));
        assertEquals(3, Vostok.Event.config().getAsyncQueueCapacity());

        Vostok.Event.reinit(new VKEventConfig().asyncQueueCapacity(99));
        assertEquals(99, Vostok.Event.config().getAsyncQueueCapacity());
    }

    @Test
    void testCloseClearsListeners() {
        AtomicInteger count = new AtomicInteger();
        Vostok.Event.init();
        Vostok.Event.on(UserCreatedEvent.class, e -> count.incrementAndGet());
        Vostok.Event.close();

        VKEventPublishResult r = Vostok.Event.publish(new UserCreatedEvent(7L));

        assertEquals(0, count.get());
        assertEquals(0, r.getMatchedListeners());
        assertTrue(Vostok.Event.started());
    }

    // ---------------------------------------------------------------- 新增测试

    /**
     * once 监听器仅触发一次：连续两次 publish，处理器只被调用一次。
     */
    @Test
    void testOnceListenerFiresOnlyOnce() {
        AtomicInteger count = new AtomicInteger();
        Vostok.Event.init();
        Vostok.Event.once(UserCreatedEvent.class, e -> count.incrementAndGet());

        Vostok.Event.publish(new UserCreatedEvent(10L));
        Vostok.Event.publish(new UserCreatedEvent(11L));

        assertEquals(1, count.get());
    }

    /**
     * 并发 publish 下，once 监听器仍只执行一次（CAS 保护）。
     */
    @Test
    void testOnceConcurrentPublish() throws Exception {
        AtomicInteger count = new AtomicInteger();
        Vostok.Event.init(new VKEventConfig().asyncCoreThreads(4).asyncMaxThreads(8));
        Vostok.Event.once(UserCreatedEvent.class, e -> count.incrementAndGet());

        int threads = 8;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Thread> ts = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final long uid = i;
            Thread t = new Thread(() -> {
                ready.countDown();
                try { go.await(); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                Vostok.Event.publish(new UserCreatedEvent(uid));
            });
            ts.add(t);
            t.start();
        }
        ready.await();
        go.countDown();
        for (Thread t : ts) t.join(2000);

        assertEquals(1, count.get());
    }

    /**
     * 高优先级监听器先于低优先级监听器执行。
     */
    @Test
    void testPriorityOrderGlobalPriority() {
        List<String> logs = new CopyOnWriteArrayList<>();
        Vostok.Event.init();
        // 注册顺序：LOW 先注册，HIGH 后注册；期望 HIGH 先执行
        Vostok.Event.on(UserCreatedEvent.class, VKEventPriority.LOW, e -> logs.add("low"));
        Vostok.Event.on(UserCreatedEvent.class, VKEventPriority.HIGH, e -> logs.add("high"));
        Vostok.Event.on(UserCreatedEvent.class, VKEventPriority.NORMAL, e -> logs.add("normal"));

        Vostok.Event.publish(new UserCreatedEvent(20L));

        assertEquals(List.of("high", "normal", "low"), logs);
    }

    /**
     * Predicate 过滤器：不满足条件的事件跳过，满足条件的正常执行。
     */
    @Test
    void testPredicateFilterSkipsNonMatching() {
        AtomicInteger count = new AtomicInteger();
        Vostok.Event.init();
        // 只处理 userId > 100 的事件
        Vostok.Event.on(UserCreatedEvent.class, VKListenerMode.SYNC, VKEventPriority.NORMAL,
                e -> e.userId > 100,
                e -> count.incrementAndGet());

        Vostok.Event.publish(new UserCreatedEvent(50L));   // 不满足，跳过
        Vostok.Event.publish(new UserCreatedEvent(200L));  // 满足，执行

        assertEquals(1, count.get());
    }

    /**
     * subscription.cancel() 生效：取消后监听器不再接收事件。
     */
    @Test
    void testCancelSubscription() {
        AtomicInteger count = new AtomicInteger();
        Vostok.Event.init();
        VKEventSubscription sub = Vostok.Event.on(UserCreatedEvent.class, e -> count.incrementAndGet());

        Vostok.Event.publish(new UserCreatedEvent(30L)); // 触发一次
        sub.cancel();
        Vostok.Event.publish(new UserCreatedEvent(31L)); // 已取消，不触发

        assertEquals(1, count.get());
    }

    /**
     * 无匹配监听器时，死信处理器被调用。
     */
    @Test
    void testDeadLetterHandlerCalledOnNoListeners() {
        AtomicReference<Object> captured = new AtomicReference<>();
        Vostok.Event.init();
        Vostok.Event.onDeadLetter(captured::set);

        UserCreatedEvent evt = new UserCreatedEvent(40L);
        Vostok.Event.publish(evt);

        assertSame(evt, captured.get());
    }

    /**
     * publishAsync 等待所有异步监听器完成后 future 才 complete。
     */
    @Test
    void testPublishAsyncWaitsForAsyncListeners() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AtomicInteger count = new AtomicInteger();
        Vostok.Event.init(new VKEventConfig().asyncCoreThreads(2).asyncMaxThreads(2));
        Vostok.Event.on(OrderPaidEvent.class, VKListenerMode.ASYNC, e -> {
            started.countDown();
            count.incrementAndGet();
        });

        CompletableFuture<VKEventPublishResult> future =
                Vostok.Event.publishAsync(new OrderPaidEvent("ORD-2"));
        VKEventPublishResult r = future.get(3, TimeUnit.SECONDS);

        // publishAsync 完成时，异步监听器必已执行
        assertEquals(1, r.getMatchedListeners());
        assertEquals(1, r.getAsyncSubmitted());
        assertEquals(1, count.get());
    }

    /**
     * 异步监听器抛出异常时，asyncFailed > 0（publishAsync 统计）。
     */
    @Test
    void testPublishAsyncReturnsAsyncFailed() throws Exception {
        Vostok.Event.init(new VKEventConfig()
                .asyncCoreThreads(1)
                .asyncMaxThreads(1)
                .listenerErrorStrategy(VKEventListenerErrorStrategy.CONTINUE));
        Vostok.Event.on(OrderPaidEvent.class, VKListenerMode.ASYNC, e -> {
            throw new RuntimeException("async-fail");
        });

        CompletableFuture<VKEventPublishResult> future =
                Vostok.Event.publishAsync(new OrderPaidEvent("ORD-3"));
        VKEventPublishResult r = future.get(3, TimeUnit.SECONDS);

        assertEquals(1, r.getAsyncSubmitted());
        assertEquals(1, r.getAsyncFailed());
    }

    /**
     * scan() 自动注册带 @VKEventHandler 注解的方法。
     */
    @Test
    void testScanAnnotatedBean() {
        AtomicInteger count = new AtomicInteger();
        Vostok.Event.init();

        class MyHandler {
            @VKEventHandler
            public void handle(UserCreatedEvent e) {
                count.incrementAndGet();
            }
        }

        List<VKEventSubscription> subs = Vostok.Event.scan(new MyHandler());
        assertEquals(1, subs.size());

        Vostok.Event.publish(new UserCreatedEvent(50L));
        assertEquals(1, count.get());
    }

    /**
     * scan() 中 @VKEventHandler(once=true) 仅触发一次。
     */
    @Test
    void testScanOnceAnnotation() {
        AtomicInteger count = new AtomicInteger();
        Vostok.Event.init();

        class OnceHandler {
            @VKEventHandler(once = true)
            public void handle(OrderPaidEvent e) {
                count.incrementAndGet();
            }
        }

        Vostok.Event.scan(new OnceHandler());

        Vostok.Event.publish(new OrderPaidEvent("O1"));
        Vostok.Event.publish(new OrderPaidEvent("O2"));

        assertEquals(1, count.get());
    }

    /**
     * 性能缓存命中：重复 publish 同类型事件，监听器计数一致（缓存不影响正确性）。
     */
    @Test
    void testCacheHitOnRepeatPublish() {
        AtomicInteger count = new AtomicInteger();
        Vostok.Event.init();
        Vostok.Event.on(UserCreatedEvent.class, e -> count.incrementAndGet());

        int times = 100;
        for (int i = 0; i < times; i++) {
            Vostok.Event.publish(new UserCreatedEvent(i));
        }

        assertEquals(times, count.get());
    }

    /**
     * Bug2 回归：reinit 后旧监听器不再响应（reinit 应清空 listeners）。
     */
    @Test
    void testReinitClearsListeners() {
        AtomicInteger count = new AtomicInteger();
        Vostok.Event.init();
        Vostok.Event.on(UserCreatedEvent.class, e -> count.incrementAndGet());

        // reinit 应清除所有已注册监听器
        Vostok.Event.reinit(new VKEventConfig());
        Vostok.Event.publish(new UserCreatedEvent(99L));

        assertEquals(0, count.get());
    }

    // ---------------------------------------------------------------- 内部事件类

    private static class BaseEvent {
    }

    private static final class UserCreatedEvent extends BaseEvent {
        private final long userId;

        private UserCreatedEvent(long userId) {
            this.userId = userId;
        }
    }

    private static final class OrderPaidEvent extends BaseEvent {
        private final String orderNo;

        private OrderPaidEvent(String orderNo) {
            this.orderNo = orderNo;
        }
    }
}
