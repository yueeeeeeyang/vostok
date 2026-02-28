package yueyang.vostok.game;

public class VKGameConfig {
    private boolean enabled = true;
    private boolean autoStartTicker = true;
    private int tickRate = 20;
    private int tickWorkerThreads = Math.max(1, Runtime.getRuntime().availableProcessors());
    private long tickTimeoutMs = 50L;

    private int maxRooms = 10_000;
    private int maxPlayersPerRoom = 128;
    private int maxCommandsPerTick = 1024;
    private int roomCommandQueueCapacity = 4096;
    private int highPriorityWeight = 4;
    private int normalPriorityWeight = 2;
    private int lowPriorityWeight = 1;

    private long roomIdleTimeoutMs = 30L * 60L * 1000L;
    private long roomEmptyTimeoutMs = 10L * 60L * 1000L;
    private long roomMaxLifetimeMs = 0L;
    private long roomDrainTimeoutMs = 15_000L;
    private boolean removeEmptyRoomOnLastLeave = false;

    private boolean sessionHostingEnabled = true;
    private long reconnectGraceMs = 60_000L;

    private boolean antiCheatEnabled = true;
    private int maxCommandsPerSecondPerPlayer = 30;
    private boolean enforceMonotonicClientSeq = true;
    private long maxClientTimestampSkewMs = 30_000L;
    private boolean requireOnlinePlayerCommand = false;

    private boolean matchmakingEnabled = false;
    private int matchmakingRoomSize = 2;
    private long matchmakingMaxWaitMs = 15_000L;
    private int matchmakingBaseRatingTolerance = 100;
    private int matchmakingRatingExpandPerSecond = 50;
    private String matchmakingRoomIdPrefix = "match-";
    private long matchResultTtlMs = 2 * 60_000L;
    private long matchNotifyRetryIntervalMs = 1000L;
    private long matchNotifyMaxRetryIntervalMs = 30_000L;
    private int matchNotifyMaxAttempts = 30;
    private boolean matchJoinRequiresToken = true;
    private long matchJoinTokenTtlMs = 30_000L;

    private boolean roomPersistenceEnabled = false;
    private String roomPersistenceDir = "./data/vostok-game";
    private int roomSnapshotEveryTicks = 20;
    private boolean roomWalEnabled = true;
    private boolean roomRecoveryEnabled = true;

    private int messageStreamCapacity = 500;
    private long messageDefaultTtlMs = 5 * 60_000L;
    private long messageRetryIntervalMs = 1000L;
    private long messageRetryMaxIntervalMs = 30_000L;
    private int messageRetryMaxAttempts = 30;

    private int hotRoomCommandThreshold = 256;
    private int hotRoomQueuedCommandThreshold = 512;
    private long hotRoomCostThresholdMs = 10L;
    private double shardImbalanceThreshold = 1.6d;
    private int maxShardMigrationsPerTick = 1;
    private long shardMigrationCooldownMs = 3000L;

    private int roomMaxConsecutiveLogicErrors = 10;

    private long shutdownWaitMs = 3000L;

    // 热点评分权重（P2 #13）：原先硬编码在 VKGameShardBalancer.hotRoomScore，现可通过配置调整
    private double hotRoomScoreCommandWeight = 8.0;
    private double hotRoomScoreQueueWeight = 2.0;
    private double hotRoomScoreCostWeight = 1.0;

    public VKGameConfig copy() {
        return new VKGameConfig()
                .enabled(enabled)
                .autoStartTicker(autoStartTicker)
                .tickRate(tickRate)
                .tickWorkerThreads(tickWorkerThreads)
                .tickTimeoutMs(tickTimeoutMs)
                .maxRooms(maxRooms)
                .maxPlayersPerRoom(maxPlayersPerRoom)
                .maxCommandsPerTick(maxCommandsPerTick)
                .roomCommandQueueCapacity(roomCommandQueueCapacity)
                .highPriorityWeight(highPriorityWeight)
                .normalPriorityWeight(normalPriorityWeight)
                .lowPriorityWeight(lowPriorityWeight)
                .roomIdleTimeoutMs(roomIdleTimeoutMs)
                .roomEmptyTimeoutMs(roomEmptyTimeoutMs)
                .roomMaxLifetimeMs(roomMaxLifetimeMs)
                .roomDrainTimeoutMs(roomDrainTimeoutMs)
                .removeEmptyRoomOnLastLeave(removeEmptyRoomOnLastLeave)
                .sessionHostingEnabled(sessionHostingEnabled)
                .reconnectGraceMs(reconnectGraceMs)
                .antiCheatEnabled(antiCheatEnabled)
                .maxCommandsPerSecondPerPlayer(maxCommandsPerSecondPerPlayer)
                .enforceMonotonicClientSeq(enforceMonotonicClientSeq)
                .maxClientTimestampSkewMs(maxClientTimestampSkewMs)
                .requireOnlinePlayerCommand(requireOnlinePlayerCommand)
                .matchmakingEnabled(matchmakingEnabled)
                .matchmakingRoomSize(matchmakingRoomSize)
                .matchmakingMaxWaitMs(matchmakingMaxWaitMs)
                .matchmakingBaseRatingTolerance(matchmakingBaseRatingTolerance)
                .matchmakingRatingExpandPerSecond(matchmakingRatingExpandPerSecond)
                .matchmakingRoomIdPrefix(matchmakingRoomIdPrefix)
                .matchResultTtlMs(matchResultTtlMs)
                .matchNotifyRetryIntervalMs(matchNotifyRetryIntervalMs)
                .matchNotifyMaxRetryIntervalMs(matchNotifyMaxRetryIntervalMs)
                .matchNotifyMaxAttempts(matchNotifyMaxAttempts)
                .matchJoinRequiresToken(matchJoinRequiresToken)
                .matchJoinTokenTtlMs(matchJoinTokenTtlMs)
                .roomPersistenceEnabled(roomPersistenceEnabled)
                .roomPersistenceDir(roomPersistenceDir)
                .roomSnapshotEveryTicks(roomSnapshotEveryTicks)
                .roomWalEnabled(roomWalEnabled)
                .roomRecoveryEnabled(roomRecoveryEnabled)
                .messageStreamCapacity(messageStreamCapacity)
                .messageDefaultTtlMs(messageDefaultTtlMs)
                .messageRetryIntervalMs(messageRetryIntervalMs)
                .messageRetryMaxIntervalMs(messageRetryMaxIntervalMs)
                .messageRetryMaxAttempts(messageRetryMaxAttempts)
                .hotRoomCommandThreshold(hotRoomCommandThreshold)
                .hotRoomQueuedCommandThreshold(hotRoomQueuedCommandThreshold)
                .hotRoomCostThresholdMs(hotRoomCostThresholdMs)
                .shardImbalanceThreshold(shardImbalanceThreshold)
                .maxShardMigrationsPerTick(maxShardMigrationsPerTick)
                .shardMigrationCooldownMs(shardMigrationCooldownMs)
                .roomMaxConsecutiveLogicErrors(roomMaxConsecutiveLogicErrors)
                .shutdownWaitMs(shutdownWaitMs)
                .hotRoomScoreCommandWeight(hotRoomScoreCommandWeight)
                .hotRoomScoreQueueWeight(hotRoomScoreQueueWeight)
                .hotRoomScoreCostWeight(hotRoomScoreCostWeight);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public VKGameConfig enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public boolean isAutoStartTicker() {
        return autoStartTicker;
    }

    public VKGameConfig autoStartTicker(boolean autoStartTicker) {
        this.autoStartTicker = autoStartTicker;
        return this;
    }

    public int getTickRate() {
        return tickRate;
    }

    public VKGameConfig tickRate(int tickRate) {
        this.tickRate = Math.max(1, tickRate);
        return this;
    }

    public int getTickWorkerThreads() {
        return tickWorkerThreads;
    }

    public VKGameConfig tickWorkerThreads(int tickWorkerThreads) {
        this.tickWorkerThreads = Math.max(1, tickWorkerThreads);
        return this;
    }

    public long getTickTimeoutMs() {
        return tickTimeoutMs;
    }

    public VKGameConfig tickTimeoutMs(long tickTimeoutMs) {
        this.tickTimeoutMs = Math.max(0L, tickTimeoutMs);
        return this;
    }

    public int getMaxRooms() {
        return maxRooms;
    }

    public VKGameConfig maxRooms(int maxRooms) {
        this.maxRooms = Math.max(1, maxRooms);
        return this;
    }

    public int getMaxPlayersPerRoom() {
        return maxPlayersPerRoom;
    }

    public VKGameConfig maxPlayersPerRoom(int maxPlayersPerRoom) {
        this.maxPlayersPerRoom = Math.max(1, maxPlayersPerRoom);
        return this;
    }

    public int getMaxCommandsPerTick() {
        return maxCommandsPerTick;
    }

    public VKGameConfig maxCommandsPerTick(int maxCommandsPerTick) {
        this.maxCommandsPerTick = Math.max(1, maxCommandsPerTick);
        return this;
    }

    public int getRoomCommandQueueCapacity() {
        return roomCommandQueueCapacity;
    }

    public VKGameConfig roomCommandQueueCapacity(int roomCommandQueueCapacity) {
        this.roomCommandQueueCapacity = Math.max(1, roomCommandQueueCapacity);
        return this;
    }

    public int getHighPriorityWeight() {
        return highPriorityWeight;
    }

    public VKGameConfig highPriorityWeight(int highPriorityWeight) {
        this.highPriorityWeight = Math.max(1, highPriorityWeight);
        return this;
    }

    public int getNormalPriorityWeight() {
        return normalPriorityWeight;
    }

    public VKGameConfig normalPriorityWeight(int normalPriorityWeight) {
        this.normalPriorityWeight = Math.max(1, normalPriorityWeight);
        return this;
    }

    public int getLowPriorityWeight() {
        return lowPriorityWeight;
    }

    public VKGameConfig lowPriorityWeight(int lowPriorityWeight) {
        this.lowPriorityWeight = Math.max(1, lowPriorityWeight);
        return this;
    }

    public long getRoomIdleTimeoutMs() {
        return roomIdleTimeoutMs;
    }

    public VKGameConfig roomIdleTimeoutMs(long roomIdleTimeoutMs) {
        this.roomIdleTimeoutMs = Math.max(0L, roomIdleTimeoutMs);
        return this;
    }

    public long getRoomEmptyTimeoutMs() {
        return roomEmptyTimeoutMs;
    }

    public VKGameConfig roomEmptyTimeoutMs(long roomEmptyTimeoutMs) {
        this.roomEmptyTimeoutMs = Math.max(0L, roomEmptyTimeoutMs);
        return this;
    }

    public long getRoomMaxLifetimeMs() {
        return roomMaxLifetimeMs;
    }

    public VKGameConfig roomMaxLifetimeMs(long roomMaxLifetimeMs) {
        this.roomMaxLifetimeMs = Math.max(0L, roomMaxLifetimeMs);
        return this;
    }

    public long getRoomDrainTimeoutMs() {
        return roomDrainTimeoutMs;
    }

    public VKGameConfig roomDrainTimeoutMs(long roomDrainTimeoutMs) {
        this.roomDrainTimeoutMs = Math.max(0L, roomDrainTimeoutMs);
        return this;
    }

    public boolean isRemoveEmptyRoomOnLastLeave() {
        return removeEmptyRoomOnLastLeave;
    }

    public VKGameConfig removeEmptyRoomOnLastLeave(boolean removeEmptyRoomOnLastLeave) {
        this.removeEmptyRoomOnLastLeave = removeEmptyRoomOnLastLeave;
        return this;
    }

    public boolean isSessionHostingEnabled() {
        return sessionHostingEnabled;
    }

    public VKGameConfig sessionHostingEnabled(boolean sessionHostingEnabled) {
        this.sessionHostingEnabled = sessionHostingEnabled;
        return this;
    }

    public long getReconnectGraceMs() {
        return reconnectGraceMs;
    }

    public VKGameConfig reconnectGraceMs(long reconnectGraceMs) {
        this.reconnectGraceMs = Math.max(0L, reconnectGraceMs);
        return this;
    }

    public boolean isAntiCheatEnabled() {
        return antiCheatEnabled;
    }

    public VKGameConfig antiCheatEnabled(boolean antiCheatEnabled) {
        this.antiCheatEnabled = antiCheatEnabled;
        return this;
    }

    public int getMaxCommandsPerSecondPerPlayer() {
        return maxCommandsPerSecondPerPlayer;
    }

    public VKGameConfig maxCommandsPerSecondPerPlayer(int maxCommandsPerSecondPerPlayer) {
        this.maxCommandsPerSecondPerPlayer = Math.max(1, maxCommandsPerSecondPerPlayer);
        return this;
    }

    public boolean isEnforceMonotonicClientSeq() {
        return enforceMonotonicClientSeq;
    }

    public VKGameConfig enforceMonotonicClientSeq(boolean enforceMonotonicClientSeq) {
        this.enforceMonotonicClientSeq = enforceMonotonicClientSeq;
        return this;
    }

    public long getMaxClientTimestampSkewMs() {
        return maxClientTimestampSkewMs;
    }

    public VKGameConfig maxClientTimestampSkewMs(long maxClientTimestampSkewMs) {
        this.maxClientTimestampSkewMs = Math.max(0L, maxClientTimestampSkewMs);
        return this;
    }

    public boolean isRequireOnlinePlayerCommand() {
        return requireOnlinePlayerCommand;
    }

    public VKGameConfig requireOnlinePlayerCommand(boolean requireOnlinePlayerCommand) {
        this.requireOnlinePlayerCommand = requireOnlinePlayerCommand;
        return this;
    }

    public boolean isMatchmakingEnabled() {
        return matchmakingEnabled;
    }

    public VKGameConfig matchmakingEnabled(boolean matchmakingEnabled) {
        this.matchmakingEnabled = matchmakingEnabled;
        return this;
    }

    public int getMatchmakingRoomSize() {
        return matchmakingRoomSize;
    }

    public VKGameConfig matchmakingRoomSize(int matchmakingRoomSize) {
        this.matchmakingRoomSize = Math.max(2, matchmakingRoomSize);
        return this;
    }

    public long getMatchmakingMaxWaitMs() {
        return matchmakingMaxWaitMs;
    }

    public VKGameConfig matchmakingMaxWaitMs(long matchmakingMaxWaitMs) {
        this.matchmakingMaxWaitMs = Math.max(0L, matchmakingMaxWaitMs);
        return this;
    }

    public int getMatchmakingBaseRatingTolerance() {
        return matchmakingBaseRatingTolerance;
    }

    public VKGameConfig matchmakingBaseRatingTolerance(int matchmakingBaseRatingTolerance) {
        this.matchmakingBaseRatingTolerance = Math.max(0, matchmakingBaseRatingTolerance);
        return this;
    }

    public int getMatchmakingRatingExpandPerSecond() {
        return matchmakingRatingExpandPerSecond;
    }

    public VKGameConfig matchmakingRatingExpandPerSecond(int matchmakingRatingExpandPerSecond) {
        this.matchmakingRatingExpandPerSecond = Math.max(0, matchmakingRatingExpandPerSecond);
        return this;
    }

    public String getMatchmakingRoomIdPrefix() {
        return matchmakingRoomIdPrefix;
    }

    public VKGameConfig matchmakingRoomIdPrefix(String matchmakingRoomIdPrefix) {
        this.matchmakingRoomIdPrefix = (matchmakingRoomIdPrefix == null || matchmakingRoomIdPrefix.isBlank())
                ? "match-"
                : matchmakingRoomIdPrefix.trim();
        return this;
    }

    public long getMatchResultTtlMs() {
        return matchResultTtlMs;
    }

    public VKGameConfig matchResultTtlMs(long matchResultTtlMs) {
        this.matchResultTtlMs = Math.max(1000L, matchResultTtlMs);
        return this;
    }

    public long getMatchNotifyRetryIntervalMs() {
        return matchNotifyRetryIntervalMs;
    }

    public VKGameConfig matchNotifyRetryIntervalMs(long matchNotifyRetryIntervalMs) {
        this.matchNotifyRetryIntervalMs = Math.max(0L, matchNotifyRetryIntervalMs);
        return this;
    }

    public long getMatchNotifyMaxRetryIntervalMs() {
        return matchNotifyMaxRetryIntervalMs;
    }

    public VKGameConfig matchNotifyMaxRetryIntervalMs(long matchNotifyMaxRetryIntervalMs) {
        this.matchNotifyMaxRetryIntervalMs = Math.max(0L, matchNotifyMaxRetryIntervalMs);
        return this;
    }

    public int getMatchNotifyMaxAttempts() {
        return matchNotifyMaxAttempts;
    }

    public VKGameConfig matchNotifyMaxAttempts(int matchNotifyMaxAttempts) {
        this.matchNotifyMaxAttempts = Math.max(1, matchNotifyMaxAttempts);
        return this;
    }

    public boolean isMatchJoinRequiresToken() {
        return matchJoinRequiresToken;
    }

    public VKGameConfig matchJoinRequiresToken(boolean matchJoinRequiresToken) {
        this.matchJoinRequiresToken = matchJoinRequiresToken;
        return this;
    }

    public long getMatchJoinTokenTtlMs() {
        return matchJoinTokenTtlMs;
    }

    public VKGameConfig matchJoinTokenTtlMs(long matchJoinTokenTtlMs) {
        this.matchJoinTokenTtlMs = Math.max(1000L, matchJoinTokenTtlMs);
        return this;
    }

    public boolean isRoomPersistenceEnabled() {
        return roomPersistenceEnabled;
    }

    public VKGameConfig roomPersistenceEnabled(boolean roomPersistenceEnabled) {
        this.roomPersistenceEnabled = roomPersistenceEnabled;
        return this;
    }

    public String getRoomPersistenceDir() {
        return roomPersistenceDir;
    }

    public VKGameConfig roomPersistenceDir(String roomPersistenceDir) {
        this.roomPersistenceDir = (roomPersistenceDir == null || roomPersistenceDir.isBlank())
                ? "./data/vostok-game"
                : roomPersistenceDir.trim();
        return this;
    }

    public int getRoomSnapshotEveryTicks() {
        return roomSnapshotEveryTicks;
    }

    public VKGameConfig roomSnapshotEveryTicks(int roomSnapshotEveryTicks) {
        this.roomSnapshotEveryTicks = Math.max(1, roomSnapshotEveryTicks);
        return this;
    }

    public boolean isRoomWalEnabled() {
        return roomWalEnabled;
    }

    public VKGameConfig roomWalEnabled(boolean roomWalEnabled) {
        this.roomWalEnabled = roomWalEnabled;
        return this;
    }

    public boolean isRoomRecoveryEnabled() {
        return roomRecoveryEnabled;
    }

    public VKGameConfig roomRecoveryEnabled(boolean roomRecoveryEnabled) {
        this.roomRecoveryEnabled = roomRecoveryEnabled;
        return this;
    }

    public int getMessageStreamCapacity() {
        return messageStreamCapacity;
    }

    public VKGameConfig messageStreamCapacity(int messageStreamCapacity) {
        this.messageStreamCapacity = Math.max(10, messageStreamCapacity);
        return this;
    }

    public long getMessageDefaultTtlMs() {
        return messageDefaultTtlMs;
    }

    public VKGameConfig messageDefaultTtlMs(long messageDefaultTtlMs) {
        this.messageDefaultTtlMs = Math.max(1000L, messageDefaultTtlMs);
        return this;
    }

    public long getMessageRetryIntervalMs() {
        return messageRetryIntervalMs;
    }

    public VKGameConfig messageRetryIntervalMs(long messageRetryIntervalMs) {
        this.messageRetryIntervalMs = Math.max(0L, messageRetryIntervalMs);
        return this;
    }

    public long getMessageRetryMaxIntervalMs() {
        return messageRetryMaxIntervalMs;
    }

    public VKGameConfig messageRetryMaxIntervalMs(long messageRetryMaxIntervalMs) {
        this.messageRetryMaxIntervalMs = Math.max(0L, messageRetryMaxIntervalMs);
        return this;
    }

    public int getMessageRetryMaxAttempts() {
        return messageRetryMaxAttempts;
    }

    public VKGameConfig messageRetryMaxAttempts(int messageRetryMaxAttempts) {
        this.messageRetryMaxAttempts = Math.max(1, messageRetryMaxAttempts);
        return this;
    }

    public int getHotRoomCommandThreshold() {
        return hotRoomCommandThreshold;
    }

    public VKGameConfig hotRoomCommandThreshold(int hotRoomCommandThreshold) {
        this.hotRoomCommandThreshold = Math.max(1, hotRoomCommandThreshold);
        return this;
    }

    public int getHotRoomQueuedCommandThreshold() {
        return hotRoomQueuedCommandThreshold;
    }

    public VKGameConfig hotRoomQueuedCommandThreshold(int hotRoomQueuedCommandThreshold) {
        this.hotRoomQueuedCommandThreshold = Math.max(1, hotRoomQueuedCommandThreshold);
        return this;
    }

    public long getHotRoomCostThresholdMs() {
        return hotRoomCostThresholdMs;
    }

    public VKGameConfig hotRoomCostThresholdMs(long hotRoomCostThresholdMs) {
        this.hotRoomCostThresholdMs = Math.max(1L, hotRoomCostThresholdMs);
        return this;
    }

    public double getShardImbalanceThreshold() {
        return shardImbalanceThreshold;
    }

    public VKGameConfig shardImbalanceThreshold(double shardImbalanceThreshold) {
        this.shardImbalanceThreshold = Math.max(1.0d, shardImbalanceThreshold);
        return this;
    }

    public int getMaxShardMigrationsPerTick() {
        return maxShardMigrationsPerTick;
    }

    public VKGameConfig maxShardMigrationsPerTick(int maxShardMigrationsPerTick) {
        this.maxShardMigrationsPerTick = Math.max(1, maxShardMigrationsPerTick);
        return this;
    }

    public long getShardMigrationCooldownMs() {
        return shardMigrationCooldownMs;
    }

    public VKGameConfig shardMigrationCooldownMs(long shardMigrationCooldownMs) {
        this.shardMigrationCooldownMs = Math.max(0L, shardMigrationCooldownMs);
        return this;
    }

    public int getRoomMaxConsecutiveLogicErrors() {
        return roomMaxConsecutiveLogicErrors;
    }

    public VKGameConfig roomMaxConsecutiveLogicErrors(int roomMaxConsecutiveLogicErrors) {
        this.roomMaxConsecutiveLogicErrors = Math.max(0, roomMaxConsecutiveLogicErrors);
        return this;
    }

    public long getShutdownWaitMs() {
        return shutdownWaitMs;
    }

    public VKGameConfig shutdownWaitMs(long shutdownWaitMs) {
        this.shutdownWaitMs = Math.max(0L, shutdownWaitMs);
        return this;
    }

    public double getHotRoomScoreCommandWeight() {
        return hotRoomScoreCommandWeight;
    }

    public VKGameConfig hotRoomScoreCommandWeight(double hotRoomScoreCommandWeight) {
        this.hotRoomScoreCommandWeight = Math.max(0.0, hotRoomScoreCommandWeight);
        return this;
    }

    public double getHotRoomScoreQueueWeight() {
        return hotRoomScoreQueueWeight;
    }

    public VKGameConfig hotRoomScoreQueueWeight(double hotRoomScoreQueueWeight) {
        this.hotRoomScoreQueueWeight = Math.max(0.0, hotRoomScoreQueueWeight);
        return this;
    }

    public double getHotRoomScoreCostWeight() {
        return hotRoomScoreCostWeight;
    }

    public VKGameConfig hotRoomScoreCostWeight(double hotRoomScoreCostWeight) {
        this.hotRoomScoreCostWeight = Math.max(0.0, hotRoomScoreCostWeight);
        return this;
    }
}
