package yueyang.vostok;

import org.junit.jupiter.api.Test;
import yueyang.vostok.cluster.VKClusterNodeStatus;
import yueyang.vostok.cluster.core.VKClusterRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokClusterReconnectTest extends ClusterTestBase {

    @Test
    void testRestartWithSameNodeIdReplacesOldIncarnation() {
        String cluster = ClusterTestSupport.clusterName();
        String secret = "reconnect-secret";
        int portA = ClusterTestSupport.freePort();
        int portB = ClusterTestSupport.freePort();
        int portB2 = ClusterTestSupport.freePort();

        VKClusterRuntime a = startRuntime(ClusterTestSupport.config(cluster, secret, "node-a", portA));
        VKClusterRuntime b = startRuntime(ClusterTestSupport.config(cluster, secret, "node-b", portB, "127.0.0.1:" + portA));

        ClusterTestSupport.awaitCondition(() -> a.nodes().size() == 2, 4000, "node-b should join node-a");
        long oldIncarnation = a.node("node-b").getIncarnation();

        ClusterTestSupport.hardCrash(b);
        ClusterTestSupport.awaitCondition(() -> a.node("node-b") != null
                && a.node("node-b").getStatus() == VKClusterNodeStatus.DEAD,
                4000,
                "old node-b should become DEAD after hard crash");

        VKClusterRuntime b2 = startRuntime(ClusterTestSupport.config(cluster, secret, "node-b", portB2, "127.0.0.1:" + portA));
        assertTrue(b2.awaitReady(3000));

        ClusterTestSupport.awaitCondition(() -> a.node("node-b") != null
                && a.node("node-b").getStatus() == VKClusterNodeStatus.ALIVE
                && a.node("node-b").getPort() == portB2
                && a.node("node-b").getIncarnation() > oldIncarnation,
                5000,
                "new incarnation should replace the old node-b snapshot");

        assertEquals(1, a.stats().getOpenConnections());
    }
}

