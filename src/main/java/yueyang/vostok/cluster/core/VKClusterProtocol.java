package yueyang.vostok.cluster.core;

import yueyang.vostok.cluster.VKClusterNodeStatus;
import yueyang.vostok.cluster.exception.VKClusterErrorCode;
import yueyang.vostok.cluster.exception.VKClusterException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Cluster 二进制帧协议。 */
final class VKClusterProtocol {
    static final int VERSION = 1;
    static final int HELLO = 1;
    static final int HELLO_ACK = 2;
    static final int PING = 3;
    static final int PONG = 4;
    static final int MEMBERSHIP_SYNC = 5;
    static final int BROADCAST = 6;
    static final int ACK = 7;
    static final int LEAVE = 8;

    private VKClusterProtocol() {
    }

    static Frame hello(String clusterName, String nodeId, String host, int port,
                       long incarnation, Map<String, String> labels,
                       String nonce, String signature) {
        return new Frame(HELLO, clusterName, nodeId, host, port, incarnation,
                labels, nonce, signature, null, null, null, false, 0L, null, 0L, null, 0L);
    }

    static Frame helloAck(String clusterName, String nodeId, String host, int port,
                          long incarnation, Map<String, String> labels,
                          String nonce, String signature,
                          List<MemberRecord> members) {
        return new Frame(HELLO_ACK, clusterName, nodeId, host, port, incarnation,
                labels, nonce, signature, members, null, null, false, 0L, null, 0L, null, 0L);
    }

    static Frame ping(long timestamp) {
        return new Frame(PING, null, null, null, 0, 0, null, null, null,
                null, null, null, false, 0L, null, timestamp, null, 0L);
    }

    static Frame pong(long timestamp) {
        return new Frame(PONG, null, null, null, 0, 0, null, null, null,
                null, null, null, false, 0L, null, timestamp, null, 0L);
    }

    static Frame membershipSync(List<MemberRecord> members) {
        return new Frame(MEMBERSHIP_SYNC, null, null, null, 0, 0, null, null, null,
                members, null, null, false, 0L, null, 0L, null, 0L);
    }

    static Frame broadcast(String messageId, String topic, String fromNodeId,
                           boolean reliable, long sentAt, byte[] payload) {
        return new Frame(BROADCAST, null, null, null, 0, 0, null, null, null,
                null, messageId, topic, reliable, sentAt, payload, 0L, null, 0L);
    }

    static Frame ack(String messageId) {
        return new Frame(ACK, null, null, null, 0, 0, null, null, null,
                null, messageId, null, false, 0L, null, 0L, null, 0L);
    }

    static Frame leave(String nodeId, long incarnation, long timestamp) {
        return new Frame(LEAVE, null, nodeId, null, 0, incarnation, null, null, null,
                null, null, null, false, 0L, null, timestamp, null, 0L);
    }

    static byte[] encode(Frame frame) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(256);
            DataOutputStream out = new DataOutputStream(baos);
            out.writeInt(VERSION);
            out.writeInt(frame.type());
            switch (frame.type()) {
                case HELLO, HELLO_ACK -> {
                    writeString(out, frame.clusterName());
                    writeString(out, frame.nodeId());
                    writeString(out, frame.host());
                    out.writeInt(frame.port());
                    out.writeLong(frame.incarnation());
                    writeMap(out, frame.labels());
                    writeString(out, frame.nonce());
                    writeString(out, frame.signature());
                    if (frame.type() == HELLO_ACK) {
                        writeMembers(out, frame.members());
                    }
                }
                case PING, PONG -> out.writeLong(frame.timestamp());
                case MEMBERSHIP_SYNC -> writeMembers(out, frame.members());
                case BROADCAST -> {
                    writeString(out, frame.messageId());
                    writeString(out, frame.topic());
                    writeString(out, frame.fromNodeId());
                    out.writeBoolean(frame.reliable());
                    out.writeLong(frame.sentAt());
                    byte[] payload = frame.payload() == null ? new byte[0] : frame.payload();
                    out.writeInt(payload.length);
                    out.write(payload);
                }
                case ACK -> writeString(out, frame.messageId());
                case LEAVE -> {
                    writeString(out, frame.nodeId());
                    out.writeLong(frame.incarnation());
                    out.writeLong(frame.timestamp());
                }
                default -> throw new VKClusterException(VKClusterErrorCode.PROTOCOL_ERROR, "Unknown cluster frame type: " + frame.type());
            }
            out.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new VKClusterException(VKClusterErrorCode.IO_ERROR, "Failed to encode cluster frame", e);
        }
    }

    static Frame decode(byte[] bytes) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            int version = in.readInt();
            if (version != VERSION) {
                throw new VKClusterException(VKClusterErrorCode.PROTOCOL_ERROR,
                        "Unsupported cluster protocol version: " + version);
            }
            int type = in.readInt();
            return switch (type) {
                case HELLO -> hello(
                        readString(in), readString(in), readString(in), in.readInt(), in.readLong(),
                        readMap(in), readString(in), readString(in));
                case HELLO_ACK -> helloAck(
                        readString(in), readString(in), readString(in), in.readInt(), in.readLong(),
                        readMap(in), readString(in), readString(in), readMembers(in));
                case PING -> ping(in.readLong());
                case PONG -> pong(in.readLong());
                case MEMBERSHIP_SYNC -> membershipSync(readMembers(in));
                case BROADCAST -> {
                    String messageId = readString(in);
                    String topic = readString(in);
                    String fromNodeId = readString(in);
                    boolean reliable = in.readBoolean();
                    long sentAt = in.readLong();
                    int payloadLen = in.readInt();
                    byte[] payload = new byte[payloadLen];
                    in.readFully(payload);
                    yield broadcast(messageId, topic, fromNodeId, reliable, sentAt, payload);
                }
                case ACK -> ack(readString(in));
                case LEAVE -> leave(readString(in), in.readLong(), in.readLong());
                default -> throw new VKClusterException(VKClusterErrorCode.PROTOCOL_ERROR,
                        "Unknown cluster frame type: " + type);
            };
        } catch (IOException e) {
            throw new VKClusterException(VKClusterErrorCode.PROTOCOL_ERROR, "Failed to decode cluster frame", e);
        }
    }

    private static void writeMembers(DataOutputStream out, List<MemberRecord> members) throws IOException {
        List<MemberRecord> safeMembers = members == null ? List.of() : members;
        out.writeInt(safeMembers.size());
        for (MemberRecord member : safeMembers) {
            writeString(out, member.nodeId());
            writeString(out, member.clusterName());
            writeString(out, member.host());
            out.writeInt(member.port());
            out.writeInt(member.status().ordinal());
            writeMap(out, member.labels());
            out.writeLong(member.incarnation());
            out.writeLong(member.discoveredAt());
            out.writeLong(member.lastSeenAt());
        }
    }

    private static List<MemberRecord> readMembers(DataInputStream in) throws IOException {
        int size = in.readInt();
        List<MemberRecord> members = new ArrayList<>(Math.max(0, size));
        for (int i = 0; i < size; i++) {
            String nodeId = readString(in);
            String clusterName = readString(in);
            String host = readString(in);
            int port = in.readInt();
            int statusOrdinal = in.readInt();
            Map<String, String> labels = readMap(in);
            long incarnation = in.readLong();
            long discoveredAt = in.readLong();
            long lastSeenAt = in.readLong();
            VKClusterNodeStatus status = VKClusterNodeStatus.values()[statusOrdinal];
            members.add(new MemberRecord(nodeId, clusterName, host, port, status, labels,
                    incarnation, discoveredAt, lastSeenAt));
        }
        return members;
    }

    private static void writeMap(DataOutputStream out, Map<String, String> values) throws IOException {
        Map<String, String> safeValues = values == null ? Map.of() : values;
        out.writeInt(safeValues.size());
        for (Map.Entry<String, String> entry : safeValues.entrySet()) {
            writeString(out, entry.getKey());
            writeString(out, entry.getValue());
        }
    }

    private static Map<String, String> readMap(DataInputStream in) throws IOException {
        int size = in.readInt();
        Map<String, String> map = new LinkedHashMap<>(Math.max(1, size));
        for (int i = 0; i < size; i++) {
            map.put(readString(in), readString(in));
        }
        return map;
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len < 0) {
            throw new IOException("Negative string length");
        }
        if (len == 0) {
            return "";
        }
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    record Frame(int type,
                 String clusterName,
                 String nodeId,
                 String host,
                 int port,
                 long incarnation,
                 Map<String, String> labels,
                 String nonce,
                 String signature,
                 List<MemberRecord> members,
                 String messageId,
                 String topic,
                 boolean reliable,
                 long sentAt,
                 byte[] payload,
                 long timestamp,
                 String fromNodeId,
                 long reserved) {
    }

    record MemberRecord(String nodeId,
                        String clusterName,
                        String host,
                        int port,
                        VKClusterNodeStatus status,
                        Map<String, String> labels,
                        long incarnation,
                        long discoveredAt,
                        long lastSeenAt) {
    }
}
