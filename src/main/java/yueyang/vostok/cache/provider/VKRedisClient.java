package yueyang.vostok.cache.provider;

import yueyang.vostok.cache.VKCacheConfig;
import yueyang.vostok.cache.exception.VKCacheErrorCode;
import yueyang.vostok.cache.exception.VKCacheException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VKRedisClient implements VKCacheClient {
    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public VKRedisClient(String host, int port, VKCacheConfig config) {
        try {
            this.socket = new Socket();
            this.socket.connect(new InetSocketAddress(host, port), Math.max(100, config.getConnectTimeoutMs()));
            this.socket.setSoTimeout(Math.max(100, config.getReadTimeoutMs()));
            this.in = new BufferedInputStream(socket.getInputStream());
            this.out = new BufferedOutputStream(socket.getOutputStream());
            authAndSelect(config);
        } catch (IOException e) {
            throw new VKCacheException(VKCacheErrorCode.CONNECTION_ERROR,
                    "Failed to connect redis " + host + ":" + port, e);
        }
    }

    @Override
    public byte[] get(String key) {
        Object resp = send(command("GET", key));
        if (resp == null) {
            return null;
        }
        return (byte[]) resp;
    }

    @Override
    public void set(String key, byte[] value, long ttlMs) {
        if (ttlMs > 0) {
            send(command("SET", key, value, "PX", String.valueOf(ttlMs)));
        } else {
            send(command("SET", key, value));
        }
    }

    @Override
    public long del(String... keys) {
        if (keys == null || keys.length == 0) {
            return 0;
        }
        List<byte[]> args = new ArrayList<>();
        args.add(bytes("DEL"));
        for (String key : keys) {
            args.add(bytes(key));
        }
        Object resp = send(args);
        return toLong(resp);
    }

    @Override
    public boolean exists(String key) {
        Object resp = send(command("EXISTS", key));
        return toLong(resp) > 0;
    }

    @Override
    public boolean expire(String key, long ttlMs) {
        if (ttlMs <= 0) {
            return false;
        }
        Object resp = send(command("PEXPIRE", key, String.valueOf(ttlMs)));
        return toLong(resp) > 0;
    }

    @Override
    public long incrBy(String key, long delta) {
        Object resp = send(command("INCRBY", key, String.valueOf(delta)));
        return toLong(resp);
    }

    @Override
    public List<byte[]> mget(String... keys) {
        List<byte[]> args = new ArrayList<>();
        args.add(bytes("MGET"));
        if (keys != null) {
            for (String key : keys) {
                args.add(bytes(key));
            }
        }
        Object resp = send(args);
        if (!(resp instanceof List<?> list)) {
            return List.of();
        }
        List<byte[]> out = new ArrayList<>(list.size());
        for (Object item : list) {
            out.add(item == null ? null : (byte[]) item);
        }
        return out;
    }

    @Override
    public void mset(Map<String, byte[]> kv) {
        if (kv == null || kv.isEmpty()) {
            return;
        }
        List<byte[]> args = new ArrayList<>();
        args.add(bytes("MSET"));
        for (Map.Entry<String, byte[]> e : kv.entrySet()) {
            args.add(bytes(e.getKey()));
            args.add(e.getValue());
        }
        send(args);
    }

    public boolean ping() {
        Object resp = send(command("PING"));
        if (resp instanceof String s) {
            return "PONG".equalsIgnoreCase(s);
        }
        return false;
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignore) {
        }
    }

    private void authAndSelect(VKCacheConfig config) {
        if (config.getPassword() != null && !config.getPassword().isBlank()) {
            if (config.getUsername() != null && !config.getUsername().isBlank()) {
                send(command("AUTH", config.getUsername(), config.getPassword()));
            } else {
                send(command("AUTH", config.getPassword()));
            }
        }
        if (config.getDatabase() > 0) {
            send(command("SELECT", String.valueOf(config.getDatabase())));
        }
    }

    private List<byte[]> command(Object... args) {
        List<byte[]> out = new ArrayList<>(args.length);
        for (Object arg : args) {
            if (arg == null) {
                out.add(new byte[0]);
                continue;
            }
            if (arg instanceof byte[] b) {
                out.add(b);
                continue;
            }
            out.add(bytes(String.valueOf(arg)));
        }
        return out;
    }

    private byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private synchronized Object send(List<byte[]> args) {
        try {
            writeCommand(args);
            out.flush();
            return readResp();
        } catch (IOException e) {
            throw new VKCacheException(VKCacheErrorCode.CONNECTION_ERROR,
                    "Redis command failed: " + e.getMessage(), e);
        }
    }

    private void writeCommand(List<byte[]> args) throws IOException {
        out.write(('*'));
        out.write(String.valueOf(args.size()).getBytes(StandardCharsets.UTF_8));
        writeCrlf();
        for (byte[] arg : args) {
            byte[] safe = arg == null ? new byte[0] : arg;
            out.write('$');
            out.write(String.valueOf(safe.length).getBytes(StandardCharsets.UTF_8));
            writeCrlf();
            out.write(safe);
            writeCrlf();
        }
    }

    private Object readResp() throws IOException {
        int prefix = in.read();
        if (prefix < 0) {
            throw new IOException("Redis connection closed");
        }
        return switch (prefix) {
            case '+' -> readSimpleString();
            case '-' -> throw new VKCacheException(VKCacheErrorCode.COMMAND_ERROR, readSimpleString());
            case ':' -> readLong();
            case '$' -> readBulkString();
            case '*' -> readArray();
            default -> throw new IOException("Unsupported RESP type: " + (char) prefix);
        };
    }

    private String readSimpleString() throws IOException {
        return new String(readLineBytes(), StandardCharsets.UTF_8);
    }

    private long readLong() throws IOException {
        return Long.parseLong(new String(readLineBytes(), StandardCharsets.UTF_8));
    }

    private byte[] readBulkString() throws IOException {
        int len = Integer.parseInt(new String(readLineBytes(), StandardCharsets.UTF_8));
        if (len < 0) {
            return null;
        }
        byte[] data = in.readNBytes(len);
        readCrLfStrict();
        return data;
    }

    private List<Object> readArray() throws IOException {
        int count = Integer.parseInt(new String(readLineBytes(), StandardCharsets.UTF_8));
        if (count < 0) {
            return null;
        }
        List<Object> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(readResp());
        }
        return out;
    }

    private byte[] readLineBytes() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(64);
        while (true) {
            int b = in.read();
            if (b < 0) {
                throw new IOException("Unexpected EOF");
            }
            if (b == '\r') {
                int n = in.read();
                if (n != '\n') {
                    throw new IOException("Invalid line ending");
                }
                break;
            }
            bos.write(b);
        }
        return bos.toByteArray();
    }

    private void readCrLfStrict() throws IOException {
        int c1 = in.read();
        int c2 = in.read();
        if (c1 != '\r' || c2 != '\n') {
            throw new IOException("Invalid CRLF");
        }
    }

    private void writeCrlf() throws IOException {
        out.write('\r');
        out.write('\n');
    }

    private long toLong(Object resp) {
        if (resp instanceof Number n) {
            return n.longValue();
        }
        if (resp instanceof byte[] b) {
            return Long.parseLong(new String(b, StandardCharsets.UTF_8));
        }
        if (resp instanceof String s) {
            return Long.parseLong(s);
        }
        return 0L;
    }
}
