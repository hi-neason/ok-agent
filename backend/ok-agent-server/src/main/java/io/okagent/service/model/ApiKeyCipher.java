package io.okagent.service.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ApiKeyCipher {
    private static final String CURRENT_PREFIX = "v2:";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public ApiKeyCipher(@Value("${ok-agent.security.api-key-encryption-key:local-development-key}") String masterKey) {
        try {
            key = new SecretKeySpec(
                    MessageDigest.getInstance("SHA-256").digest(masterKey.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to initialize API key cipher", exception);
        }
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            RANDOM.nextBytes(iv);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return CURRENT_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception exception) {
            throw unableToProcess(exception);
        }
    }

    public String decrypt(String cipherText) {
        return cipherText.startsWith(CURRENT_PREFIX) ? decryptCurrent(cipherText) : decryptLegacy(cipherText);
    }

    private String decryptCurrent(String cipherText) {
        try {
            byte[] payload = Base64.getDecoder().decode(cipherText.substring(CURRENT_PREFIX.length()));
            if (payload.length <= GCM_IV_BYTES) {
                throw new IllegalArgumentException("Encrypted payload is too short");
            }
            byte[] iv = Arrays.copyOfRange(payload, 0, GCM_IV_BYTES);
            byte[] encrypted = Arrays.copyOfRange(payload, GCM_IV_BYTES, payload.length);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw unableToProcess(exception);
        }
    }

    /** Reads ciphertext created before the authenticated v2 format was introduced. */
    private String decryptLegacy(String cipherText) {
        try {
            var cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key);
            return new String(cipher.doFinal(Base64.getDecoder().decode(cipherText)), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw unableToProcess(exception);
        }
    }

    private static IllegalStateException unableToProcess(Exception exception) {
        return new IllegalStateException("Unable to process API key", exception);
    }
}
