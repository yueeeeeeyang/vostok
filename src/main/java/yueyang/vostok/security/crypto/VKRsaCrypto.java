package yueyang.vostok.security.crypto;

import yueyang.vostok.security.exception.VKSecurityException;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class VKRsaCrypto {
    private static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String RSA_SIGN_ALGORITHM = "SHA256withRSA";

    private VKRsaCrypto() {
    }

    public static VKRsaKeyPair generateRsaKeyPair() {
        return generateRsaKeyPair(2048);
    }

    public static VKRsaKeyPair generateRsaKeyPair(int bits) {
        try {
            int keyBits = Math.max(2048, bits);
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(keyBits);
            KeyPair pair = generator.generateKeyPair();
            return new VKRsaKeyPair(toPublicPem(pair.getPublic()), toPrivatePem(pair.getPrivate()));
        } catch (Exception e) {
            throw new VKSecurityException("Generate RSA key pair failed: " + e.getMessage());
        }
    }

    public static String encryptByPublicKey(String plainText, String publicKeyPem) {
        try {
            PublicKey publicKey = parsePublicKey(publicKeyPem);
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encrypted = cipher.doFinal(valueBytes(plainText));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (VKSecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new VKSecurityException("RSA encrypt failed: " + e.getMessage());
        }
    }

    public static String decryptByPrivateKey(String cipherBase64, String privateKeyPem) {
        try {
            PrivateKey privateKey = parsePrivateKey(privateKeyPem);
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] plain = cipher.doFinal(Base64.getDecoder().decode(cipherBase64));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (VKSecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new VKSecurityException("RSA decrypt failed: " + e.getMessage());
        }
    }

    public static String sign(String text, String privateKeyPem) {
        try {
            PrivateKey privateKey = parsePrivateKey(privateKeyPem);
            Signature signature = Signature.getInstance(RSA_SIGN_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(valueBytes(text));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (VKSecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new VKSecurityException("RSA sign failed: " + e.getMessage());
        }
    }

    public static boolean verify(String text, String signatureBase64, String publicKeyPem) {
        try {
            PublicKey publicKey = parsePublicKey(publicKeyPem);
            Signature signature = Signature.getInstance(RSA_SIGN_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(valueBytes(text));
            return signature.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception e) {
            return false;
        }
    }

    private static PublicKey parsePublicKey(String publicKeyPem) {
        try {
            byte[] der = Base64.getDecoder().decode(stripPem(publicKeyPem));
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new VKSecurityException("Public key parse failed: " + e.getMessage());
        }
    }

    private static PrivateKey parsePrivateKey(String privateKeyPem) {
        try {
            byte[] der = Base64.getDecoder().decode(stripPem(privateKeyPem));
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new VKSecurityException("Private key parse failed: " + e.getMessage());
        }
    }

    private static String toPublicPem(PublicKey publicKey) {
        return pem("PUBLIC KEY", publicKey.getEncoded());
    }

    private static String toPrivatePem(PrivateKey privateKey) {
        return pem("PRIVATE KEY", privateKey.getEncoded());
    }

    private static String pem(String type, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----";
    }

    private static String stripPem(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new VKSecurityException("PEM is blank");
        }
        return pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
    }

    private static byte[] valueBytes(String value) {
        return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
    }
}
