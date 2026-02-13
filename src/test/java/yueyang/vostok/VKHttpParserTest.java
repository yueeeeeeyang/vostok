package yueyang.vostok;

import org.junit.jupiter.api.Test;
import yueyang.vostok.web.http.VKHttpParser;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VKHttpParserTest {
    @Test
    void testChunkedParse() {
        String req = "POST /chunk HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Transfer-Encoding: chunked\r\n" +
                "\r\n" +
                "5\r\n" +
                "hello\r\n" +
                "0\r\n\r\n";
        byte[] buf = req.getBytes(StandardCharsets.US_ASCII);
        VKHttpParser parser = new VKHttpParser(8192, 1024 * 1024);
        var parsed = parser.parse(buf, buf.length, null, false);
        assertNotNull(parsed);
        assertEquals("hello", parsed.request().bodyText());
    }

    @Test
    void testExpectContinueDetect() {
        String req = "POST /x HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Expect: 100-continue\r\n" +
                "Content-Length: 5\r\n" +
                "\r\n";
        byte[] buf = req.getBytes(StandardCharsets.US_ASCII);
        VKHttpParser parser = new VKHttpParser(8192, 1024 * 1024);
        assertTrue(parser.shouldSendContinue(buf, buf.length));
    }
}
