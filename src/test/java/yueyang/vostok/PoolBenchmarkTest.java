package yueyang.vostok;

import org.junit.jupiter.api.Test;

import yueyang.vostok.data.VKDataConfig;
import yueyang.vostok.data.dialect.VKDialectType;
import yueyang.vostok.data.pool.VKDataSource;

import java.sql.Connection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class PoolBenchmarkTest {
    @Test
    void benchmarkBorrowReturn() throws Exception {
        assumeTrue(Boolean.getBoolean("vostok.bench"), "Skip benchmark by default");

        int threads = Integer.getInteger("bench.threads", 32);
        int loops = Integer.getInteger("bench.loops", 2000);
        int warmup = Integer.getInteger("bench.warmup", 200);

        VKDataConfig cfg = new VKDataConfig()
                .url("jdbc:h2:mem:bench_pool;MODE=MySQL;DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .minIdle(2)
                .maxActive(Math.max(8, threads))
                .maxWaitMs(30000)
                .validationQuery("SELECT 1");

        VKDataSource ds = new VKDataSource(cfg);

        // warmup
        for (int i = 0; i < warmup; i++) {
            try (Connection c = ds.getConnection()) {
                // no-op
            }
        }

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        long start = System.nanoTime();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    for (int j = 0; j < loops; j++) {
                        try (Connection c = ds.getConnection()) {
                            // no-op
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        long end = System.nanoTime();
        pool.shutdownNow();

        long totalOps = (long) threads * loops;
        double seconds = (end - start) / 1_000_000_000.0;
        double opsPerSec = totalOps / seconds;

        System.out.println("[PoolBenchmark] threads=" + threads + " loops=" + loops
                + " ops=" + totalOps + " timeSec=" + String.format("%.3f", seconds)
                + " ops/s=" + String.format("%.2f", opsPerSec));
    }
}
