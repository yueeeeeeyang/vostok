package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokWebMvcControllerScanTest {
    @AfterEach
    void tearDown() {
        try {
            Vostok.Web.stop();
        } catch (Exception ignore) {
        }
    }

    @Test
    void testControllerScan() throws Exception {
        Vostok.Web.init(0)
                .controllers("yueyang.vostok.webmvc");
        Vostok.Web.start();

        int port = Vostok.Web.port();
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> resp = client.send(HttpRequest.newBuilder()
                        .uri(new URI("http://127.0.0.1:" + port + "/scan/ping"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"data\":\"pong\""));
    }
}
