package yueyang.vostok;

import org.junit.jupiter.api.Test;
import yueyang.vostok.data.VKDataConfig;
import yueyang.vostok.data.dialect.VKDialectType;

import java.sql.DriverManager;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class H2ConcurrencyPressureDetailTest {
    @Test
    void testConcurrencyPressureOnH2() throws Exception {
        String url = "jdbc:h2:mem:h2bench;MODE=MySQL;DB_CLOSE_DELAY=-1";

        try (var conn = DriverManager.getConnection(url, "sa", "");
             var stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_user");
            stmt.execute("CREATE TABLE t_user (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_name VARCHAR(64) NOT NULL, age INT)");
        }

        Vostok.Data.close();
        VKDataConfig cfg = new VKDataConfig()
                .url(url)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .minIdle(2)
                .maxActive(20)
                .maxWaitMs(30000)
                .validationQuery("SELECT 1");
        Vostok.Data.init(cfg, "yueyang.vostok");

        int rounds = Integer.getInteger("vostok.test.h2.rounds", 1);
        int threads = Integer.getInteger("vostok.test.h2.threads", 10);
        int loops = Integer.getInteger("vostok.test.h2.loops", 50);
        try {
            for (int r = 0; r < rounds; r++) {
                final int round = r;
                AtomicInteger errors = new AtomicInteger();
                AtomicReference<Throwable> firstError = new AtomicReference<>();
                var pool = Executors.newFixedThreadPool(threads);
                var latch = new CountDownLatch(threads);
                for (int i = 0; i < threads; i++) {
                    int idx = i;
                    pool.submit(() -> {
                        try {
                            for (int j = 0; j < loops; j++) {
                                UserEntity u = new UserEntity();
                                u.setName("U-" + round + "-" + idx + "-" + j);
                                u.setAge(j);
                                Vostok.Data.insert(u);
                                Vostok.Data.findAll(UserEntity.class);
                            }
                        } catch (Throwable t) {
                            errors.incrementAndGet();
                            firstError.compareAndSet(null, t);
                            t.printStackTrace();
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                latch.await();
                pool.shutdown();
                assertEquals(0, errors.get(), firstError.get() == null ? "" : stackOf(firstError.get()));
            }
        } finally {
            Vostok.Data.close();
        }
    }

    private static String stackOf(Throwable t) {
        if (t == null) {
            return "";
        }
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }
}
