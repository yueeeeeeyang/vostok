package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.web.mvc.annotation.VKApi;
import yueyang.vostok.web.mvc.annotation.VKGet;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokWebMvcAutoReturnTest {
    @AfterEach
    void tearDown() {
        try {
            Vostok.Web.stop();
        } catch (Exception ignore) {
        }
    }

    static class UserOut {
        public long id;
        public String name;

        UserOut(long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @VKApi("/auto")
    static class AutoApi {
        @VKGet("/text")
        public String text() {
            return "ok";
        }

        @VKGet("/obj")
        public UserOut obj() {
            return new UserOut(1, "Tom");
        }

        @VKGet("/void")
        public void direct(yueyang.vostok.web.http.VKResponse res) {
            res.status(202).text("manual");
        }
    }

    @Test
    void testAutoWrapAndVoidBehavior() throws Exception {
        Vostok.Web.init(0)
                .controller(new AutoApi());
        Vostok.Web.start();

        int port = Vostok.Web.port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> t = client.send(HttpRequest.newBuilder()
                        .uri(new URI("http://127.0.0.1:" + port + "/auto/text"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, t.statusCode());
        assertTrue(t.body().contains("\"statusCode\":200"));
        assertTrue(t.body().contains("\"data\":\"ok\""));

        HttpResponse<String> o = client.send(HttpRequest.newBuilder()
                        .uri(new URI("http://127.0.0.1:" + port + "/auto/obj"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, o.statusCode());
        assertTrue(o.body().contains("\"name\":\"Tom\""));

        HttpResponse<String> v = client.send(HttpRequest.newBuilder()
                        .uri(new URI("http://127.0.0.1:" + port + "/auto/void"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(202, v.statusCode());
        assertEquals("manual", v.body());
    }
}
