package online.misterpilot.platform.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link KeyUtil}.
 * <p>
 * KeyUtil is a pure utility — no Spring context needed.
 */
@DisplayName("KeyUtil")
class KeyUtilTest {

    private KeyUtil keyUtil;

    @BeforeEach
    void setUp() {
        keyUtil = new KeyUtil();
    }

    // ── Key Generation ────────────────────────────────────

    @Nested
    @DisplayName("generateApiKey()")
    class GenerateApiKey {

        @Test
        @DisplayName("Should produce a key starting with mp_sk_")
        void shouldStartWithPrefix() {
            String key = keyUtil.generateApiKey();
            assertThat(key).startsWith("mp_sk_");
        }

        @Test
        @DisplayName("Should produce a key of length 6+48 = 54 characters")
        void shouldHaveCorrectLength() {
            String key = keyUtil.generateApiKey();
            assertThat(key).hasSize(54); // "mp_sk_" (6) + 48 hex chars
        }

        @Test
        @DisplayName("Should produce unique keys across multiple generations")
        void shouldBeUnique() {
            String key1 = keyUtil.generateApiKey();
            String key2 = keyUtil.generateApiKey();
            String key3 = keyUtil.generateApiKey();

            assertThat(key1)
                    .isNotEqualTo(key2)
                    .isNotEqualTo(key3);
            assertThat(key2).isNotEqualTo(key3);
        }

        @Test
        @DisplayName("Should contain only hexadecimal characters after prefix")
        void shouldContainOnlyHexAfterPrefix() {
            String key = keyUtil.generateApiKey();
            String randomPart = key.substring("mp_sk_".length());

            assertThat(randomPart).matches("^[0-9a-f]{48}$");
        }

        @Test
        @DisplayName("Should produce keys with high entropy — no repeating patterns")
        void shouldHaveHighEntropy() {
            // Generate many keys and verify the first 8 chars aren't all identical
            String key1 = keyUtil.generateApiKey();
            String key2 = keyUtil.generateApiKey();
            String key3 = keyUtil.generateApiKey();

            String prefix1 = key1.substring("mp_sk_".length(), "mp_sk_".length() + 8);
            String prefix2 = key2.substring("mp_sk_".length(), "mp_sk_".length() + 8);
            String prefix3 = key3.substring("mp_sk_".length(), "mp_sk_".length() + 8);

            // At least 2 out of 3 should be different (extremely likely with SecureRandom)
            boolean allSame = prefix1.equals(prefix2) && prefix2.equals(prefix3);
            assertThat(allSame).isFalse();
        }
    }

    // ── Placeholder Generation ───────────────────────────────

    @Nested
    @DisplayName("buildPlaceholder()")
    class BuildPlaceholder {

        @Test
        @DisplayName("Should produce placeholder starting with mp_sk_")
        void shouldStartWithPrefix() {
            String key = "mp_sk_a3f2c8b1e9d4f7a6c5b2e8d1f4a7c3b9e6d2f8a1c4b7";
            String placeholder = keyUtil.buildPlaceholder(key);
            assertThat(placeholder).startsWith("mp_sk_");
        }

        @Test
        @DisplayName("Should show first 8 characters of the random part")
        void shouldShowFirst8Chars() {
            String key = "mp_sk_a3f2c8b1e9d4f7a6c5b2e8d1f4a7c3b9e6d2f8a1c4b7";
            String placeholder = keyUtil.buildPlaceholder(key);
            // After "mp_sk_", the next 8 chars should be visible
            assertThat(placeholder).contains("a3f2c8b1");
        }

        @Test
        @DisplayName("Should show last 4 characters of the random part")
        void shouldShowLast4Chars() {
            String key = "mp_sk_a3f2c8b1e9d4f7a6c5b2e8d1f4a7c3b9e6d2f8a1c4b7";
            String placeholder = keyUtil.buildPlaceholder(key);
            assertThat(placeholder).endsWith("c4b7");
        }

        @Test
        @DisplayName("Should contain **** between visible parts")
        void shouldContainAsterisks() {
            String key = "mp_sk_a3f2c8b1e9d4f7a6c5b2e8d1f4a7c3b9e6d2f8a1c4b7";
            String placeholder = keyUtil.buildPlaceholder(key);
            assertThat(placeholder).contains("****");
        }

        @Test
        @DisplayName("Should produce expected format: mp_sk_XXXXXXXX****YYYY")
        void shouldMatchExpectedFormat() {
            String key = "mp_sk_a3f2c8b1e9d4f7a6c5b2e8d1f4a7c3b9e6d2f8a1c4b7";
            String placeholder = keyUtil.buildPlaceholder(key);

            assertThat(placeholder).matches("^mp_sk_[0-9a-f]{8}\\*\\*\\*\\*[0-9a-f]{4}$");
            assertThat(placeholder).isEqualTo("mp_sk_a3f2c8b1****c4b7");
        }

        @Test
        @DisplayName("Should mask the middle portion of the key")
        void shouldMaskMiddle() {
            String key = "mp_sk_012345670123456701234567012345670123456701234567";
            String placeholder = keyUtil.buildPlaceholder(key);

            // Only 6 + 8 + 4 + 4 = 22 visible chars
            assertThat(placeholder).hasSize(22);
            // Verify the middle is fully masked (only one "****")
            long asteriskBlocks = placeholder.chars()
                    .filter(c -> c == '*')
                    .count();
            assertThat(asteriskBlocks).isEqualTo(4);
        }

        @Test
        @DisplayName("Should handle any valid key format")
        void shouldHandleAnyValidKey() {
            for (int i = 0; i < 20; i++) {
                String key = keyUtil.generateApiKey();
                String placeholder = keyUtil.buildPlaceholder(key);

                // Should not throw, should have correct format
                assertThat(placeholder).matches("^mp_sk_[0-9a-f]{8}\\*\\*\\*\\*[0-9a-f]{4}$");
            }
        }
    }

    // ── Hashing ────────────────────────────────────────────

    @Nested
    @DisplayName("hashApiKey()")
    class HashApiKey {

        @Test
        @DisplayName("Should produce a 64-character hex string (SHA-256)")
        void shouldProduceSha256Length() {
            String key = "mp_sk_a3f2c8b1e9d4f7a6c5b2e8d1f4a7c3b9e6d2f8a1c4b7";
            String hash = keyUtil.hashApiKey(key);

            assertThat(hash).hasSize(64); // SHA-256 → 32 bytes → 64 hex chars
        }

        @Test
        @DisplayName("Should be deterministic — same input → same hash")
        void shouldBeDeterministic() {
            String key = "mp_sk_test1234abcd5678efgh9012ijkl3456mnop7890qrstuvwx";

            String hash1 = keyUtil.hashApiKey(key);
            String hash2 = keyUtil.hashApiKey(key);
            String hash3 = keyUtil.hashApiKey(key);

            assertThat(hash1).isEqualTo(hash2).isEqualTo(hash3);
        }

        @Test
        @DisplayName("Should produce different hashes for different inputs")
        void shouldProduceDifferentHashes() {
            String key1 = "mp_sk_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
            String key2 = "mp_sk_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

            String hash1 = keyUtil.hashApiKey(key1);
            String hash2 = keyUtil.hashApiKey(key2);

            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("Should contain only hexadecimal characters")
        void shouldContainOnlyHex() {
            String key = keyUtil.generateApiKey();
            String hash = keyUtil.hashApiKey(key);

            assertThat(hash).matches("^[0-9a-f]{64}$");
        }

        @Test
        @DisplayName("Should produce same hash regardless of case in hashing (method is case-sensitive)")
        void shouldBeCaseSensitive() {
            String lowercaseKey = "mp_sk_abc";
            String uppercaseKey = "mp_sk_ABC";

            String hashLower = keyUtil.hashApiKey(lowercaseKey);
            String hashUpper = keyUtil.hashApiKey(uppercaseKey);

            // Different case → different bytes → different hash
            assertThat(hashLower).isNotEqualTo(hashUpper);
        }
    }

    // ── Verification ───────────────────────────────────────

    @Nested
    @DisplayName("verifyApiKey()")
    class VerifyApiKey {

        @Test
        @DisplayName("Should return true when plain key matches stored hash")
        void shouldVerifyMatchingKey() {
            String plainKey = "mp_sk_a3f2c8b1e9d4f7a6c5b2e8d1f4a7c3b9e6d2f8a1c4b7";
            String storedHash = keyUtil.hashApiKey(plainKey);

            assertThat(keyUtil.verifyApiKey(plainKey, storedHash)).isTrue();
        }

        @Test
        @DisplayName("Should return false when plain key does not match stored hash")
        void shouldRejectNonMatchingKey() {
            String plainKey = "mp_sk_a3f2c8b1e9d4f7a6c5b2e8d1f4a7c3b9e6d2f8a1c4b7";
            String wrongKey = "mp_sk_ffffffffffffffffffffffffffffffffffffffffffffffff";
            String storedHash = keyUtil.hashApiKey(plainKey);

            assertThat(keyUtil.verifyApiKey(wrongKey, storedHash)).isFalse();
        }

        @Test
        @DisplayName("Should return false for an obviously invalid hash")
        void shouldRejectInvalidHash() {
            String plainKey = keyUtil.generateApiKey();

            assertThat(keyUtil.verifyApiKey(plainKey, "not-a-valid-sha256-hash-at-all")).isFalse();
        }

        @Test
        @DisplayName("Should return false for empty strings")
        void shouldRejectEmpty() {
            String plainKey = keyUtil.generateApiKey();
            String storedHash = keyUtil.hashApiKey(plainKey);

            assertThat(keyUtil.verifyApiKey("", storedHash)).isFalse();
        }

        @Test
        @DisplayName("Should return true for generated + verified round-trip")
        void shouldRoundTrip() {
            for (int i = 0; i < 10; i++) {
                String plainKey = keyUtil.generateApiKey();
                String storedHash = keyUtil.hashApiKey(plainKey);
                assertThat(keyUtil.verifyApiKey(plainKey, storedHash)).isTrue();
            }
        }

        @Test
        @DisplayName("Should reject keys that differ by one character")
        void shouldRejectOffByOne() {
            String plainKey = keyUtil.generateApiKey();
            String storedHash = keyUtil.hashApiKey(plainKey);

            // Flip last character
            char[] chars = plainKey.toCharArray();
            chars[chars.length - 1] = (chars[chars.length - 1] == 'a') ? 'b' : 'a';
            String alteredKey = new String(chars);

            assertThat(keyUtil.verifyApiKey(alteredKey, storedHash)).isFalse();
        }
    }
}
