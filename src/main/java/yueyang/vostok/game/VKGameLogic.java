package yueyang.vostok.game;

import yueyang.vostok.game.command.VKGameCommand;
import yueyang.vostok.game.room.VKGamePlayerSession;
import yueyang.vostok.game.room.VKGameRoom;

public interface VKGameLogic {
    default void onRoomStart(VKGameRoom room) {
    }

    default void onRoomClose(VKGameRoom room) {
    }

    default void onRoomDraining(VKGameRoom room, String reason) {
    }

    default void onPlayerJoin(VKGameRoom room, VKGamePlayerSession player) {
    }

    default void onPlayerLeave(VKGameRoom room, VKGamePlayerSession player) {
    }

    default void onPlayerDisconnect(VKGameRoom room, VKGamePlayerSession player) {
    }

    default void onPlayerReconnect(VKGameRoom room, VKGamePlayerSession player) {
    }

    default void onCommand(VKGameRoom room, VKGameCommand command) {
    }

    default void onTick(VKGameRoom room, long tickNo) {
    }
}
