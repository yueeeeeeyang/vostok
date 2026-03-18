package yueyang.vostok.cluster;

import yueyang.vostok.cluster.core.VKClusterRuntime;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Cluster 模块静态门面。 */
public class VostokCluster {
    private static final VKClusterRuntime RUNTIME = new VKClusterRuntime();

    protected VostokCluster() {
    }

    public static void init() {
        RUNTIME.init(new VKClusterConfig());
    }

    public static void init(VKClusterConfig config) {
        RUNTIME.init(config);
    }

    public static void reinit(VKClusterConfig config) {
        RUNTIME.reinit(config);
    }

    public static boolean started() {
        return RUNTIME.started();
    }

    public static VKClusterConfig config() {
        return RUNTIME.config();
    }

    public static void close() {
        RUNTIME.close();
    }

    public static boolean awaitReady(long timeoutMs) {
        return RUNTIME.awaitReady(timeoutMs);
    }

    public static VKClusterNode self() {
        return RUNTIME.self();
    }

    public static List<VKClusterNode> nodes() {
        return RUNTIME.nodes();
    }

    public static VKClusterNode node(String nodeId) {
        return RUNTIME.node(nodeId);
    }

    public static VKClusterStats stats() {
        return RUNTIME.stats();
    }

    public static VKClusterSubscription on(String topic, VKClusterMessageListener listener) {
        return RUNTIME.on(topic, listener);
    }

    public static void off(VKClusterSubscription subscription) {
        RUNTIME.off(subscription);
    }

    public static void offAll(String topic) {
        RUNTIME.offAll(topic);
    }

    public static CompletableFuture<VKClusterBroadcastResult> broadcast(String topic, byte[] payload) {
        return RUNTIME.broadcast(topic, payload);
    }

    public static CompletableFuture<VKClusterBroadcastResult> broadcastReliable(String topic, byte[] payload) {
        return RUNTIME.broadcastReliable(topic, payload);
    }

    public static CompletableFuture<VKClusterBroadcastResult> broadcastBestEffort(String topic, byte[] payload) {
        return RUNTIME.broadcastBestEffort(topic, payload);
    }
}
