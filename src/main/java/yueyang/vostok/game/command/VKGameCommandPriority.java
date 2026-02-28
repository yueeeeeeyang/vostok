package yueyang.vostok.game.command;

/**
 * 房间内命令优先级。
 * HIGH 通常用于输入/战斗关键帧，LOW 用于可延迟的非关键事件。
 */
public enum VKGameCommandPriority {
    HIGH,
    NORMAL,
    LOW
}
