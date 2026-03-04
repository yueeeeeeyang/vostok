package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.web.mvc.VKWebResult;
import yueyang.vostok.web.mvc.annotation.VKApi;
import yueyang.vostok.web.mvc.annotation.VKGet;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokWebMvcWebResultTest {
    @AfterEach
    void tearDown() {
        try {
            Vostok.Web.stop();
        } catch (Exception ignore) {
        }
    }

    @VKApi("/result")
    static class ResultApi {
        @VKGet("/biz")
        public VKWebResult<String> biz() {
            return VKWebResult.of(418, "teapot", "x");
        }
    }

    @Test
    void testWebResultHttpStatusAndRuntimeFields() throws Exception {
        Vostok.Web.init(0)
                .controller(new ResultApi());
        Vostok.Web.start();

        int port = Vostok.Web.port();
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> resp = client.send(HttpRequest.newBuilder()
                        .uri(new URI("http://127.0.0.1:" + port + "/result/biz"))
                        .header("X-Trace-Id", "trace-mvc-1")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(418, resp.statusCode());
        assertTrue(resp.body().contains("\"statusCode\":418"));
        assertTrue(resp.body().contains("\"errorMessage\":\"teapot\""));
        assertTrue(resp.body().contains("\"traceId\":\"trace-mvc-1\""));
        assertTrue(resp.body().contains("\"requestTime\":"));
        assertTrue(resp.body().contains("\"responseTime\":"));
        assertTrue(resp.body().contains("\"requestCostMs\":"));
    }
}
