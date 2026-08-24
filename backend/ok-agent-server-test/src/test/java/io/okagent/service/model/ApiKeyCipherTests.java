package io.okagent.service.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class ApiKeyCipherTests {

    private static final String MASTER_KEY = "test-encryption-key";

    @Test
    void encryptsWithRandomizedAuthenticatedCiphertext() {
        ApiKeyCipher cipher = new ApiKeyCipher(MASTER_KEY);

        String first = cipher.encrypt("secret-value");
        String second = cipher.encrypt("secret-value");

        assertThat(first).startsWith("v2:").isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("secret-value");
        assertThat(cipher.decrypt(second)).isEqualTo("secret-value");
    }

    @Test
    void rejectsTamperedCiphertext() {
        ApiKeyCipher cipher = new ApiKeyCipher(MASTER_KEY);
        String encrypted = cipher.encrypt("secret-value");
        byte[] payload = Base64.getDecoder().decode(encrypted.substring("v2:".length()));
        payload[payload.length - 1] ^= 1;
        String tampered = "v2:" + Base64.getEncoder().encodeToString(payload);

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to process API key");
    }

    @Test
    void decryptsLegacyEcbCiphertext() throws Exception {
        ApiKeyCipher cipher = new ApiKeyCipher(MASTER_KEY);

        assertThat(cipher.decrypt(legacyEncrypt("legacy-secret"))).isEqualTo("legacy-secret");
    }

    private static String legacyEncrypt(String value) throws Exception {
        byte[] key = MessageDigest.getInstance("SHA-256").digest(MASTER_KEY.getBytes(StandardCharsets.UTF_8));
        var cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        return Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
