package online.misterpilot.platform.entity;

import online.misterpilot.platform.enums.TransactionStatus;
import online.misterpilot.platform.enums.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for entity classes — builders, defaults, relationships.
 */
@DisplayName("Entities")
class EntityTests {

    @Nested
    @DisplayName("User")
    class UserTests {

        @Test
        @DisplayName("Should build with all fields")
        void shouldBuild() {
            User user = User.builder()
                    .id(1L)
                    .name("John")
                    .email("john-example-com")
                    .googleId("g-123")
                    .passwordHash("$2a$hash")
                    .build();

            assertThat(user.getId()).isEqualTo(1L);
            assertThat(user.getName()).isEqualTo("John");
            assertThat(user.getEmail()).isEqualTo("john-example-com");
            assertThat(user.getGoogleId()).isEqualTo("g-123");
            assertThat(user.getPasswordHash()).isEqualTo("$2a$hash");
        }

        @Test
        @DisplayName("Should set createdAt on persist")
        void shouldSetCreatedAt() {
            User user = new User();
            user.onCreate();

            assertThat(user.getCreatedAt()).isNotNull();
            assertThat(user.getCreatedAt()).isBeforeOrEqualTo(LocalDateTime.now());
        }

        @Test
        @DisplayName("Should allow null googleId (email/password user)")
        void shouldAllowNullGoogleId() {
            User user = User.builder()
                    .name("Alice")
                    .email("alice-example-com")
                    .passwordHash("hash")
                    .build();

            assertThat(user.getGoogleId()).isNull();
        }

        @Test
        @DisplayName("Should allow null passwordHash (Google-only user)")
        void shouldAllowNullPasswordHash() {
            User user = User.builder()
                    .name("Bob")
                    .email("bob-example-com")
                    .googleId("g-456")
                    .build();

            assertThat(user.getPasswordHash()).isNull();
        }
    }

    @Nested
    @DisplayName("ApiKey")
    class ApiKeyTests {

        @Test
        @DisplayName("Should build with all fields")
        void shouldBuild() {
            User user = User.builder().id(1L).email("x-example-com").name("X").build();

            var apiKey = new ApiKey();
            apiKey.setId(10L);
            apiKey.setHashValue("abc123hash");
            apiKey.setKeyPlaceholder("mp_sk_a3f2c8b1****c4b7");
            apiKey.setUser(user);
            apiKey.setActive(true);

            assertThat(apiKey.getId()).isEqualTo(10L);
            assertThat(apiKey.getHashValue()).isEqualTo("abc123hash");
            assertThat(apiKey.getKeyPlaceholder()).isEqualTo("mp_sk_a3f2c8b1****c4b7");
            assertThat(apiKey.getUser()).isEqualTo(user);
            assertThat(apiKey.getActive()).isTrue();
        }

        @Test
        @DisplayName("Should default active to true")
        void shouldDefaultActive() {
            var apiKey = new ApiKey();
            assertThat(apiKey.getActive()).isTrue();
        }

        @Test
        @DisplayName("Should set createdAt on persist")
        void shouldSetCreatedAt() {
            var apiKey = new ApiKey();
            apiKey.onCreate();

            assertThat(apiKey.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should allow lastUsedAt to be null")
        void shouldAllowNullLastUsedAt() {
            var apiKey = new ApiKey();
            assertThat(apiKey.getLastUsedAt()).isNull();
        }

        @Test
        @DisplayName("Should toggle active flag")
        void shouldToggleActive() {
            var apiKey = new ApiKey();
            assertThat(apiKey.getActive()).isTrue();

            apiKey.setActive(false);
            assertThat(apiKey.getActive()).isFalse();

            apiKey.setActive(true);
            assertThat(apiKey.getActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("Wallet")
    class WalletTests {

        @Test
        @DisplayName("Should build with zero balance")
        void shouldBuildWithZeroBalance() {
            User user = User.builder().id(1L).email("x-example-com").name("X").build();

            Wallet wallet = Wallet.builder()
                    .user(user)
                    .balance(BigDecimal.ZERO)
                    .build();

            assertThat(wallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(wallet.getUser()).isEqualTo(user);
        }

        @Test
        @DisplayName("Should set updatedAt on update")
        void shouldSetUpdatedAt() {
            Wallet wallet = new Wallet();
            wallet.onUpdate();

            assertThat(wallet.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should handle arbitrary balance")
        void shouldHandleBalance() {
            Wallet wallet = Wallet.builder()
                    .balance(new BigDecimal("1500.50"))
                    .build();

            assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("1500.50"));
        }

        @Test
        @DisplayName("Should handle high precision balance")
        void shouldHandleHighPrecision() {
            Wallet wallet = Wallet.builder()
                    .balance(new BigDecimal("9999999.99"))
                    .build();

            assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("9999999.99"));
        }
    }

    @Nested
    @DisplayName("Transaction")
    class TransactionTests {

        @Test
        @DisplayName("Should build with all fields")
        void shouldBuild() {
            Wallet wallet = Wallet.builder().id(1L).build();

            Transaction txn = Transaction.builder()
                    .id(100L)
                    .wallet(wallet)
                    .type(TransactionType.RECHARGE)
                    .status(TransactionStatus.PENDING)
                    .amount(new BigDecimal("500.00"))
                    .orderId("order_abc")
                    .paymentId("pay_xyz")
                    .build();

            assertThat(txn.getId()).isEqualTo(100L);
            assertThat(txn.getWallet()).isEqualTo(wallet);
            assertThat(txn.getType()).isEqualTo(TransactionType.RECHARGE);
            assertThat(txn.getStatus()).isEqualTo(TransactionStatus.PENDING);
            assertThat(txn.getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
            assertThat(txn.getOrderId()).isEqualTo("order_abc");
            assertThat(txn.getPaymentId()).isEqualTo("pay_xyz");
        }

        @Test
        @DisplayName("Should allow null wallet (for detached transactions)")
        void shouldAllowNullWallet() {
            Transaction txn = Transaction.builder()
                    .type(TransactionType.RECHARGE)
                    .status(TransactionStatus.SUCCESS)
                    .amount(BigDecimal.ONE)
                    .build();

            assertThat(txn.getWallet()).isNull();
        }

        @Test
        @DisplayName("Should set createdAt on persist")
        void shouldSetCreatedAt() {
            Transaction txn = new Transaction();
            txn.onCreate();

            assertThat(txn.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should transition status from PENDING to SUCCESS")
        void shouldTransitionStatus() {
            Transaction txn = Transaction.builder()
                    .status(TransactionStatus.PENDING)
                    .build();

            txn.setStatus(TransactionStatus.SUCCESS);
            assertThat(txn.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        }

        @Test
        @DisplayName("Should transition status from PENDING to FAILED")
        void shouldTransitionToFailed() {
            Transaction txn = Transaction.builder()
                    .status(TransactionStatus.PENDING)
                    .build();

            txn.setStatus(TransactionStatus.FAILED);
            assertThat(txn.getStatus()).isEqualTo(TransactionStatus.FAILED);
        }
    }

    @Nested
    @DisplayName("TokenUsage")
    class TokenUsageTests {

        @Test
        @DisplayName("Should build with all fields")
        void shouldBuild() {
            var apiKey = new ApiKey();
            apiKey.setId(1L);
            User user = User.builder().id(1L).email("x-example-com").name("X").build();

            TokenUsage usage = TokenUsage.builder()
                    .id(50L)
                    .apiKey(apiKey)
                    .user(user)
                    .model("deepseek-v4-pro")
                    .outputTokens(1500L)
                    .cacheHitTokens(300L)
                    .cacheMissTokens(200L)
                    .costUsd(new BigDecimal("0.0015"))
                    .costInr(new BigDecimal("0.14"))
                    .build();

            assertThat(usage.getId()).isEqualTo(50L);
            assertThat(usage.getApiKey()).isEqualTo(apiKey);
            assertThat(usage.getUser()).isEqualTo(user);
            assertThat(usage.getModel()).isEqualTo("deepseek-v4-pro");
            assertThat(usage.getOutputTokens()).isEqualTo(1500L);
            assertThat(usage.getCacheHitTokens()).isEqualTo(300L);
            assertThat(usage.getCacheMissTokens()).isEqualTo(200L);
            assertThat(usage.getCostUsd()).isEqualByComparingTo(new BigDecimal("0.0015"));
            assertThat(usage.getCostInr()).isEqualByComparingTo(new BigDecimal("0.14"));
        }

        @Test
        @DisplayName("Should default token counts to 0")
        void shouldDefaultTokensToZero() {
            TokenUsage usage = new TokenUsage();

            assertThat(usage.getOutputTokens()).isEqualTo(0L);
            assertThat(usage.getCacheHitTokens()).isEqualTo(0L);
            assertThat(usage.getCacheMissTokens()).isEqualTo(0L);
        }

        @Test
        @DisplayName("Should set createdAt on persist")
        void shouldSetCreatedAt() {
            TokenUsage usage = new TokenUsage();
            usage.onCreate();

            assertThat(usage.getCreatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("PasswordResetToken")
    class PasswordResetTokenTests {

        @Test
        @DisplayName("Should build with all fields")
        void shouldBuild() {
            LocalDateTime expiry = LocalDateTime.now().plusMinutes(15);

            PasswordResetToken token = PasswordResetToken.builder()
                    .id(1L)
                    .email("user-example-com")
                    .token("uuid-token-123")
                    .expiresAt(expiry)
                    .used(false)
                    .build();

            assertThat(token.getId()).isEqualTo(1L);
            assertThat(token.getEmail()).isEqualTo("user-example-com");
            assertThat(token.getToken()).isEqualTo("uuid-token-123");
            assertThat(token.getExpiresAt()).isEqualTo(expiry);
            assertThat(token.getUsed()).isFalse();
        }

        @Test
        @DisplayName("Should default 'used' to false")
        void shouldDefaultUsedToFalse() {
            PasswordResetToken token = new PasswordResetToken();
            assertThat(token.getUsed()).isFalse();
        }

        @Test
        @DisplayName("Should mark token as used")
        void shouldMarkAsUsed() {
            PasswordResetToken token = new PasswordResetToken();
            token.setUsed(true);
            assertThat(token.getUsed()).isTrue();
        }

        @Test
        @DisplayName("Should detect expiry")
        void shouldDetectExpiry() {
            PasswordResetToken token = PasswordResetToken.builder()
                    .expiresAt(LocalDateTime.now().minusMinutes(1))
                    .build();

            assertThat(token.getExpiresAt()).isBefore(LocalDateTime.now());
        }

        @Test
        @DisplayName("Should be valid when not expired")
        void shouldBeValidWhenNotExpired() {
            PasswordResetToken token = PasswordResetToken.builder()
                    .expiresAt(LocalDateTime.now().plusMinutes(15))
                    .build();

            assertThat(token.getExpiresAt()).isAfter(LocalDateTime.now());
        }
    }
}
