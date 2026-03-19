package yueyang.vostok.web.spi;

import yueyang.vostok.web.VKWebConfig;

/**
 * 自定义 Web 引擎工厂。
 *
 * 业务项目可在这里注入 Netty、Undertow 或其他实现，复用 Vostok 的路由、中间件、限流和 MVC 语义。
 */
@FunctionalInterface
public interface VKWebServerFactory {
    VKWebServerEngine create(VKWebConfig config, VKWebRuntimeSupport runtime);
}
