package yueyang.vostok;

import org.junit.jupiter.api.Test;
import yueyang.vostok.cluster.core.VKClusterRuntime;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokClusterLoadTest extends ClusterTestBase {

    @Test
    void testSmallPacketBroadcastLoadDoesNotDeadlock() throws Exception {
        String cluster = ClusterTestSupport.clusterName();
        String secret = "load-secret";
        int portA = ClusterTestSupport.freePort();
        int portB = ClusterTestSupport.freePort();
        int portC = ClusterTestSupport.freePort();
        int portD = ClusterTestSupport.freePort();

        VKClusterRuntime a = startRuntime(ClusterTestSupport.config(cluster, secret, "node-a", portA));
        VKClusterRuntime b = startRuntime(ClusterTestSupport.config(cluster, secret, "node-b", portB, "127.0.0.1:" + portA));
        VKClusterRuntime c = startRuntime(ClusterTestSupport.config(cluster, secret, "node-c", portC, "127.0.0.1:" + portA));
        VKClusterRuntime d = startRuntime(ClusterTestSupport.config(cluster, secret, "node-d", portD, "127.0.0.1:" + portA));

        ClusterTestSupport.awaitCondition(() -> a.nodes().size() == 4, 5000, "all nodes should discover each other");
        ClusterTestSupport.awaitCondition(() -> b.nodes().size() == 4, 5000, "all nodes should discover each other");
        ClusterTestSupport.awaitCondition(() -> c.nodes().size() == 4, 5000, "all nodes should discover each other");
        ClusterTestSupport.awaitCondition(() -> d.nodes().size() == 4, 5000, "all nodes should discover each other");

        AtomicInteger countA = new AtomicInteger();
        AtomicInteger countB = new AtomicInteger();
        AtomicInteger countC = new AtomicInteger();
        AtomicInteger countD = new AtomicInteger();
        int rounds = 5;
        int totalMessages = rounds * 4;
        CountDownLatch latch = new CountDownLatch(totalMessages * 4);

        a.on("bulk", m -> { countA.incrementAndGet(); latch.countDown(); });
        b.on("bulk", m -> { countB.incrementAndGet(); latch.countDown(); });
        c.on("bulk", m -> { countC.incrementAndGet(); latch.countDown(); });
        d.on("bulk", m -> { countD.incrementAndGet(); latch.countDown(); });

        for (int i = 0; i < rounds; i++) {
            a.broadcastBestEffort("bulk", ("a-" + i).getBytes(StandardCharsets.UTF_8)).get(2, TimeUnit.SECONDS);
            b.broadcastBestEffort("bulk", ("b-" + i).getBytes(StandardCharsets.UTF_8)).get(2, TimeUnit.SECONDS);
            c.broadcastBestEffort("bulk", ("c-" + i).getBytes(StandardCharsets.UTF_8)).get(2, TimeUnit.SECONDS);
            d.broadcastBestEffort("bulk", ("d-" + i).getBytes(StandardCharsets.UTF_8)).get(2, TimeUnit.SECONDS);
        }

        assertTrue(latch.await(8, TimeUnit.SECONDS));
        assertEquals(totalMessages, countA.get());
        assertEquals(totalMessages, countB.get());
        assertEquals(totalMessages, countC.get());
        assertEquals(totalMessages, countD.get());
    }
}

