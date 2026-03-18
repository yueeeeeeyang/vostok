package yueyang.vostok;

import org.junit.jupiter.api.Test;
import yueyang.vostok.cluster.VKClusterConfig;
import yueyang.vostok.cluster.core.VKClusterRuntime;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokClusterBestEffortBroadcastTest extends ClusterTestBase {

    @Test
    void testBestEffortBroadcastReturnsImmediatelyAndRemoteQueueDropsWhenBusy() throws Exception {
        String cluster = ClusterTestSupport.clusterName();
        String secret = "besteffort-secret";
        int portA = ClusterTestSupport.freePort();
        int portB = ClusterTestSupport.freePort();

        VKClusterConfig busyConfig = ClusterTestSupport.config(cluster, secret, "node-b", portB, "127.0.0.1:" + portA)
                .workerThreads(1)
                .listenerQueueCapacity(1);

        VKClusterRuntime a = startRuntime(ClusterTestSupport.config(cluster, secret, "node-a", portA));
        VKClusterRuntime b = startRuntime(busyConfig);

        ClusterTestSupport.awaitCondition(() -> a.nodes().size() == 2, 4000, "nodes should discover each other");

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        b.on("busy", message -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
        });

        a.broadcastBestEffort("busy", "m1".getBytes(StandardCharsets.UTF_8)).get(2, TimeUnit.SECONDS);
        assertTrue(started.await(2, TimeUnit.SECONDS));
        a.broadcastBestEffort("busy", "m2".getBytes(StandardCharsets.UTF_8)).get(2, TimeUnit.SECONDS);
        ClusterTestSupport.awaitCondition(() -> b.stats().getReceivedMessages() >= 2,
                3000,
                "remote node should receive the first two busy messages");

        var result = a.broadcastBestEffort("busy", "m3".getBytes(StandardCharsets.UTF_8)).get(2, TimeUnit.SECONDS);
        ClusterTestSupport.awaitCondition(() -> b.stats().getQueueDrops() > 0,
                3000,
                "busy remote node should drop best-effort messages when listener queue is full");
        release.countDown();

        assertEquals(1, result.getTargetedNodes());
        assertEquals(0, result.getAckedNodes());
        assertEquals(0, result.getFailedNodes());
    }
}
