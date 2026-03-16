package yueyang.vostok.web.core;

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
                // 关闭阶段要继续把队列里的尾部访问日志排空，避免“请求刚结束就停机”时丢日志。
                String line = running ? queue.poll(500, TimeUnit.MILLISECONDS) : queue.poll();
                if (line != null) {
                    flushDropped();
                    VKWebLogSupport.accessInfo(line);
                }
            } catch (InterruptedException ignore) {
                // stop() 会主动 interrupt 唤醒 poll；此处不能重新设置中断标记，
                // 否则后续 poll 会立即再次抛出 InterruptedException，导致剩余队列无法排空。
            } catch (Throwable ignore) {
            }
        }
    }

    private void flushDropped() {
        long n = dropped.getAndSet(0);
        if (n > 0) {
            VKWebLogSupport.accessWarn("AccessLog queue full, dropped=" + n);
        }
    }
}
