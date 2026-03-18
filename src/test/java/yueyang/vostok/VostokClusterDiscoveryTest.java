package yueyang.vostok;

import org.junit.jupiter.api.Test;
import yueyang.vostok.cluster.VKClusterNodeStatus;
import yueyang.vostok.cluster.core.VKClusterRuntime;

import static org.junit.jupiter.api.Assertions.*;

public class VostokClusterDiscoveryTest extends ClusterTestBase {

    @Test
    void testSeedDiscoveryAndNodeCache() {
        String cluster = ClusterTestSupport.clusterName();
        String secret = "secret-1";
        int portA = ClusterTestSupport.freePort();
        int portB = ClusterTestSupport.freePort();
        int portC = ClusterTestSupport.freePort();

        VKClusterRuntime a = startRuntime(ClusterTestSupport.config(cluster, secret, "node-a", portA));
        VKClusterRuntime b = startRuntime(ClusterTestSupport.config(cluster, secret, "node-b", portB, "127.0.0.1:" + portA));
        VKClusterRuntime c = startRuntime(ClusterTestSupport.config(cluster, secret, "node-c", portC, "127.0.0.1:" + portA));

        assertTrue(b.awaitReady(3000));
        assertTrue(c.awaitReady(3000));
        ClusterTestSupport.awaitCondition(() -> a.nodes().size() == 3, 4000, "node-a should discover all nodes");
        ClusterTestSupport.awaitCondition(() -> b.nodes().size() == 3, 4000, "node-b should discover all nodes");
        ClusterTestSupport.awaitCondition(() -> c.nodes().size() == 3, 4000, "node-c should discover all nodes");

        assertEquals(VKClusterNodeStatus.ALIVE, a.node("node-b").getStatus());
        assertEquals(VKClusterNodeStatus.ALIVE, a.node("node-c").getStatus());
        assertEquals("node-a", a.self().getNodeId());
        assertEquals(3, a.stats().getTotalNodes());
    }

    @Test
    void testLeftAndDeadStatusTransitions() {
        String cluster = ClusterTestSupport.clusterName();
        String secret = "secret-2";
        int portA = ClusterTestSupport.freePort();
        int portB = ClusterTestSupport.freePort();
        int portC = ClusterTestSupport.freePort();

        VKClusterRuntime a = startRuntime(ClusterTestSupport.config(cluster, secret, "node-a", portA));
        VKClusterRuntime b = startRuntime(ClusterTestSupport.config(cluster, secret, "node-b", portB, "127.0.0.1:" + portA));
        VKClusterRuntime c = startRuntime(ClusterTestSupport.config(cluster, secret, "node-c", portC, "127.0.0.1:" + portA));

        ClusterTestSupport.awaitCondition(() -> a.nodes().size() == 3, 4000, "all nodes should join");

        b.close();
        ClusterTestSupport.awaitCondition(() -> a.node("node-b") != null
                && a.node("node-b").getStatus() == VKClusterNodeStatus.LEFT, 4000,
                "graceful close should become LEFT");

        ClusterTestSupport.hardCrash(c);
        ClusterTestSupport.awaitCondition(() -> a.node("node-c") != null
                && a.node("node-c").getStatus() == VKClusterNodeStatus.SUSPECT, 2000,
                "hard crash should become SUSPECT first");
        ClusterTestSupport.awaitCondition(() -> a.node("node-c") != null
                && a.node("node-c").getStatus() == VKClusterNodeStatus.DEAD, 4000,
                "hard crash should eventually become DEAD");
    }
}
