package yueyang.vostok;

import org.junit.jupiter.api.Test;
import yueyang.vostok.cluster.core.VKClusterRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokClusterAuthTest extends ClusterTestBase {

    @Test
    void testRejectsWrongSecret() {
        String cluster = ClusterTestSupport.clusterName();
        int portA = ClusterTestSupport.freePort();
        int portB = ClusterTestSupport.freePort();

        VKClusterRuntime a = startRuntime(ClusterTestSupport.config(cluster, "secret-a", "node-a", portA));
        VKClusterRuntime b = startRuntime(ClusterTestSupport.config(cluster, "secret-b", "node-b", portB, "127.0.0.1:" + portA));

        assertFalse(b.awaitReady(600));
        ClusterTestSupport.awaitCondition(() -> a.stats().getAuthFailures() > 0 || b.stats().getAuthFailures() > 0,
                3000, "auth failure should be recorded");
        assertEquals(1, a.nodes().size());
    }

    @Test
    void testRejectsWrongClusterNameAndInvalidFrames() {
        String secret = "shared-secret";
        int portA = ClusterTestSupport.freePort();
        int portB = ClusterTestSupport.freePort();

        VKClusterRuntime a = startRuntime(ClusterTestSupport.config("cluster-a", secret, "node-a", portA));
        VKClusterRuntime b = startRuntime(ClusterTestSupport.config("cluster-b", secret, "node-b", portB, "127.0.0.1:" + portA));

        assertFalse(b.awaitReady(600));
        ClusterTestSupport.awaitCondition(() -> a.stats().getAuthFailures() > 0 || b.stats().getAuthFailures() > 0,
                3000, "cluster name mismatch should trigger auth failure");

        ClusterTestSupport.sendProtocolVersionMismatchFrame(portA);
        ClusterTestSupport.sendGarbageFrame(portA);
        ClusterTestSupport.awaitCondition(() -> a.stats().getProtocolErrors() > 0,
                3000, "invalid frames should increment protocol errors");
    }
}

