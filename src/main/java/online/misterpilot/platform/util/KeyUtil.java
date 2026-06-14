package online.misterpilot.platform.util;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

@Component
public class KeyUtil {

    private static final String KEY_PREFIX = "mp_sk_";
    private static final int RANDOM_BYTES = 24; // 24 bytes → 48 hex chars
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ==================== Key Generation ====================

    /**
     * Generates a new MisterPilot API key.
     * Format: mp_sk_<48 random hex chars>
     * Example: mp_sk_a3f2c8b1e9d4f7a6c5b2e8d1f4a7c3b9e6d2f8a1c4b7
     */
    public String generateApiKey() {
        byte[] bytes = new byte[RANDOM_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        String randomHex = HexFormat.of().formatHex(bytes);
        return KEY_PREFIX + randomHex;
    }

    // ==================== Placeholder ====================

    /**
     * Builds a safe display placeholder from a plain key.
     * Format: mp_sk_<first 8 random chars>****<last 4 chars>
     * Example: mp_sk_a3f2c8b1****c4b7
     */
    public String buildPlaceholder(String plainKey) {
        String randomPart = plainKey.substring(KEY_PREFIX.length()); // 48 hex chars
        return KEY_PREFIX
                + randomPart.substring(0, 8)
                + "****"
                + randomPart.substring(randomPart.length() - 4);
    }

    // ==================== Hashing ====================

    /**
     * Hashes a plain-text API key with SHA-256.
     * Deterministic — same input always produces the same hash,
     * so we can look it up via ApiKeyRepository.findByHashValue(hash).
     */
    public String hashApiKey(String plainKey) {
        byte[] hash = sha256(plainKey.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    // ==================== Verification ====================

    /**
     * Verifies a plain-text API key against a stored SHA-256 hash.
     * Uses constant-time comparison to prevent timing attacks.
     */
    public boolean verifyApiKey(String plainKey, String storedHash) {
        String computedHash = hashApiKey(plainKey);
        return MessageDigest.isEqual(
                computedHash.getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }

    // ==================== Internal ====================

    private byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

}
