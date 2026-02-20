package yueyang.vostok;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import yueyang.vostok.http.VKHttpClientConfig;
import yueyang.vostok.http.VKHttpConfig;
import yueyang.vostok.http.exception.VKHttpErrorCode;
import yueyang.vostok.http.exception.VKHttpException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class VostokHttpTlsTest {
    private HttpsServer httpsServer;

    @AfterEach
    void tearDown() {
        if (httpsServer != null) {
            httpsServer.stop(0);
        }
        Vostok.Http.close();
    }

    @Test
    void testHttpsWithTrustStoreConfig() throws Exception {
        Assumptions.assumeTrue(hasKeytool(), "keytool is not available");

        Path dir = Files.createTempDirectory("vostok-http-tls");
        Path serverP12 = dir.resolve("server.p12");
        Path certPem = dir.resolve("server.crt");
        Path trustP12 = dir.resolve("trust.p12");
        String pwd = "changeit";

        genServerKeystore(serverP12, pwd);
        exportServerCert(serverP12, pwd, certPem);
        importTrustStore(certPem, trustP12, pwd);

        httpsServer = newHttpsServer(serverP12, pwd);
        String httpsBase = "https://localhost:" + httpsServer.getAddress().getPort();

        Vostok.Http.init(new VKHttpConfig().maxRetries(0).logEnabled(false));

        Vostok.Http.registerClient("secure", new VKHttpClientConfig()
                .baseUrl(httpsBase)
                .trustStore(trustP12.toString(), pwd, "PKCS12"));

        String body = Vostok.Http.get("/ping")
                .client("secure")
                .execute()
                .bodyText();
        assertEquals("pong", body);
    }

    @Test
    void testHttpsWithoutTrustStoreShouldFail() throws Exception {
        Assumptions.assumeTrue(hasKeytool(), "keytool is not available");

        Path dir = Files.createTempDirectory("vostok-http-tls-fail");
        Path serverP12 = dir.resolve("server.p12");
        String pwd = "changeit";
        genServerKeystore(serverP12, pwd);

        httpsServer = newHttpsServer(serverP12, pwd);
        String httpsBase = "https://localhost:" + httpsServer.getAddress().getPort();

        Vostok.Http.init(new VKHttpConfig().maxRetries(0).logEnabled(false));
        Vostok.Http.registerClient("secure", new VKHttpClientConfig().baseUrl(httpsBase));

        VKHttpException ex = assertThrows(VKHttpException.class,
                () -> Vostok.Http.get("/ping").client("secure").execute());
        assertEquals(VKHttpErrorCode.NETWORK_ERROR, ex.getCode());
    }

    @Test
    void testHttpsWithCustomSslContext() throws Exception {
        Assumptions.assumeTrue(hasKeytool(), "keytool is not available");

        Path dir = Files.createTempDirectory("vostok-http-tls-context");
        Path serverP12 = dir.resolve("server.p12");
        Path certPem = dir.resolve("server.crt");
        Path trustP12 = dir.resolve("trust.p12");
        String pwd = "changeit";

        genServerKeystore(serverP12, pwd);
        exportServerCert(serverP12, pwd, certPem);
        importTrustStore(certPem, trustP12, pwd);

        httpsServer = newHttpsServer(serverP12, pwd);
        String httpsBase = "https://localhost:" + httpsServer.getAddress().getPort();

        SSLContext sslContext = buildClientSslContext(trustP12, pwd);

        Vostok.Http.init(new VKHttpConfig().maxRetries(0).logEnabled(false));
        Vostok.Http.registerClient("secure", new VKHttpClientConfig()
                .baseUrl(httpsBase)
                .sslContext(sslContext));

        String body = Vostok.Http.get("/ping")
                .client("secure")
                .execute()
                .bodyText();
        assertEquals("pong", body);
    }

    private static boolean hasKeytool() {
        try {
            Process p = new ProcessBuilder("keytool", "-help").start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void genServerKeystore(Path serverP12, String pwd) throws Exception {
        exec(
                "keytool", "-genkeypair",
                "-alias", "server",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-storetype", "PKCS12",
                "-keystore", serverP12.toString(),
                "-storepass", pwd,
                "-keypass", pwd,
                "-dname", "CN=localhost, OU=Test, O=Vostok, L=NA, ST=NA, C=US",
                "-validity", "2",
                "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-noprompt"
        );
    }

    private static void exportServerCert(Path serverP12, String pwd, Path certPem) throws Exception {
        exec(
                "keytool", "-exportcert",
                "-alias", "server",
                "-keystore", serverP12.toString(),
                "-storetype", "PKCS12",
                "-storepass", pwd,
                "-rfc",
                "-file", certPem.toString()
        );
    }

    private static void importTrustStore(Path certPem, Path trustP12, String pwd) throws Exception {
        exec(
                "keytool", "-importcert",
                "-alias", "server",
                "-file", certPem.toString(),
                "-keystore", trustP12.toString(),
                "-storetype", "PKCS12",
                "-storepass", pwd,
                "-noprompt"
        );
    }

    private static HttpsServer newHttpsServer(Path serverP12, String pwd) throws Exception {
        SSLContext sslContext = buildServerSslContext(serverP12, pwd);
        HttpsServer server = HttpsServer.create(new InetSocketAddress(0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
            @Override
            public void configure(HttpsParameters params) {
                SSLParameters p = sslContext.getDefaultSSLParameters();
                params.setSSLParameters(p);
            }
        });
        server.createContext("/ping", exchange -> {
            byte[] out = "pong".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static SSLContext buildServerSslContext(Path serverP12, String pwd) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(serverP12)) {
            ks.load(in, pwd.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, pwd.toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);
        return sslContext;
    }

    private static SSLContext buildClientSslContext(Path trustP12, String pwd) throws Exception {
        KeyStore ts = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(trustP12)) {
            ts.load(in, pwd.toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);
        return sslContext;
    }

    private static void exec(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        byte[] out;
        try (InputStream in = p.getInputStream()) {
            out = in.readAllBytes();
        }
        int code = p.waitFor();
        if (code != 0) {
            throw new IllegalStateException("Command failed(" + code + "): " + String.join(" ", cmd)
                    + "\n" + new String(out, StandardCharsets.UTF_8));
        }
    }
}
