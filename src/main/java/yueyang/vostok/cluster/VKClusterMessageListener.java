package yueyang.vostok.cluster;

/** 集群消息监听器。 */
@FunctionalInterface
public interface VKClusterMessageListener {
    void onMessage(VKClusterMessage message) throws Exception;
}
