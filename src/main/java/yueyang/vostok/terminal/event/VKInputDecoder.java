package yueyang.vostok.terminal.event;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes common key sequences.
 */
public final class VKInputDecoder {
    public VKKeyEvent decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return VKKeyEvent.of(VKKey.UNKNOWN);
        }
        if (bytes.length == 1) {
            int b = bytes[0] & 0xFF;
            if (b == 3) {
                return VKKeyEvent.of(VKKey.CTRL_C);
            }
            if (b == 9) {
                return VKKeyEvent.of(VKKey.TAB);
            }
            if (b == 10 || b == 13) {
                return VKKeyEvent.of(VKKey.ENTER);
            }
            if (b == 27) {
                return VKKeyEvent.of(VKKey.ESC);
            }
            if (b == 127 || b == 8) {
                return VKKeyEvent.of(VKKey.BACKSPACE);
            }
            return VKKeyEvent.ch((char) b);
        }

        String raw = new String(bytes, StandardCharsets.UTF_8);
        return switch (raw) {
            case "\u001B[A" -> new VKKeyEvent(VKKey.UP, '\0', raw);
            case "\u001B[B" -> new VKKeyEvent(VKKey.DOWN, '\0', raw);
            case "\u001B[C" -> new VKKeyEvent(VKKey.RIGHT, '\0', raw);
            case "\u001B[D" -> new VKKeyEvent(VKKey.LEFT, '\0', raw);
            case "\u001B[3~" -> new VKKeyEvent(VKKey.DELETE, '\0', raw);
            case "\u001B[H", "\u001B[1~" -> new VKKeyEvent(VKKey.HOME, '\0', raw);
            case "\u001B[F", "\u001B[4~" -> new VKKeyEvent(VKKey.END, '\0', raw);
            case "\u001B[5~" -> new VKKeyEvent(VKKey.PAGE_UP, '\0', raw);
            case "\u001B[6~" -> new VKKeyEvent(VKKey.PAGE_DOWN, '\0', raw);
            default -> new VKKeyEvent(VKKey.UNKNOWN, '\0', raw);
        };
    }

    public VKKeyEvent readEvent(InputStream in) {
        if (in == null) {
            return VKKeyEvent.of(VKKey.UNKNOWN);
        }
        try {
            int first = in.read();
            if (first < 0) {
                return VKKeyEvent.of(VKKey.UNKNOWN);
            }
            if (first != 27) {
                return decode(new byte[]{(byte) first});
            }
            return readEscapeSequence(in, first, 16);
        } catch (IOException e) {
            return VKKeyEvent.of(VKKey.UNKNOWN);
        }
    }

    public VKKeyEvent pollEvent(InputStream in) {
        if (in == null) {
            return null;
        }
        try {
            if (in.available() <= 0) {
                return null;
            }
            int first = in.read();
            if (first < 0) {
                return null;
            }
            if (first != 27) {
                return decode(new byte[]{(byte) first});
            }
            int len = Math.max(2, Math.min(16, in.available() + 1));
            return readEscapeSequence(in, first, len);
        } catch (IOException e) {
            return VKKeyEvent.of(VKKey.UNKNOWN);
        }
    }

    private VKKeyEvent readEscapeSequence(InputStream in, int first, int maxBytes) throws IOException {
        List<Byte> list = new ArrayList<>();
        list.add((byte) first);

        for (int i = 0; i < maxBytes - 1; i++) {
            if (in.available() <= 0) {
                break;
            }
            int next = in.read();
            if (next < 0) {
                break;
            }
            list.add((byte) next);
            if (isEscapeEnd(next)) {
                break;
            }
        }
        byte[] seq = new byte[list.size()];
        for (int i = 0; i < list.size(); i++) {
            seq[i] = list.get(i);
        }
        return decode(seq);
    }

    private boolean isEscapeEnd(int b) {
        return (b >= 'A' && b <= 'Z') || b == '~' || b == '^';
    }
}
