package yueyang.vostok.web.websocket;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private final VKWsRegistry registry;
    private final ConcurrentHashMap<String, Object> attributes;

    public VKWebSocketSession(String id,
                              String path,
                              String traceId,
                              InetSocketAddress remoteAddress,
                              Supplier<Boolean> openSupplier,
                              Consumer<VKWsFrame> sender,
                              Runnable closeAction,
                              VKWsRegistry registry,
                              Map<String, Object> attributes) {
        this.id = id;
        this.path = path;
        this.traceId = traceId;
        this.remoteAddress = remoteAddress;
        this.openSupplier = openSupplier;
        this.sender = sender;
        this.closeAction = closeAction;
        this.registry = registry;
        this.attributes = new ConcurrentHashMap<>();
        if (attributes != null && !attributes.isEmpty()) {
            this.attributes.putAll(attributes);
        }
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

    public Map<String, Object> attributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public Object getAttribute(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return attributes.get(key);
    }

    public <T> T getAttribute(String key, Class<T> type) {
        if (type == null) {
            return null;
        }
        Object value = getAttribute(key);
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            return null;
        }
        return type.cast(value);
    }

    public VKWebSocketSession setAttribute(String key, Object value) {
        if (key == null || key.isBlank()) {
            return this;
        }
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
        return this;
    }

    public Object removeAttribute(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return attributes.remove(key);
    }

    public boolean hasAttribute(String key) {
        return getAttribute(key) != null;
    }

    public boolean joinRoom(String room) {
        return registry != null && registry.joinRoom(path, id, room);
    }

    public boolean leaveRoom(String room) {
        return registry != null && registry.leaveRoom(path, id, room);
    }

    public boolean joinGroup(String group) {
        return registry != null && registry.joinGroup(path, id, group);
    }

    public boolean leaveGroup(String group) {
        return registry != null && registry.leaveGroup(path, id, group);
    }

    public int broadcastRoom(String room, String message) {
        return registry == null ? 0 : registry.broadcastRoomText(path, room, message);
    }

    public int broadcastGroup(String group, String message) {
        return registry == null ? 0 : registry.broadcastGroupText(path, group, message);
    }

    public int broadcastRoomAndGroup(String room, String group, String message) {
        return registry == null ? 0 : registry.broadcastRoomAndGroupText(path, room, group, message);
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
