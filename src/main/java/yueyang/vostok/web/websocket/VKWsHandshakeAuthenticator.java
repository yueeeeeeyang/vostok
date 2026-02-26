package yueyang.vostok.web.websocket;

@FunctionalInterface
public interface VKWsHandshakeAuthenticator {
    VKWsAuthResult authenticate(VKWsHandshakeContext context);

    static VKWsHandshakeAuthenticator allowAll() {
        return ctx -> VKWsAuthResult.allow();
    }
}
