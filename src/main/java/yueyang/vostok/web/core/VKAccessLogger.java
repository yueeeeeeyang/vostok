package yueyang.vostok.web.core;

import yueyang.vostok.Vostok;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

final class VKAccessLogger {
    private final ArrayBlockingQueue<String> queue;
    private final AtomicLong dropped = new AtomicLong();
    private volatile boolean running;
    private Thread worker;

    VKAccessLogger(int queueSize) {
        this.queue = new ArrayBlockingQueue<>(Math.max(256, queueSize));
    }

    void start() {
        if (running) {
            return;
        }
        running = true;
        worker = new Thread(this::runLoop, "vostok-web-accesslog");
        worker.start();
    }

    void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
            try {
                worker.join(2000);
            } catch (InterruptedException ignore) {
                Thread.currentThread().interrupt();
            }
        }
        flushDropped();
    }

    void offer(String line) {
        if (!running || line == null) {
            return;
        }
        if (!queue.offer(line)) {
            dropped.incrementAndGet();
        }
    }

    private void runLoop() {
        while (running || !queue.isEmpty()) {
            try {
                String line = queue.poll(500, TimeUnit.MILLISECONDS);
                if (line != null) {
                    flushDropped();
                    Vostok.Log.info(line);
                }
            } catch (InterruptedException ignore) {
                Thread.currentThread().interrupt();
            } catch (Throwable ignore) {
            }
        }
    }

    private void flushDropped() {
        long n = dropped.getAndSet(0);
        if (n > 0) {
            Vostok.Log.warn("AccessLog queue full, dropped=" + n);
        }
    }
}
