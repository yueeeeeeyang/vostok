package yueyang.vostok.security.keystore;

import yueyang.vostok.security.crypto.VKRsaKeyPair;

public interface VKKeyStore {
    String getOrCreateAesKey(String keyId);

    VKRsaKeyPair getOrCreateRsaKeyPair(String keyId);

    void rotateAesKey(String keyId);

    void rotateRsaKeyPair(String keyId);
}
