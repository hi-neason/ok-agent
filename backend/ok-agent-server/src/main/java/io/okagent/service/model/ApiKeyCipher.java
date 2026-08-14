package io.okagent.service.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ApiKeyCipher {
  private final SecretKeySpec key;
  public ApiKeyCipher(@Value("${ok-agent.security.api-key-encryption-key:local-development-key}") String masterKey) {
    try { key = new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(masterKey.getBytes(StandardCharsets.UTF_8)), "AES"); }
    catch (Exception exception) { throw new IllegalStateException("Unable to initialize API key cipher", exception); }
  }
  public String encrypt(String plainText) { return crypt(Cipher.ENCRYPT_MODE, plainText); }
  public String decrypt(String cipherText) { return crypt(Cipher.DECRYPT_MODE, cipherText); }
  private String crypt(int mode, String value) { try { var cipher = Cipher.getInstance("AES/ECB/PKCS5Padding"); cipher.init(mode, key); var bytes = mode == Cipher.ENCRYPT_MODE ? value.getBytes(StandardCharsets.UTF_8) : Base64.getDecoder().decode(value); var result = cipher.doFinal(bytes); return mode == Cipher.ENCRYPT_MODE ? Base64.getEncoder().encodeToString(result) : new String(result, StandardCharsets.UTF_8); } catch (Exception exception) { throw new IllegalStateException("Unable to process API key", exception); } }
}
