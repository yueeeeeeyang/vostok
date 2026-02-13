package yueyang.vostok.web.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class VKWorkerPool {
    private final ThreadPoolExecutor executor;

    VKWorkerPool(int threads, int queueSize) {
        this.executor = new ThreadPoolExecutor(
                threads,
                threads,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueSize),
                new NamedThreadFactory("vostok-web-worker")
        );
    }

    boolean submit(Runnable task) {
        try {
            executor.execute(task);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    void shutdown() {
        executor.shutdownNow();
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger idx = new AtomicInteger(1);

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + idx.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
