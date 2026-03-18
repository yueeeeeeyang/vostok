package yueyang.vostok.cluster;

/** Cluster 订阅句柄，可用于主动取消 topic 订阅。 */
public final class VKClusterSubscription {
    private final long id;
    private final String topic;
    private final Runnable cancelHook;

    public VKClusterSubscription(long id, String topic, Runnable cancelHook) {
        this.id = id;
        this.topic = topic;
        this.cancelHook = cancelHook;
    }

    public void cancel() {
        if (cancelHook != null) {
            cancelHook.run();
        }
    }

    public long getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }
}
