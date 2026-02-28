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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

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
