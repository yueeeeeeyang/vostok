package yueyang.vostok.web.websocket;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class VKWebSocketSession {
    private final String id;
    private final String path;
    private final String traceId;
    private final InetSocketAddress remoteAddress;
    private final Supplier<Boolean> openSupplier;
    private final Consumer<VKWsFrame> sender;
    private final Runnable closeAction;

    public VKWebSocketSession(String id,
                              String path,
                              String traceId,
                              InetSocketAddress remoteAddress,
                              Supplier<Boolean> openSupplier,
                              Consumer<VKWsFrame> sender,
                              Runnable closeAction) {
        this.id = id;
        this.path = path;
        this.traceId = traceId;
        this.remoteAddress = remoteAddress;
        this.openSupplier = openSupplier;
        this.sender = sender;
        this.closeAction = closeAction;
    }

    public String id() {
        return id;
    }

    public String path() {
        return path;
    }

    public String traceId() {
        return traceId;
    }

    public InetSocketAddress remoteAddress() {
        return remoteAddress;
    }

    public boolean isOpen() {
        return openSupplier.get();
    }

    public void sendText(String msg) {
        byte[] data = msg == null ? new byte[0] : msg.getBytes(StandardCharsets.UTF_8);
        sender.accept(VKWsFrame.text(data));
    }

    public void sendBinary(byte[] data) {
        sender.accept(VKWsFrame.binary(data == null ? new byte[0] : data));
    }

    public void ping(byte[] payload) {
        sender.accept(VKWsFrame.ping(payload == null ? new byte[0] : payload));
    }

    public void close() {
        close(1000, "");
    }

    public void close(int code, String reason) {
        byte[] reasonBytes = reason == null ? new byte[0] : reason.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[2 + reasonBytes.length];
        payload[0] = (byte) ((code >> 8) & 0xFF);
        payload[1] = (byte) (code & 0xFF);
        System.arraycopy(reasonBytes, 0, payload, 2, reasonBytes.length);
        sender.accept(VKWsFrame.close(payload));
        closeAction.run();
    }
}
