package yueyang.vostok.game.room;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class VKGamePlayerSession {
    private final String playerId;
    private final long joinedAt;
    private final String sessionToken;
    private final AtomicLong lastActiveAt = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong disconnectedAt = new AtomicLong(-1L);
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private volatile boolean online = true;

    public VKGamePlayerSession(String playerId) {
        this.playerId = playerId;
        this.joinedAt = System.currentTimeMillis();
        this.sessionToken = UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 快照恢复构造器（P1 #6）：从持久化快照还原 sessionToken 和 joinedAt，
     * 保证断线玩家重启后仍能凭原 token 重连。恢复的 session 初始为离线状态。
     */
    public VKGamePlayerSession(String playerId, String sessionToken, long joinedAt) {
        this.playerId = playerId;
        this.sessionToken = (sessionToken == null || sessionToken.isBlank())
                ? UUID.randomUUID().toString().replace("-", "")
                : sessionToken;
        this.joinedAt = joinedAt > 0 ? joinedAt : System.currentTimeMillis();
        this.online = false;
        this.disconnectedAt.set(System.currentTimeMillis());
    }

    public String getPlayerId() {
        return playerId;
    }

    public long getJoinedAt() {
        return joinedAt;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public long getLastActiveAt() {
        return lastActiveAt.get();
    }

    public void touch() {
        lastActiveAt.set(System.currentTimeMillis());
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
        if (online) {
            disconnectedAt.set(-1L);
        } else {
            disconnectedAt.set(System.currentTimeMillis());
        }
        touch();
    }

    public long getDisconnectedAt() {
        return disconnectedAt.get();
    }

    public void markDisconnected(long nowMs) {
        this.online = false;
        this.disconnectedAt.set(nowMs);
        this.lastActiveAt.set(nowMs);
    }

    public void markReconnected(long nowMs) {
        this.online = true;
        this.disconnectedAt.set(-1L);
        this.lastActiveAt.set(nowMs);
    }

    public Map<String, Object> attributes() {
        return attributes;
    }
}
