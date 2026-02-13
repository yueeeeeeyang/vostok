package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.data.VKDataConfig;
import yueyang.vostok.data.config.VKBatchFailStrategy;
import yueyang.vostok.data.dialect.VKDialectType;
import yueyang.vostok.data.exception.VKStateException;
import yueyang.vostok.common.json.VKJson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokWebAutoCrudTest {
    private static final String JDBC_URL = "jdbc:h2:mem:webcrud;MODE=MySQL;DB_CLOSE_DELAY=-1";

    @AfterEach
    void tearDown() {
        try {
            Vostok.Web.stop();
        } catch (Exception ignore) {
        }
        try {
            Vostok.Data.close();
        } catch (Exception ignore) {
        }
    }

    @Test
    void testAutoCrudDefaultScan() throws Exception {
        ensureData();

        Vostok.Web.init(0).autoCrud();
        Vostok.Web.start();

        int port = Vostok.Web.port();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        HttpRequest post = HttpRequest.newBuilder()
                .uri(new URI("http://127.0.0.1:" + port + "/user"))
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Tom\",\"age\":20}"))
                .build();
        HttpResponse<String> postRes = client.send(post, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, postRes.statusCode());
        assertTrue(postRes.body().contains("\"inserted\""));

        HttpRequest list = HttpRequest.newBuilder()
                .uri(new URI("http://127.0.0.1:" + port + "/user"))
                .GET()
                .build();
        HttpResponse<String> listRes = client.send(list, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, listRes.statusCode());

        List<?> listObj = VKJson.fromJson(listRes.body(), List.class);
        assertEquals(1, listObj.size());
        Map<?, ?> item = (Map<?, ?>) listObj.get(0);
        Object id = item.get("id");

        HttpRequest get = HttpRequest.newBuilder()
                .uri(new URI("http://127.0.0.1:" + port + "/user/" + id))
                .GET()
                .build();
        HttpResponse<String> getRes = client.send(get, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getRes.statusCode());
        assertTrue(getRes.body().contains("Tom"));

        HttpRequest put = HttpRequest.newBuilder()
                .uri(new URI("http://127.0.0.1:" + port + "/user/" + id))
                .PUT(HttpRequest.BodyPublishers.ofString("{\"name\":\"Jerry\",\"age\":21}"))
                .build();
        HttpResponse<String> putRes = client.send(put, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, putRes.statusCode());
        assertTrue(putRes.body().contains("\"updated\""));

        HttpRequest del = HttpRequest.newBuilder()
                .uri(new URI("http://127.0.0.1:" + port + "/user/" + id))
                .DELETE()
                .build();
        HttpResponse<String> delRes = client.send(del, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, delRes.statusCode());
        assertTrue(delRes.body().contains("\"deleted\""));
    }

    private static void ensureData() {
        try {
            Vostok.Data.findAll(UserEntity.class);
            return;
        } catch (VKStateException ignore) {
        }

        VKDataConfig cfg = new VKDataConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .minIdle(1)
                .maxActive(3)
                .maxWaitMs(10000)
                .batchSize(2)
                .batchFailStrategy(VKBatchFailStrategy.FAIL_FAST);

        Vostok.Data.init(cfg, "yueyang.vostok");

        try (var conn = java.sql.DriverManager.getConnection(JDBC_URL, "sa", "");
             var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS t_user (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_name VARCHAR(64) NOT NULL, age INT)");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
