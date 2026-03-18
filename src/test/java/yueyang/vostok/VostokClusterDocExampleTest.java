package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.cluster.VKClusterConfig;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokClusterDocExampleTest {

    @AfterEach
    void tearDown() {
        Vostok.Cluster.close();
    }

    @Test
    void testReadmeStyleExampleRuns() throws Exception {
        String cluster = ClusterTestSupport.clusterName();
        int port = ClusterTestSupport.freePort();

        Vostok.Cluster.init(new VKClusterConfig()
                .clusterName(cluster)
                .clusterSecret("doc-secret")
                .nodeId("doc-node")
                .bindHost("127.0.0.1")
                .bindPort(port)
                .advertiseHost("127.0.0.1")
                .advertisePort(port));

        CountDownLatch local = new CountDownLatch(1);
        AtomicReference<String> text = new AtomicReference<>();
        Vostok.Cluster.on("demo", message -> {
            text.set(new String(message.getPayload(), StandardCharsets.UTF_8));
            local.countDown();
        });

        var result = Vostok.Cluster.broadcastBestEffort("demo", "hello-cluster".getBytes(StandardCharsets.UTF_8))
                .get(3, TimeUnit.SECONDS);

        assertTrue(local.await(2, TimeUnit.SECONDS));
        assertTrue(result.isLocalDelivered());
        assertEquals(0, result.getTargetedNodes());
        assertEquals("hello-cluster", text.get());
        assertEquals("doc-node", Vostok.Cluster.self().getNodeId());
    }
}
