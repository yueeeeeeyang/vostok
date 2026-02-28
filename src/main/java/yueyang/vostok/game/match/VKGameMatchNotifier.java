package yueyang.vostok.game.match;

@FunctionalInterface
public interface VKGameMatchNotifier {
    void onMatchFound(VKGameMatchResult result);
}
