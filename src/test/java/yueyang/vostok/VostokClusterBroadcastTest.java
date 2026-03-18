package yueyang.vostok;

import org.junit.jupiter.api.Test;
import yueyang.vostok.cluster.VKClusterBroadcastResult;
import yueyang.vostok.cluster.core.VKClusterRuntime;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokClusterBroadcastTest extends ClusterTestBase {

    @Test
    void testReliableBroadcastReachesLocalAndRemoteListeners() throws Exception {
        String cluster = ClusterTestSupport.clusterName();
        String secret = "broadcast-secret";
        int portA = ClusterTestSupport.freePort();
        int portB = ClusterTestSupport.freePort();

        VKClusterRuntime a = startRuntime(ClusterTestSupport.config(cluster, secret, "node-a", portA));
        VKClusterRuntime b = startRuntime(ClusterTestSupport.config(cluster, secret, "node-b", portB, "127.0.0.1:" + portA));

        ClusterTestSupport.awaitCondition(() -> a.nodes().size() == 2, 4000, "nodes should discover each other");

        CountDownLatch local = new CountDownLatch(1);
        CountDownLatch remote = new CountDownLatch(1);
        AtomicReference<String> localText = new AtomicReference<>();
        AtomicReference<String> remoteText = new AtomicReference<>();

        a.on("orders", message -> {
            localText.set(new String(message.getPayload(), StandardCharsets.UTF_8));
            local.countDown();
        });
        b.on("orders", message -> {
            remoteText.set(new String(message.getPayload(), StandardCharsets.UTF_8));
            remote.countDown();
        });

        VKClusterBroadcastResult result = a.broadcast("orders", "hello-cluster".getBytes(StandardCharsets.UTF_8))
                .get(5, TimeUnit.SECONDS);

        assertTrue(local.await(3, TimeUnit.SECONDS));
        assertTrue(remote.await(3, TimeUnit.SECONDS));
        assertEquals("hello-cluster", localText.get());
        assertEquals("hello-cluster", remoteText.get());
        assertEquals(1, result.getTargetedNodes());
        assertEquals(1, result.getAckedNodes());
        assertEquals(0, result.getFailedNodes());
        assertTrue(result.isLocalDelivered());
    }

    @Test
    void testOffAndOffAllPreventFurtherDelivery() throws Exception {
        String cluster = ClusterTestSupport.clusterName();
        String secret = "broadcast-secret-2";
        int portA = ClusterTestSupport.freePort();

        VKClusterRuntime a = startRuntime(ClusterTestSupport.config(cluster, secret, "node-a", portA));
        AtomicInteger count = new AtomicInteger();

        var sub = a.on("only", message -> count.incrementAndGet());
        sub.cancel();
        a.broadcastBestEffort("only", "x".getBytes(StandardCharsets.UTF_8)).get(2, TimeUnit.SECONDS);
        Thread.sleep(120L);
        assertEquals(0, count.get());

        a.on("clear", message -> count.incrementAndGet());
        a.on("clear", message -> count.incrementAndGet());
        a.offAll("clear");
        a.broadcastBestEffort("clear", "y".getBytes(StandardCharsets.UTF_8)).get(2, TimeUnit.SECONDS);
        Thread.sleep(120L);
        assertEquals(0, count.get());
    }
}

