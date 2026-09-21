package com.knowledgegraph.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 密钥加密存储单元测试（AES-GCM，无数据库依赖）。
 */
class SecretCipherTest {

    private final SecretCipher cipher = new SecretCipher("unit-test-master-key");

    @Test
    void encryptThenDecryptRoundTrips() {
        String secret = "sk-abc123XYZ/密文测试";
        String encrypted = cipher.encrypt(secret);

        assertNotEquals(secret, encrypted);
        assertEquals(secret, cipher.decrypt(encrypted));
    }

    @Test
    void encryptedValueDiffersBetweenCallsDueToRandomIv() {
        String secret = "same-plain-text";
        assertNotEquals(cipher.encrypt(secret), cipher.encrypt(secret));
    }

    @Test
    void decryptingWithDifferentMasterKeyReturnsEmpty() {
        SecretCipher other = new SecretCipher("another-master-key");
        String encrypted = cipher.encrypt("top-secret");
        assertEquals("", other.decrypt(encrypted));
    }

    @Test
    void blankValuesStayBlank() {
        assertEquals("", cipher.encrypt(null));
        assertEquals("", cipher.encrypt(""));
        assertEquals("", cipher.decrypt(null));
        assertEquals("", cipher.decrypt(""));
    }

    @Test
    void encryptedOutputIsBase64WithIvPrefix() {
        String encrypted = cipher.encrypt("payload");
        byte[] data = java.util.Base64.getDecoder().decode(encrypted);
        assertTrue(data.length > 12, "输出应包含 12 字节 IV 前缀");
    }
}
