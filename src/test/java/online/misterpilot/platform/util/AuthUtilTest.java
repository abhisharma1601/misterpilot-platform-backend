package online.misterpilot.platform.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import online.misterpilot.platform.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AuthUtil}.
 */
@DisplayName("AuthUtil")
class AuthUtilTest {

    private AuthUtil authUtil;

    private static final String TEST_JWT_SECRET =
            "ThisIsA32CharacterLongSecret!012";

    @BeforeEach
    void setUp() {
        authUtil = new AuthUtil(TEST_JWT_SECRET);
    }

    // ── JWT Generation ──────────────────────────────────────

    @Nested
    @DisplayName("generateJwt()")
    class GenerateJwt {

        @Test
        @DisplayName("Should produce a non-null, non-blank JWT string")
        void shouldProduceToken() {
            User user = User.builder()
                    .id(1L)
                    .email("test-example-com")
                    .name("Test User")
                    .build();

            String token = authUtil.generateJwt(user);

            assertThat(token).isNotBlank();
            assertThat(token.split("\\.")).hasSize(3);
        }

        @Test
        @DisplayName("Should embed correct user claims")
        void shouldEmbedClaims() {
            User user = User.builder()
                    .id(42L)
                    .email("alice-misterpilot-online")
                    .name("Alice")
                    .googleId("google-12345")
                    .build();

            String token = authUtil.generateJwt(user);
            Claims claims = authUtil.parseJwt(token);

            assertThat(claims.getSubject()).isEqualTo("42");
            assertThat(claims.get("email", String.class)).isEqualTo("alice-misterpilot-online");
            assertThat(claims.get("name", String.class)).isEqualTo("Alice");
            assertThat(claims.get("googleId", String.class)).isEqualTo("google-12345");
        }

        @Test
        @DisplayName("Should set issued-at and expiration claims")
        void shouldSetTimeClaims() {
            User user = User.builder()
                    .id(1L)
                    .email("test-example-com")
                    .name("Test")
                    .build();

            String token = authUtil.generateJwt(user);
            Claims claims = authUtil.parseJwt(token);

            assertThat(claims.getIssuedAt()).isNotNull();
            assertThat(claims.getExpiration()).isNotNull();

            assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());

            long diffMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
            long sevenDaysMs = 7L * 24 * 60 * 60 * 1000;
            assertThat(diffMs).isEqualTo(sevenDaysMs);
        }

        @Test
        @DisplayName("Should generate distinct tokens for different users")
        void shouldBeDistinctPerUser() {
            User user1 = User.builder().id(1L).email("a-b-com").name("A").build();
            User user2 = User.builder().id(2L).email("c-d-com").name("B").build();

            String token1 = authUtil.generateJwt(user1);
            String token2 = authUtil.generateJwt(user2);

            assertThat(token1).isNotEqualTo(token2);
        }

        @Test
        @DisplayName("Should handle user with null googleId")
        void shouldHandleNullGoogleId() {
            User user = User.builder()
                    .id(1L)
                    .email("test-example-com")
                    .name("Test")
                    .googleId(null)
                    .build();

            String token = authUtil.generateJwt(user);
            Claims claims = authUtil.parseJwt(token);

            assertThat(claims.get("googleId", String.class)).isNull();
        }
    }

    // ── JWT Parsing & Validation ────────────────────────────

    @Nested
    @DisplayName("parseJwt()")
    class ParseJwt {

        @Test
        @DisplayName("Should successfully parse a valid token")
        void shouldParseValidToken() {
            User user = User.builder().id(1L).email("x-y-com").name("X").build();
            String token = authUtil.generateJwt(user);

            Claims claims = authUtil.parseJwt(token);
            assertThat(claims).isNotNull();
        }

        @Test
        @DisplayName("Should throw for an invalid / tampered token")
        void shouldThrowForTamperedToken() {
            User user = User.builder().id(1L).email("x-y-com").name("X").build();
            String token = authUtil.generateJwt(user);

            String tampered = token.substring(0, token.length() - 5) + "XXXXX";

            assertThatThrownBy(() -> authUtil.parseJwt(tampered))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("Should throw for completely garbage input")
        void shouldThrowForGarbage() {
            assertThatThrownBy(() -> authUtil.parseJwt("not.a.valid.token"))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("Should throw for empty string")
        void shouldThrowForEmpty() {
            assertThatThrownBy(() -> authUtil.parseJwt(""))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    // ── Token Validation ────────────────────────────────────

    @Nested
    @DisplayName("validateToken()")
    class ValidateToken {

        @Test
        @DisplayName("Should return null for a valid token")
        void shouldReturnNullForValid() {
            User user = User.builder().id(1L).email("a-b-com").name("A").build();
            String token = authUtil.generateJwt(user);

            assertThat(authUtil.validateToken(token)).isNull();
        }

        @Test
        @DisplayName("Should return error message for null token")
        void shouldReturnErrorForNull() {
            assertThat(authUtil.validateToken(null)).isEqualTo("Missing token");
        }

        @Test
        @DisplayName("Should return error message for blank token")
        void shouldReturnErrorForBlank() {
            assertThat(authUtil.validateToken("   ")).isEqualTo("Missing token");
        }

        @Test
        @DisplayName("Should return error for tampered token")
        void shouldReturnErrorForTampered() {
            User user = User.builder().id(1L).email("x-y-com").name("X").build();
            String token = authUtil.generateJwt(user);
            String tampered = token.substring(0, token.length() - 5) + "XXXXX";

            String result = authUtil.validateToken(tampered);
            assertThat(result).isNotNull();
            assertThat(result).containsIgnoringCase("signature");
        }

        @Test
        @DisplayName("Should return error for malformed token")
        void shouldReturnErrorForMalformed() {
            String result = authUtil.validateToken("this.is.malformed");
            assertThat(result).isNotNull();
            assertThat(result).containsIgnoringCase("malformed");
        }
    }

    @Nested
    @DisplayName("isJwtValid()")
    class IsJwtValid {

        @Test
        @DisplayName("Should return true for a valid token")
        void shouldReturnTrue() {
            User user = User.builder().id(1L).email("a-b-com").name("A").build();
            String token = authUtil.generateJwt(user);

            assertThat(authUtil.isJwtValid(token)).isTrue();
        }

        @Test
        @DisplayName("Should return false for null token")
        void shouldReturnFalseForNull() {
            assertThat(authUtil.isJwtValid(null)).isFalse();
        }

        @Test
        @DisplayName("Should return false for garbage")
        void shouldReturnFalseForGarbage() {
            assertThat(authUtil.isJwtValid("garbage")).isFalse();
        }
    }

    // ── User ID Extraction ──────────────────────────────────

    @Nested
    @DisplayName("getUserIdFromJwt()")
    class GetUserIdFromJwt {

        @Test
        @DisplayName("Should extract correct user ID")
        void shouldExtractUserId() {
            User user = User.builder().id(999L).email("x-y-com").name("X").build();
            String token = authUtil.generateJwt(user);

            assertThat(authUtil.getUserIdFromJwt(token)).isEqualTo(999L);
        }

        @Test
        @DisplayName("Should extract IDs for different users")
        void shouldExtractDifferentIds() {
            User user1 = User.builder().id(100L).email("a-b-com").name("A").build();
            User user2 = User.builder().id(200L).email("c-d-com").name("C").build();

            assertThat(authUtil.getUserIdFromJwt(authUtil.generateJwt(user1))).isEqualTo(100L);
            assertThat(authUtil.getUserIdFromJwt(authUtil.generateJwt(user2))).isEqualTo(200L);
        }

        @Test
        @DisplayName("Should throw for invalid token")
        void shouldThrowForInvalid() {
            assertThatThrownBy(() -> authUtil.getUserIdFromJwt("garbage.token.here"))
                    .isInstanceOf(JwtException.class);
        }
    }

    // ── Password Hashing ────────────────────────────────────

    @Nested
    @DisplayName("hashPassword()")
    class HashPassword {

        @Test
        @DisplayName("Should produce a BCrypt hash")
        void shouldProduceBcryptHash() {
            String hash = authUtil.hashPassword("MySecurePassword123!");
            assertThat(hash).startsWith("$2a$");
        }

        @Test
        @DisplayName("Should produce different hashes for same password (salt)")
        void shouldUseRandomSalt() {
            String hash1 = authUtil.hashPassword("password");
            String hash2 = authUtil.hashPassword("password");

            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("Should produce different hashes for different passwords")
        void shouldProduceDifferentHashesForDifferentPasswords() {
            String hash1 = authUtil.hashPassword("alpha");
            String hash2 = authUtil.hashPassword("beta");
            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("Should handle passwords with special characters")
        void shouldHandleSpecialChars() {
            String password = "Pssw0rd!#$%^&*()_+-=[]{}|;:',.<>?/~`";
            String hash = authUtil.hashPassword(password);
            assertThat(hash).startsWith("$2a$");
        }

        @Test
        @DisplayName("Should handle max-length passwords (BCrypt 72 byte limit)")
        void shouldHandleLongPasswords() {
            // BCrypt has a 72-byte limit. 71 chars should work fine.
            String password = "a".repeat(71);
            String hash = authUtil.hashPassword(password);
            assertThat(hash).startsWith("$2a$");
        }

        @Test
        @DisplayName("Should handle unicode passwords")
        void shouldHandleUnicode() {
            String password = "PASSWORD 123! TEST";
            String hash = authUtil.hashPassword(password);
            assertThat(hash).startsWith("$2a$");
        }
    }

    // ── Password Verification ───────────────────────────────

    @Nested
    @DisplayName("verifyPassword()")
    class VerifyPassword {

        @Test
        @DisplayName("Should return true for correct password")
        void shouldVerifyCorrectPassword() {
            String plainPassword = "CorrectHorseBatteryStaple";
            String hash = authUtil.hashPassword(plainPassword);

            assertThat(authUtil.verifyPassword(plainPassword, hash)).isTrue();
        }

        @Test
        @DisplayName("Should return false for wrong password")
        void shouldRejectWrongPassword() {
            String hash = authUtil.hashPassword("CorrectPassword");

            assertThat(authUtil.verifyPassword("WrongPassword", hash)).isFalse();
        }

        @Test
        @DisplayName("Should be case-sensitive")
        void shouldBeCaseSensitive() {
            String hash = authUtil.hashPassword("Password123");

            assertThat(authUtil.verifyPassword("password123", hash)).isFalse();
        }

        @Test
        @DisplayName("Should handle whitespace differences")
        void shouldDetectWhitespaceDifference() {
            String hash = authUtil.hashPassword(" mypassword ");

            assertThat(authUtil.verifyPassword("mypassword", hash)).isFalse();
        }

        @Test
        @DisplayName("Should throw for null password")
        void shouldHandleNullPassword() {
            String hash = authUtil.hashPassword("something");

            assertThatThrownBy(() -> authUtil.verifyPassword(null, hash))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should handle null hash gracefully (returns false)")
        void shouldHandleNullHash() {
            // BCryptPasswordEncoder.matches() may throw or return false depending on version.
            // Just verify it doesn't crash unexpectedly.
            try {
                boolean result = authUtil.verifyPassword("password", null);
                assertThat(result).isFalse();
            } catch (IllegalArgumentException e) {
                // also acceptable — Spring Security may reject null hash
                assertThat(e.getMessage()).containsIgnoringCase("encoded");
            }
        }
    }

    // ── Cross-AuthUtil Isolation ────────────────────────────

    @Nested
    @DisplayName("Cross-instance isolation")
    class CrossInstanceIsolation {

        @Test
        @DisplayName("Tokens signed by one AuthUtil should be invalid for another (different secret)")
        void tokensFromDifferentSecretsShouldBeInvalid() {
            AuthUtil auth1 = new AuthUtil("SecretKeyOne__123456789012345678");
            AuthUtil auth2 = new AuthUtil("SecretKeyTwo__123456789012345678");

            User user = User.builder().id(1L).email("a-b-com").name("A").build();
            String token = auth1.generateJwt(user);

            assertThat(auth2.isJwtValid(token)).isFalse();
        }

        @Test
        @DisplayName("Tokens signed by one AuthUtil should be valid for another (same secret)")
        void tokensFromSameSecretShouldBeValid() {
            AuthUtil auth1 = new AuthUtil(TEST_JWT_SECRET);
            AuthUtil auth2 = new AuthUtil(TEST_JWT_SECRET);

            User user = User.builder().id(1L).email("a-b-com").name("A").build();
            String token = auth1.generateJwt(user);

            assertThat(auth2.isJwtValid(token)).isTrue();
        }
    }
}
