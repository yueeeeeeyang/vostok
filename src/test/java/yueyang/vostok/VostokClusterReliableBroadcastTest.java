package yueyang.vostok;

import org.junit.jupiter.api.Test;
import yueyang.vostok.cluster.VKClusterConfig;
import yueyang.vostok.cluster.VKClusterNodeStatus;
import yueyang.vostok.cluster.core.VKClusterRuntime;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokClusterReliableBroadcastTest extends ClusterTestBase {

    @Test
    void testReliableBroadcastRetriesAndKeepsFrozenTargets() throws Exception {
        String cluster = ClusterTestSupport.clusterName();
        String secret = "reliable-secret";
        int portA = ClusterTestSupport.freePort();
        int portB = ClusterTestSupport.freePort();
        int portC = ClusterTestSupport.freePort();

        VKClusterConfig busyConfig = ClusterTestSupport.config(cluster, secret, "node-b", portB, "127.0.0.1:" + portA)
                .workerThreads(1)
                .listenerQueueCapacity(1)
                .reliableAckTimeoutMs(100)
                .reliableRetryBaseMs(100)
                .reliableMaxRetries(2);

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

        var future = a.broadcastReliable("busy", "m3".getBytes(StandardCharsets.UTF_8));
        VKClusterRuntime c = startRuntime(ClusterTestSupport.config(cluster, secret, "node-c", portC, "127.0.0.1:" + portA));
        assertTrue(c.awaitReady(3000));

        var result = future.get(5, TimeUnit.SECONDS);
        release.countDown();

        ClusterTestSupport.awaitCondition(() -> a.node("node-c") != null
                && a.node("node-c").getStatus() == VKClusterNodeStatus.ALIVE,
                4000,
                "node-c should join after the reliable broadcast has started");

        assertEquals(1, result.getTargetedNodes());
        assertEquals(0, result.getAckedNodes());
        assertEquals(1, result.getFailedNodes());
        assertTrue(a.stats().getReliableRetries() > 0);
    }
}
