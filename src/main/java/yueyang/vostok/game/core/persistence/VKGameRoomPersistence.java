package yueyang.vostok.game.core.persistence;

import yueyang.vostok.game.command.VKGameCommand;
import yueyang.vostok.game.VKGameConfig;
import yueyang.vostok.game.room.VKGamePlayerSession;
import yueyang.vostok.game.room.VKGameRoom;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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
 * - WAL：按命令追加写，便于问题排查与后续回放扩展。
 */
public final class VKGameRoomPersistence {
    public static final class RoomSnapshot {
        public final String roomId;
        public final String gameType;
        public final long tick;
        public final long savedAtMs;
        public final List<String> players;
        public final Map<String, String> attributes;

        public RoomSnapshot(String roomId,
                            String gameType,
                            long tick,
                            long savedAtMs,
                            List<String> players,
                            Map<String, String> attributes) {
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

                List<VKGamePlayerSession> players = room.players();
                props.setProperty("player.count", String.valueOf(players.size()));
                for (int i = 0; i < players.size(); i++) {
                    VKGamePlayerSession p = players.get(i);
                    props.setProperty("player." + i, enc(p.getPlayerId()));
                }

                Map<String, Object> attrs = room.attributes();
                ArrayList<Map.Entry<String, String>> attrEntries = new ArrayList<>();
                for (var e : attrs.entrySet()) {
                    if (e.getKey() == null) {
                        continue;
                    }
                    attrEntries.add(Map.entry(e.getKey(), String.valueOf(e.getValue())));
                }
                props.setProperty("attr.count", String.valueOf(attrEntries.size()));
                for (int i = 0; i < attrEntries.size(); i++) {
                    Map.Entry<String, String> e = attrEntries.get(i);
                    props.setProperty("attr." + i + ".k", enc(e.getKey()));
                    props.setProperty("attr." + i + ".v", enc(e.getValue()));
                }

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
                String payload = command.getPayload() == null ? "" : String.valueOf(command.getPayload());
                String line = nowMs
                        + "\t" + tickNo
                        + "\t" + enc(command.getPlayerId())
                        + "\t" + enc(command.getType())
                        + "\t" + command.getClientSeq()
                        + "\t" + command.getTimestampMs()
                        + "\t" + command.getPriority()
                        + "\t" + enc(payload)
                        + "\n";
                Files.writeString(
                        wal,
                        line,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND
                );
            } catch (IOException e) {
                logger.accept("room_wal", e);
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
        roomLocks.clear();
    }

    private RoomSnapshot readSnapshot(Path file) {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file, StandardOpenOption.READ)) {
            props.load(in);
        } catch (IOException e) {
            logger.accept("room_snapshot_read", e);
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
        ArrayList<String> players = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            String raw = props.getProperty("player." + i);
            if (raw == null || raw.isBlank()) {
                continue;
            }
            players.add(dec(raw));
        }

        int attrCount = (int) parseLong(props.getProperty("attr.count"), 0L);
        HashMap<String, String> attrs = new HashMap<>();
        for (int i = 0; i < attrCount; i++) {
            String k = props.getProperty("attr." + i + ".k");
            String v = props.getProperty("attr." + i + ".v");
            if (k == null || v == null) {
                continue;
            }
            attrs.put(dec(k), dec(v));
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

    private static Path roomDataDir(VKGameConfig cfg) {
        return Path.of(cfg.getRoomPersistenceDir()).resolve("rooms");
    }
}
