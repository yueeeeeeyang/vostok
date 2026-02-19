package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.data.exception.VKArgumentException;
import yueyang.vostok.event.VKEventConfig;
import yueyang.vostok.event.VKEventListenerErrorStrategy;
import yueyang.vostok.event.VKEventPublishResult;
import yueyang.vostok.event.VKEventRejectionPolicy;
import yueyang.vostok.event.VKEventSubscription;
import yueyang.vostok.event.VKListenerMode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
        List<String> logs = new java.util.concurrent.CopyOnWriteArrayList<>();
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
