package yueyang.vostok.cluster.core;

import yueyang.vostok.cluster.VKClusterStats;

import java.util.concurrent.atomic.AtomicLong;

/** Cluster 运行时原子统计。 */
final class VKClusterStatsCollector {
    private final AtomicLong sentFrames = new AtomicLong();
    private final AtomicLong receivedFrames = new AtomicLong();
    private final AtomicLong sentMessages = new AtomicLong();
    private final AtomicLong receivedMessages = new AtomicLong();
    private final AtomicLong reliableRetries = new AtomicLong();
    private final AtomicLong ackSent = new AtomicLong();
    private final AtomicLong ackReceived = new AtomicLong();
    private final AtomicLong authFailures = new AtomicLong();
    private final AtomicLong protocolErrors = new AtomicLong();
    private final AtomicLong queueDrops = new AtomicLong();
    private final AtomicLong bytesSent = new AtomicLong();
    private final AtomicLong bytesReceived = new AtomicLong();

    void onFrameSent(int bytes) {
        sentFrames.incrementAndGet();
        bytesSent.addAndGet(Math.max(0, bytes));
    }

    void onFrameReceived(int bytes) {
        receivedFrames.incrementAndGet();
        bytesReceived.addAndGet(Math.max(0, bytes));
    }

    void onMessageSent() {
        sentMessages.incrementAndGet();
    }

    void onMessageReceived() {
        receivedMessages.incrementAndGet();
    }

    void onReliableRetry() {
        reliableRetries.incrementAndGet();
    }

    void onAckSent() {
        ackSent.incrementAndGet();
    }

    void onAckReceived() {
        ackReceived.incrementAndGet();
    }

    void onAuthFailure() {
        authFailures.incrementAndGet();
    }

    void onProtocolError() {
        protocolErrors.incrementAndGet();
    }

    void onQueueDrop() {
        queueDrops.incrementAndGet();
    }

    VKClusterStats snapshot(int totalNodes, int aliveNodes, int openConnections) {
        return new VKClusterStats(
                totalNodes,
                aliveNodes,
                openConnections,
                sentFrames.get(),
                receivedFrames.get(),
                sentMessages.get(),
                receivedMessages.get(),
                reliableRetries.get(),
                ackSent.get(),
                ackReceived.get(),
                authFailures.get(),
                protocolErrors.get(),
                queueDrops.get(),
                bytesSent.get(),
                bytesReceived.get()
        );
    }
}
