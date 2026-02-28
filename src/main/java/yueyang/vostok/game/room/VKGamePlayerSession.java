package yueyang.vostok.game.room;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class VKGamePlayerSession {
    private final String playerId;
    private final long joinedAt;
    private final String sessionToken = UUID.randomUUID().toString().replace("-", "");
    private final AtomicLong lastActiveAt = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong disconnectedAt = new AtomicLong(-1L);
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private volatile boolean online = true;

    public VKGamePlayerSession(String playerId) {
        this.playerId = playerId;
        this.joinedAt = System.currentTimeMillis();
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
