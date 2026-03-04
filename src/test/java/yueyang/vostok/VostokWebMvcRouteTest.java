package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.web.mvc.annotation.VKApi;
import yueyang.vostok.web.mvc.annotation.VKGet;
import yueyang.vostok.web.mvc.annotation.VKPath;
import yueyang.vostok.web.mvc.annotation.VKQuery;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokWebMvcRouteTest {
    @AfterEach
    void tearDown() {
        try {
            Vostok.Web.stop();
        } catch (Exception ignore) {
        }
    }

    @VKApi("/api/users")
    static class UserApi {
        @VKGet("/detail/{id}")
        public java.util.Map<String, Object> detail(@VKPath("id") long id,
                                                    @VKQuery(value = "page", required = false, defaultValue = "1") int page) {
            return java.util.Map.of("id", id, "page", page);
        }
    }

    @Test
    void testVkApiRouteMapping() throws Exception {
        Vostok.Web.init(0)
                .controller(new UserApi());
        Vostok.Web.start();

        int port = Vostok.Web.port();
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> resp = client.send(HttpRequest.newBuilder()
                        .uri(new URI("http://127.0.0.1:" + port + "/api/users/detail/9?page=3"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"statusCode\":200"));
        assertTrue(resp.body().contains("\"id\":9"));
        assertTrue(resp.body().contains("\"page\":3"));
    }
}
