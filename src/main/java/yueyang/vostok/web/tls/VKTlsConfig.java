package yueyang.vostok.web.tls;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

/**
 * HTTPS/TLS 配置类。
 *
 * 通过 keyStorePath 和 keyStorePassword 加载 KeyStore，构建 SSLContext。
 * 支持 PKCS12 格式（推荐）和 JKS 格式。
 *
 * 使用示例：
 * <pre>
 *   VKTlsConfig tls = new VKTlsConfig()
 *       .keyStorePath("/path/to/server.p12")
 *       .keyStorePassword("changeit");
 *   VKWebConfig config = new VKWebConfig().port(8443).tls(tls);
 * </pre>
 */
public final class VKTlsConfig {
    /** KeyStore 文件路径（必需）。 */
    private String keyStorePath;
    /** KeyStore 密码（必需）。 */
    private String keyStorePassword;
    /** KeyStore 类型，默认 PKCS12。 */
    private String keyStoreType = "PKCS12";
    /** SSL/TLS 协议，默认 TLS。 */
    private String sslProtocol = "TLS";
    /** 启用的协议版本列表（null 表示使用默认值）。 */
    private String[] enabledProtocols;
    /** 启用的密码套件列表（null 表示使用默认值）。 */
    private String[] enabledCipherSuites;
    /** 是否要求客户端证书认证，默认 false。 */
    private boolean clientAuth = false;

    public String getKeyStorePath() {
        return keyStorePath;
    }

    public VKTlsConfig keyStorePath(String keyStorePath) {
        this.keyStorePath = keyStorePath;
        return this;
    }

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    public VKTlsConfig keyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
        return this;
    }

    public String getKeyStoreType() {
        return keyStoreType;
    }

    public VKTlsConfig keyStoreType(String keyStoreType) {
        this.keyStoreType = keyStoreType == null ? "PKCS12" : keyStoreType;
        return this;
    }

    public String getSslProtocol() {
        return sslProtocol;
    }

    public VKTlsConfig sslProtocol(String sslProtocol) {
        this.sslProtocol = sslProtocol == null ? "TLS" : sslProtocol;
        return this;
    }

    public String[] getEnabledProtocols() {
        return enabledProtocols;
    }

    public VKTlsConfig enabledProtocols(String... enabledProtocols) {
        this.enabledProtocols = enabledProtocols;
        return this;
    }

    public String[] getEnabledCipherSuites() {
        return enabledCipherSuites;
    }

    public VKTlsConfig enabledCipherSuites(String... enabledCipherSuites) {
        this.enabledCipherSuites = enabledCipherSuites;
        return this;
    }

    public boolean isClientAuth() {
        return clientAuth;
    }

    public VKTlsConfig clientAuth(boolean clientAuth) {
        this.clientAuth = clientAuth;
        return this;
    }

    /**
     * 根据配置加载 KeyStore 并构建 SSLContext。
     *
     * @return 初始化完成的 SSLContext，可直接用于创建 SSLEngine
     * @throws Exception 若 KeyStore 加载失败或参数非法则抛出异常
     */
    public SSLContext buildSslContext() throws Exception {
        if (keyStorePath == null || keyStorePath.isBlank()) {
            throw new IllegalArgumentException("TLS keyStorePath is required");
        }
        char[] password = keyStorePassword == null ? new char[0] : keyStorePassword.toCharArray();

        // 加载 KeyStore
        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
        try (InputStream in = Files.newInputStream(Path.of(keyStorePath))) {
            keyStore.load(in, password);
        }

        // 初始化 KeyManagerFactory（持有服务端私钥和证书）
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password);

        // 初始化 TrustManagerFactory（验证客户端证书，双向认证时使用）
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        // 构建并初始化 SSLContext
        SSLContext ctx = SSLContext.getInstance(sslProtocol);
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return ctx;
    }
}
