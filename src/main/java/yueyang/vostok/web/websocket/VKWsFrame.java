package yueyang.vostok.web.websocket;

public final class VKWsFrame {
    public static final int OPCODE_CONTINUATION = 0x0;
    public static final int OPCODE_TEXT = 0x1;
    public static final int OPCODE_BINARY = 0x2;
    public static final int OPCODE_CLOSE = 0x8;
    public static final int OPCODE_PING = 0x9;
    public static final int OPCODE_PONG = 0xA;

    private final int opcode;
    private final byte[] payload;

    private VKWsFrame(int opcode, byte[] payload) {
        this.opcode = opcode;
        this.payload = payload == null ? new byte[0] : payload;
    }

    public int opcode() {
        return opcode;
    }

    public byte[] payload() {
        return payload;
    }

    public byte[] encode() {
        int len = payload.length;
        int head = 2 + (len >= 126 && len <= 65535 ? 2 : (len > 65535 ? 8 : 0));
        byte[] out = new byte[head + len];
        out[0] = (byte) (0x80 | (opcode & 0x0F));
        int p = 1;
        if (len < 126) {
            out[p++] = (byte) len;
        } else if (len <= 65535) {
            out[p++] = 126;
            out[p++] = (byte) ((len >> 8) & 0xFF);
            out[p++] = (byte) (len & 0xFF);
        } else {
            out[p++] = 127;
            long l = len & 0xFFFFFFFFL;
            for (int i = 7; i >= 0; i--) {
                out[p++] = (byte) ((l >>> (8 * i)) & 0xFF);
            }
        }
        System.arraycopy(payload, 0, out, p, payload.length);
        return out;
    }

    public static VKWsFrame text(byte[] payload) {
        return new VKWsFrame(OPCODE_TEXT, payload);
    }

    public static VKWsFrame binary(byte[] payload) {
        return new VKWsFrame(OPCODE_BINARY, payload);
    }

    public static VKWsFrame pong(byte[] payload) {
        return new VKWsFrame(OPCODE_PONG, payload);
    }

    public static VKWsFrame ping(byte[] payload) {
        return new VKWsFrame(OPCODE_PING, payload);
    }

    public static VKWsFrame close(byte[] payload) {
        return new VKWsFrame(OPCODE_CLOSE, payload);
    }
}
