package yueyang.vostok.event.core;

import yueyang.vostok.Vostok;
import yueyang.vostok.event.VKEventConfig;
import yueyang.vostok.event.VKEventDeadLetterHandler;
import yueyang.vostok.event.VKEventHandler;
import yueyang.vostok.event.VKEventListener;
import yueyang.vostok.event.VKEventListenerErrorStrategy;
import yueyang.vostok.event.VKEventPriority;
import yueyang.vostok.event.VKEventPublishResult;
import yueyang.vostok.event.VKEventRejectionPolicy;
import yueyang.vostok.event.VKEventSubscription;
import yueyang.vostok.event.VKListenerMode;
import yueyang.vostok.util.VKAssert;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * 事件系统核心运行时，单例。
 *
 * <p>职责：
 * <ul>
 *   <li>维护监听器注册表（{@link #listeners}），支持按事件类型分组、优先级排序</li>
 *   <li>提供同步/异步两种执行模式，异步任务由内部线程池驱动</li>
 *   <li>通过版本号缓存（{@link #resolveCache}）加速层级事件匹配</li>
 *   <li>支持 once（一次性）、Predicate 过滤、优先级、dead letter、注解扫描等高级特性</li>
 * </ul>
 *
 * <p>线程安全：init/reinit/close 通过 {@code LOCK} 对象锁串行化；
 * on/off/publish 使用 ConcurrentHashMap + CopyOnWriteArrayList + CAS 实现无锁并发。
 */
public final class VKEventRuntime {
    private static final Object LOCK = new Object();
    private static final VKEventRuntime INSTANCE = new VKEventRuntime();

    /** 监听器 ID 生成器，单调递增，保证注册顺序可排序 */
    private final AtomicLong listenerId = new AtomicLong(1);

    /**
     * 监听器注册表：eventType → 该类型下所有 ListenerSlot 的有序列表。
     * CopyOnWriteArrayList 保证读无锁，写时复制避免并发修改。
     */
    private final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<ListenerSlot>> listeners =
            new ConcurrentHashMap<>();

    /**
     * 缓存版本号：每次 on/off/offAll/reinit/close 修改 listeners 后递增。
     * resolveMatched 命中缓存的条件：version 一致。
     */
    private final AtomicLong cacheVersion = new AtomicLong(0);

    /**
     * 层级解析缓存：eventClass → (version, 已排序的 ListenerSlot 不可变列表)。
     * 版本不一致时重建，避免每次 publish 全量扫描 listeners。
     */
    private final ConcurrentHashMap<Class<?>, CacheEntry> resolveCache = new ConcurrentHashMap<>();

    private volatile VKEventConfig config = new VKEventConfig();
    private volatile ThreadPoolExecutor asyncExecutor;
    private volatile boolean initialized;

    /** 死信处理器：事件没有任何匹配监听器时调用 */
    private volatile VKEventDeadLetterHandler deadLetterHandler;

    /**
     * 版本化缓存条目，存储特定 eventClass 在指定版本下解析出的监听器快照。
     */
    private record CacheEntry(long version, List<ListenerSlot> slots) {}

    /**
     * publishInternal 的中间结果，供 publish() 和 publishAsync() 共用。
     *
     * @param matched        匹配到的监听器数量
     * @param syncExecuted   同步监听器成功执行数
     * @param syncFailed     同步监听器执行失败数
     * @param asyncSubmitted 异步任务成功提交数
     * @param asyncRejected  异步任务被拒绝数
     * @param asyncFailedCount 异步监听器执行失败计数器（publishAsync 等待后读取）
     * @param startNanos     发布开始时间（System.nanoTime）
     * @param asyncFutures   所有已提交的异步任务 future 列表
     */
    private record PublishState(
            int matched,
            int syncExecuted,
            int syncFailed,
            int asyncSubmitted,
            int asyncRejected,
            AtomicInteger asyncFailedCount,
            long startNanos,
            List<CompletableFuture<Void>> asyncFutures
    ) {}

    private VKEventRuntime() {
    }

    public static VKEventRuntime getInstance() {
        return INSTANCE;
    }

    public boolean started() {
        return initialized;
    }

    public VKEventConfig config() {
        return config.copy();
    }

    public void init(VKEventConfig cfg) {
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            config = normalized(cfg);
            asyncExecutor = buildExecutor(config);
            initialized = true;
        }
    }

    public void reinit(VKEventConfig cfg) {
        ThreadPoolExecutor old;
        synchronized (LOCK) {
            config = normalized(cfg);
            old = asyncExecutor;
            asyncExecutor = buildExecutor(config);
            // Bug2 fix: reinit 必须清空旧监听器，否则旧订阅在新配置下仍会响应
            listeners.clear();
            // 清空后使缓存失效
            cacheVersion.incrementAndGet();
            initialized = true;
        }
        shutdownExecutor(old, config.getShutdownWaitMs());
    }

    public void close() {
        ThreadPoolExecutor old;
        synchronized (LOCK) {
            old = asyncExecutor;
            asyncExecutor = null;
            listeners.clear();
            // 关闭后使缓存失效
            cacheVersion.incrementAndGet();
            initialized = false;
            listenerId.set(1);
        }
        shutdownExecutor(old, config.getShutdownWaitMs());
    }

    // ------------------------------------------------------------------ on()

    /** 注册同步监听器，使用默认 NORMAL 优先级 */
    public <T> VKEventSubscription on(Class<T> eventType, VKListenerMode mode, VKEventListener<T> listener) {
        return on(eventType, mode, VKEventPriority.NORMAL, null, listener);
    }

    /** 注册带优先级的同步监听器 */
    public <T> VKEventSubscription on(Class<T> eventType, VKEventPriority priority, VKEventListener<T> listener) {
        return on(eventType, VKListenerMode.SYNC, priority, null, listener);
    }

    /** 注册带模式和优先级的监听器 */
    public <T> VKEventSubscription on(Class<T> eventType, VKListenerMode mode, VKEventPriority priority,
                                       VKEventListener<T> listener) {
        return on(eventType, mode, priority, null, listener);
    }

    /**
     * 注册监听器（全参数版本）。
     *
     * @param eventType 监听的事件类型（支持多态，父类监听器会接收子类事件）
     * @param mode      执行模式：SYNC 在 publish 线程执行，ASYNC 提交到线程池
     * @param priority  优先级，值越小越先执行
     * @param filter    可选前置过滤器；为 null 表示不过滤；filter 不满足时跳过执行（不消耗 once token）
     * @param listener  事件处理逻辑
     * @return 订阅句柄，可通过 cancel() 或 VostokEvent.off() 取消
     */
    @SuppressWarnings("unchecked")
    public <T> VKEventSubscription on(Class<T> eventType, VKListenerMode mode, VKEventPriority priority,
                                       Predicate<T> filter, VKEventListener<T> listener) {
        ensureInit();
        VKAssert.notNull(eventType, "Event type is null");
        VKAssert.notNull(mode, "Listener mode is null");
        VKAssert.notNull(priority, "Event priority is null");
        VKAssert.notNull(listener, "Event listener is null");

        long id = listenerId.getAndIncrement();
        Predicate<Object> objFilter = filter == null ? null : (Predicate<Object>) (Object) filter;
        ListenerSlot slot = new ListenerSlot(id, eventType, mode, priority, false,
                (VKEventListener<Object>) listener, objFilter);
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(slot);
        // 新增监听器后使解析缓存失效
        cacheVersion.incrementAndGet();

        Runnable cancelHook = () -> offById(eventType, id);
        return new VKEventSubscription(id, eventType, mode, priority, cancelHook);
    }

    // --------------------------------------------------------------- once()

    /** 注册一次性同步监听器，触发一次后自动注销 */
    public <T> VKEventSubscription once(Class<T> eventType, VKEventListener<T> listener) {
        return onceInternal(eventType, VKListenerMode.SYNC, VKEventPriority.NORMAL, listener);
    }

    /** 注册一次性监听器，指定执行模式 */
    public <T> VKEventSubscription once(Class<T> eventType, VKListenerMode mode, VKEventListener<T> listener) {
        return onceInternal(eventType, mode, VKEventPriority.NORMAL, listener);
    }

    /**
     * 内部 once 注册：支持优先级参数，供 once() 和 scan() 共用。
     * once 监听器使用 AtomicBoolean fired 通过 CAS 保证并发 publish 下仅触发一次。
     */
    @SuppressWarnings("unchecked")
    private <T> VKEventSubscription onceInternal(Class<T> eventType, VKListenerMode mode,
                                                  VKEventPriority priority, VKEventListener<T> listener) {
        ensureInit();
        VKAssert.notNull(eventType, "Event type is null");
        VKAssert.notNull(mode, "Listener mode is null");
        VKAssert.notNull(priority, "Event priority is null");
        VKAssert.notNull(listener, "Event listener is null");

        long id = listenerId.getAndIncrement();
        // once=true：ListenerSlot 构造时初始化 fired AtomicBoolean
        ListenerSlot slot = new ListenerSlot(id, eventType, mode, priority, true,
                (VKEventListener<Object>) listener, null);
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(slot);
        cacheVersion.incrementAndGet();

        Runnable cancelHook = () -> offById(eventType, id);
        return new VKEventSubscription(id, eventType, mode, priority, cancelHook);
    }

    // ----------------------------------------------------------------- off()

    public void off(VKEventSubscription subscription) {
        if (subscription == null || subscription.getEventType() == null) {
            return;
        }
        offById(subscription.getEventType(), subscription.getId());
    }

    /**
     * 按 ID 原子删除监听器（Bug1 fix：使用 compute() 替代先读后写的 TOCTOU 模式）。
     * compute() 保证 removeIf 和 null 清理的原子性，避免并发 off 时遗留空列表或漏删。
     */
    private void offById(Class<?> eventType, long id) {
        listeners.compute(eventType, (k, slots) -> {
            if (slots == null) return null;
            slots.removeIf(it -> it.id == id);
            // 列表为空时返回 null，ConcurrentHashMap 会将 key 删除
            return slots.isEmpty() ? null : slots;
        });
        // 移除后使解析缓存失效
        cacheVersion.incrementAndGet();
    }

    public void offAll(Class<?> eventType) {
        if (eventType == null) {
            return;
        }
        listeners.remove(eventType);
        cacheVersion.incrementAndGet();
    }

    // ---------------------------------------------------------- onDeadLetter

    /**
     * 注册死信处理器。当 publish 的事件无任何匹配监听器时，调用该 handler。
     * 全局唯一，后注册覆盖前注册。
     */
    public void onDeadLetter(VKEventDeadLetterHandler handler) {
        this.deadLetterHandler = handler;
    }

    // -------------------------------------------------------------- publish()

    /**
     * 同步发布事件。
     * enabled 检查紧跟 ensureInit 之后（提前返回，避免无谓解析）。
     * 不等待异步监听器完成；返回结果中 asyncFailed 固定为 0。
     */
    public VKEventPublishResult publish(Object event) {
        ensureInit();
        VKAssert.notNull(event, "Event is null");
        // enabled 检查：移到最前（ensureInit 之后）提前短路
        if (!config.isEnabled()) {
            return new VKEventPublishResult(0, 0, 0, 0, 0, 0, 0L);
        }
        PublishState state = publishInternal(event);
        long costNs = System.nanoTime() - state.startNanos();
        // publish() 不等待异步 future，asyncFailed 无法统计，固定为 0
        return new VKEventPublishResult(
                state.matched(), state.syncExecuted(), state.syncFailed(),
                state.asyncSubmitted(), state.asyncRejected(), 0, costNs
        );
    }

    /**
     * 异步发布事件：提交后等待所有 ASYNC 监听器 future 全部完成，再 complete 返回的 CompletableFuture。
     * 这使调用方可通过 .get() / .join() 确认所有异步监听器已执行完毕。
     * asyncFailed 在所有 future 完成后统计。
     */
    public CompletableFuture<VKEventPublishResult> publishAsync(Object event) {
        ensureInit();
        VKAssert.notNull(event, "Event is null");
        if (!config.isEnabled()) {
            return CompletableFuture.completedFuture(
                    new VKEventPublishResult(0, 0, 0, 0, 0, 0, 0L));
        }

        PublishState state = publishInternal(event);
        List<CompletableFuture<Void>> futures = state.asyncFutures();

        if (futures.isEmpty()) {
            // 没有异步监听器，立即完成
            long costNs = System.nanoTime() - state.startNanos();
            return CompletableFuture.completedFuture(new VKEventPublishResult(
                    state.matched(), state.syncExecuted(), state.syncFailed(),
                    state.asyncSubmitted(), state.asyncRejected(),
                    state.asyncFailedCount().get(), costNs
            ));
        }

        // allOf 等待所有异步监听器 future 完成（含异常完成），handle 保证不传播异常
        CompletableFuture<Void> allOf =
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        return allOf.handle((v, ex) -> {
            long costNs = System.nanoTime() - state.startNanos();
            return new VKEventPublishResult(
                    state.matched(), state.syncExecuted(), state.syncFailed(),
                    state.asyncSubmitted(), state.asyncRejected(),
                    state.asyncFailedCount().get(), costNs
            );
        });
    }

    // ----------------------------------------------------------------- scan()

    /**
     * 扫描 bean 对象（及其所有父类，不含 Object）的 declared 方法，
     * 将带 {@link VKEventHandler} 注解的方法自动注册为监听器。
     *
     * <p>校验规则：被注解方法必须恰好有 1 个参数，参数类型即为监听的事件类型。
     * 违规时抛 VKArgumentException。
     *
     * @param bean 含 @VKEventHandler 方法的对象实例
     * @return 注册成功的订阅句柄列表（顺序与扫描顺序一致）
     */
    public List<VKEventSubscription> scan(Object bean) {
        VKAssert.notNull(bean, "Bean is null");
        ensureInit();
        List<VKEventSubscription> subs = new ArrayList<>();
        // 扫描本类及所有父类（不含 Object），父类方法亦可被发现
        Class<?> cls = bean.getClass();
        while (cls != null && cls != Object.class) {
            for (Method method : cls.getDeclaredMethods()) {
                VKEventHandler ann = method.getAnnotation(VKEventHandler.class);
                if (ann == null) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                // 校验：方法必须恰好有 1 个参数
                VKAssert.isTrue(params.length == 1,
                        "Method annotated with @VKEventHandler must have exactly 1 parameter: "
                                + method.getName());
                // 允许访问非 public 方法
                method.setAccessible(true);

                @SuppressWarnings("unchecked")
                Class<Object> eventType = (Class<Object>) params[0];
                VKListenerMode mode = ann.mode();
                VKEventPriority priority = ann.priority();
                boolean once = ann.once();

                // 将反射调用包装为 VKEventListener
                VKEventListener<Object> listener = event -> {
                    try {
                        method.invoke(bean, event);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        // 解包 InvocationTargetException，透传原始异常
                        Throwable cause = e.getCause();
                        if (cause instanceof RuntimeException re) throw re;
                        if (cause instanceof Exception ex) throw ex;
                        throw new RuntimeException(cause != null ? cause : e);
                    }
                };

                VKEventSubscription sub = once
                        ? onceInternal(eventType, mode, priority, listener)
                        : on(eventType, mode, priority, null, listener);
                subs.add(sub);
            }
            cls = cls.getSuperclass();
        }
        return subs;
    }

    // -------------------------------------------------------- publishInternal

    /**
     * 核心发布逻辑，供 publish() 和 publishAsync() 共用。
     * 遍历匹配的监听器，分别执行同步/异步分支，记录统计信息。
     *
     * @param event 要发布的事件对象
     * @return 中间状态对象，包含计数器和 asyncFutures 列表
     */
    private PublishState publishInternal(Object event) {
        // Bug6 fix: 全部改为 nanoTime 精度
        long startNanos = System.nanoTime();
        List<ListenerSlot> matched = resolveMatched(event.getClass());
        AtomicInteger asyncFailedCount = new AtomicInteger(0);
        List<CompletableFuture<Void>> asyncFutures = new ArrayList<>();

        if (matched.isEmpty()) {
            // dead letter：无任何匹配监听器时通知处理器
            VKEventDeadLetterHandler dlh = deadLetterHandler;
            if (dlh != null) {
                try {
                    dlh.handle(event);
                } catch (Throwable ignore) {
                    // dead letter handler 异常不影响主流程
                }
            }
            return new PublishState(0, 0, 0, 0, 0, asyncFailedCount, startNanos, asyncFutures);
        }

        int syncExecuted = 0;
        int syncFailed = 0;
        int asyncSubmitted = 0;
        int asyncRejected = 0;

        for (ListenerSlot slot : matched) {
            // 过滤器检查：先判断过滤（不消耗 once token），filter 不满足则跳过
            if (slot.filter != null && !slot.filter.test(event)) {
                continue;
            }

            // once 语义：CAS 保证并发 publish 场景下仅一个线程能执行此监听器
            if (slot.once && !slot.fired.compareAndSet(false, true)) {
                // 已被其他线程触发，本次跳过
                continue;
            }

            if (slot.mode == VKListenerMode.SYNC) {
                try {
                    slot.listener.onEvent(event);
                    syncExecuted++;
                } catch (Throwable t) {
                    syncFailed++;
                    logListenerError("sync", event, slot, t);
                    if (config.getListenerErrorStrategy() == VKEventListenerErrorStrategy.FAIL_FAST) {
                        // FAIL_FAST：同步监听器异常时立即注销 once 并重新抛出
                        if (slot.once) offById(slot.eventType, slot.id);
                        throw toRuntime(t);
                    }
                }
                // once 监听器执行后（无论成功/失败）立即注销
                if (slot.once) {
                    offById(slot.eventType, slot.id);
                }
                continue;
            }

            // ASYNC 模式
            try {
                ThreadPoolExecutor executor = asyncExecutor;
                // Bug7: executor 可能因 close() 与 publish() 并发而为 null；此 null 检查防护该竞争窗口
                if (executor == null) {
                    throw new RejectedExecutionException("Async executor is not ready");
                }
                CompletableFuture<Void> future = new CompletableFuture<>();
                executor.execute(() -> {
                    try {
                        slot.listener.onEvent(event);
                        future.complete(null);
                    } catch (Throwable t) {
                        // Bug5: 统计异步失败数
                        asyncFailedCount.incrementAndGet();
                        logListenerError("async", event, slot, t);
                        // Bug4: FAIL_FAST 对 async 有效：异常时将 future 标记为失败
                        // publishAsync 的 allOf 会检测到，调用方可判断 asyncFailed > 0
                        if (config.getListenerErrorStrategy() == VKEventListenerErrorStrategy.FAIL_FAST) {
                            future.completeExceptionally(t);
                        } else {
                            // CONTINUE：即便失败也标记 future 完成，不阻断 publishAsync 等待
                            future.complete(null);
                        }
                    } finally {
                        // once 异步监听器：在异步线程执行完成后注销
                        if (slot.once) {
                            offById(slot.eventType, slot.id);
                        }
                    }
                });
                // 仅在成功提交后将 future 加入等待列表
                asyncFutures.add(future);
                asyncSubmitted++;
            } catch (RejectedExecutionException ex) {
                asyncRejected++;
                logReject(event, slot, ex);
                // 任务被拒绝，once 的 fired 标记需重置，给下次 publish 机会
                if (slot.once) {
                    slot.fired.set(false);
                }
            }
        }

        return new PublishState(matched.size(), syncExecuted, syncFailed,
                asyncSubmitted, asyncRejected, asyncFailedCount, startNanos, asyncFutures);
    }

    // ------------------------------------------------------- resolveMatched

    /**
     * Perf1：带版本号的解析缓存。
     * 若缓存版本与当前 cacheVersion 一致，直接返回缓存的已排序 unmodifiable 列表；
     * 否则调用 doResolve 全量重建并写入缓存。
     */
    private List<ListenerSlot> resolveMatched(Class<?> eventClass) {
        long v = cacheVersion.get();
        CacheEntry entry = resolveCache.get(eventClass);
        // 缓存命中：版本一致，直接返回（Perf2：返回 unmodifiable，无需防御性拷贝）
        if (entry != null && entry.version() == v) {
            return entry.slots();
        }
        // 缓存未命中：全量扫描所有 eventType，找出与 eventClass 匹配（含父类/接口）的 slot
        List<ListenerSlot> resolved = doResolve(eventClass);
        resolveCache.put(eventClass, new CacheEntry(v, resolved));
        return resolved;
    }

    /**
     * 全量解析：遍历 listeners，收集所有 isAssignableFrom(eventClass) 的 slot，
     * 按 (priority.value ASC, id ASC) 排序后返回 unmodifiable 快照。
     *
     * <p>Perf3：排序在此处执行一次并缓存，publish 直接使用有序结果。
     * <p>Perf2：返回 unmodifiableList，缓存共享，避免每次 publish 防御性拷贝。
     */
    private List<ListenerSlot> doResolve(Class<?> eventClass) {
        List<ListenerSlot> out = new ArrayList<>();
        for (Map.Entry<Class<?>, CopyOnWriteArrayList<ListenerSlot>> e : listeners.entrySet()) {
            if (e.getKey().isAssignableFrom(eventClass)) {
                out.addAll(e.getValue());
            }
        }
        // 按优先级升序（HIGHEST=1 最先），同优先级按注册 ID 升序（先注册先执行）
        out.sort(Comparator.comparingInt((ListenerSlot s) -> s.priority.getValue())
                           .thenComparingLong(s -> s.id));
        return Collections.unmodifiableList(out);
    }

    // ---------------------------------------------------------- ensureInit

    private void ensureInit() {
        if (initialized) {
            return;
        }
        synchronized (LOCK) {
            if (!initialized) {
                config = normalized(config);
                asyncExecutor = buildExecutor(config);
                initialized = true;
            }
        }
    }

    // --------------------------------------------------------- 工具方法

    private VKEventConfig normalized(VKEventConfig cfg) {
        return (cfg == null ? new VKEventConfig() : cfg.copy());
    }

    private ThreadPoolExecutor buildExecutor(VKEventConfig cfg) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                Math.max(1, cfg.getAsyncCoreThreads()),
                Math.max(cfg.getAsyncCoreThreads(), cfg.getAsyncMaxThreads()),
                Math.max(0, cfg.getAsyncKeepAliveMs()),
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(1, cfg.getAsyncQueueCapacity())),
                new EventThreadFactory(),
                rejectionHandler(cfg.getRejectionPolicy())
        );
        executor.allowCoreThreadTimeOut(false);
        return executor;
    }

    private java.util.concurrent.RejectedExecutionHandler rejectionHandler(VKEventRejectionPolicy policy) {
        VKEventRejectionPolicy p = policy == null ? VKEventRejectionPolicy.CALLER_RUNS : policy;
        if (p == VKEventRejectionPolicy.ABORT) {
            return new ThreadPoolExecutor.AbortPolicy();
        }
        if (p == VKEventRejectionPolicy.DISCARD) {
            return new ThreadPoolExecutor.DiscardPolicy();
        }
        return new ThreadPoolExecutor.CallerRunsPolicy();
    }

    private void shutdownExecutor(ThreadPoolExecutor executor, long waitMs) {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(Math.max(0, waitMs), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    /**
     * Bug3 fix: 日志字段名由 listenerType 改为 subscribedType，语义更准确（记录注册时的事件类型）。
     */
    private void logListenerError(String mode, Object event, ListenerSlot slot, Throwable t) {
        try {
            Vostok.Log.error("Vostok.Event {} listener failed, eventType={}, subscribedType={}, err={}",
                    mode, event.getClass().getName(), slot.eventType.getName(), t.getMessage());
            Vostok.Log.error("Vostok.Event listener stack", t);
        } catch (Throwable ignore) {
            // 日志失败不影响业务流程
        }
    }

    private void logReject(Object event, ListenerSlot slot, Throwable t) {
        try {
            Vostok.Log.warn("Vostok.Event async listener rejected, eventType={}, subscribedType={}, err={}",
                    event.getClass().getName(), slot.eventType.getName(), t.getMessage());
        } catch (Throwable ignore) {
            // 日志失败不影响业务流程
        }
    }

    private RuntimeException toRuntime(Throwable t) {
        if (t instanceof RuntimeException re) {
            return re;
        }
        return new IllegalStateException(t.getMessage(), t);
    }

    // ---------------------------------------------------------- 内部类

    private static final class EventThreadFactory implements ThreadFactory {
        private final AtomicLong n = new AtomicLong(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "vostok-event-async-" + n.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }

    /**
     * 监听器槽，持有注册时的全部元数据和执行器。
     *
     * @param id        全局唯一 ID，用于排序和注销
     * @param eventType 注册时指定的事件类型（可为父类/接口）
     * @param mode      执行模式
     * @param priority  优先级
     * @param once      是否一次性；true 时初始化 fired AtomicBoolean
     * @param listener  实际监听器实现
     * @param filter    可选过滤器；null 表示不过滤
     */
    private static final class ListenerSlot {
        final long id;
        final Class<?> eventType;
        final VKListenerMode mode;
        final VKEventPriority priority;
        /** 是否一次性监听器 */
        final boolean once;
        /**
         * CAS 保护 once 语义：compareAndSet(false, true) 保证并发 publish 下仅一个线程执行。
         * 仅 once=true 时有效（非 null）；once=false 时为 null，不做 CAS。
         */
        final AtomicBoolean fired;
        /** 可选前置过滤器；null 表示不过滤 */
        final Predicate<Object> filter;
        final VKEventListener<Object> listener;

        ListenerSlot(long id, Class<?> eventType, VKListenerMode mode, VKEventPriority priority,
                     boolean once, VKEventListener<Object> listener, Predicate<Object> filter) {
            this.id = id;
            this.eventType = eventType;
            this.mode = mode;
            this.priority = priority;
            this.once = once;
            // once=true 时初始化 fired，false 时不初始化（节省对象分配）
            this.fired = once ? new AtomicBoolean(false) : null;
            this.listener = listener;
            this.filter = filter;
        }
    }
}
