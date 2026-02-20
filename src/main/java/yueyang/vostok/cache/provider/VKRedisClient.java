package yueyang.vostok.cache.provider;

import yueyang.vostok.cache.VKCacheConfig;
import yueyang.vostok.cache.exception.VKCacheErrorCode;
import yueyang.vostok.cache.exception.VKCacheException;

import javax.net.ssl.SSLSocketFactory;
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class VKRedisClient implements VKCacheClient {
    private final VKRedisTopologyResolver resolver;
    private final VKCacheConfig config;
    private final Map<String, Conn> conns = new ConcurrentHashMap<>();

    public VKRedisClient(VKRedisTopologyResolver resolver, VKCacheConfig config) {
        this.resolver = resolver;
        this.config = config;
    }

    @Override
    public byte[] get(String key) {
        Object resp = send(key, command("GET", key));
        return resp == null ? null : (byte[]) resp;
    }

    @Override
    public void set(String key, byte[] value, long ttlMs) {
        if (ttlMs > 0) {
            send(key, command("SET", key, value, "PX", String.valueOf(ttlMs)));
        } else {
            send(key, command("SET", key, value));
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
        Object resp = send(keys[0], args);
        return toLong(resp);
    }

    @Override
    public boolean exists(String key) {
        return toLong(send(key, command("EXISTS", key))) > 0;
    }

    @Override
    public boolean expire(String key, long ttlMs) {
        if (ttlMs <= 0) {
            return false;
        }
        return toLong(send(key, command("PEXPIRE", key, String.valueOf(ttlMs)))) > 0;
    }

    @Override
    public long incrBy(String key, long delta) {
        return toLong(send(key, command("INCRBY", key, String.valueOf(delta))));
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
        Object resp = send(keys != null && keys.length > 0 ? keys[0] : null, args);
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
        String keyHint = null;
        for (Map.Entry<String, byte[]> e : kv.entrySet()) {
            if (keyHint == null) {
                keyHint = e.getKey();
            }
            args.add(bytes(e.getKey()));
            args.add(e.getValue());
        }
        send(keyHint, args);
    }

    @Override
    public long hset(String key, String field, byte[] value) {
        return toLong(send(key, command("HSET", key, field, value)));
    }

    @Override
    public byte[] hget(String key, String field) {
        Object resp = send(key, command("HGET", key, field));
        return resp == null ? null : (byte[]) resp;
    }

    @Override
    public Map<String, byte[]> hgetAll(String key) {
        Object resp = send(key, command("HGETALL", key));
        if (!(resp instanceof List<?> list)) {
            return Map.of();
        }
        Map<String, byte[]> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < list.size(); i += 2) {
            String field = str(list.get(i));
            out.put(field, (byte[]) list.get(i + 1));
        }
        return out;
    }

    @Override
    public long hdel(String key, String... fields) {
        List<byte[]> args = new ArrayList<>();
        args.add(bytes("HDEL"));
        args.add(bytes(key));
        if (fields != null) {
            for (String field : fields) {
                args.add(bytes(field));
            }
        }
        return toLong(send(key, args));
    }

    @Override
    public long lpush(String key, byte[]... values) {
        List<byte[]> args = new ArrayList<>();
        args.add(bytes("LPUSH"));
        args.add(bytes(key));
        if (values != null) {
            args.addAll(Arrays.asList(values));
        }
        return toLong(send(key, args));
    }

    @Override
    public List<byte[]> lrange(String key, long start, long stop) {
        Object resp = send(key, command("LRANGE", key, String.valueOf(start), String.valueOf(stop)));
        if (!(resp instanceof List<?> list)) {
            return List.of();
        }
        List<byte[]> out = new ArrayList<>();
        for (Object item : list) {
            out.add(item == null ? null : (byte[]) item);
        }
        return out;
    }

    @Override
    public long sadd(String key, byte[]... members) {
        List<byte[]> args = new ArrayList<>();
        args.add(bytes("SADD"));
        args.add(bytes(key));
        if (members != null) {
            args.addAll(Arrays.asList(members));
        }
        return toLong(send(key, args));
    }

    @Override
    public Set<byte[]> smembers(String key) {
        Object resp = send(key, command("SMEMBERS", key));
        if (!(resp instanceof List<?> list)) {
            return Set.of();
        }
        Set<byte[]> out = new LinkedHashSet<>();
        for (Object item : list) {
            if (item instanceof byte[] b) {
                out.add(b);
            }
        }
        return out;
    }

    @Override
    public long zadd(String key, double score, byte[] member) {
        return toLong(send(key, command("ZADD", key, String.valueOf(score), member)));
    }

    @Override
    public List<byte[]> zrange(String key, long start, long stop) {
        Object resp = send(key, command("ZRANGE", key, String.valueOf(start), String.valueOf(stop)));
        if (!(resp instanceof List<?> list)) {
            return List.of();
        }
        List<byte[]> out = new ArrayList<>();
        for (Object item : list) {
            out.add(item == null ? null : (byte[]) item);
        }
        return out;
    }

    @Override
    public List<String> scan(String pattern, int count) {
        String p = pattern == null || pattern.isBlank() ? "*" : pattern;
        int c = Math.max(1, count);
        Object resp = send(null, command("SCAN", "0", "MATCH", p, "COUNT", String.valueOf(c)));
        if (!(resp instanceof List<?> list) || list.size() < 2 || !(list.get(1) instanceof List<?> keys)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object key : keys) {
            out.add(str(key));
        }
        return out;
    }

    @Override
    public boolean ping() {
        try {
            Object resp = send(null, command("PING"));
            return resp instanceof String s && "PONG".equalsIgnoreCase(s);
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public void close() {
        for (Conn conn : conns.values()) {
            conn.closeSilently();
        }
        conns.clear();
    }

    private Object send(String keyHint, List<byte[]> args) {
        int attempts = Math.max(1, config.getReconnectMaxAttempts() + 1);
        RuntimeException last = null;
        for (int i = 0; i < attempts; i++) {
            VKRedisEndpoint endpoint = resolver.choose(keyHint);
            try {
                Conn conn = conn(endpoint);
                maybeHeartbeat(conn, endpoint);
                Object resp = conn.send(args);
                resolver.markSuccess(endpoint);
                return resp;
            } catch (RuntimeException e) {
                last = e;
                resolver.markFailure(endpoint);
                invalidate(endpoint);
                if (i + 1 < attempts) {
                    backoff(i);
                }
            }
        }
        throw last == null
                ? new VKCacheException(VKCacheErrorCode.CONNECTION_ERROR, "Redis command failed")
                : last;
    }

    private Conn conn(VKRedisEndpoint endpoint) {
        return conns.computeIfAbsent(endpoint.key(), key -> Conn.connect(endpoint, config));
    }

    private void invalidate(VKRedisEndpoint endpoint) {
        Conn conn = conns.remove(endpoint.key());
        if (conn != null) {
            conn.closeSilently();
        }
    }

    private void maybeHeartbeat(Conn conn, VKRedisEndpoint endpoint) {
        int interval = Math.max(1000, config.getHeartbeatIntervalMs());
        if (System.currentTimeMillis() - conn.lastPingMs < interval) {
            return;
        }
        try {
            Object resp = conn.send(command("PING"));
            if (!(resp instanceof String s) || !"PONG".equalsIgnoreCase(s)) {
                throw new VKCacheException(VKCacheErrorCode.CONNECTION_ERROR, "Redis ping invalid response");
            }
            conn.lastPingMs = System.currentTimeMillis();
        } catch (RuntimeException e) {
            invalidate(endpoint);
            throw e;
        }
    }

    private void backoff(int attempt) {
        long base = Math.max(1, config.getRetryBackoffBaseMs());
        long max = Math.max(base, config.getRetryBackoffMaxMs());
        long delay = Math.min(max, base << Math.min(8, attempt));
        if (config.isRetryJitterEnabled()) {
            delay += ThreadLocalRandom.current().nextLong(Math.max(1, delay / 2 + 1));
            delay = Math.min(max, delay);
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private List<byte[]> command(Object... args) {
        List<byte[]> out = new ArrayList<>(args.length);
        for (Object arg : args) {
            if (arg == null) {
                out.add(new byte[0]);
            } else if (arg instanceof byte[] b) {
                out.add(b);
            } else {
                out.add(bytes(String.valueOf(arg)));
            }
        }
        return out;
    }

    private byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
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

    private String str(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof byte[] b) {
            return new String(b, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    private static final class Conn {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private volatile long lastPingMs = System.currentTimeMillis();

        private Conn(Socket socket, InputStream in, OutputStream out) {
            this.socket = socket;
            this.in = in;
            this.out = out;
        }

        static Conn connect(VKRedisEndpoint endpoint, VKCacheConfig config) {
            try {
                Socket socket = config.isSsl()
                        ? SSLSocketFactory.getDefault().createSocket()
                        : new Socket();
                socket.connect(new InetSocketAddress(endpoint.host(), endpoint.port()),
                        Math.max(100, config.getConnectTimeoutMs()));
                socket.setSoTimeout(Math.max(100, config.getReadTimeoutMs()));
                InputStream in = new BufferedInputStream(socket.getInputStream());
                OutputStream out = new BufferedOutputStream(socket.getOutputStream());
                Conn conn = new Conn(socket, in, out);
                conn.authAndSelect(config);
                return conn;
            } catch (Exception e) {
                throw new VKCacheException(VKCacheErrorCode.CONNECTION_ERROR,
                        "Failed to connect redis " + endpoint.key(), e);
            }
        }

        synchronized Object send(List<byte[]> args) {
            try {
                writeCommand(args);
                out.flush();
                return readResp();
            } catch (IOException e) {
                closeSilently();
                throw new VKCacheException(VKCacheErrorCode.CONNECTION_ERROR,
                        "Redis command failed: " + e.getMessage(), e);
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
                if (arg instanceof byte[] b) {
                    out.add(b);
                } else {
                    out.add(String.valueOf(arg).getBytes(StandardCharsets.UTF_8));
                }
            }
            return out;
        }

        private void writeCommand(List<byte[]> args) throws IOException {
            out.write('*');
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

        void closeSilently() {
            try {
                socket.close();
            } catch (IOException ignore) {
            }
        }
    }
}
