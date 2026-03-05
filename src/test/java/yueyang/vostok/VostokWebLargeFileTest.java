package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.web.VKWebConfig;
import yueyang.vostok.web.middleware.VKGzipConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 大文件传输集成测试。
 *
 * 覆盖场景：
 * 1. 跨 64KB 阈值的静态文件服务（res.body vs res.file 两条路径）
 * 2. 通过 res.file() 直接服务大文件
 * 3. 文件内容完整性（SHA-256）
 * 4. 并发多连接同时下载大文件
 * 5. Keep-Alive 连接串行下载多个大文件
 * 6. Gzip 中间件对 res.file() 响应正确跳过（不压缩）
 * 7. 典型 Vite bundle 尺寸（200KB、500KB、1MB）
 * 8. 边界值：恰好 64KB、64KB+1 字节
 */
public class VostokWebLargeFileTest {

    @AfterEach
    void tearDown() {
        try {
            Vostok.Web.stop();
        } catch (Exception ignore) {
        }
    }

    // -----------------------------------------------------------------------
    // 辅助方法
    // -----------------------------------------------------------------------

    /** 生成指定大小的随机字节数组（确保不可压缩，排除 Gzip 干扰）。 */
    private static byte[] randomBytes(int size) {
        byte[] data = new byte[size];
        new Random(42).nextBytes(data);
        return data;
    }

    /** 计算字节数组的 SHA-256 摘要（十六进制字符串）。 */
    private static String sha256(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(data));
    }

    /** 将字节数组写入临时文件，返回文件路径。 */
    private static Path writeTempFile(Path dir, String name, byte[] data) throws IOException {
        Path p = dir.resolve(name);
        Files.write(p, data);
        return p;
    }

    /**
     * 通过原始 TCP socket 发送 HTTP/1.1 GET 请求并读取完整响应体。
     * 使用 Content-Length 而非连接关闭来判断响应完整性，
     * 以便在 Keep-Alive 连接上精确读取单个响应。
     */
    private static byte[] rawGet(String host, int port, String path, int timeoutMs) throws Exception {
        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            String req = "GET " + path + " HTTP/1.1\r\n" +
                    "Host: " + host + ":" + port + "\r\n" +
                    "Connection: close\r\n\r\n";
            out.write(req.getBytes(StandardCharsets.US_ASCII));
            out.flush();

            InputStream in = socket.getInputStream();
            // 读取全部响应（Connection: close，服务端写完即关闭）
            return in.readAllBytes();
        }
    }

    /**
     * 从原始 HTTP 响应字节中分离 header 和 body。
     * 返回 body 部分（\r\n\r\n 之后的内容）。
     */
    private static byte[] extractBody(byte[] raw) {
        // 搜索 \r\n\r\n
        for (int i = 0; i <= raw.length - 4; i++) {
            if (raw[i] == '\r' && raw[i + 1] == '\n' && raw[i + 2] == '\r' && raw[i + 3] == '\n') {
                int bodyStart = i + 4;
                byte[] body = new byte[raw.length - bodyStart];
                System.arraycopy(raw, bodyStart, body, 0, body.length);
                return body;
            }
        }
        return new byte[0];
    }

    /** 从原始 HTTP 响应中提取 Content-Length 头值，返回 -1 表示未找到。 */
    private static int extractContentLength(byte[] raw) {
        String header = extractHeaderSection(raw);
        for (String line : header.split("\r\n")) {
            if (line.toLowerCase().startsWith("content-length:")) {
                return Integer.parseInt(line.substring("content-length:".length()).trim());
            }
        }
        return -1;
    }

    /** 提取 HTTP 响应的状态码。 */
    private static int extractStatus(byte[] raw) {
        String first = new String(raw, 0, Math.min(raw.length, 20), StandardCharsets.US_ASCII);
        // "HTTP/1.1 200 OK"
        String[] parts = first.split(" ");
        if (parts.length >= 2) {
            try {
                return Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignore) {
            }
        }
        return -1;
    }

    private static String extractHeaderSection(byte[] raw) {
        for (int i = 0; i <= raw.length - 4; i++) {
            if (raw[i] == '\r' && raw[i + 1] == '\n' && raw[i + 2] == '\r' && raw[i + 3] == '\n') {
                return new String(raw, 0, i, StandardCharsets.US_ASCII);
            }
        }
        return new String(raw, StandardCharsets.US_ASCII);
    }

    // -----------------------------------------------------------------------
    // 测试：静态文件服务 - 64KB 阈值边界
    // -----------------------------------------------------------------------

    /**
     * 恰好 64KB 的文件：VKStaticHandler 使用 res.body(Files.readAllBytes())。
     * 验证：Content-Length 正确、body 完整。
     */
    @Test
    void testStaticFile_exactly64KB() throws Exception {
        int size = 64 * 1024;
        byte[] data = randomBytes(size);
        Path dir = Files.createTempDirectory("vk_large_test");
        writeTempFile(dir, "file64k.bin", data);

        Vostok.Web.init(0).staticDir("/files", dir.toString());
        Vostok.Web.start();
        int port = Vostok.Web.port();

        byte[] raw = rawGet("127.0.0.1", port, "/files/file64k.bin", 10_000);
        int status = extractStatus(raw);
        int contentLength = extractContentLength(raw);
        byte[] body = extractBody(raw);

        assertEquals(200, status, "Expected 200 OK");
        assertEquals(size, contentLength, "Content-Length must match file size");
        assertEquals(size, body.length,
                "Body length mismatch: expected " + size + ", got " + body.length);
        assertEquals(sha256(data), sha256(body), "Body SHA-256 mismatch — data corruption or truncation");
    }

    /**
     * 64KB + 1 字节：VKStaticHandler 切换到 res.file() (FileChannel.transferTo)。
     * 验证：Content-Length 正确、body 完整。
     */
    @Test
    void testStaticFile_justAbove64KB() throws Exception {
        int size = 64 * 1024 + 1;
        byte[] data = randomBytes(size);
        Path dir = Files.createTempDirectory("vk_large_test");
        writeTempFile(dir, "file64k1.bin", data);

        Vostok.Web.init(0).staticDir("/files", dir.toString());
        Vostok.Web.start();
        int port = Vostok.Web.port();

        byte[] raw = rawGet("127.0.0.1", port, "/files/file64k1.bin", 10_000);
        int status = extractStatus(raw);
        int contentLength = extractContentLength(raw);
        byte[] body = extractBody(raw);

        assertEquals(200, status);
        assertEquals(size, contentLength,
                "Content-Length must equal file size for file-mode response");
        assertEquals(size, body.length,
                "Body truncated: expected " + size + " bytes, got " + body.length);
        assertEquals(sha256(data), sha256(body), "Body SHA-256 mismatch after file-mode transfer");
    }

    /**
     * 200KB 文件 —— 典型 Vite 小 chunk 尺寸。
     */
    @Test
    void testStaticFile_200KB() throws Exception {
        int size = 200 * 1024;
        byte[] data = randomBytes(size);
        Path dir = Files.createTempDirectory("vk_large_test");
        writeTempFile(dir, "chunk_200k.js", data);

        Vostok.Web.init(0).staticDir("/assets", dir.toString());
        Vostok.Web.start();
        int port = Vostok.Web.port();

        byte[] raw = rawGet("127.0.0.1", port, "/assets/chunk_200k.js", 15_000);
        byte[] body = extractBody(raw);

        assertEquals(200, extractStatus(raw));
        assertEquals(size, extractContentLength(raw));
        assertEquals(size, body.length, "200KB JS chunk truncated");
        assertEquals(sha256(data), sha256(body), "200KB JS chunk corrupted");
    }

    /**
     * 500KB 文件 —— 典型 Vite main bundle 尺寸。
     */
    @Test
    void testStaticFile_500KB() throws Exception {
        int size = 500 * 1024;
        byte[] data = randomBytes(size);
        Path dir = Files.createTempDirectory("vk_large_test");
        writeTempFile(dir, "main_500k.js", data);

        Vostok.Web.init(0).staticDir("/assets", dir.toString());
        Vostok.Web.start();
        int port = Vostok.Web.port();

        byte[] raw = rawGet("127.0.0.1", port, "/assets/main_500k.js", 20_000);
        byte[] body = extractBody(raw);

        assertEquals(200, extractStatus(raw));
        assertEquals(size, extractContentLength(raw));
        assertEquals(size, body.length, "500KB JS bundle truncated");
        assertEquals(sha256(data), sha256(body), "500KB JS bundle corrupted");
    }

    /**
     * 1MB 文件 —— 大型单页应用 bundle 尺寸。
     */
    @Test
    void testStaticFile_1MB() throws Exception {
        int size = 1024 * 1024;
        byte[] data = randomBytes(size);
        Path dir = Files.createTempDirectory("vk_large_test");
        writeTempFile(dir, "vendor_1m.js", data);

        Vostok.Web.init(0).staticDir("/assets", dir.toString());
        Vostok.Web.start();
        int port = Vostok.Web.port();

        byte[] raw = rawGet("127.0.0.1", port, "/assets/vendor_1m.js", 30_000);
        byte[] body = extractBody(raw);

        assertEquals(200, extractStatus(raw));
        assertEquals(size, extractContentLength(raw));
        assertEquals(size, body.length, "1MB vendor bundle truncated");
        assertEquals(sha256(data), sha256(body), "1MB vendor bundle corrupted");
    }

    // -----------------------------------------------------------------------
    // 测试：直接 res.file() 路由（模拟 OrientApplication.writeFileResponse）
    // -----------------------------------------------------------------------

    /**
     * 通过 res.file() 直接服务大文件（模拟 SPA fallback 路径）。
     * 这是 OrientApplication.writeFileResponse() 的行为：无论文件大小一律用 file 模式。
     */
    @Test
    void testDirectFileRoute_200KB() throws Exception {
        int size = 200 * 1024;
        byte[] data = randomBytes(size);
        Path dir = Files.createTempDirectory("vk_large_test");
        Path file = writeTempFile(dir, "index_200k.js", data);

        Vostok.Web.init(0)
                .get("/app.js", (req, res) -> {
                    try {
                        long fileSize = Files.size(file);
                        res.status(200)
                                .header("Content-Type", "application/javascript")
                                .file(file, fileSize);
                    } catch (Exception e) {
                        res.status(500).text("error: " + e.getMessage());
                    }
                });
        Vostok.Web.start();
        int port = Vostok.Web.port();

        byte[] raw = rawGet("127.0.0.1", port, "/app.js", 15_000);
        byte[] body = extractBody(raw);

        assertEquals(200, extractStatus(raw));
        assertEquals(size, extractContentLength(raw));
        assertEquals(size, body.length,
                "Direct res.file() route: body truncated at " + body.length + " / " + size);
        assertEquals(sha256(data), sha256(body), "Direct res.file() route: data corrupted");
    }

    /**
     * 直接 res.file() 路由，1MB 文件（SPA fallback + 大 vendor 场景）。
     */
    @Test
    void testDirectFileRoute_1MB() throws Exception {
        int size = 1024 * 1024;
        byte[] data = randomBytes(size);
        Path dir = Files.createTempDirectory("vk_large_test");
        Path file = writeTempFile(dir, "vendor_1m.js", data);

        Vostok.Web.init(0)
                .get("/vendor.js", (req, res) -> {
                    try {
                        res.status(200)
                                .header("Content-Type", "application/javascript")
                                .file(file, Files.size(file));
                    } catch (Exception e) {
                        res.status(500).text("error: " + e.getMessage());
                    }
                });
        Vostok.Web.start();
        int port = Vostok.Web.port();

        byte[] raw = rawGet("127.0.0.1", port, "/vendor.js", 30_000);
        byte[] body = extractBody(raw);

        assertEquals(200, extractStatus(raw));
        assertEquals(size, extractContentLength(raw));
        assertEquals(size, body.length, "1MB vendor: body truncated");
        assertEquals(sha256(data), sha256(body), "1MB vendor: data corrupted");
    }

    // -----------------------------------------------------------------------
    // 测试：并发多连接同时下载大文件
    // -----------------------------------------------------------------------

    /**
     * 20 个并发连接同时下载同一个 300KB 文件。
     * 复现多 reactor/worker 线程同时 transferTo 时的竞态或 buffer 污染问题。
     */
    @Test
    void testConcurrentDownload_300KB_20clients() throws Exception {
        int size = 300 * 1024;
        byte[] data = randomBytes(size);
        Path dir = Files.createTempDirectory("vk_large_test");
        writeTempFile(dir, "concurrent.js", data);
        String expectedHash = sha256(data);

        Vostok.Web.init(
                new VKWebConfig()
                        .port(0)
                        .ioThreads(2)
                        .workerThreads(8)
        ).staticDir("/assets", dir.toString());
        Vostok.Web.start();
        int port = Vostok.Web.port();

        int clients = 20;
        ExecutorService pool = Executors.newFixedThreadPool(clients);
        List<CompletableFuture<String>> futures = new ArrayList<>();

        for (int i = 0; i < clients; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    byte[] raw = rawGet("127.0.0.1", port, "/assets/concurrent.js", 20_000);
                    int status = extractStatus(raw);
                    int cl = extractContentLength(raw);
                    byte[] body = extractBody(raw);

                    if (status != 200) return "BAD_STATUS:" + status;
                    if (cl != size) return "BAD_CONTENT_LENGTH:" + cl;
                    if (body.length != size) return "TRUNCATED:" + body.length;
                    return sha256(body);
                } catch (Exception e) {
                    return "ERROR:" + e.getMessage();
                }
            }, pool));
        }

        pool.shutdown();
        pool.awaitTermination(60, TimeUnit.SECONDS);

        List<String> failures = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            String result = futures.get(i).get();
            if (!expectedHash.equals(result)) {
                failures.add("Client " + i + ": " + result);
            }
        }
        assertTrue(failures.isEmpty(),
                "Concurrent download failures (" + failures.size() + "/" + clients + "):\n"
                        + String.join("\n", failures));
    }

    // -----------------------------------------------------------------------
    // 测试：Keep-Alive 连接串行下载多个大文件
    // -----------------------------------------------------------------------

    /**
     * 在同一个 Keep-Alive TCP 连接上串行发送 3 个大文件请求。
     * 验证每个响应的 Content-Length 和 body 内容均完整且不混淆。
     */
    @Test
    void testKeepAlive_serialLargeFiles() throws Exception {
        int size1 = 200 * 1024;
        int size2 = 350 * 1024;
        int size3 = 100 * 1024;

        byte[] data1 = randomBytes(size1);
        byte[] data2 = randomBytes(size2);
        byte[] data3 = randomBytes(size3);

        Path dir = Files.createTempDirectory("vk_large_test");
        writeTempFile(dir, "a.js", data1);
        writeTempFile(dir, "b.js", data2);
        writeTempFile(dir, "c.js", data3);

        Vostok.Web.init(
                new VKWebConfig()
                        .port(0)
                        .keepAliveTimeoutMs(30_000)
        ).staticDir("/assets", dir.toString());
        Vostok.Web.start();
        int port = Vostok.Web.port();

        // 通过 HttpClient 发起串行 Keep-Alive 请求（HttpClient 默认复用连接）
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        record FileCheck(String path, byte[] expected, int size) {
        }

        List<FileCheck> checks = List.of(
                new FileCheck("/assets/a.js", data1, size1),
                new FileCheck("/assets/b.js", data2, size2),
                new FileCheck("/assets/c.js", data3, size3)
        );

        for (FileCheck check : checks) {
            HttpResponse<byte[]> res = client.send(
                    HttpRequest.newBuilder()
                            .uri(new URI("http://127.0.0.1:" + port + check.path()))
                            .GET()
                            .timeout(Duration.ofSeconds(20))
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray());

            assertEquals(200, res.statusCode(), "Keep-Alive request to " + check.path());
            assertEquals(check.size(), res.body().length,
                    "Keep-Alive body truncated for " + check.path()
                            + ": expected " + check.size() + ", got " + res.body().length);
            assertEquals(sha256(check.expected()), sha256(res.body()),
                    "Keep-Alive body corrupted for " + check.path());
        }
    }

    // -----------------------------------------------------------------------
    // 测试：Gzip 中间件对 res.file() 响应正确跳过
    // -----------------------------------------------------------------------

    /**
     * 启用 Gzip 中间件时，res.file() 的大文件响应不得被压缩。
     * 若 Content-Encoding: gzip 出现，说明 Gzip 错误地处理了 file 模式响应。
     */
    @Test
    void testGzipSkipsFileResponse() throws Exception {
        int size = 200 * 1024;
        // 使用可压缩的 ASCII 内容（text/ MIME），确保 Gzip 有动机压缩
        byte[] data = "function test() { return 'hello world'; }\n".repeat(4096).getBytes(StandardCharsets.UTF_8);
        Path dir = Files.createTempDirectory("vk_large_test");
        writeTempFile(dir, "compressible.js", data);
        long actualSize = data.length;

        Vostok.Web.init(0)
                .gzip(new VKGzipConfig().minBytes(1024))
                .staticDir("/assets", dir.toString());
        Vostok.Web.start();
        int port = Vostok.Web.port();

        HttpClient client = HttpClient.newBuilder().build();
        HttpResponse<byte[]> res = client.send(
                HttpRequest.newBuilder()
                        .uri(new URI("http://127.0.0.1:" + port + "/assets/compressible.js"))
                        .header("Accept-Encoding", "gzip")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, res.statusCode());
        // file-mode 响应不得被 Gzip 压缩（Content-Encoding 不应为 gzip）
        String encoding = res.headers().firstValue("Content-Encoding").orElse("none");
        assertNotEquals("gzip", encoding,
                "Gzip middleware must NOT compress file-mode responses (res.file()), but Content-Encoding was: " + encoding);

        // Content-Length 必须等于原始文件大小（非压缩大小）
        long cl = res.headers().firstValueAsLong("Content-Length").orElse(-1);
        assertEquals(actualSize, cl,
                "Content-Length must equal original file size for non-gzipped file response");
        assertEquals(actualSize, res.body().length,
                "Body length must equal original file size");
    }

    // -----------------------------------------------------------------------
    // 测试：多次重复请求同一大文件（复现 NIO buffer 状态污染）
    // -----------------------------------------------------------------------

    /**
     * 对同一大文件重复请求 10 次，每次用独立连接。
     * 复现 FileChannel position 或 NIO 写 buffer 没有正确重置的 bug。
     */
    @Test
    void testRepeatDownload_sameFile_10times() throws Exception {
        int size = 500 * 1024;
        byte[] data = randomBytes(size);
        Path dir = Files.createTempDirectory("vk_large_test");
        writeTempFile(dir, "repeat.js", data);
        String expectedHash = sha256(data);

        Vostok.Web.init(0).staticDir("/assets", dir.toString());
        Vostok.Web.start();
        int port = Vostok.Web.port();

        for (int i = 0; i < 10; i++) {
            byte[] raw = rawGet("127.0.0.1", port, "/assets/repeat.js", 20_000);
            byte[] body = extractBody(raw);
            assertEquals(size, body.length,
                    "Iteration " + i + ": body truncated at " + body.length);
            assertEquals(expectedHash, sha256(body),
                    "Iteration " + i + ": data corrupted");
        }
    }

    // -----------------------------------------------------------------------
    // 测试：流式写入 —— Content-Length 与实际 body 一致性
    // -----------------------------------------------------------------------

    // -----------------------------------------------------------------------
    // 测试：慢速客户端 —— 强制 transferTo / channel.write 分多次写入
    // -----------------------------------------------------------------------

    /**
     * 客户端设置极小的 SO_RCVBUF（8KB），强制服务端 socket 发送缓冲区频繁满，
     * 触发 FileChannel.transferTo 多次循环写入（transferred == 0 的情况）。
     * 验证在这种背压（back-pressure）场景下，文件内容仍然完整。
     */
    @Test
    void testSlowClient_smallReceiveBuffer_500KB() throws Exception {
        int size = 500 * 1024;
        byte[] data = randomBytes(size);
        Path dir = Files.createTempDirectory("vk_large_test");
        writeTempFile(dir, "slow_client.js", data);
        String expectedHash = sha256(data);

        Vostok.Web.init(0).staticDir("/assets", dir.toString());
        Vostok.Web.start();
        int port = Vostok.Web.port();

        try (Socket socket = new Socket("127.0.0.1", port)) {
            // 极小接收缓冲区：强制服务端发送窗口受限，触发多次 transferTo(0)
            socket.setReceiveBufferSize(4 * 1024);
            socket.setSoTimeout(30_000);

            // 发送请求
            OutputStream out = socket.getOutputStream();
            String req = "GET /assets/slow_client.js HTTP/1.1\r\n" +
                    "Host: 127.0.0.1:" + port + "\r\n" +
                    "Connection: close\r\n\r\n";
            out.write(req.getBytes(StandardCharsets.US_ASCII));
            out.flush();

            // 慢速读取：每次只读取 4KB，模拟浏览器慢速消费
            InputStream in = socket.getInputStream();
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                baos.write(buf, 0, n);
                // 模拟处理延迟（给服务端制造背压）
                Thread.sleep(2);
            }
            byte[] raw = baos.toByteArray();

            int status = extractStatus(raw);
            int cl = extractContentLength(raw);
            byte[] body = extractBody(raw);

            assertEquals(200, status, "Slow client: status should be 200");
            assertEquals(size, cl, "Slow client: Content-Length mismatch");
            assertEquals(size, body.length,
                    "Slow client: body truncated at " + body.length + " / " + size
                            + " — transferTo partial write not handled correctly");
            assertEquals(expectedHash, sha256(body),
                    "Slow client: data corrupted during multi-chunk transferTo");
        }
    }

    /**
     * 慢速客户端 + Keep-Alive：在同一连接上，慢速接收第一个大文件响应后，
     * 再正常接收第二个文件响应。验证 writeLoop 在部分写入后能正确恢复。
     */
    @Test
    void testSlowClient_keepAlive_twoFiles() throws Exception {
        int size1 = 300 * 1024;
        int size2 = 100 * 1024;
        byte[] data1 = randomBytes(size1);
        byte[] data2 = randomBytes(size2);
        Path dir = Files.createTempDirectory("vk_large_test");
        writeTempFile(dir, "first.js", data1);
        writeTempFile(dir, "second.js", data2);

        Vostok.Web.init(new VKWebConfig().port(0).keepAliveTimeoutMs(30_000))
                .staticDir("/assets", dir.toString());
        Vostok.Web.start();
        int port = Vostok.Web.port();

        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setReceiveBufferSize(4 * 1024);
            socket.setSoTimeout(30_000);

            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // 第一个请求（Keep-Alive）
            String req1 = "GET /assets/first.js HTTP/1.1\r\n" +
                    "Host: 127.0.0.1:" + port + "\r\n" +
                    "Connection: keep-alive\r\n\r\n";
            out.write(req1.getBytes(StandardCharsets.US_ASCII));
            out.flush();

            // 慢速读取第一个响应（按 Content-Length 精确读取）
            byte[] resp1 = readHttpResponse(in, size1, 2);

            int cl1 = extractContentLength(resp1);
            byte[] body1 = extractBody(resp1);

            assertEquals(size1, cl1, "Keep-Alive req1: Content-Length wrong");
            assertEquals(size1, body1.length,
                    "Keep-Alive req1: body truncated at " + body1.length);
            assertEquals(sha256(data1), sha256(body1),
                    "Keep-Alive req1: data corrupted");

            // 第二个请求（Connection: close）
            String req2 = "GET /assets/second.js HTTP/1.1\r\n" +
                    "Host: 127.0.0.1:" + port + "\r\n" +
                    "Connection: close\r\n\r\n";
            out.write(req2.getBytes(StandardCharsets.US_ASCII));
            out.flush();

            byte[] resp2 = in.readAllBytes();
            byte[] body2 = extractBody(resp2);

            assertEquals(size2, extractContentLength(resp2), "Keep-Alive req2: Content-Length wrong");
            assertEquals(size2, body2.length, "Keep-Alive req2: body truncated");
            assertEquals(sha256(data2), sha256(body2), "Keep-Alive req2: data corrupted");
        }
    }

    /**
     * 读取一个 HTTP/1.1 响应（Keep-Alive 场景）。
     * 先读 header（到 \r\n\r\n），提取 Content-Length，再精确读取 body。
     *
     * @param in          socket 输入流
     * @param expectedBodySize 预期 body 字节数（用于分配缓冲区）
     * @param delayMs     每次读取之间的模拟延迟（毫秒）
     */
    private static byte[] readHttpResponse(InputStream in, int expectedBodySize, long delayMs) throws Exception {
        // 先读 header（最多 16KB）
        byte[] headerBuf = new byte[16 * 1024];
        int headerLen = 0;
        int bodyStart = -1;
        while (headerLen < headerBuf.length) {
            int b = in.read();
            if (b == -1) break;
            headerBuf[headerLen++] = (byte) b;
            // 检测 \r\n\r\n
            if (headerLen >= 4
                    && headerBuf[headerLen - 4] == '\r'
                    && headerBuf[headerLen - 3] == '\n'
                    && headerBuf[headerLen - 2] == '\r'
                    && headerBuf[headerLen - 1] == '\n') {
                bodyStart = headerLen;
                break;
            }
        }
        if (bodyStart < 0) {
            throw new IOException("Header end not found");
        }

        // 从 header 中提取 Content-Length
        String headerStr = new String(headerBuf, 0, headerLen, StandardCharsets.US_ASCII);
        int contentLength = -1;
        for (String line : headerStr.split("\r\n")) {
            if (line.toLowerCase().startsWith("content-length:")) {
                contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
                break;
            }
        }
        if (contentLength < 0) {
            throw new IOException("No Content-Length header found");
        }

        // 精确读取 body
        byte[] body = new byte[contentLength];
        int read = 0;
        byte[] readBuf = new byte[4 * 1024];
        while (read < contentLength) {
            int toRead = Math.min(readBuf.length, contentLength - read);
            int n = in.read(readBuf, 0, toRead);
            if (n == -1) break;
            System.arraycopy(readBuf, 0, body, read, n);
            read += n;
            if (delayMs > 0) {
                Thread.sleep(delayMs);
            }
        }

        // 拼装完整响应返回（header + body），与其他 helper 兼容
        byte[] full = new byte[headerLen + read];
        System.arraycopy(headerBuf, 0, full, 0, headerLen);
        System.arraycopy(body, 0, full, headerLen, read);
        return full;
    }

    // -----------------------------------------------------------------------
    // 测试：HTTP 流水线（pipelining）响应顺序正确性
    // -----------------------------------------------------------------------

    /**
     * 在同一 TCP 连接上连续发送两个请求（HTTP pipelining），
     * 验证服务端按请求顺序返回响应，不发生响应交叉或错序。
     *
     * 潜在 bug：dispatch() 将两个请求同时投入 worker 池，
     * 若 worker2 先完成，writeQueue 中 Response2 先于 Response1，
     * 客户端收到的 Content-Length 与 body 内容不匹配。
     */
    @Test
    void testPipelining_twoLargeFiles_responseOrder() throws Exception {
        int size1 = 300 * 1024;
        int size2 = 200 * 1024;
        byte[] data1 = randomBytes(size1);
        byte[] data2 = randomBytes(size2);
        String hash1 = sha256(data1);
        String hash2 = sha256(data2);

        Path dir = Files.createTempDirectory("vk_large_test");
        writeTempFile(dir, "bundle1.js", data1);
        writeTempFile(dir, "bundle2.js", data2);

        Vostok.Web.init(new VKWebConfig()
                .port(0)
                .keepAliveTimeoutMs(30_000)
                .workerThreads(8)  // 多 worker 线程增加乱序概率
        ).staticDir("/assets", dir.toString());
        Vostok.Web.start();
        int port = Vostok.Web.port();

        // 多次重复测试，增加触发竞态的概率
        for (int trial = 0; trial < 5; trial++) {
            try (Socket socket = new Socket("127.0.0.1", port)) {
                socket.setSoTimeout(30_000);
                OutputStream out = socket.getOutputStream();
                InputStream in = socket.getInputStream();

                // 一次性发送两个请求（pipelining）
                String twoRequests =
                        "GET /assets/bundle1.js HTTP/1.1\r\n" +
                                "Host: 127.0.0.1:" + port + "\r\n" +
                                "Connection: keep-alive\r\n\r\n" +
                                "GET /assets/bundle2.js HTTP/1.1\r\n" +
                                "Host: 127.0.0.1:" + port + "\r\n" +
                                "Connection: close\r\n\r\n";
                out.write(twoRequests.getBytes(StandardCharsets.US_ASCII));
                out.flush();

                // 读取第一个响应（按 Content-Length 精确读取）
                byte[] resp1 = readHttpResponse(in, size1, 0);
                int cl1 = extractContentLength(resp1);
                byte[] body1 = extractBody(resp1);

                // 读取第二个响应
                byte[] resp2 = in.readAllBytes();
                int cl2 = extractContentLength(resp2);
                byte[] body2 = extractBody(resp2);

                // 断言：响应必须按请求顺序到达，且内容完整正确
                assertEquals(size1, cl1, "Trial " + trial + ": Response1 Content-Length wrong");
                assertEquals(size1, body1.length,
                        "Trial " + trial + ": Response1 body truncated (" + body1.length + "/" + size1 + ")");
                assertEquals(hash1, sha256(body1),
                        "Trial " + trial + ": Response1 data mismatch — possible response ordering bug");

                assertEquals(size2, cl2, "Trial " + trial + ": Response2 Content-Length wrong");
                assertEquals(size2, body2.length,
                        "Trial " + trial + ": Response2 body truncated (" + body2.length + "/" + size2 + ")");
                assertEquals(hash2, sha256(body2),
                        "Trial " + trial + ": Response2 data mismatch — possible response ordering bug");
            }
        }
    }

    /**
     * 验证各文件大小档位下 Content-Length 头与实际接收字节数完全一致。
     * 使用原始 socket 以精确读取字节数（避免 HttpClient 自动处理影响计量）。
     */
    @Test
    void testContentLengthAccuracy_multipleSizes() throws Exception {
        Path dir = Files.createTempDirectory("vk_large_test");

        int[] sizes = {
                32 * 1024,          // 32KB  — body 模式
                64 * 1024,          // 64KB  — body 模式（边界）
                64 * 1024 + 1,      // 64KB+1 — file 模式（边界）
                128 * 1024,         // 128KB
                256 * 1024,         // 256KB
                512 * 1024,         // 512KB
                1024 * 1024,        // 1MB
        };

        for (int size : sizes) {
            byte[] data = randomBytes(size);
            String filename = "size_" + size + ".bin";
            writeTempFile(dir, filename, data);
        }

        Vostok.Web.init(0).staticDir("/files", dir.toString());
        Vostok.Web.start();
        int port = Vostok.Web.port();

        for (int size : sizes) {
            String filename = "size_" + size + ".bin";
            byte[] raw = rawGet("127.0.0.1", port, "/files/" + filename, 30_000);

            int status = extractStatus(raw);
            int cl = extractContentLength(raw);
            byte[] body = extractBody(raw);

            assertEquals(200, status, "size=" + size + ": unexpected status");
            assertEquals(size, cl,
                    "size=" + size + ": Content-Length mismatch (declared " + cl + ")");
            assertEquals(size, body.length,
                    "size=" + size + ": body length " + body.length + " != declared " + size);
        }
    }
}
