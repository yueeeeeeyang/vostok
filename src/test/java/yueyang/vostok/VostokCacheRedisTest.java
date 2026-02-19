package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.cache.VKCacheConfig;
import yueyang.vostok.cache.VKCacheProviderType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
                    .endpoints("127.0.0.1:" + server.port())
                    .maxActive(3)
                    .minIdle(0)
                    .maxWaitMs(300)
                    .connectTimeoutMs(1000)
                    .readTimeoutMs(1000)
                    .testOnBorrow(true)
                    .codec("string")
                    .keyPrefix("app:");

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

            Vostok.Cache.set("ttl", "x", 80);
            assertEquals("x", Vostok.Cache.get("ttl"));
            Thread.sleep(120);
            assertNull(Vostok.Cache.get("ttl"));

            assertEquals(1, Vostok.Cache.delete("k1"));
            assertFalse(Vostok.Cache.exists("k1"));
        }
    }

    private static final class FakeRedisServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Thread acceptThread;
        private final Map<String, Entry> kv = new ConcurrentHashMap<>();
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
                    String op = upper(cmd.get(0));
                    switch (op) {
                        case "PING" -> writeSimple(out, "PONG");
                        case "AUTH", "SELECT" -> writeSimple(out, "OK");
                        case "SET" -> handleSet(cmd, out);
                        case "GET" -> writeBulk(out, readValue(str(cmd, 1)));
                        case "DEL" -> writeInteger(out, handleDel(cmd));
                        case "EXISTS" -> writeInteger(out, readValue(str(cmd, 1)) == null ? 0 : 1);
                        case "PEXPIRE" -> writeInteger(out, handleExpire(cmd));
                        case "INCRBY" -> writeInteger(out, handleIncrBy(cmd));
                        case "MGET" -> handleMget(cmd, out);
                        case "MSET" -> handleMset(cmd, out);
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
                if (kv.remove(str(cmd, i)) != null) {
                    c++;
                }
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
    }
}
