package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.web.http.VKUploadedFile;
import yueyang.vostok.web.mvc.annotation.VKApi;
import yueyang.vostok.web.mvc.annotation.VKBody;
import yueyang.vostok.web.mvc.annotation.VKCookie;
import yueyang.vostok.web.mvc.annotation.VKFile;
import yueyang.vostok.web.mvc.annotation.VKForm;
import yueyang.vostok.web.mvc.annotation.VKHeader;
import yueyang.vostok.web.mvc.annotation.VKPath;
import yueyang.vostok.web.mvc.annotation.VKPost;
import yueyang.vostok.web.mvc.annotation.VKQuery;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokWebMvcBindingTest {
    @AfterEach
    void tearDown() {
        try {
            Vostok.Web.stop();
        } catch (Exception ignore) {
        }
    }

    static class BindBody {
        public String name;
        public int age;
    }

    @VKApi("/bind")
    static class BindApi {
        @VKPost("/json/{id}")
        public java.util.Map<String, Object> bindJson(@VKPath("id") long id,
                                                      @VKQuery(value = "q", required = false, defaultValue = "7") int q,
                                                      @VKHeader(value = "X-Token", required = true) String token,
                                                      @VKCookie(value = "sid", required = true) String sid,
                                                      @VKBody BindBody body) {
            return java.util.Map.of(
                    "id", id,
                    "q", q,
                    "token", token,
                    "sid", sid,
                    "name", body.name,
                    "age", body.age
            );
        }

        @VKPost("/upload")
        public java.util.Map<String, Object> upload(@VKForm("name") String name,
                                                    @VKFile("file") VKUploadedFile file) {
            return java.util.Map.of(
                    "name", name,
                    "file", file == null ? "" : file.fileName()
            );
        }
    }

    @Test
    void testBindingJsonHeaderCookiePathQuery() throws Exception {
        Vostok.Web.init(0)
                .controller(new BindApi());
        Vostok.Web.start();

        int port = Vostok.Web.port();
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(new URI("http://127.0.0.1:" + port + "/bind/json/12?q=3"))
                .header("Content-Type", "application/json")
                .header("X-Token", "t-001")
                .header("Cookie", "sid=s-abc")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Tom\",\"age\":18}"))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"id\":12"));
        assertTrue(resp.body().contains("\"q\":3"));
        assertTrue(resp.body().contains("\"token\":\"t-001\""));
        assertTrue(resp.body().contains("\"sid\":\"s-abc\""));
        assertTrue(resp.body().contains("\"name\":\"Tom\""));
        assertTrue(resp.body().contains("\"age\":18"));
    }

    @Test
    void testBindingMultipartFormAndFile() throws Exception {
        yueyang.vostok.web.VKWebConfig cfg = new yueyang.vostok.web.VKWebConfig()
                .port(0)
                .multipartEnabled(true)
                .multipartInMemoryThresholdBytes(8);
        Vostok.Web.init(cfg)
                .controller(new BindApi());
        Vostok.Web.start();

        int port = Vostok.Web.port();
        String boundary = "----vk-mvc-boundary";
        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"name\"\r\n\r\n"
                + "neo\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"a.txt\"\r\n"
                + "Content-Type: text/plain\r\n\r\n"
                + "hello\r\n"
                + "--" + boundary + "--\r\n";

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> resp = client.send(HttpRequest.newBuilder()
                        .uri(new URI("http://127.0.0.1:" + port + "/bind/upload"))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"name\":\"neo\""));
        assertTrue(resp.body().contains("\"file\":\"a.txt\""));
    }
}
