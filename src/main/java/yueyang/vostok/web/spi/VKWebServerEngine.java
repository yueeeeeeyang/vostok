package yueyang.vostok.web.spi;

/**
 * Web 服务器引擎 SPI。
 *
 * 该接口只约束传输层生命周期，业务语义通过 VKWebRuntimeSupport 复用。
 */
public interface VKWebServerEngine {
    void start();

    void stop();

    boolean isStarted();

    int port();
}
