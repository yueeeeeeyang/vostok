package yueyang.vostok.terminal.event;

import java.io.IOException;
import java.io.InputStream;

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

        String raw = new String(bytes);
        return switch (raw) {
            case "\u001B[A" -> new VKKeyEvent(VKKey.UP, '\0', raw);
            case "\u001B[B" -> new VKKeyEvent(VKKey.DOWN, '\0', raw);
            case "\u001B[C" -> new VKKeyEvent(VKKey.RIGHT, '\0', raw);
            case "\u001B[D" -> new VKKeyEvent(VKKey.LEFT, '\0', raw);
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
            byte[] seq = new byte[3];
            seq[0] = (byte) first;
            int n1 = in.read();
            if (n1 < 0) {
                return decode(new byte[]{(byte) first});
            }
            seq[1] = (byte) n1;
            int n2 = in.read();
            if (n2 < 0) {
                return decode(new byte[]{seq[0], seq[1]});
            }
            seq[2] = (byte) n2;
            return decode(seq);
        } catch (IOException e) {
            return VKKeyEvent.of(VKKey.UNKNOWN);
        }
    }
}
