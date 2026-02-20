package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.cache.VKCacheConfig;
import yueyang.vostok.cache.VKCacheProviderType;
import yueyang.vostok.cache.VKRedisMode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class VostokCacheRedisTest {
    @AfterEach
    void tearDown() {
        Vostok.Cache.close();
    }

    @Test
    void testRedisProviderWithFakeServer() throws Exception {
        try (FakeRedisServer server = new FakeRedisServer()) {
            VKCacheConfig cfg = new VKCacheConfig()
                    .providerType(VKCacheProviderType.REDIS)
                    .redisMode(VKRedisMode.SINGLE)
                    .endpoints("127.0.0.1:" + server.port())
                    .maxActive(3)
                    .minIdle(0)
                    .maxWaitMs(300)
                    .connectTimeoutMs(1000)
                    .readTimeoutMs(1000)
                    .heartbeatIntervalMs(200)
                    .reconnectMaxAttempts(2)
                    .testOnBorrow(true)
                    .codec("string")
                    .keyPrefix("app:")
                    .username("u")
                    .password("p");

            Vostok.Cache.init(cfg);
            assertTrue(Vostok.Cache.started());

            Vostok.Cache.set("k1", "v1");
            assertEquals("v1", Vostok.Cache.get("k1"));
            assertTrue(Vostok.Cache.exists("k1"));

            assertEquals(1, Vostok.Cache.incr("n"));
            assertEquals(8, Vostok.Cache.incrBy("n", 7));
            assertEquals(7, Vostok.Cache.decr("n"));

            Vostok.Cache.mset(Map.of("a", "A", "b", "B"));
            assertEquals(Arrays.asList("A", "B", null), Vostok.Cache.mget(String.class, "a", "b", "x"));

            assertEquals(1, Vostok.Cache.hset("h", "f1", "v1"));
            assertEquals("v1", Vostok.Cache.hget("h", "f1", String.class));
            assertEquals(1, Vostok.Cache.hgetAll("h", String.class).size());

            assertEquals(2, Vostok.Cache.lpush("l", "a", "b"));
            assertEquals(List.of("b", "a"), Vostok.Cache.lrange("l", 0, -1, String.class));

            assertEquals(2, Vostok.Cache.sadd("s", "x", "y", "x"));
            assertEquals(Set.of("x", "y"), Vostok.Cache.smembers("s", String.class));

            assertEquals(1, Vostok.Cache.zadd("z", 2, "b"));
            assertEquals(1, Vostok.Cache.zadd("z", 1, "a"));
            assertEquals(List.of("a", "b"), Vostok.Cache.zrange("z", 0, -1, String.class));

            assertFalse(Vostok.Cache.scan("app:*", 100).isEmpty());

            Vostok.Cache.set("ttl", "x", 80);
            assertEquals("x", Vostok.Cache.get("ttl"));
            Thread.sleep(120);
            assertNull(Vostok.Cache.get("ttl"));

            assertEquals(1, Vostok.Cache.delete("k1"));
            assertFalse(Vostok.Cache.exists("k1"));
            assertTrue(server.authCalls.get() > 0);
        }
    }

    @Test
    void testSentinelModeFailover() throws Exception {
        try (FakeRedisServer s1 = new FakeRedisServer(); FakeRedisServer s2 = new FakeRedisServer()) {
            VKCacheConfig cfg = new VKCacheConfig()
                    .providerType(VKCacheProviderType.REDIS)
                    .redisMode(VKRedisMode.SENTINEL)
                    .endpoints("127.0.0.1:" + s1.port(), "127.0.0.1:" + s2.port())
                    .connectTimeoutMs(300)
                    .readTimeoutMs(300)
                    .reconnectMaxAttempts(3)
                    .heartbeatIntervalMs(100)
                    .codec("string");

            Vostok.Cache.init(cfg);
            Vostok.Cache.set("ha", "ok");
            assertEquals("ok", Vostok.Cache.get("ha"));

            s1.close();
            Thread.sleep(50);
            Vostok.Cache.set("ha2", "ok2");
            assertEquals("ok2", Vostok.Cache.get("ha2"));
            assertTrue(s2.commandCount.get() > 0);
        }
    }

    private static final class FakeRedisServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Thread acceptThread;

        private final Map<String, Entry> kv = new ConcurrentHashMap<>();
        private final Map<String, Map<String, byte[]>> hashes = new ConcurrentHashMap<>();
        private final Map<String, List<byte[]>> lists = new ConcurrentHashMap<>();
        private final Map<String, Set<BytesKey>> sets = new ConcurrentHashMap<>();
        private final Map<String, List<ZEntry>> zsets = new ConcurrentHashMap<>();
        private final AtomicInteger commandCount = new AtomicInteger();
        private final AtomicInteger authCalls = new AtomicInteger();
        private volatile boolean running = true;

        private FakeRedisServer() throws IOException {
            this.serverSocket = new ServerSocket(0);
            this.acceptThread = new Thread(this::acceptLoop, "fake-redis-accept");
            this.acceptThread.start();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        private void acceptLoop() {
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    Thread t = new Thread(() -> handle(socket), "fake-redis-client");
                    t.start();
                } catch (IOException e) {
                    if (!running) {
                        return;
                    }
                }
            }
        }

        private void handle(Socket socket) {
            try (socket;
                 InputStream in = socket.getInputStream();
                 OutputStream out = socket.getOutputStream()) {
                while (running) {
                    List<byte[]> cmd = readCommand(in);
                    if (cmd == null || cmd.isEmpty()) {
                        return;
                    }
                    commandCount.incrementAndGet();
                    String op = upper(cmd.get(0));
                    switch (op) {
                        case "PING" -> writeSimple(out, "PONG");
                        case "AUTH" -> {
                            authCalls.incrementAndGet();
                            writeSimple(out, "OK");
                        }
                        case "SELECT" -> writeSimple(out, "OK");
                        case "SET" -> handleSet(cmd, out);
                        case "GET" -> writeBulk(out, readValue(str(cmd, 1)));
                        case "DEL" -> writeInteger(out, handleDel(cmd));
                        case "EXISTS" -> writeInteger(out, readValue(str(cmd, 1)) == null ? 0 : 1);
                        case "PEXPIRE" -> writeInteger(out, handleExpire(cmd));
                        case "INCRBY" -> writeInteger(out, handleIncrBy(cmd));
                        case "MGET" -> handleMget(cmd, out);
                        case "MSET" -> handleMset(cmd, out);
                        case "HSET" -> handleHset(cmd, out);
                        case "HGET" -> handleHget(cmd, out);
                        case "HGETALL" -> handleHgetAll(cmd, out);
                        case "HDEL" -> handleHdel(cmd, out);
                        case "LPUSH" -> handleLpush(cmd, out);
                        case "LRANGE" -> handleLrange(cmd, out);
                        case "SADD" -> handleSadd(cmd, out);
                        case "SMEMBERS" -> handleSmembers(cmd, out);
                        case "ZADD" -> handleZadd(cmd, out);
                        case "ZRANGE" -> handleZrange(cmd, out);
                        case "SCAN" -> handleScan(cmd, out);
                        default -> writeError(out, "ERR unknown command");
                    }
                    out.flush();
                }
            } catch (IOException ignore) {
            }
        }

        private void handleSet(List<byte[]> cmd, OutputStream out) throws IOException {
            String key = str(cmd, 1);
            byte[] value = cmd.size() > 2 ? cmd.get(2) : null;
            long expireAt = 0;
            if (cmd.size() >= 5 && "PX".equalsIgnoreCase(str(cmd, 3))) {
                long ttl = Long.parseLong(str(cmd, 4));
                expireAt = System.currentTimeMillis() + ttl;
            }
            kv.put(key, new Entry(copy(value), expireAt));
            writeSimple(out, "OK");
        }

        private long handleDel(List<byte[]> cmd) {
            long c = 0;
            for (int i = 1; i < cmd.size(); i++) {
                String key = str(cmd, i);
                if (kv.remove(key) != null) {
                    c++;
                }
                hashes.remove(key);
                lists.remove(key);
                sets.remove(key);
                zsets.remove(key);
            }
            return c;
        }

        private long handleExpire(List<byte[]> cmd) {
            String key = str(cmd, 1);
            long ttl = Long.parseLong(str(cmd, 2));
            Entry e = readEntry(key);
            if (e == null) {
                return 0;
            }
            kv.put(key, new Entry(e.value, System.currentTimeMillis() + ttl));
            return 1;
        }

        private long handleIncrBy(List<byte[]> cmd) {
            String key = str(cmd, 1);
            long delta = Long.parseLong(str(cmd, 2));
            Entry e = readEntry(key);
            long current = 0;
            long expireAt = 0;
            if (e != null && e.value != null) {
                current = Long.parseLong(new String(e.value, StandardCharsets.UTF_8));
                expireAt = e.expireAtMs;
            }
            long next = current + delta;
            kv.put(key, new Entry(String.valueOf(next).getBytes(StandardCharsets.UTF_8), expireAt));
            return next;
        }

        private void handleMget(List<byte[]> cmd, OutputStream out) throws IOException {
            writeArrayLen(out, cmd.size() - 1);
            for (int i = 1; i < cmd.size(); i++) {
                writeBulk(out, readValue(str(cmd, i)));
            }
        }

        private void handleMset(List<byte[]> cmd, OutputStream out) throws IOException {
            for (int i = 1; i + 1 < cmd.size(); i += 2) {
                kv.put(str(cmd, i), new Entry(copy(cmd.get(i + 1)), 0));
            }
            writeSimple(out, "OK");
        }

        private void handleHset(List<byte[]> cmd, OutputStream out) throws IOException {
            String key = str(cmd, 1);
            String field = str(cmd, 2);
            byte[] value = copy(cmd.get(3));
            Map<String, byte[]> map = hashes.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
            boolean exists = map.containsKey(field);
            map.put(field, value);
            writeInteger(out, exists ? 0 : 1);
        }

        private void handleHget(List<byte[]> cmd, OutputStream out) throws IOException {
            String key = str(cmd, 1);
            String field = str(cmd, 2);
            Map<String, byte[]> map = hashes.get(key);
            writeBulk(out, map == null ? null : map.get(field));
        }

        private void handleHgetAll(List<byte[]> cmd, OutputStream out) throws IOException {
            String key = str(cmd, 1);
            Map<String, byte[]> map = hashes.getOrDefault(key, Map.of());
            writeArrayLen(out, map.size() * 2);
            for (Map.Entry<String, byte[]> entry : map.entrySet()) {
                writeBulk(out, entry.getKey().getBytes(StandardCharsets.UTF_8));
                writeBulk(out, entry.getValue());
            }
        }

        private void handleHdel(List<byte[]> cmd, OutputStream out) throws IOException {
            String key = str(cmd, 1);
            Map<String, byte[]> map = hashes.get(key);
            if (map == null) {
                writeInteger(out, 0);
                return;
            }
            long count = 0;
            for (int i = 2; i < cmd.size(); i++) {
                if (map.remove(str(cmd, i)) != null) {
                    count++;
                }
            }
            writeInteger(out, count);
        }

        private void handleLpush(List<byte[]> cmd, OutputStream out) throws IOException {
            String key = str(cmd, 1);
            List<byte[]> list = lists.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
            for (int i = 2; i < cmd.size(); i++) {
                list.add(0, copy(cmd.get(i)));
            }
            writeInteger(out, list.size());
        }

        private void handleLrange(List<byte[]> cmd, OutputStream out) throws IOException {
            String key = str(cmd, 1);
            int start = Integer.parseInt(str(cmd, 2));
            int stop = Integer.parseInt(str(cmd, 3));
            List<byte[]> list = lists.getOrDefault(key, List.of());
            List<byte[]> range = slice(list, start, stop);
            writeArrayLen(out, range.size());
            for (byte[] item : range) {
                writeBulk(out, item);
            }
        }

        private void handleSadd(List<byte[]> cmd, OutputStream out) throws IOException {
            String key = str(cmd, 1);
            Set<BytesKey> set = sets.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());
            long added = 0;
            for (int i = 2; i < cmd.size(); i++) {
                if (set.add(new BytesKey(copy(cmd.get(i))))) {
                    added++;
                }
            }
            writeInteger(out, added);
        }

        private void handleSmembers(List<byte[]> cmd, OutputStream out) throws IOException {
            String key = str(cmd, 1);
            Set<BytesKey> set = sets.getOrDefault(key, Set.of());
            writeArrayLen(out, set.size());
            for (BytesKey item : set) {
                writeBulk(out, item.value);
            }
        }

        private void handleZadd(List<byte[]> cmd, OutputStream out) throws IOException {
            String key = str(cmd, 1);
            double score = Double.parseDouble(str(cmd, 2));
            byte[] member = copy(cmd.get(3));
            List<ZEntry> list = zsets.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
            boolean exists = false;
            for (int i = 0; i < list.size(); i++) {
                if (Arrays.equals(list.get(i).member, member)) {
                    list.set(i, new ZEntry(score, member));
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                list.add(new ZEntry(score, member));
            }
            list.sort((a, b) -> Double.compare(a.score, b.score));
            writeInteger(out, exists ? 0 : 1);
        }

        private void handleZrange(List<byte[]> cmd, OutputStream out) throws IOException {
            String key = str(cmd, 1);
            int start = Integer.parseInt(str(cmd, 2));
            int stop = Integer.parseInt(str(cmd, 3));
            List<ZEntry> list = zsets.getOrDefault(key, List.of());
            List<ZEntry> range = slice(list, start, stop);
            writeArrayLen(out, range.size());
            for (ZEntry item : range) {
                writeBulk(out, item.member);
            }
        }

        private void handleScan(List<byte[]> cmd, OutputStream out) throws IOException {
            String pattern = "*";
            int count = 10;
            for (int i = 1; i + 1 < cmd.size(); i++) {
                String token = str(cmd, i);
                if ("MATCH".equalsIgnoreCase(token)) {
                    pattern = str(cmd, i + 1);
                }
                if ("COUNT".equalsIgnoreCase(token)) {
                    count = Integer.parseInt(str(cmd, i + 1));
                }
            }
            List<String> all = new ArrayList<>(kv.keySet());
            all.addAll(hashes.keySet());
            all.addAll(lists.keySet());
            all.addAll(sets.keySet());
            all.addAll(zsets.keySet());
            List<String> matched = new ArrayList<>();
            for (String key : all) {
                if (matched.size() >= count) {
                    break;
                }
                if (match(pattern, key)) {
                    matched.add(key);
                }
            }

            writeArrayLen(out, 2);
            writeBulk(out, "0".getBytes(StandardCharsets.UTF_8));
            writeArrayLen(out, matched.size());
            for (String key : matched) {
                writeBulk(out, key.getBytes(StandardCharsets.UTF_8));
            }
        }

        private boolean match(String pattern, String key) {
            if ("*".equals(pattern)) {
                return true;
            }
            if (!pattern.contains("*")) {
                return key.equals(pattern);
            }
            String[] parts = pattern.split("\\*", -1);
            int pos = 0;
            for (String part : parts) {
                if (part.isEmpty()) {
                    continue;
                }
                int found = key.indexOf(part, pos);
                if (found < 0) {
                    return false;
                }
                pos = found + part.length();
            }
            return pattern.endsWith("*") || pos == key.length();
        }

        private <T> List<T> slice(List<T> list, int start, int stop) {
            if (list.isEmpty()) {
                return List.of();
            }
            int from = start < 0 ? list.size() + start : start;
            int to = stop < 0 ? list.size() + stop : stop;
            from = Math.max(0, from);
            to = Math.min(list.size() - 1, to);
            if (from > to) {
                return List.of();
            }
            return new ArrayList<>(list.subList(from, to + 1));
        }

        private Entry readEntry(String key) {
            Entry e = kv.get(key);
            if (e == null) {
                return null;
            }
            if (e.expired()) {
                kv.remove(key, e);
                return null;
            }
            return e;
        }

        private byte[] readValue(String key) {
            Entry e = readEntry(key);
            return e == null ? null : copy(e.value);
        }

        private List<byte[]> readCommand(InputStream in) throws IOException {
            int first = in.read();
            if (first < 0) {
                return null;
            }
            if (first != '*') {
                throw new IOException("Invalid RESP array");
            }
            int count = Integer.parseInt(readLine(in));
            List<byte[]> out = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int bulk = in.read();
                if (bulk != '$') {
                    throw new IOException("Invalid RESP bulk string");
                }
                int len = Integer.parseInt(readLine(in));
                byte[] data = in.readNBytes(len);
                readCrLf(in);
                out.add(data);
            }
            return out;
        }

        private void writeSimple(OutputStream out, String s) throws IOException {
            out.write('+');
            out.write(s.getBytes(StandardCharsets.UTF_8));
            writeCrLf(out);
        }

        private void writeError(OutputStream out, String s) throws IOException {
            out.write('-');
            out.write(s.getBytes(StandardCharsets.UTF_8));
            writeCrLf(out);
        }

        private void writeInteger(OutputStream out, long value) throws IOException {
            out.write(':');
            out.write(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            writeCrLf(out);
        }

        private void writeBulk(OutputStream out, byte[] data) throws IOException {
            if (data == null) {
                out.write("$-1\r\n".getBytes(StandardCharsets.UTF_8));
                return;
            }
            out.write('$');
            out.write(String.valueOf(data.length).getBytes(StandardCharsets.UTF_8));
            writeCrLf(out);
            out.write(data);
            writeCrLf(out);
        }

        private void writeArrayLen(OutputStream out, int size) throws IOException {
            out.write('*');
            out.write(String.valueOf(size).getBytes(StandardCharsets.UTF_8));
            writeCrLf(out);
        }

        private String readLine(InputStream in) throws IOException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            while (true) {
                int b = in.read();
                if (b < 0) {
                    throw new IOException("EOF");
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
            return bos.toString(StandardCharsets.UTF_8);
        }

        private void readCrLf(InputStream in) throws IOException {
            int c1 = in.read();
            int c2 = in.read();
            if (c1 != '\r' || c2 != '\n') {
                throw new IOException("Invalid CRLF");
            }
        }

        private void writeCrLf(OutputStream out) throws IOException {
            out.write('\r');
            out.write('\n');
        }

        private String str(List<byte[]> cmd, int idx) {
            if (idx >= cmd.size()) {
                return "";
            }
            return new String(cmd.get(idx), StandardCharsets.UTF_8);
        }

        private String upper(byte[] data) {
            return new String(data, StandardCharsets.UTF_8).toUpperCase();
        }

        private byte[] copy(byte[] src) {
            if (src == null) {
                return null;
            }
            return Arrays.copyOf(src, src.length);
        }

        @Override
        public void close() throws Exception {
            running = false;
            try {
                serverSocket.close();
            } catch (IOException ignore) {
            }
            acceptThread.interrupt();
            acceptThread.join(1000);
        }

        private static final class Entry {
            private final byte[] value;
            private final long expireAtMs;

            private Entry(byte[] value, long expireAtMs) {
                this.value = value;
                this.expireAtMs = expireAtMs;
            }

            private boolean expired() {
                return expireAtMs > 0 && System.currentTimeMillis() >= expireAtMs;
            }
        }

        private static final class BytesKey {
            private final byte[] value;

            private BytesKey(byte[] value) {
                this.value = value;
            }

            @Override
            public boolean equals(Object obj) {
                return obj instanceof BytesKey other && Arrays.equals(value, other.value);
            }

            @Override
            public int hashCode() {
                return Arrays.hashCode(value);
            }
        }

        private static final class ZEntry {
            private final double score;
            private final byte[] member;

            private ZEntry(double score, byte[] member) {
                this.score = score;
                this.member = member;
            }
        }
    }
}
