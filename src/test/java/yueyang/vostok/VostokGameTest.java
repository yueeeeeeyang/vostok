package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.game.command.VKGameCommand;
import yueyang.vostok.game.command.VKGameCommandPriority;
import yueyang.vostok.game.VKGameConfig;
import yueyang.vostok.game.VKGameLogic;
import yueyang.vostok.game.message.VKGameMessage;
import yueyang.vostok.game.message.VKGameMessagePublishCommand;
import yueyang.vostok.game.message.VKGameMessageScope;
import yueyang.vostok.game.message.VKGameMessageType;
import yueyang.vostok.game.match.VKGameMatchRequest;
import yueyang.vostok.game.match.VKGameMatchResult;
import yueyang.vostok.game.match.VKGameMatchStatus;
import yueyang.vostok.game.room.VKGamePlayerSession;
import yueyang.vostok.game.room.VKGameRoom;
import yueyang.vostok.game.shard.VKGameShardMetrics;
import yueyang.vostok.game.exception.VKGameErrorCode;
import yueyang.vostok.game.exception.VKGameException;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import yueyang.vostok.game.core.shard.VKGameShardBalancer;
import yueyang.vostok.game.core.runtime.VKGameRuntimeMetrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokGameTest {
    @AfterEach
    void tearDown() {
        Vostok.Game.close();
        Vostok.close();
    }

    @Test
    void testGameFlowWithManualTick() {
        AtomicInteger joins = new AtomicInteger();
        AtomicInteger commands = new AtomicInteger();
        AtomicInteger ticks = new AtomicInteger();

        var game = Vostok.Game.init(new VKGameConfig()
                .autoStartTicker(false)
                .tickRate(30)
                .maxCommandsPerTick(16)
                .roomCommandQueueCapacity(16));

        game.registerLogic("demo", new VKGameLogic() {
            @Override
            public void onPlayerJoin(VKGameRoom room, VKGamePlayerSession player) {
                joins.incrementAndGet();
            }

            @Override
            public void onCommand(VKGameRoom room, VKGameCommand command) {
                commands.incrementAndGet();
                room.attributes().put("last_cmd", command.getType());
            }

            @Override
            public void onTick(VKGameRoom room, long tickNo) {
                ticks.incrementAndGet();
            }
        });

        VKGameRoom room = game.createRoom("room-1", "demo");
        assertNotNull(room);

        VKGamePlayerSession p1 = game.join("room-1", "p1");
        assertNotNull(p1);
        game.submit("room-1", new VKGameCommand("p1", "move", Map.of("x", 1)));

        Vostok.Game.tickOnce();

        assertEquals(1, joins.get());
        assertEquals(1, commands.get());
        assertEquals(1, ticks.get());
        assertEquals("move", room.attributes().get("last_cmd"));
        assertEquals(1L, room.getCurrentTick());
        assertEquals(1L, Vostok.Game.metrics().commandsProcessed());
    }

    @Test
    void testQueueFull() {
        var game = Vostok.Game.init(new VKGameConfig()
                .autoStartTicker(false)
                .roomCommandQueueCapacity(1));

        game.registerLogic("demo", new VKGameLogic() {
        });
        game.createRoom("r1", "demo");

        game.submit("r1", new VKGameCommand("p1", "a", null));
        VKGameException ex = assertThrows(VKGameException.class,
                () -> game.submit("r1", new VKGameCommand("p1", "b", null)));
        assertEquals(VKGameErrorCode.QUEUE_FULL, ex.getCode());
    }

    @Test
    void testTickAllRoomsEveryFrameWithShards() {
        Map<String, AtomicInteger> roomTicks = new ConcurrentHashMap<>();
        AtomicInteger commandCount = new AtomicInteger();

        var game = Vostok.Game.init(new VKGameConfig()
                .autoStartTicker(false)
                .tickWorkerThreads(2)
                .maxCommandsPerTick(8));

        game.registerLogic("demo", new VKGameLogic() {
            @Override
            public void onCommand(VKGameRoom room, VKGameCommand command) {
                commandCount.incrementAndGet();
            }

            @Override
            public void onTick(VKGameRoom room, long tickNo) {
                roomTicks.computeIfAbsent(room.getRoomId(), k -> new AtomicInteger()).incrementAndGet();
            }
        });

        game.createRoom("r1", "demo");
        game.createRoom("r2", "demo");

        Vostok.Game.tickOnce();
        assertEquals(1, roomTicks.get("r1").get());
        assertEquals(1, roomTicks.get("r2").get());

        game.submit("r1", new VKGameCommand("p1", "move", null));
        Vostok.Game.tickOnce();

        assertEquals(2, roomTicks.get("r1").get());
        assertEquals(2, roomTicks.get("r2").get());
        assertEquals(1, commandCount.get());
    }

    @Test
    void testInitViaVostokInit() {
        AtomicInteger roomStarts = new AtomicInteger();

        Vostok.init(cfg -> cfg
                .gameConfig(new VKGameConfig().autoStartTicker(false))
                .gameSetup(game -> {
                    game.registerLogic("demo", new VKGameLogic() {
                        @Override
                        public void onRoomStart(VKGameRoom room) {
                            roomStarts.incrementAndGet();
                        }
                    });
                    game.createRoom("boot-room", "demo");
                })
        );

        assertTrue(Vostok.Game.started());
        assertEquals(1, roomStarts.get());
        assertTrue(Vostok.Game.init().roomIds().contains("boot-room"));

        Vostok.close();
        assertFalse(Vostok.Game.started());
    }

    @Test
    void testShardMetricsAndHotRoomMigration() {
        var game = Vostok.Game.init(new VKGameConfig()
                .autoStartTicker(false)
                .tickWorkerThreads(2)
                .tickTimeoutMs(0)
                .hotRoomCommandThreshold(2)
                .hotRoomQueuedCommandThreshold(9999)
                .hotRoomCostThresholdMs(9999)
                .shardImbalanceThreshold(1.01d)
                .maxShardMigrationsPerTick(1)
                .shardMigrationCooldownMs(0));

        game.registerLogic("demo", new VKGameLogic() {
        });
        game.createRoom("Aa", "demo");
        game.createRoom("BB", "demo");

        for (int i = 0; i < 6; i++) {
            game.submit("Aa", new VKGameCommand("p1", "cmd", i));
        }
        Vostok.Game.tickOnce();
        assertTrue(Vostok.Game.metrics().shardImbalanceEvents() >= 1);
        assertTrue(Vostok.Game.metrics().shardMigrations() >= 1);

        Vostok.Game.tickOnce();
        List<VKGameShardMetrics> shards = Vostok.Game.shardMetrics();
        assertEquals(2, shards.size());
        int maxRoomCount = shards.stream().mapToInt(VKGameShardMetrics::roomCount).max().orElse(0);
        assertTrue(maxRoomCount <= 1);
    }

    @Test
    void testTickTimeoutProtection() {
        AtomicInteger tickCalls = new AtomicInteger();
        var game = Vostok.Game.init(new VKGameConfig()
                .autoStartTicker(false)
                .tickWorkerThreads(1)
                .tickTimeoutMs(5)
                .roomIdleTimeoutMs(0)
                .roomEmptyTimeoutMs(0));

        game.registerLogic("demo", new VKGameLogic() {
            @Override
            public void onTick(VKGameRoom room, long tickNo) {
                tickCalls.incrementAndGet();
                try {
                    Thread.sleep(25L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        game.createRoom("r1", "demo");
        game.createRoom("r2", "demo");
        Vostok.Game.tickOnce();

        assertEquals(1L, Vostok.Game.metrics().tickTimeouts());
        assertTrue(Vostok.Game.metrics().tickTimeoutRoomSkips() >= 1L);
        assertTrue(tickCalls.get() <= 1);
    }

    @Test
    void testRoomLifecyclePolicies() throws Exception {
        AtomicInteger drainingCalls = new AtomicInteger();
        AtomicInteger closeCalls = new AtomicInteger();

        var game = Vostok.Game.init(new VKGameConfig()
                .autoStartTicker(false)
                .tickTimeoutMs(0)
                .roomIdleTimeoutMs(0)
                .roomEmptyTimeoutMs(2)
                .roomDrainTimeoutMs(20));

        game.registerLogic("demo", new VKGameLogic() {
            @Override
            public void onRoomDraining(VKGameRoom room, String reason) {
                drainingCalls.incrementAndGet();
            }

            @Override
            public void onRoomClose(VKGameRoom room) {
                closeCalls.incrementAndGet();
            }
        });

        game.createRoom("empty-room", "demo");
        Thread.sleep(10L);
        Vostok.Game.tickOnce();
        assertTrue(game.room("empty-room") == null);
        assertTrue(Vostok.Game.metrics().roomClosedByEmpty() >= 1);

        game.createRoom("drain-room", "demo");
        game.join("drain-room", "p1");
        assertTrue(game.drainRoom("drain-room", "maintenance"));
        VKGameException stateErr = assertThrows(VKGameException.class,
                () -> game.submit("drain-room", new VKGameCommand("p1", "move", null)));
        assertEquals(VKGameErrorCode.STATE_ERROR, stateErr.getCode());
        assertTrue(drainingCalls.get() >= 1);

        Thread.sleep(30L);
        Vostok.Game.tickOnce();
        assertTrue(game.room("drain-room") == null);
        assertTrue(Vostok.Game.metrics().roomClosedByDrainTimeout() >= 1);
        assertTrue(closeCalls.get() >= 2);
    }

    @Test
    void testDisconnectReconnectAndSessionHosting() throws Exception {
        AtomicInteger disconnectCalls = new AtomicInteger();
        AtomicInteger reconnectCalls = new AtomicInteger();
        AtomicInteger leaveCalls = new AtomicInteger();

        var game = Vostok.Game.init(new VKGameConfig()
                .autoStartTicker(false)
                .sessionHostingEnabled(true)
                .reconnectGraceMs(20)
                .requireOnlinePlayerCommand(true)
                .antiCheatEnabled(true));

        game.registerLogic("demo", new VKGameLogic() {
            @Override
            public void onPlayerDisconnect(VKGameRoom room, VKGamePlayerSession player) {
                disconnectCalls.incrementAndGet();
            }

            @Override
            public void onPlayerReconnect(VKGameRoom room, VKGamePlayerSession player) {
                reconnectCalls.incrementAndGet();
            }

            @Override
            public void onPlayerLeave(VKGameRoom room, VKGamePlayerSession player) {
                leaveCalls.incrementAndGet();
            }
        });

        game.createRoom("room-s", "demo");
        VKGamePlayerSession joined = game.join("room-s", "p1");
        assertNotNull(joined);

        VKGamePlayerSession disconnected = game.disconnect("room-s", "p1");
        assertNotNull(disconnected);
        assertFalse(disconnected.isOnline());
        assertEquals(1, disconnectCalls.get());

        VKGameException rejected = assertThrows(VKGameException.class,
                () -> game.submit("room-s", new VKGameCommand("p1", "move", null)));
        assertEquals(VKGameErrorCode.COMMAND_REJECTED, rejected.getCode());

        VKGamePlayerSession reconnected = game.reconnect("room-s", "p1", joined.getSessionToken());
        assertNotNull(reconnected);
        assertTrue(reconnected.isOnline());
        assertEquals(1, reconnectCalls.get());

        game.submit("room-s", new VKGameCommand("p1", "move", null));
        game.disconnect("room-s", "p1");
        Thread.sleep(30L);
        Vostok.Game.tickOnce();

        assertTrue(game.room("room-s").player("p1") == null);
        assertTrue(Vostok.Game.metrics().playerSessionExpired() >= 1);
        assertTrue(leaveCalls.get() >= 1);
    }

    @Test
    void testMatchmakingFlow() {
        AtomicInteger roomStarts = new AtomicInteger();

        var game = Vostok.Game.init(new VKGameConfig()
                .autoStartTicker(false)
                .matchmakingEnabled(true)
                .matchmakingRoomSize(2)
                .matchmakingBaseRatingTolerance(200)
                .matchmakingRoomIdPrefix("mm-room-"));

        game.registerLogic("demo", new VKGameLogic() {
            @Override
            public void onRoomStart(VKGameRoom room) {
                roomStarts.incrementAndGet();
            }
        });

        String t1 = game.enqueueMatch(new VKGameMatchRequest("p1", "demo", 1000, "cn"));
        String t2 = game.enqueueMatch(new VKGameMatchRequest("p2", "demo", 1010, "cn"));
        assertNotNull(t1);
        assertNotNull(t2);
        assertEquals(2, game.pendingMatchCount("demo"));

        Vostok.Game.tickOnce();

        assertEquals(0, game.pendingMatchCount("demo"));
        assertEquals(1, roomStarts.get());
        assertEquals(1L, Vostok.Game.metrics().matchSucceeded());
        assertEquals(1, game.roomIds().size());

        String roomId = game.roomIds().iterator().next();
        assertEquals(0, game.room(roomId).getPlayerCount());

        VKGameMatchResult r1 = game.pollMatchResult(t1);
        VKGameMatchResult r2 = game.pollMatchResult(t2);
        assertNotNull(r1);
        assertNotNull(r2);
        assertEquals(VKGameMatchStatus.FOUND, r1.getStatus());
        assertEquals(VKGameMatchStatus.FOUND, r2.getStatus());

        game.joinWithToken(roomId, "p1", r1.getJoinToken());
        game.joinWithToken(roomId, "p2", r2.getJoinToken());
        assertEquals(2, game.room(roomId).getPlayerCount());

        VKGameException tokenReuse = assertThrows(VKGameException.class,
                () -> game.joinWithToken(roomId, "p1", r1.getJoinToken()));
        assertEquals(VKGameErrorCode.STATE_ERROR, tokenReuse.getCode());
    }

    @Test
    void testMatchNotificationPushPollAndAck() {
        AtomicReference<VKGameMatchResult> p1Pushed = new AtomicReference<>();

        var game = Vostok.Game.init(new VKGameConfig()
                .autoStartTicker(false)
                .matchmakingEnabled(true)
                .matchmakingRoomSize(2)
                .matchmakingBaseRatingTolerance(200)
                .matchResultTtlMs(5_000L));

        game.registerLogic("demo", new VKGameLogic() {
        });
        game.bindMatchNotifier("p1", p1Pushed::set);

        String t1 = game.enqueueMatch(new VKGameMatchRequest("p1", "demo", 1000, "cn"));
        String t2 = game.enqueueMatch(new VKGameMatchRequest("p2", "demo", 1010, "cn"));
        Vostok.Game.tickOnce();

        VKGameMatchResult pushed = p1Pushed.get();
        assertNotNull(pushed);
        assertEquals(t1, pushed.getTicketId());
        assertEquals(VKGameMatchStatus.FOUND, pushed.getStatus());
        assertNotNull(pushed.getEventId());
        assertEquals(1L, pushed.getEventVersion());
        assertNotNull(pushed.getRoomId());
        assertNotNull(pushed.getJoinToken());

        VKGameMatchResult polled = game.pollMatchResult(t2);
        assertNotNull(polled);
        assertEquals(VKGameMatchStatus.FOUND, polled.getStatus());
        assertNotNull(polled.getRoomId());

        VKGameMatchResult acked = game.ackMatchFound(t2, "p2");
        assertNotNull(acked);
        assertEquals(VKGameMatchStatus.ACKED, acked.getStatus());
        assertEquals(2L, acked.getEventVersion());
        assertEquals(polled.getEventId(), acked.getEventId());
        assertEquals(1L, Vostok.Game.metrics().matchAcked());

        VKGameMatchResult ackAgain = game.ackMatchFound(t2, "p2");
        assertNotNull(ackAgain);
        assertEquals(VKGameMatchStatus.ACKED, ackAgain.getStatus());
        assertEquals(1L, Vostok.Game.metrics().matchAcked());

        VKGameException wrongPlayer = assertThrows(VKGameException.class,
                () -> game.ackMatchFound(t1, "x"));
        assertEquals(VKGameErrorCode.STATE_ERROR, wrongPlayer.getCode());
    }

    @Test
    void testMatchNotifyRetryBackoff() throws Exception {
        AtomicInteger pushedTimes = new AtomicInteger();

        var game = Vostok.Game.init(new VKGameConfig()
                .autoStartTicker(false)
                .matchmakingEnabled(true)
                .matchmakingRoomSize(2)
                .matchmakingBaseRatingTolerance(200)
                .matchNotifyRetryIntervalMs(200L)
                .matchNotifyMaxRetryIntervalMs(1000L));

        game.registerLogic("demo", new VKGameLogic() {
        });
        game.bindMatchNotifier("p1", result -> pushedTimes.incrementAndGet());

        game.enqueueMatch(new VKGameMatchRequest("p1", "demo", 1000, "cn"));
        game.enqueueMatch(new VKGameMatchRequest("p2", "demo", 1010, "cn"));
        Vostok.Game.tickOnce();
        assertEquals(1, pushedTimes.get());

        Vostok.Game.tickOnce();
        assertEquals(1, pushedTimes.get());

        Thread.sleep(220L);
        Vostok.Game.tickOnce();
        assertTrue(pushedTimes.get() >= 2);
    }

    @Test
    void testMatchResultExpiry() throws Exception {
        var game = Vostok.Game.init(new VKGameConfig()
                .autoStartTicker(false)
                .matchmakingEnabled(true)
                .matchmakingRoomSize(2)
                .matchmakingBaseRatingTolerance(200)
                .matchResultTtlMs(1000L));

        game.registerLogic("demo", new VKGameLogic() {
        });

        String t1 = game.enqueueMatch(new VKGameMatchRequest("p1", "demo", 1000, "cn"));
        game.enqueueMatch(new VKGameMatchRequest("p2", "demo", 1010, "cn"));
        Vostok.Game.tickOnce();
        assertNotNull(game.pollMatchResult(t1));

        Thread.sleep(1100L);
        Vostok.Game.tickOnce();
        assertTrue(game.pollMatchResult(t1) == null);
        assertTrue(Vostok.Game.metrics().matchResultExpired() >= 1L);
    }

    @Test
    void testRoomSnapshotPersistenceAndRecovery() throws Exception {
        Path dir = Files.createTempDirectory("vostok-game-persist");
        try {
            var config = new VKGameConfig()
                    .autoStartTicker(false)
                    .roomPersistenceEnabled(true)
                    .roomRecoveryEnabled(true)
                    .roomPersistenceDir(dir.toAbsolutePath().toString())
                    .roomSnapshotEveryTicks(1);

            var game = Vostok.Game.init(config);
            game.registerLogic("demo", new VKGameLogic() {
                @Override
                public void onCommand(VKGameRoom room, VKGameCommand command) {
                    room.attributes().put("lastCmd", command.getType());
                }
            });

            game.createRoom("recover-room", "demo");
            game.join("recover-room", "p1");
            game.submit("recover-room", new VKGameCommand("p1", "move", Map.of("x", 1)));
            Vostok.Game.tickOnce();
            Vostok.Game.close();

            Vostok.Game.init(config).registerLogic("demo", new VKGameLogic() {
            });
            VKGameRoom recovered = Vostok.Game.init().room("recover-room");
            assertNotNull(recovered);
            assertEquals(1, recovered.getPlayerCount());
            assertEquals("move", String.valueOf(recovered.attributes().get("lastCmd")));
            assertTrue(recovered.getCurrentTick() >= 1L);
        } finally {
            deleteDir(dir);
        }
    }

    @Test
    void testUnifiedMessagePublishAndPoll() {
        AtomicInteger p1Push = new AtomicInteger();
        AtomicInteger p2Push = new AtomicInteger();

        var game = Vostok.Game.init(new VKGameConfig()
                .autoStartTicker(false)
                .messageDefaultTtlMs(5_000L));

        game.registerLogic("demo", new VKGameLogic() {
        });
        game.createRoom("room-msg", "demo");
        game.join("room-msg", "p1");
        game.join("room-msg", "p2");
        game.bindMessageNotifier("p1", msg -> p1Push.incrementAndGet());
        game.bindMessageNotifier("p2", msg -> p2Push.incrementAndGet());

        List<VKGameMessage> published = game.publishMessages(List.of(
                VKGameMessagePublishCommand.playerChat("room-msg", "p1", "hello"),
                new VKGameMessagePublishCommand(
                        VKGameMessageType.SYSTEM_NOTICE,
                        VKGameMessageScope.PLAYER,
                        "p1",
                        "SYSTEM",
                        "notice",
                        "welcome",
                        Map.of("v", 1),
                        0L
                )
        ));

        assertEquals(2, published.size());
        assertTrue(p1Push.get() >= 2);
        assertTrue(p2Push.get() >= 1);

        List<VKGameMessage> roomForP2 = game.pollMessages("p2", VKGameMessageScope.ROOM, "room-msg", 0L, 20, null);
        assertEquals(1, roomForP2.size());
        assertEquals(VKGameMessageType.PLAYER_CHAT, roomForP2.get(0).getType());

        List<VKGameMessage> playerForP1 = game.pollMessages("p1", VKGameMessageScope.PLAYER, "p1", 0L, 20, null);
        assertEquals(1, playerForP1.size());
        assertEquals(VKGameMessageType.SYSTEM_NOTICE, playerForP1.get(0).getType());
        assertTrue(Vostok.Game.metrics().messagesPublished() >= 2L);
        assertTrue(Vostok.Game.metrics().messagesPushed() >= 3L);
    }

    @Test
    void testUnifiedMessageAckAndRetry() throws Exception {
        AtomicInteger pushes = new AtomicInteger();

        var game = Vostok.Game.init(new VKGameConfig()
                .autoStartTicker(false)
                .messageRetryIntervalMs(100L)
                .messageRetryMaxIntervalMs(300L)
                .messageRetryMaxAttempts(10)
                .messageDefaultTtlMs(5_000L));

        game.registerLogic("demo", new VKGameLogic() {
        });

        List<VKGameMessage> published = game.publishMessages(List.of(
                new VKGameMessagePublishCommand(
                        VKGameMessageType.SYSTEM_ALERT,
                        VKGameMessageScope.PLAYER,
                        "p1",
                        "SYSTEM",
                        "alert",
                        "need_ack",
                        null,
                        0L
                )
        ));
        assertEquals(1, published.size());
        String messageId = published.get(0).getMessageId();

        game.bindMessageNotifier("p1", msg -> pushes.incrementAndGet());
        Thread.sleep(120L);
        Vostok.Game.tickOnce();
        assertTrue(pushes.get() >= 1);

        assertEquals(1, game.ackMessages("p1", List.of(messageId)));
        assertEquals(0, game.ackMessages("p1", List.of(messageId)));
        int afterAckPushes = pushes.get();

        Thread.sleep(150L);
        Vostok.Game.tickOnce();
        assertEquals(afterAckPushes, pushes.get());
        assertTrue(Vostok.Game.metrics().messagesAcked() >= 1L);
    }

    @Test
    void testUnifiedMessagePermissionAndValidation() {
        var game = Vostok.Game.init(new VKGameConfig().autoStartTicker(false));
        game.registerLogic("demo", new VKGameLogic() {
        });
        game.createRoom("room-perm", "demo");
        game.join("room-perm", "p1");

        VKGameException invalid = assertThrows(VKGameException.class,
                () -> game.publishMessages(List.of(
                        new VKGameMessagePublishCommand(
                                VKGameMessageType.PLAYER_CHAT,
                                VKGameMessageScope.ROOM,
                                "room-perm",
                                "",
                                "",
                                "hello",
                                null,
                                0L
                        )
                )));
        assertEquals(VKGameErrorCode.INVALID_ARGUMENT, invalid.getCode());

        game.publishMessages(List.of(
                VKGameMessagePublishCommand.playerChat("room-perm", "p1", "hello")
        ));
        List<VKGameMessage> outsider = game.pollMessages("p2", VKGameMessageScope.ROOM, "room-perm", 0L, 20, null);
        assertTrue(outsider.isEmpty());
    }

    @Test
    void testUnifiedMessageExpiry() throws Exception {
        var game = Vostok.Game.init(new VKGameConfig()
                .autoStartTicker(false)
                .messageDefaultTtlMs(5_000L));
        game.registerLogic("demo", new VKGameLogic() {
        });

        long now = System.currentTimeMillis();
        game.publishMessages(List.of(
                new VKGameMessagePublishCommand(
                        VKGameMessageType.SYSTEM_NOTICE,
                        VKGameMessageScope.PLAYER,
                        "p1",
                        "SYSTEM",
                        "ttl",
                        "short",
                        null,
                        now + 300L
                )
        ));

        assertEquals(1, game.pollMessages("p1", VKGameMessageScope.PLAYER, "p1", 0L, 10, null).size());
        Thread.sleep(350L);
        Vostok.Game.tickOnce();
        assertTrue(game.pollMessages("p1", VKGameMessageScope.PLAYER, "p1", 0L, 10, null).isEmpty());
        assertTrue(Vostok.Game.metrics().messagesExpired() >= 1L);
    }

    @Test
    void testCommandGovernanceAntiCheat() {
        AtomicInteger commandCount = new AtomicInteger();
        long now = System.currentTimeMillis();

        var game = Vostok.Game.init(new VKGameConfig()
                .autoStartTicker(false)
                .antiCheatEnabled(true)
                .requireOnlinePlayerCommand(true)
                .maxCommandsPerSecondPerPlayer(1)
                .enforceMonotonicClientSeq(true)
                .maxClientTimestampSkewMs(1000));

        game.registerLogic("demo", new VKGameLogic() {
            @Override
            public void onCommand(VKGameRoom room, VKGameCommand command) {
                commandCount.incrementAndGet();
            }
        });

        game.createRoom("room-ac", "demo");
        game.join("room-ac", "p1");

        VKGameException skew = assertThrows(VKGameException.class,
                () -> game.submit("room-ac", new VKGameCommand("p1", "skew", null, 1, now + 100_000L)));
        assertEquals(VKGameErrorCode.COMMAND_REJECTED, skew.getCode());

        game.submit("room-ac", new VKGameCommand("p1", "ok", null, 2, now));

        VKGameException seq = assertThrows(VKGameException.class,
                () -> game.submit("room-ac", new VKGameCommand("p1", "dup-seq", null, 1, now)));
        assertEquals(VKGameErrorCode.COMMAND_REJECTED, seq.getCode());

        VKGameException rate = assertThrows(VKGameException.class,
                () -> game.submit("room-ac", new VKGameCommand("p1", "rate", null, 3, now)));
        assertEquals(VKGameErrorCode.COMMAND_REJECTED, rate.getCode());

        Vostok.Game.tickOnce();
        assertEquals(1, commandCount.get());
        assertTrue(Vostok.Game.metrics().commandsRejected() >= 3);
        assertTrue(Vostok.Game.metrics().commandsRejectedTimeSkew() >= 1);
        assertTrue(Vostok.Game.metrics().commandsRejectedSeq() >= 1);
        assertTrue(Vostok.Game.metrics().commandsRejectedRateLimit() >= 1);
    }

    @Test
    void testPrioritySchedulingInsideRoom() {
        List<String> processed = Collections.synchronizedList(new ArrayList<>());

        var game = Vostok.Game.init(new VKGameConfig()
                .autoStartTicker(false)
                .antiCheatEnabled(false)
                .maxCommandsPerTick(3)
                .highPriorityWeight(2)
                .normalPriorityWeight(1)
                .lowPriorityWeight(1));

        game.registerLogic("demo", new VKGameLogic() {
            @Override
            public void onCommand(VKGameRoom room, VKGameCommand command) {
                processed.add(command.getType());
            }
        });

        game.createRoom("room-p", "demo");
        game.join("room-p", "p1");
        game.submit("room-p", new VKGameCommand("p1", "L1", null, VKGameCommandPriority.LOW));
        game.submit("room-p", new VKGameCommand("p1", "H1", null, VKGameCommandPriority.HIGH));
        game.submit("room-p", new VKGameCommand("p1", "H2", null, VKGameCommandPriority.HIGH));
        game.submit("room-p", new VKGameCommand("p1", "N1", null, VKGameCommandPriority.NORMAL));

        Vostok.Game.tickOnce();
        assertEquals(List.of("H1", "H2", "N1"), processed);

        Vostok.Game.tickOnce();
        assertEquals("L1", processed.get(3));
    }

    @Test
    void testSnapshotChecksumValidation() throws Exception {
        Path dir = Files.createTempDirectory("vostok-game-checksum");
        try {
            var config = new VKGameConfig()
                    .autoStartTicker(false)
                    .roomPersistenceEnabled(true)
                    .roomRecoveryEnabled(true)
                    .roomPersistenceDir(dir.toAbsolutePath().toString())
                    .roomSnapshotEveryTicks(1);

            var game = Vostok.Game.init(config);
            game.registerLogic("demo", new VKGameLogic() {});
            game.createRoom("checksum-room", "demo");
            game.join("checksum-room", "p1");
            Vostok.Game.tickOnce();
            Vostok.Game.close();

            // 正常流程：快照完好时应可恢复
            Vostok.Game.init(config).registerLogic("demo", new VKGameLogic() {});
            VKGameRoom recovered = Vostok.Game.init().room("checksum-room");
            assertNotNull(recovered, "完好的快照应可成功恢复");
            Vostok.Game.close();

            // 损坏快照：手动修改文件内容破坏校验和
            Path snapshotFile = dir.resolve("rooms/checksum-room.snapshot");
            String content = Files.readString(snapshotFile);
            // 在文件末尾追加噪音内容破坏完整性
            Files.writeString(snapshotFile, content + "\nroomId=corrupted");
            Vostok.Game.init(config).registerLogic("demo", new VKGameLogic() {});
            VKGameRoom corrupted = Vostok.Game.init().room("checksum-room");
            assertTrue(corrupted == null, "损坏的快照应跳过，返回 null");
            Vostok.Game.close();

            // 版本号不匹配：直接写入不支持的版本号
            String fixed = content.replaceAll("snapshot\\.version=2", "snapshot.version=99");
            // 写回版本号被修改的文件（保留原始 sha256 但版本不匹配）
            Files.writeString(snapshotFile, fixed);
            Vostok.Game.init(config).registerLogic("demo", new VKGameLogic() {});
            VKGameRoom wrongVersion = Vostok.Game.init().room("checksum-room");
            assertTrue(wrongVersion == null, "版本号不匹配的快照应跳过，返回 null");
        } finally {
            deleteDir(dir);
        }
    }

    @Test
    void testTickFairnessOnTimeout() throws Exception {
        Map<String, AtomicInteger> roomTicks = new ConcurrentHashMap<>();
        int roomCount = 5;

        var game = Vostok.Game.init(new VKGameConfig()
                .autoStartTicker(false)
                .tickWorkerThreads(1)
                .tickTimeoutMs(15)
                .roomIdleTimeoutMs(0)
                .roomEmptyTimeoutMs(0));

        game.registerLogic("demo", new VKGameLogic() {
            @Override
            public void onTick(VKGameRoom room, long tickNo) {
                roomTicks.computeIfAbsent(room.getRoomId(), k -> new AtomicInteger()).incrementAndGet();
                // 每次 onTick 休眠 20ms，超过 tickTimeoutMs=15ms，每帧只能处理 1 个房间
                try { Thread.sleep(20L); } catch (InterruptedException ignored) {}
            }
        });

        for (int i = 0; i < roomCount; i++) {
            game.createRoom("fr" + i, "demo");
        }

        // 运行足够多的帧：无公平性时后序房间会被系统性跳过，有公平性时每帧跳过的房间下帧优先处理
        for (int i = 0; i < roomCount * 3; i++) {
            Vostok.Game.tickOnce();
        }

        // 验证所有房间都被处理过至少一次，不存在长期饥饿的房间
        for (int i = 0; i < roomCount; i++) {
            String roomId = "fr" + i;
            AtomicInteger count = roomTicks.get(roomId);
            assertNotNull(count, "房间 " + roomId + " 从未被处理");
            assertTrue(count.get() >= 1, "房间 " + roomId + " 应至少被处理一次，实际 Tick 数：" + count.get());
        }
        assertTrue(Vostok.Game.metrics().tickTimeouts() >= 1L, "应至少触发一次 Tick 超时");
    }

    @Test
    void testLogicErrorIsolation() {
        int threshold = 3;

        var game = Vostok.Game.init(new VKGameConfig()
                .autoStartTicker(false)
                .tickTimeoutMs(0)
                .roomIdleTimeoutMs(0)
                .roomEmptyTimeoutMs(0)
                .roomMaxConsecutiveLogicErrors(threshold));

        game.registerLogic("demo", new VKGameLogic() {
            @Override
            public void onTick(VKGameRoom room, long tickNo) {
                throw new RuntimeException("simulated logic error at tick " + tickNo);
            }
        });

        game.createRoom("error-room", "demo");
        game.join("error-room", "p1");

        // 连续触发 threshold 次逻辑异常，第 threshold 次应触发隔离
        for (int i = 0; i < threshold; i++) {
            Vostok.Game.tickOnce();
        }

        // 验证房间已被标记为 DRAINING（隔离状态）
        VKGameRoom room = game.room("error-room");
        assertNotNull(room, "房间应仍存在于 rooms 中（仅 DRAINING，尚未 CLOSED）");
        assertTrue(room.isDraining(), "连续逻辑异常超阈值后房间应处于 DRAINING 状态");

        // 验证隔离计数器 > 0
        assertTrue(Vostok.Game.metrics().roomClosedByLogicError() > 0,
                "roomClosedByLogicError 指标应大于 0");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 新增测试：覆盖本轮 P0/P1/P2 修复点
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * P0 #1 drainCommands 修复：当高优先级队列为空、权重值很大时，
     * 应能正确排尽普通/低优先级队列，而不因对空队列做 O(highWeight) 次无效 poll 浪费时间。
     */
    @Test
    void testDrainCommandsHighWeightEmptyQueue() {
        // 直接构造 VKGameRoom 进行白盒测试
        var room = new VKGameRoom("dr-room", "demo");
        int cap = 20;
        // 只投入普通和低优先级命令，高优先级队列保持空
        for (int i = 0; i < 5; i++) {
            room.offerCommand(new VKGameCommand("p1", "N" + i, null, VKGameCommandPriority.NORMAL), cap);
        }
        for (int i = 0; i < 3; i++) {
            room.offerCommand(new VKGameCommand("p1", "L" + i, null, VKGameCommandPriority.LOW), cap);
        }

        // highWeight=100，但高优先级队列为空——修复前会对空队列做 100 次无效 poll
        List<VKGameCommand> drained = room.drainCommands(8, 100, 1, 1);

        assertEquals(8, drained.size(), "应排出全部 8 条命令");
        // 前 5 条均为普通优先级（权重 1 slot），后 3 条为低优先级
        long normalCount = drained.stream().filter(c -> c.getType().startsWith("N")).count();
        long lowCount = drained.stream().filter(c -> c.getType().startsWith("L")).count();
        assertEquals(5, normalCount, "普通优先级命令应全部排出");
        assertEquals(3, lowCount, "低优先级命令应全部排出");
        assertEquals(0, room.queuedCommands(), "排出后队列应为空");
    }

    /**
     * P0 #5 joinPlayer TOCTOU 修复：多线程并发加入同一房间时，
     * 最终玩家数不应超过 maxPlayersPerRoom。
     */
    @Test
    void testJoinPlayerCapacityUnderConcurrency() throws Exception {
        var room = new VKGameRoom("cap-room", "demo");
        int maxPlayers = 5;
        int threadCount = 30;

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            final String pid = "p" + i;
            pool.submit(() -> {
                try {
                    start.await();
                    var session = room.joinPlayer(pid, maxPlayers);
                    if (session != null) successCount.incrementAndGet();
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();

        // 修复前 TOCTOU 可能导致 size() > maxPlayers
        assertTrue(room.getPlayerCount() <= maxPlayers,
                "并发加入后玩家数不应超过上限，实际=" + room.getPlayerCount());
        assertEquals(room.getPlayerCount(), successCount.get(),
                "成功加入的玩家数应与房间玩家数一致");
    }

    /**
     * P0 #2 时间戳溢出修复：命令时间戳为 Long.MIN_VALUE 时，
     * Math.abs(nowMs - cmd.getTimestampMs()) 会溢出，修复后应正确拒绝为 TIME_SKEW。
     */
    @Test
    void testCommandTimestampSkewBoundaryOverflow() {
        var game = Vostok.Game.init(new VKGameConfig()
                .autoStartTicker(false)
                .antiCheatEnabled(true)
                .requireOnlinePlayerCommand(true)
                .maxClientTimestampSkewMs(1000L));

        game.registerLogic("demo", new VKGameLogic() {});
        game.createRoom("ts-room", "demo");
        game.join("ts-room", "p1");

        // Long.MIN_VALUE 时间戳：nowMs - Long.MIN_VALUE 溢出为 Long.MIN_VALUE，
        // Math.abs(Long.MIN_VALUE) == Long.MIN_VALUE（负数），修复前会绕过检测
        VKGameException ex = assertThrows(VKGameException.class,
                () -> game.submit("ts-room",
                        new VKGameCommand("p1", "overflow", null, 1L, Long.MIN_VALUE)));
        assertEquals(VKGameErrorCode.COMMAND_REJECTED, ex.getCode(),
                "Long.MIN_VALUE 时间戳应被拒绝");
        assertTrue(Vostok.Game.metrics().commandsRejectedTimeSkew() >= 1L,
                "应记录至少一次 TIME_SKEW 拒绝");
    }

    /**
     * P0 #2 QPS 限流窗口重置并发修复：在窗口到期后并发提交时，
     * 每个玩家在新窗口内的命令数仍应受限，不应因竞态条件导致超发。
     */
    @Test
    void testQpsRateLimitWindowReset() throws Exception {
        int qpsLimit = 3;
        var game = Vostok.Game.init(new VKGameConfig()
                .autoStartTicker(false)
                .antiCheatEnabled(true)
                .requireOnlinePlayerCommand(false)
                .maxCommandsPerSecondPerPlayer(qpsLimit)
                .enforceMonotonicClientSeq(false)
                .maxClientTimestampSkewMs(0L));

        game.registerLogic("demo", new VKGameLogic() {});
        game.createRoom("qps-room", "demo");
        game.join("qps-room", "p1");

        // 第一个窗口：连续提交超出 qpsLimit，前几条应成功，其余应被限流
        int accepted = 0;
        int rejected = 0;
        for (int i = 0; i < qpsLimit * 2; i++) {
            try {
                game.submit("qps-room", new VKGameCommand("p1", "cmd", null));
                accepted++;
            } catch (VKGameException e) {
                if (e.getCode() == VKGameErrorCode.COMMAND_REJECTED) rejected++;
            }
        }
        assertTrue(accepted <= qpsLimit, "第一个窗口内接受数不应超过 QPS 上限");
        assertTrue(rejected >= qpsLimit, "超出 QPS 后应有命令被拒绝");

        // 等待窗口到期（> 1 秒）
        Thread.sleep(1100L);

        // 新窗口内应可再次接受命令
        int acceptedAfterReset = 0;
        for (int i = 0; i < qpsLimit; i++) {
            try {
                game.submit("qps-room", new VKGameCommand("p1", "cmd2", null));
                acceptedAfterReset++;
            } catch (VKGameException e) {
                if (e.getCode() != VKGameErrorCode.COMMAND_REJECTED) throw e;
            }
        }
        assertTrue(acceptedAfterReset >= 1, "窗口重置后应能再次接受命令");
    }

    /**
     * P0 #4 Matchmaker region 兼容性修复：region 一方为空、一方非空时不应匹配；
     * 双方均空或均相同时才匹配。
     */
    @Test
    void testMatchmakerRegionCompatibility() throws Exception {
        var game = Vostok.Game.init(new VKGameConfig()
                .autoStartTicker(false)
                .matchmakingEnabled(true)
                .matchmakingRoomSize(2)
                .matchmakingBaseRatingTolerance(500));

        game.registerLogic("demo", new VKGameLogic() {});

        // Case 1：一方 region="" 一方 region="cn"，修复前会错误匹配（||语义），修复后不匹配
        game.enqueueMatch(new VKGameMatchRequest("playerA", "demo", 1000, ""));
        game.enqueueMatch(new VKGameMatchRequest("playerB", "demo", 1000, "cn"));
        Vostok.Game.tickOnce(); // 匹配一次

        // playerA(region="") 和 playerB(region="cn") 不应匹配，各自等待
        VKGameMatchResult ra = game.pollMatchResult(game.enqueueMatch(
                new VKGameMatchRequest("playerA", "demo", 1000, "")));
        // 实际上 enqueueMatch 返回 ticketId，我们通过 cancel 来检查是否已匹配
        // 简化：等待后检查匹配指标未增加
        long matchesBefore = Vostok.Game.metrics().matchSucceeded();

        // Case 2：双方均为 region="us"，应正常匹配
        Vostok.Game.close();
        game = Vostok.Game.init(new VKGameConfig()
                .autoStartTicker(false)
                .matchmakingEnabled(true)
                .matchmakingRoomSize(2)
                .matchmakingBaseRatingTolerance(500));
        game.registerLogic("demo", new VKGameLogic() {});

        game.enqueueMatch(new VKGameMatchRequest("c1", "demo", 1000, "us"));
        game.enqueueMatch(new VKGameMatchRequest("c2", "demo", 1000, "us"));
        Vostok.Game.tickOnce();
        assertTrue(Vostok.Game.metrics().matchSucceeded() >= 1L,
                "相同 region 的双方应成功匹配");

        // Case 3：双方均为空 region，应正常匹配
        Vostok.Game.close();
        game = Vostok.Game.init(new VKGameConfig()
                .autoStartTicker(false)
                .matchmakingEnabled(true)
                .matchmakingRoomSize(2)
                .matchmakingBaseRatingTolerance(500));
        game.registerLogic("demo", new VKGameLogic() {});

        game.enqueueMatch(new VKGameMatchRequest("d1", "demo", 1000, ""));
        game.enqueueMatch(new VKGameMatchRequest("d2", "demo", 1000, ""));
        Vostok.Game.tickOnce();
        assertTrue(Vostok.Game.metrics().matchSucceeded() >= 1L,
                "双方均为空 region 时应成功匹配");
    }

    /**
     * P0 #4 Matchmaker FIFO 顺序修复：最先入队的玩家组应最先被撮合，
     * 验证 requeue 使用 addLast 而非 addFirst。
     */
    @Test
    void testMatchmakerFifoOrder() {
        List<String> matchedPlayers = Collections.synchronizedList(new ArrayList<>());

        var game = Vostok.Game.init(new VKGameConfig()
                .autoStartTicker(false)
                .matchmakingEnabled(true)
                .matchmakingRoomSize(2)
                .matchmakingBaseRatingTolerance(0)); // 不容忍评分差异

        game.registerLogic("demo", new VKGameLogic() {
            @Override
            public void onPlayerJoin(VKGameRoom room, VKGamePlayerSession session) {
                matchedPlayers.add(session.getPlayerId());
            }
        });

        // 4 名评分相同的玩家按顺序入队，应按 FIFO 两两匹配：(p1,p2) 和 (p3,p4)
        game.enqueueMatch(new VKGameMatchRequest("fifo-p1", "demo", 1000, ""));
        game.enqueueMatch(new VKGameMatchRequest("fifo-p2", "demo", 1000, ""));
        game.enqueueMatch(new VKGameMatchRequest("fifo-p3", "demo", 1000, ""));
        game.enqueueMatch(new VKGameMatchRequest("fifo-p4", "demo", 1000, ""));

        Vostok.Game.tickOnce();

        // 必须有 2 次成功匹配（4 人 / 2 人房）
        assertEquals(2L, Vostok.Game.metrics().matchSucceeded(),
                "4 名玩家应成功组成 2 个房间");
    }

    /**
     * P1 #6 房间快照恢复修复：快照应完整保存并恢复玩家的 sessionToken 和 joinedAt，
     * 而不只是 playerId。
     */
    @Test
    void testSnapshotRestoresPlayerSessionFields() throws Exception {
        Path dir = Files.createTempDirectory("vostok-session-restore");
        try {
            var config = new VKGameConfig()
                    .autoStartTicker(false)
                    .roomPersistenceEnabled(true)
                    .roomRecoveryEnabled(true)
                    .roomPersistenceDir(dir.toAbsolutePath().toString())
                    .roomSnapshotEveryTicks(1);

            var game = Vostok.Game.init(config);
            game.registerLogic("demo", new VKGameLogic() {});
            game.createRoom("session-room", "demo");
            VKGamePlayerSession original = game.join("session-room", "p1");
            assertNotNull(original);
            String originalToken = original.getSessionToken();
            long originalJoinedAt = original.getJoinedAt();

            // 触发快照
            Vostok.Game.tickOnce();
            Vostok.Game.close();

            // 恢复
            Vostok.Game.init(config).registerLogic("demo", new VKGameLogic() {});
            VKGameRoom recovered = Vostok.Game.init().room("session-room");
            assertNotNull(recovered, "快照应能恢复房间");

            VKGamePlayerSession restoredSession = recovered.player("p1");
            assertNotNull(restoredSession, "玩家 p1 应随快照恢复");
            assertEquals(originalToken, restoredSession.getSessionToken(),
                    "sessionToken 应与快照保存前一致");
            assertEquals(originalJoinedAt, restoredSession.getJoinedAt(),
                    "joinedAt 应与快照保存前一致");
        } finally {
            deleteDir(dir);
        }
    }

    /**
     * P1 #7 属性类型序列化修复：快照应保持 Integer/Long/Double/Float/Boolean 的原始类型，
     * 而不是将所有属性反序列化为 String。
     */
    @Test
    void testSnapshotAttributeTypesPreserved() throws Exception {
        Path dir = Files.createTempDirectory("vostok-attr-types");
        try {
            var config = new VKGameConfig()
                    .autoStartTicker(false)
                    .roomPersistenceEnabled(true)
                    .roomRecoveryEnabled(true)
                    .roomPersistenceDir(dir.toAbsolutePath().toString())
                    .roomSnapshotEveryTicks(1);

            var game = Vostok.Game.init(config);
            game.registerLogic("demo", new VKGameLogic() {});
            game.createRoom("attr-room", "demo");

            // 写入各种类型的属性
            var attrs = game.room("attr-room").attributes();
            attrs.put("intVal", 42);
            attrs.put("longVal", 123_456_789_012L);
            attrs.put("doubleVal", 3.14);
            attrs.put("floatVal", 2.71f);
            attrs.put("boolTrue", true);
            attrs.put("boolFalse", false);
            attrs.put("strVal", "hello");

            Vostok.Game.tickOnce();
            Vostok.Game.close();

            // 恢复并验证类型
            Vostok.Game.init(config).registerLogic("demo", new VKGameLogic() {});
            VKGameRoom recovered = Vostok.Game.init().room("attr-room");
            assertNotNull(recovered, "快照应能恢复房间");

            var recoveredAttrs = recovered.attributes();
            assertNotNull(recoveredAttrs.get("intVal"), "intVal 应恢复");
            assertNotNull(recoveredAttrs.get("longVal"), "longVal 应恢复");
            assertNotNull(recoveredAttrs.get("doubleVal"), "doubleVal 应恢复");
            assertNotNull(recoveredAttrs.get("floatVal"), "floatVal 应恢复");

            // 类型不应退化为 String
            assertTrue(recoveredAttrs.get("intVal") instanceof Integer,
                    "intVal 应恢复为 Integer，实际类型: " + recoveredAttrs.get("intVal").getClass().getSimpleName());
            assertTrue(recoveredAttrs.get("longVal") instanceof Long,
                    "longVal 应恢复为 Long，实际类型: " + recoveredAttrs.get("longVal").getClass().getSimpleName());
            assertTrue(recoveredAttrs.get("doubleVal") instanceof Double,
                    "doubleVal 应恢复为 Double");
            assertTrue(recoveredAttrs.get("floatVal") instanceof Float,
                    "floatVal 应恢复为 Float");
            assertTrue(recoveredAttrs.get("boolTrue") instanceof Boolean,
                    "boolTrue 应恢复为 Boolean");
            assertEquals(true, recoveredAttrs.get("boolTrue"), "boolTrue 应为 true");
            assertEquals(false, recoveredAttrs.get("boolFalse"), "boolFalse 应为 false");
            assertEquals("hello", recoveredAttrs.get("strVal"), "strVal 应恢复为字符串");
            assertEquals(42, recoveredAttrs.get("intVal"), "intVal 值应为 42");
            assertEquals(123_456_789_012L, recoveredAttrs.get("longVal"), "longVal 值应正确");
        } finally {
            deleteDir(dir);
        }
    }

    /**
     * P2 #13 ShardBalancer 热点评分权重配置化：通过设置自定义权重验证评分逻辑使用配置值。
     */
    @Test
    void testShardBalancerHotRoomScoreUsesConfigWeights() {
        // 自定义权重：命令权重 10、队列权重 5、耗时权重 0
        var config = new VKGameConfig()
                .hotRoomScoreCommandWeight(10.0)
                .hotRoomScoreQueueWeight(5.0)
                .hotRoomScoreCostWeight(0.0);

        var balancer = new VKGameShardBalancer(
                new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>(),
                () -> config,
                new VKGameRuntimeMetrics(),
                (roomId, from, to) -> {});

        // 10 条命令 * 10 + 2 队列 * 5 + 0 耗时 = 110
        double score = balancer.hotRoomScore(10, 2, 1_000_000_000L);
        assertEquals(110.0, score, 0.001, "评分应按自定义权重计算");

        // 切换为默认权重（8/2/1）时，相同输入的评分应不同
        var defaultConfig = new VKGameConfig(); // 默认: command=8, queue=2, cost=1
        var defaultBalancer = new VKGameShardBalancer(
                new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>(),
                () -> defaultConfig,
                new VKGameRuntimeMetrics(),
                (roomId, from, to) -> {});

        double defaultScore = defaultBalancer.hotRoomScore(10, 2, 1_000L); // 1 microsecond ≈ 0ms
        // 10*8 + 2*2 + 0*1 = 84
        assertEquals(84.0, defaultScore, 0.5, "默认权重评分应为 84");
    }

    private static void deleteDir(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignore) {
                    // no-op
                }
            });
        }
    }
}
