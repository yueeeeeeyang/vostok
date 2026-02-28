package yueyang.vostok.game.core.persistence;

import yueyang.vostok.game.command.VKGameCommand;
import yueyang.vostok.game.VKGameConfig;
import yueyang.vostok.game.room.VKGamePlayerSession;
import yueyang.vostok.game.room.VKGameRoom;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * 房间状态持久化组件：
 * - Snapshot：按 tick 周期或房间关闭时落盘；
 * - WAL：按命令追加写，使用 per-room BufferedWriter 缓存，避免高频 tick 下每条命令都打开/关闭文件（P1 #7）。
 *
 * 属性序列化说明（P1 #7）：
 * 写入时对常见类型加类型前缀（I:/L:/D:/F:/B:/S:），恢复时还原原始类型，
 * 避免所有属性在恢复后都变成 String。
 *
 * 玩家 session 持久化（P1 #6）：
 * 快照中额外保存 sessionToken 和 joinedAt，恢复时重建完整 session，
 * 确保断线玩家重启后能凭原 token 重连。
 */
public final class VKGameRoomPersistence {

    /**
     * 已还原的玩家 session 信息（P1 #6），供 VKGameRuntime 恢复时使用。
     */
    public static final class PlayerSessionInfo {
        public final String playerId;
        public final String sessionToken;
        public final long joinedAt;

        public PlayerSessionInfo(String playerId, String sessionToken, long joinedAt) {
            this.playerId = playerId;
            this.sessionToken = sessionToken;
            this.joinedAt = joinedAt;
        }
    }

    public static final class RoomSnapshot {
        public final String roomId;
        public final String gameType;
        public final long tick;
        public final long savedAtMs;
        public final List<PlayerSessionInfo> players;
        public final Map<String, Object> attributes;

        public RoomSnapshot(String roomId,
                            String gameType,
                            long tick,
                            long savedAtMs,
                            List<PlayerSessionInfo> players,
                            Map<String, Object> attributes) {
            this.roomId = roomId;
            this.gameType = gameType;
            this.tick = tick;
            this.savedAtMs = savedAtMs;
            this.players = players;
            this.attributes = attributes;
        }
    }

    private final Supplier<VKGameConfig> configSupplier;
    private final BiConsumer<String, Throwable> logger;
    private final ConcurrentHashMap<String, Object> roomLocks = new ConcurrentHashMap<>();
    // per-room WAL 缓冲写入器（P1 #7）：避免每条命令都 open/close 文件
    private final ConcurrentHashMap<String, BufferedWriter> walWriters = new ConcurrentHashMap<>();

    public VKGameRoomPersistence(Supplier<VKGameConfig> configSupplier, BiConsumer<String, Throwable> logger) {
        this.configSupplier = configSupplier;
        this.logger = logger;
    }

    public void ensureReady() {
        VKGameConfig cfg = configSupplier.get();
        if (!cfg.isRoomPersistenceEnabled()) {
            return;
        }
        try {
            Files.createDirectories(roomDataDir(cfg));
        } catch (IOException e) {
            logger.accept("room_persist_prepare", e);
        }
    }

    public void maybeSnapshot(VKGameRoom room, long tickNo) {
        VKGameConfig cfg = configSupplier.get();
        if (!cfg.isRoomPersistenceEnabled()) {
            return;
        }
        int every = Math.max(1, cfg.getRoomSnapshotEveryTicks());
        if (tickNo <= 0 || tickNo % every != 0) {
            return;
        }
        saveSnapshot(room);
    }

    public void saveSnapshot(VKGameRoom room) {
        if (room == null) {
            return;
        }
        VKGameConfig cfg = configSupplier.get();
        if (!cfg.isRoomPersistenceEnabled()) {
            return;
        }

        Path dir = roomDataDir(cfg);
        Path target = dir.resolve(room.getRoomId() + ".snapshot");
        Path temp = dir.resolve(room.getRoomId() + ".snapshot.tmp");
        Object lock = roomLocks.computeIfAbsent(room.getRoomId(), k -> new Object());

        synchronized (lock) {
            try {
                Files.createDirectories(dir);
                Properties props = new Properties();
                props.setProperty("roomId", room.getRoomId());
                props.setProperty("gameType", room.getGameType());
                props.setProperty("tick", String.valueOf(room.getCurrentTick()));
                props.setProperty("savedAtMs", String.valueOf(System.currentTimeMillis()));

                // 持久化玩家 session（P1 #6）：保存 sessionToken + joinedAt，恢复时重建完整 session
                List<VKGamePlayerSession> players = room.players();
                props.setProperty("player.count", String.valueOf(players.size()));
                for (int i = 0; i < players.size(); i++) {
                    VKGamePlayerSession p = players.get(i);
                    props.setProperty("player." + i + ".id", enc(p.getPlayerId()));
                    props.setProperty("player." + i + ".token", enc(p.getSessionToken()));
                    props.setProperty("player." + i + ".joinedAt", String.valueOf(p.getJoinedAt()));
                }

                // 持久化属性（P1 #7）：加类型前缀，避免恢复后所有值都变成 String
                Map<String, Object> attrs = room.attributes();
                ArrayList<Map.Entry<String, Object>> attrEntries = new ArrayList<>();
                for (var e : attrs.entrySet()) {
                    if (e.getKey() == null) {
                        continue;
                    }
                    attrEntries.add(e);
                }
                props.setProperty("attr.count", String.valueOf(attrEntries.size()));
                for (int i = 0; i < attrEntries.size(); i++) {
                    Map.Entry<String, Object> e = attrEntries.get(i);
                    props.setProperty("attr." + i + ".k", enc(e.getKey()));
                    props.setProperty("attr." + i + ".v", enc(encodeTypedValue(e.getValue())));
                }

                // 写入版本号并计算完整性校验和（排除 snapshot.sha256 字段本身避免循环依赖）
                props.setProperty("snapshot.version", "2");
                props.setProperty("snapshot.sha256", computeSnapshotChecksum(props));
                try (OutputStream out = Files.newOutputStream(
                        temp,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE)) {
                    props.store(out, "vostok-game-room-snapshot");
                }
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                logger.accept("room_snapshot", e);
            }
        }
    }

    /**
     * 追加 WAL 记录（P1 #7）：使用 per-room BufferedWriter 缓存，多条命令共享一次 flush，
     * 避免高频 tick 下每条命令都执行文件打开/写入/关闭的系统调用。
     * 调用 {@link #flushWal(String)} 在 tick 结束或房间关闭时显式落盘。
     */
    public void appendWal(VKGameRoom room, VKGameCommand command, long tickNo, long nowMs) {
        if (room == null || command == null) {
            return;
        }
        VKGameConfig cfg = configSupplier.get();
        if (!cfg.isRoomPersistenceEnabled() || !cfg.isRoomWalEnabled()) {
            return;
        }

        Path dir = roomDataDir(cfg);
        Path wal = dir.resolve(room.getRoomId() + ".wal");
        Object lock = roomLocks.computeIfAbsent(room.getRoomId(), k -> new Object());

        synchronized (lock) {
            try {
                Files.createDirectories(dir);
                BufferedWriter writer = getOrCreateWalWriter(room.getRoomId(), wal);
                String payload = command.getPayload() == null ? "" : String.valueOf(command.getPayload());
                writer.write(nowMs
                        + "\t" + tickNo
                        + "\t" + enc(command.getPlayerId())
                        + "\t" + enc(command.getType())
                        + "\t" + command.getClientSeq()
                        + "\t" + command.getTimestampMs()
                        + "\t" + command.getPriority()
                        + "\t" + enc(payload));
                writer.newLine();
                // 不立即 flush，由调用方在 tick 结束或房间关闭时批量 flush
            } catch (IOException e) {
                logger.accept("room_wal", e);
            }
        }
    }

    /**
     * 批量 flush 指定房间的 WAL 缓冲（P1 #7）：在 tick 结束后或房间关闭时调用，
     * 将缓冲区内容写入磁盘。
     */
    public void flushWal(String roomId) {
        if (roomId == null) {
            return;
        }
        Object lock = roomLocks.computeIfAbsent(roomId, k -> new Object());
        synchronized (lock) {
            BufferedWriter writer = walWriters.get(roomId);
            if (writer == null) {
                return;
            }
            try {
                writer.flush();
            } catch (IOException e) {
                logger.accept("room_wal_flush", e);
            }
        }
    }

    /**
     * 关闭并清理指定房间的 WAL writer（在房间永久关闭时调用）。
     */
    public void closeWal(String roomId) {
        if (roomId == null) {
            return;
        }
        Object lock = roomLocks.computeIfAbsent(roomId, k -> new Object());
        synchronized (lock) {
            BufferedWriter writer = walWriters.remove(roomId);
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    logger.accept("room_wal_close", e);
                }
            }
        }
    }

    public List<RoomSnapshot> loadSnapshotsByGameType(String gameType) {
        VKGameConfig cfg = configSupplier.get();
        if (!cfg.isRoomPersistenceEnabled() || !cfg.isRoomRecoveryEnabled()) {
            return List.of();
        }

        Path dir = roomDataDir(cfg);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }

        ArrayList<RoomSnapshot> out = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.snapshot")) {
            for (Path p : stream) {
                RoomSnapshot snapshot = readSnapshot(p);
                if (snapshot == null) {
                    continue;
                }
                if (!Objects.equals(gameType, snapshot.gameType)) {
                    continue;
                }
                out.add(snapshot);
            }
        } catch (IOException e) {
            logger.accept("room_snapshot_load", e);
            return List.of();
        }

        out.sort(Comparator.comparingLong(s -> s.savedAtMs));
        return Collections.unmodifiableList(out);
    }

    public void clear() {
        // 关闭所有打开的 WAL writers
        for (String roomId : walWriters.keySet()) {
            closeWal(roomId);
        }
        walWriters.clear();
        roomLocks.clear();
    }

    private BufferedWriter getOrCreateWalWriter(String roomId, Path walPath) throws IOException {
        BufferedWriter writer = walWriters.get(roomId);
        if (writer == null) {
            writer = new BufferedWriter(
                    new OutputStreamWriter(
                            Files.newOutputStream(walPath,
                                    StandardOpenOption.CREATE,
                                    StandardOpenOption.WRITE,
                                    StandardOpenOption.APPEND),
                            StandardCharsets.UTF_8),
                    8192);
            walWriters.put(roomId, writer);
        }
        return writer;
    }

    private RoomSnapshot readSnapshot(Path file) {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file, StandardOpenOption.READ)) {
            props.load(in);
        } catch (IOException e) {
            logger.accept("room_snapshot_read", e);
            return null;
        }

        // 版本校验：支持 v1（仅 playerId）和 v2（playerId + sessionToken + joinedAt + 类型化属性）
        String version = props.getProperty("snapshot.version", "");
        if (!"1".equals(version) && !"2".equals(version)) {
            logger.accept("room_snapshot_read",
                    new RuntimeException("snapshot.version mismatch: expected 1 or 2, got " + version));
            return null;
        }

        // 校验和校验：数据损坏时降级为空房间恢复，防止以错误状态恢复房间
        String storedChecksum = props.getProperty("snapshot.sha256", "");
        String computedChecksum = computeSnapshotChecksum(props);
        if (!computedChecksum.equals(storedChecksum)) {
            logger.accept("room_snapshot_read",
                    new RuntimeException("snapshot.sha256 mismatch: data may be corrupted"));
            return null;
        }

        String roomId = props.getProperty("roomId", "").trim();
        String gameType = props.getProperty("gameType", "").trim();
        if (roomId.isEmpty() || gameType.isEmpty()) {
            return null;
        }

        long tick = parseLong(props.getProperty("tick"), 0L);
        long savedAt = parseLong(props.getProperty("savedAtMs"), 0L);

        int playerCount = (int) parseLong(props.getProperty("player.count"), 0L);
        ArrayList<PlayerSessionInfo> players = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            if ("2".equals(version)) {
                // v2：包含 sessionToken 和 joinedAt
                String rawId = props.getProperty("player." + i + ".id");
                String rawToken = props.getProperty("player." + i + ".token");
                String rawJoinedAt = props.getProperty("player." + i + ".joinedAt");
                if (rawId == null || rawId.isBlank()) {
                    continue;
                }
                String playerId = dec(rawId);
                String token = rawToken != null ? dec(rawToken) : "";
                long joinedAt = parseLong(rawJoinedAt, savedAt);
                players.add(new PlayerSessionInfo(playerId, token, joinedAt));
            } else {
                // v1：向后兼容，仅有 playerId
                String raw = props.getProperty("player." + i);
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                players.add(new PlayerSessionInfo(dec(raw), "", savedAt));
            }
        }

        int attrCount = (int) parseLong(props.getProperty("attr.count"), 0L);
        HashMap<String, Object> attrs = new HashMap<>();
        for (int i = 0; i < attrCount; i++) {
            String k = props.getProperty("attr." + i + ".k");
            String v = props.getProperty("attr." + i + ".v");
            if (k == null || v == null) {
                continue;
            }
            String rawValue = dec(v);
            Object typed = "2".equals(version) ? decodeTypedValue(rawValue) : rawValue;
            attrs.put(dec(k), typed);
        }

        return new RoomSnapshot(
                roomId,
                gameType,
                tick,
                savedAt,
                List.copyOf(players),
                Map.copyOf(attrs)
        );
    }

    /**
     * 带类型前缀的属性序列化（P1 #7）：
     * Integer→"I:42", Long→"L:123", Double→"D:3.14", Float→"F:1.0", Boolean→"B:true", 其余→"S:..."
     */
    private static String encodeTypedValue(Object value) {
        if (value == null) {
            return "S:";
        }
        if (value instanceof Integer) {
            return "I:" + value;
        }
        if (value instanceof Long) {
            return "L:" + value;
        }
        if (value instanceof Double) {
            return "D:" + value;
        }
        if (value instanceof Float) {
            return "F:" + value;
        }
        if (value instanceof Boolean) {
            return "B:" + value;
        }
        return "S:" + value;
    }

    /**
     * 反序列化带类型前缀的属性值（P1 #7）。
     */
    private static Object decodeTypedValue(String raw) {
        if (raw == null || raw.length() < 2 || raw.charAt(1) != ':') {
            return raw;
        }
        char type = raw.charAt(0);
        String val = raw.substring(2);
        try {
            return switch (type) {
                case 'I' -> Integer.parseInt(val);
                case 'L' -> Long.parseLong(val);
                case 'D' -> Double.parseDouble(val);
                case 'F' -> Float.parseFloat(val);
                case 'B' -> Boolean.parseBoolean(val);
                default -> val; // 'S' 或未知类型
            };
        } catch (NumberFormatException e) {
            return val;
        }
    }

    private static long parseLong(String value, long defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String enc(String value) {
        String safe = value == null ? "" : value;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(safe.getBytes(StandardCharsets.UTF_8));
    }

    private static String dec(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    /**
     * 计算快照属性的 SHA-256 校验和（排除 snapshot.sha256 key 本身，避免循环依赖）。
     * 将所有属性按 key 排序后拼接为 "key=value\n" 字节流，计算摘要并返回 HEX 字符串。
     */
    private static String computeSnapshotChecksum(Properties props) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            List<String> keys = new ArrayList<>(props.stringPropertyNames());
            Collections.sort(keys);
            for (String key : keys) {
                if ("snapshot.sha256".equals(key)) {
                    continue;
                }
                String line = key + "=" + props.getProperty(key) + "\n";
                md.update(line.getBytes(StandardCharsets.UTF_8));
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private static Path roomDataDir(VKGameConfig cfg) {
        return Path.of(cfg.getRoomPersistenceDir()).resolve("rooms");
    }
}
