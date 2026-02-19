package yueyang.vostok.security.crypto;

public final class VKRsaKeyPair {
    private final String publicKeyPem;
    private final String privateKeyPem;

    public VKRsaKeyPair(String publicKeyPem, String privateKeyPem) {
        this.publicKeyPem = publicKeyPem;
        this.privateKeyPem = privateKeyPem;
    }

    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    public String getPrivateKeyPem() {
        return privateKeyPem;
    }
}
