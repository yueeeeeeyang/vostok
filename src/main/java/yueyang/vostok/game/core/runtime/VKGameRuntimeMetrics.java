package yueyang.vostok.game.core.runtime;

import yueyang.vostok.game.VKGameMetrics;
import yueyang.vostok.game.core.command.VKGameCommandGovernor;
import yueyang.vostok.game.core.lifecycle.VKGameLifecycleReason;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Game Runtime 运行指标聚合。
 */
public final class VKGameRuntimeMetrics {
    private final AtomicLong tickCount = new AtomicLong();
    private final AtomicLong tickTimeouts = new AtomicLong();
    private final AtomicLong tickTimeoutRoomSkips = new AtomicLong();
    private final AtomicLong maxTickCostMs = new AtomicLong();

    private final AtomicLong roomCreated = new AtomicLong();
    private final AtomicLong roomClosed = new AtomicLong();
    private final AtomicLong roomDraining = new AtomicLong();
    private final AtomicLong roomDrainRequests = new AtomicLong();
    private final AtomicLong roomClosedByIdle = new AtomicLong();
    private final AtomicLong roomClosedByEmpty = new AtomicLong();
    private final AtomicLong roomClosedByLifetime = new AtomicLong();
    private final AtomicLong roomClosedByDrainTimeout = new AtomicLong();
    private final AtomicLong roomClosedByLogicError = new AtomicLong();

    private final AtomicLong playerJoined = new AtomicLong();
    private final AtomicLong playerLeft = new AtomicLong();
    private final AtomicLong playerDisconnected = new AtomicLong();
    private final AtomicLong playerReconnected = new AtomicLong();
    private final AtomicLong playerSessionExpired = new AtomicLong();

    private final AtomicLong commandsReceived = new AtomicLong();
    private final AtomicLong commandsProcessed = new AtomicLong();
    private final AtomicLong commandsDropped = new AtomicLong();
    private final AtomicLong commandsRejected = new AtomicLong();
    private final AtomicLong commandsRejectedRateLimit = new AtomicLong();
    private final AtomicLong commandsRejectedSeq = new AtomicLong();
    private final AtomicLong commandsRejectedTimeSkew = new AtomicLong();
    private final AtomicLong commandsRejectedPlayerMissing = new AtomicLong();
    private final AtomicLong commandsRejectedPlayerOffline = new AtomicLong();

    private final AtomicLong matchEnqueued = new AtomicLong();
    private final AtomicLong matchCancelled = new AtomicLong();
    private final AtomicLong matchSucceeded = new AtomicLong();
    private final AtomicLong matchNotified = new AtomicLong();
    private final AtomicLong matchAcked = new AtomicLong();
    private final AtomicLong matchResultExpired = new AtomicLong();
    private final AtomicLong messagesPublished = new AtomicLong();
    private final AtomicLong messagesPushed = new AtomicLong();
    private final AtomicLong messagesAcked = new AtomicLong();
    private final AtomicLong messagesExpired = new AtomicLong();

    private final AtomicLong shardImbalanceEvents = new AtomicLong();
    private final AtomicLong shardMigrations = new AtomicLong();

    public void onTickDone(long tickCostMs) {
        tickCount.incrementAndGet();
        updateMax(maxTickCostMs, tickCostMs);
    }

    public void onTickTimeout(long skippedRooms) {
        tickTimeouts.incrementAndGet();
        tickTimeoutRoomSkips.addAndGet(Math.max(0L, skippedRooms));
    }

    public void onRoomCreated() {
        roomCreated.incrementAndGet();
    }

    public void onRoomDraining() {
        roomDraining.incrementAndGet();
        roomDrainRequests.incrementAndGet();
    }

    public void onRoomClosed(String reasonCode, boolean wasDraining) {
        roomClosed.incrementAndGet();
        if (wasDraining) {
            roomDraining.decrementAndGet();
        }

        switch (VKGameLifecycleReason.fromCode(reasonCode)) {
            case IDLE_TIMEOUT -> roomClosedByIdle.incrementAndGet();
            case EMPTY_TIMEOUT -> roomClosedByEmpty.incrementAndGet();
            case MAX_LIFETIME -> roomClosedByLifetime.incrementAndGet();
            case DRAIN_TIMEOUT -> roomClosedByDrainTimeout.incrementAndGet();
            case LOGIC_ERROR -> roomClosedByLogicError.incrementAndGet();
            default -> {
                // manual / custom reason
            }
        }
    }

    public void onPlayerJoined() {
        playerJoined.incrementAndGet();
    }

    public void onPlayerLeft() {
        playerLeft.incrementAndGet();
    }

    public void onPlayerDisconnected() {
        playerDisconnected.incrementAndGet();
    }

    public void onPlayerReconnected() {
        playerReconnected.incrementAndGet();
    }

    public void onPlayerSessionExpired() {
        playerSessionExpired.incrementAndGet();
    }

    public void onCommandReceived() {
        commandsReceived.incrementAndGet();
    }

    public void onCommandProcessed() {
        commandsProcessed.incrementAndGet();
    }

    public void onCommandDropped() {
        commandsDropped.incrementAndGet();
    }

    public void onCommandRejected(VKGameCommandGovernor.RejectReason reason) {
        commandsRejected.incrementAndGet();
        switch (reason) {
            case RATE_LIMIT -> commandsRejectedRateLimit.incrementAndGet();
            case NON_MONOTONIC_SEQ -> commandsRejectedSeq.incrementAndGet();
            case TIME_SKEW -> commandsRejectedTimeSkew.incrementAndGet();
            case PLAYER_NOT_FOUND -> commandsRejectedPlayerMissing.incrementAndGet();
            case PLAYER_OFFLINE -> commandsRejectedPlayerOffline.incrementAndGet();
            default -> {
                // no-op
            }
        }
    }

    public void onMatchEnqueued() {
        matchEnqueued.incrementAndGet();
    }

    public void onMatchCancelled() {
        matchCancelled.incrementAndGet();
    }

    public void onMatchSucceeded() {
        matchSucceeded.incrementAndGet();
    }

    public void onMatchNotified() {
        matchNotified.incrementAndGet();
    }

    public void onMatchAcked() {
        matchAcked.incrementAndGet();
    }

    public void onMatchResultExpired() {
        matchResultExpired.incrementAndGet();
    }

    public void onMessagePublished() {
        messagesPublished.incrementAndGet();
    }

    public void onMessagePushed() {
        messagesPushed.incrementAndGet();
    }

    public void onMessageAcked() {
        messagesAcked.incrementAndGet();
    }

    public void onMessageExpired() {
        messagesExpired.incrementAndGet();
    }

    public void onRoomClosedByLogicError() {
        roomClosedByLogicError.incrementAndGet();
    }

    public void onShardImbalance() {
        shardImbalanceEvents.incrementAndGet();
    }

    public void onShardMigration() {
        shardMigrations.incrementAndGet();
    }

    public VKGameMetrics snapshot(boolean started, int roomCount, int logicCount, int pendingMatchRequests) {
        return new VKGameMetrics(
                started,
                roomCount,
                logicCount,
                pendingMatchRequests,
                tickCount.get(),
                tickTimeouts.get(),
                tickTimeoutRoomSkips.get(),
                maxTickCostMs.get(),
                roomCreated.get(),
                roomClosed.get(),
                roomDraining.get(),
                roomDrainRequests.get(),
                roomClosedByIdle.get(),
                roomClosedByEmpty.get(),
                roomClosedByLifetime.get(),
                roomClosedByDrainTimeout.get(),
                playerJoined.get(),
                playerLeft.get(),
                playerDisconnected.get(),
                playerReconnected.get(),
                playerSessionExpired.get(),
                commandsReceived.get(),
                commandsProcessed.get(),
                commandsDropped.get(),
                commandsRejected.get(),
                commandsRejectedRateLimit.get(),
                commandsRejectedSeq.get(),
                commandsRejectedTimeSkew.get(),
                commandsRejectedPlayerMissing.get(),
                commandsRejectedPlayerOffline.get(),
                matchEnqueued.get(),
                matchCancelled.get(),
                matchSucceeded.get(),
                matchNotified.get(),
                matchAcked.get(),
                matchResultExpired.get(),
                messagesPublished.get(),
                messagesPushed.get(),
                messagesAcked.get(),
                messagesExpired.get(),
                shardImbalanceEvents.get(),
                shardMigrations.get(),
                roomClosedByLogicError.get()
        );
    }

    public void reset() {
        tickCount.set(0L);
        tickTimeouts.set(0L);
        tickTimeoutRoomSkips.set(0L);
        maxTickCostMs.set(0L);

        roomCreated.set(0L);
        roomClosed.set(0L);
        roomDraining.set(0L);
        roomDrainRequests.set(0L);
        roomClosedByIdle.set(0L);
        roomClosedByEmpty.set(0L);
        roomClosedByLifetime.set(0L);
        roomClosedByDrainTimeout.set(0L);
        roomClosedByLogicError.set(0L);

        playerJoined.set(0L);
        playerLeft.set(0L);
        playerDisconnected.set(0L);
        playerReconnected.set(0L);
        playerSessionExpired.set(0L);

        commandsReceived.set(0L);
        commandsProcessed.set(0L);
        commandsDropped.set(0L);
        commandsRejected.set(0L);
        commandsRejectedRateLimit.set(0L);
        commandsRejectedSeq.set(0L);
        commandsRejectedTimeSkew.set(0L);
        commandsRejectedPlayerMissing.set(0L);
        commandsRejectedPlayerOffline.set(0L);

        matchEnqueued.set(0L);
        matchCancelled.set(0L);
        matchSucceeded.set(0L);
        matchNotified.set(0L);
        matchAcked.set(0L);
        matchResultExpired.set(0L);
        messagesPublished.set(0L);
        messagesPushed.set(0L);
        messagesAcked.set(0L);
        messagesExpired.set(0L);

        shardImbalanceEvents.set(0L);
        shardMigrations.set(0L);
    }

    private static void updateMax(AtomicLong target, long candidate) {
        long current;
        do {
            current = target.get();
            if (candidate <= current) {
                return;
            }
        } while (!target.compareAndSet(current, candidate));
    }
}
