package yueyang.vostok.terminal.event;

public record VKKeyEvent(VKKey key, char ch, String raw) {
    public static VKKeyEvent of(VKKey key) {
        return new VKKeyEvent(key, '\0', "");
    }

    public static VKKeyEvent ch(char c) {
        return new VKKeyEvent(VKKey.CHAR, c, String.valueOf(c));
    }
}
