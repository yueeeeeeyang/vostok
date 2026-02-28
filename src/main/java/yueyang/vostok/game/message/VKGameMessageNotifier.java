package yueyang.vostok.game.message;

@FunctionalInterface
public interface VKGameMessageNotifier {
    void onMessage(VKGameMessage message);
}
