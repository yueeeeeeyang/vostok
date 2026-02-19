package yueyang.vostok.security.keystore;

import yueyang.vostok.security.crypto.VKAesCrypto;
import yueyang.vostok.security.crypto.VKRsaCrypto;
import yueyang.vostok.security.crypto.VKRsaKeyPair;
import yueyang.vostok.security.exception.VKSecurityException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;

public final class LocalFileKeyStore implements VKKeyStore {
    private static final Pattern KEY_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");

    private final VKKeyStoreConfig config;
    private final Path baseDir;

    public LocalFileKeyStore(VKKeyStoreConfig config) {
        this.config = config == null ? new VKKeyStoreConfig() : config.copy();
        this.baseDir = Path.of(this.config.getBaseDir());
        if (this.config.isAutoCreate()) {
            try {
                Files.createDirectories(baseDir);
            } catch (IOException e) {
                throw new VKSecurityException("Create keystore dir failed: " + e.getMessage());
            }
        }
    }

    @Override
    public synchronized String getOrCreateAesKey(String keyId) {
        String id = normalizeKeyId(keyId);
        Path file = baseDir.resolve(id + ".aes.key");
        if (Files.exists(file)) {
            String encrypted = readText(file);
            return decryptValue(encrypted);
        }
        String key = VKAesCrypto.generateAesKeyBase64();
        writeText(file, encryptValue(key));
        return key;
    }

    @Override
    public synchronized VKRsaKeyPair getOrCreateRsaKeyPair(String keyId) {
        String id = normalizeKeyId(keyId);
        Path pub = baseDir.resolve(id + ".rsa.public.pem");
        Path pri = baseDir.resolve(id + ".rsa.private.pem");
        if (Files.exists(pub) && Files.exists(pri)) {
            return new VKRsaKeyPair(decryptValue(readText(pub)), decryptValue(readText(pri)));
        }
        VKRsaKeyPair pair = VKRsaCrypto.generateRsaKeyPair();
        writeText(pub, encryptValue(pair.getPublicKeyPem()));
        writeText(pri, encryptValue(pair.getPrivateKeyPem()));
        return pair;
    }

    @Override
    public synchronized void rotateAesKey(String keyId) {
        String id = normalizeKeyId(keyId);
        Path file = baseDir.resolve(id + ".aes.key");
        String key = VKAesCrypto.generateAesKeyBase64();
        writeText(file, encryptValue(key));
    }

    @Override
    public synchronized void rotateRsaKeyPair(String keyId) {
        String id = normalizeKeyId(keyId);
        VKRsaKeyPair pair = VKRsaCrypto.generateRsaKeyPair();
        Path pub = baseDir.resolve(id + ".rsa.public.pem");
        Path pri = baseDir.resolve(id + ".rsa.private.pem");
        writeText(pub, encryptValue(pair.getPublicKeyPem()));
        writeText(pri, encryptValue(pair.getPrivateKeyPem()));
    }

    private String encryptValue(String value) {
        return VKAesCrypto.encrypt(value, config.getMasterKey());
    }

    private String decryptValue(String encryptedValue) {
        return VKAesCrypto.decrypt(encryptedValue, config.getMasterKey());
    }

    private String readText(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new VKSecurityException("Read key file failed: " + e.getMessage());
        }
    }

    private void writeText(Path file, String value) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, value, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new VKSecurityException("Write key file failed: " + e.getMessage());
        }
    }

    private static String normalizeKeyId(String keyId) {
        if (keyId == null || keyId.isBlank()) {
            throw new VKSecurityException("keyId is blank");
        }
        String id = keyId.trim();
        if (!KEY_ID_PATTERN.matcher(id).matches()) {
            throw new VKSecurityException("keyId invalid: " + keyId);
        }
        return id;
    }
}
