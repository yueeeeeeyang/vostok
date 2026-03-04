package yueyang.vostok.office.job;

import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Office 任务运行时。 */
public final class VKOfficeJobRuntime {
    private final Object lock = new Object();
    private final VKOfficeJobStore store;
    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);

    private volatile boolean started;
    private volatile VKOfficeConfig config;
    private volatile ExecutorService workerExecutor;
    private volatile ExecutorService callbackExecutor;
    private volatile VKOfficeJobCallbackHub callbackHub;
    private volatile VKOfficeJobDispatcher dispatcher;

    public VKOfficeJobRuntime() {
        this(new VKInMemoryOfficeJobStore());
    }

    public VKOfficeJobRuntime(VKOfficeJobStore store) {
        this.store = store == null ? new VKInMemoryOfficeJobStore() : store;
    }

    public void init(VKOfficeConfig cfg) {
        synchronized (lock) {
            close();
            config = cfg;
            callbackHub = new VKOfficeJobCallbackHub();
            workerExecutor = buildExecutor(
                    cfg.getOfficeJobWorkerThreads(),
                    cfg.getOfficeJobQueueCapacity(),
                    "vostok-office-job-worker-");
            callbackExecutor = buildExecutor(
                    cfg.getOfficeJobCallbackThreads(),
                    cfg.getOfficeJobCallbackQueueCapacity(),
                    "vostok-office-job-callback-");
            dispatcher = new VKOfficeJobDispatcher(callbackHub, callbackExecutor);
            started = true;
        }
    }

    public void close() {
        synchronized (lock) {
            started = false;
            shutdownExecutor(workerExecutor);
            shutdownExecutor(callbackExecutor);
            workerExecutor = null;
            callbackExecutor = null;
            if (dispatcher != null) {
                dispatcher.clear();
            }
            dispatcher = null;
            callbackHub = null;
            states.clear();
            store.clear();
        }
    }

    public String submit(VKOfficeJobRequest request) {
        ensureStarted();
        if (request == null || request.task() == null) {
            throw new VKOfficeException(VKOfficeErrorCode.INVALID_ARGUMENT, "Office job request/task is null");
        }
        if (!config.getOfficeJobEnabled()) {
            throw new VKOfficeException(VKOfficeErrorCode.STATE_ERROR, "Office job runtime is disabled");
        }
        cleanupExpired();

        String jobId = buildJobId();
        long now = System.currentTimeMillis();
        VKOfficeJobInfo submitted = new VKOfficeJobInfo(
                jobId,
                request.type(),
                request.tag(),
                VKOfficeJobStatus.SUBMITTED,
                now,
                0L,
                0L,
                null,
                null,
                null,
                Map.of());
        store.save(submitted);

        State state = new State(jobId, request.type(), request.tag(), now);
        states.put(jobId, state);

        Future<?> future = workerExecutor.submit(() -> runJob(state, request.task()));
        state.future = future;
        return jobId;
    }

    public VKOfficeJobInfo get(String jobId) {
        ensureStarted();
        if (jobId == null || jobId.isBlank()) {
            throw new VKOfficeException(VKOfficeErrorCode.INVALID_ARGUMENT, "Office jobId is blank");
        }
        return store.get(jobId);
    }

    public List<VKOfficeJobInfo> list(VKOfficeJobQuery query) {
        ensureStarted();
        cleanupExpired();
        return store.list(query);
    }

    public boolean cancel(String jobId) {
        ensureStarted();
        State state = states.get(jobId);
        if (state == null) {
            VKOfficeJobInfo info = store.get(jobId);
            return info != null && info.status() == VKOfficeJobStatus.CANCELLED;
        }
        if (!state.cancelRequested.compareAndSet(false, true)) {
            return false;
        }
        Future<?> f = state.future;
        boolean cancelled = false;
        if (f != null) {
            cancelled = f.cancel(true);
        }
        // 处理“还未执行就取消”的场景，避免 await 一直等待。
        if (state.status == VKOfficeJobStatus.SUBMITTED || f == null || cancelled) {
            completeCancelledIfNeeded(state, "cancelled");
        }
        return true;
    }

    public VKOfficeJobResult await(String jobId, long timeoutMs) {
        ensureStarted();
        State state = states.get(jobId);
        if (state == null) {
            VKOfficeJobInfo info = store.get(jobId);
            if (info == null) {
                throw new VKOfficeException(VKOfficeErrorCode.NOT_FOUND, "Office job not found: " + jobId);
            }
            return toResult(info);
        }
        try {
            long wait = timeoutMs <= 0 ? 30_000L : timeoutMs;
            state.done.get(wait, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new VKOfficeException(VKOfficeErrorCode.STATE_ERROR,
                    "Await office job timeout/fail: " + jobId, e);
        }
        VKOfficeJobInfo info = store.get(jobId);
        if (info == null) {
            throw new VKOfficeException(VKOfficeErrorCode.NOT_FOUND, "Office job not found after await: " + jobId);
        }
        return toResult(info);
    }

    public VKOfficeJobSubscription onJob(VKOfficeJobStatus status,
                                         VKOfficeJobFilter filter,
                                         VKOfficeJobListener listener,
                                         boolean once) {
        ensureStarted();
        return callbackHub.on(status, filter, listener, once);
    }

    public void offJob(VKOfficeJobSubscription subscription) {
        ensureStarted();
        callbackHub.off(subscription);
    }

    public void offAllJobs() {
        ensureStarted();
        callbackHub.offAll();
    }

    public void onJobDeadLetter(VKOfficeJobDeadLetterHandler handler) {
        ensureStarted();
        callbackHub.onDeadLetter(handler);
    }

    private void runJob(State state, VKOfficeJobTask task) {
        if (state.done.isDone()) {
            return;
        }
        long startedAt = System.currentTimeMillis();
        if (state.cancelRequested.get()) {
            finishCancelled(state, startedAt, null);
            return;
        }
        updateStatus(state, VKOfficeJobStatus.RUNNING, startedAt, 0, null, null, null, Map.of());
        if (config.getOfficeJobNotifyOnRunning()) {
            notify(state, VKOfficeJobStatus.RUNNING, null, null);
        }

        try {
            VKOfficeJobExecutionResult result = Objects.requireNonNullElse(task.execute(), VKOfficeJobExecutionResult.empty());
            if (state.cancelRequested.get()) {
                finishCancelled(state, startedAt, result);
                return;
            }
            long finishedAt = System.currentTimeMillis();
            updateStatus(
                    state,
                    VKOfficeJobStatus.SUCCEEDED,
                    startedAt,
                    finishedAt,
                    result.resultPath(),
                    null,
                    null,
                    result.metrics());
            notify(state, VKOfficeJobStatus.SUCCEEDED, null, null);
        } catch (Throwable e) {
            if (state.cancelRequested.get() || e instanceof InterruptedException) {
                finishCancelled(state, startedAt, null);
                return;
            }
            long finishedAt = System.currentTimeMillis();
            String errorCode = e instanceof VKOfficeException oe ? oe.getCode() : VKOfficeErrorCode.WRITE_ERROR.getCode();
            String errorMsg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            updateStatus(
                    state,
                    VKOfficeJobStatus.FAILED,
                    startedAt,
                    finishedAt,
                    null,
                    errorCode,
                    errorMsg,
                    Map.of());
            notify(state, VKOfficeJobStatus.FAILED, errorCode, errorMsg);
        } finally {
            state.done.complete(null);
            states.remove(state.jobId);
        }
    }

    private void finishCancelled(State state, long startedAt, VKOfficeJobExecutionResult result) {
        synchronized (state) {
            if (state.done.isDone()) {
                return;
            }
            long finishedAt = System.currentTimeMillis();
            updateStatus(
                    state,
                    VKOfficeJobStatus.CANCELLED,
                    startedAt,
                    finishedAt,
                    result == null ? null : result.resultPath(),
                    null,
                    "cancelled",
                    result == null ? Map.of() : result.metrics());
            notify(state, VKOfficeJobStatus.CANCELLED, null, "cancelled");
        }
    }

    private void completeCancelledIfNeeded(State state, String reason) {
        synchronized (state) {
            if (state.done.isDone()) {
                return;
            }
            long now = System.currentTimeMillis();
            long start = state.startedAt > 0 ? state.startedAt : now;
            updateStatus(
                    state,
                    VKOfficeJobStatus.CANCELLED,
                    start,
                    now,
                    state.resultPath,
                    null,
                    reason,
                    state.metrics);
            notify(state, VKOfficeJobStatus.CANCELLED, null, reason);
            state.done.complete(null);
            states.remove(state.jobId);
        }
    }

    private void updateStatus(State state,
                              VKOfficeJobStatus status,
                              long startedAt,
                              long finishedAt,
                              String resultPath,
                              String errorCode,
                              String errorMessage,
                              Map<String, Object> metrics) {
        state.status = status;
        if (startedAt > 0) {
            state.startedAt = startedAt;
        }
        if (finishedAt > 0) {
            state.finishedAt = finishedAt;
        }
        state.resultPath = resultPath;
        state.errorCode = errorCode;
        state.errorMessage = errorMessage;
        state.metrics = metrics == null ? Map.of() : metrics;
        store.update(new VKOfficeJobInfo(
                state.jobId,
                state.type,
                state.tag,
                status,
                state.submittedAt,
                state.startedAt,
                state.finishedAt,
                state.resultPath,
                state.errorCode,
                state.errorMessage,
                state.metrics));
    }

    private void notify(State state, VKOfficeJobStatus status, String errorCode, String errorMessage) {
        VKOfficeJobNotification n = new VKOfficeJobNotification(
                state.jobId,
                state.type,
                state.tag,
                status,
                state.submittedAt,
                state.startedAt,
                state.finishedAt,
                state.finishedAt > 0 && state.startedAt > 0 ? Math.max(0, state.finishedAt - state.startedAt) : 0,
                state.resultPath,
                errorCode,
                errorMessage,
                state.metrics);
        dispatcher.dispatch(n);
    }

    private void ensureStarted() {
        if (!started) {
            throw new VKOfficeException(VKOfficeErrorCode.STATE_ERROR, "Office job runtime is not started");
        }
    }

    private void cleanupExpired() {
        long retention = config.getOfficeJobRetentionMs();
        if (retention <= 0) {
            return;
        }
        long deadline = System.currentTimeMillis() - retention;
        store.removeFinishedBefore(deadline);
    }

    private VKOfficeJobResult toResult(VKOfficeJobInfo info) {
        return new VKOfficeJobResult(
                info.jobId(),
                info.status(),
                info.resultPath(),
                info.errorCode(),
                info.errorMessage());
    }

    private String buildJobId() {
        return "office-job-" + seq.getAndIncrement() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static ExecutorService buildExecutor(int threads, int queueCapacity, String prefix) {
        int n = Math.max(1, threads);
        ThreadFactory tf = r -> {
            Thread t = new Thread(r);
            t.setName(prefix + t.getId());
            t.setDaemon(true);
            return t;
        };
        if (queueCapacity <= 0) {
            return new ThreadPoolExecutor(n, n, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), tf);
        }
        return new ThreadPoolExecutor(n, n, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(queueCapacity), tf);
    }

    private static void shutdownExecutor(ExecutorService executor) {
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
    }

    private static final class State {
        private final String jobId;
        private final VKOfficeJobType type;
        private final String tag;
        private final long submittedAt;
        private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
        private final CompletableFuture<Void> done = new CompletableFuture<>();
        private volatile VKOfficeJobStatus status = VKOfficeJobStatus.SUBMITTED;
        private volatile long startedAt;
        private volatile long finishedAt;
        private volatile String resultPath;
        private volatile String errorCode;
        private volatile String errorMessage;
        private volatile Map<String, Object> metrics = Map.of();
        private volatile Future<?> future;

        private State(String jobId, VKOfficeJobType type, String tag, long submittedAt) {
            this.jobId = jobId;
            this.type = type == null ? VKOfficeJobType.CUSTOM : type;
            this.tag = tag;
            this.submittedAt = submittedAt;
        }
    }
}
