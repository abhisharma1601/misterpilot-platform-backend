package online.misterpilot.platform.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.misterpilot.platform.dto.request.*;
import online.misterpilot.platform.dto.response.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for DTO classes — builder patterns, JSON serialization, null safety.
 */
@DisplayName("DTOs")
class DtoTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Request DTOs ────────────────────────────────────────

    @Nested
    @DisplayName("RegisterRequest")
    class RegisterRequestTests {

        @Test
        @DisplayName("Should hold name, email, password")
        void shouldHoldFields() {
            RegisterRequest req = new RegisterRequest();
            req.setName("Alice");
            req.setEmail("alice@test.com");
            req.setPassword("secret123");

            assertThat(req.getName()).isEqualTo("Alice");
            assertThat(req.getEmail()).isEqualTo("alice@test.com");
            assertThat(req.getPassword()).isEqualTo("secret123");
        }

        @Test
        @DisplayName("Should allow null password (Google registration)")
        void shouldAllowNullPassword() {
            RegisterRequest req = new RegisterRequest();
            req.setName("Bob");
            req.setEmail("bob@test.com");

            assertThat(req.getPassword()).isNull();
        }
    }

    @Nested
    @DisplayName("LoginRequest")
    class LoginRequestTests {

        @Test
        @DisplayName("Should hold email and password")
        void shouldHoldCredentials() {
            LoginRequest req = new LoginRequest();
            req.setEmail("user@test.com");
            req.setPassword("pass123");

            assertThat(req.getEmail()).isEqualTo("user@test.com");
            assertThat(req.getPassword()).isEqualTo("pass123");
        }
    }

    @Nested
    @DisplayName("UsageChargeRequest")
    class UsageChargeRequestTests {

        @Test
        @DisplayName("Should build and hold all fields")
        void shouldBuild() {
            UsageChargeRequest req = UsageChargeRequest.builder()
                    .apiKey("mp_sk_testtest1234")
                    .costInr(new BigDecimal("1.25"))
                    .model("deepseek-v4-pro")
                    .outputTokens(1000)
                    .cacheHitTokens(500)
                    .cacheMissTokens(200)
                    .build();

            assertThat(req.getApiKey()).isEqualTo("mp_sk_testtest1234");
            assertThat(req.getCostInr()).isEqualByComparingTo(new BigDecimal("1.25"));
            assertThat(req.getModel()).isEqualTo("deepseek-v4-pro");
            assertThat(req.getOutputTokens()).isEqualTo(1000);
            assertThat(req.getCacheHitTokens()).isEqualTo(500);
            assertThat(req.getCacheMissTokens()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("CostCalculationRequest")
    class CostCalculationRequestTests {

        @Test
        @DisplayName("Should build with all fields")
        void shouldBuild() {
            CostCalculationRequest req = CostCalculationRequest.builder()
                    .outputTokens(500)
                    .cacheHitTokens(100)
                    .cacheMissTokens(50)
                    .model("deepseek-v4-flash")
                    .keyType("misterpilot")
                    .build();

            assertThat(req.getOutputTokens()).isEqualTo(500);
            assertThat(req.getCacheHitTokens()).isEqualTo(100);
            assertThat(req.getCacheMissTokens()).isEqualTo(50);
            assertThat(req.getModel()).isEqualTo("deepseek-v4-flash");
            assertThat(req.getKeyType()).isEqualTo("misterpilot");
        }
    }

    @Nested
    @DisplayName("CreateOrderRequest")
    class CreateOrderRequestTests {

        @Test
        @DisplayName("Should hold amount")
        void shouldHoldAmount() {
            CreateOrderRequest req = CreateOrderRequest.builder()
                    .amount(new BigDecimal("500.00"))
                    .build();

            assertThat(req.getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
        }
    }

    @Nested
    @DisplayName("ForgotPasswordRequest")
    class ForgotPasswordRequestTests {

        @Test
        @DisplayName("Should hold email")
        void shouldHoldEmail() {
            ForgotPasswordRequest req = new ForgotPasswordRequest();
            req.setEmail("reset@test.com");
            assertThat(req.getEmail()).isEqualTo("reset@test.com");
        }
    }

    @Nested
    @DisplayName("ResetPasswordRequest")
    class ResetPasswordRequestTests {

        @Test
        @DisplayName("Should hold token and newPassword")
        void shouldHoldFields() {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setToken("reset-token-123");
            req.setNewPassword("NewSecurePass1!");

            assertThat(req.getToken()).isEqualTo("reset-token-123");
            assertThat(req.getNewPassword()).isEqualTo("NewSecurePass1!");
        }
    }

    @Nested
    @DisplayName("DisableKeyRequest")
    class DisableKeyRequestTests {

        @Test
        @DisplayName("Should hold apiKeyId")
        void shouldHoldApiKeyId() {
            DisableKeyRequest req = new DisableKeyRequest();
            req.setApiKeyId(42);

            assertThat(req.getApiKeyId()).isEqualTo(42);
        }

        @Test
        @DisplayName("To string should mask apiKeyId")
        void shouldMaskInToString() {
            DisableKeyRequest req = new DisableKeyRequest();
            req.setApiKeyId(42);

            assertThat(req.toString()).doesNotContain("42");
            assertThat(req.toString()).contains("***");
        }
    }

    @Nested
    @DisplayName("VerifyKeyRequest")
    class VerifyKeyRequestTests {

        @Test
        @DisplayName("Should hold apiKey")
        void shouldHoldApiKey() {
            VerifyKeyRequest req = new VerifyKeyRequest();
            req.setApiKey("mp_sk_testkey");

            assertThat(req.getApiKey()).isEqualTo("mp_sk_testkey");
        }

        @Test
        @DisplayName("To string should mask the key")
        void shouldMaskInToString() {
            VerifyKeyRequest req = new VerifyKeyRequest();
            req.setApiKey("mp_sk_secretkey1234567890abcdef1234567890abcdef");

            String str = req.toString();
            assertThat(str).doesNotContain("secretkey");
            assertThat(str).containsAnyOf("***", "API_KEY");
        }
    }

    // ── Response DTOs ───────────────────────────────────────

    @Nested
    @DisplayName("LoginResponse")
    class LoginResponseTests {

        @Test
        @DisplayName("Should build with all fields")
        void shouldBuild() {
            LoginResponse resp = LoginResponse.builder()
                    .token("jwt.token.here")
                    .userId(1L)
                    .name("John")
                    .email("john@test.com")
                    .build();

            assertThat(resp.getToken()).isEqualTo("jwt.token.here");
            assertThat(resp.getUserId()).isEqualTo(1L);
            assertThat(resp.getName()).isEqualTo("John");
            assertThat(resp.getEmail()).isEqualTo("john@test.com");
        }
    }

    @Nested
    @DisplayName("GenerateKeyResponse")
    class GenerateKeyResponseTests {

        @Test
        @DisplayName("Should hold the plain key and message")
        void shouldHoldKeyAndMessage() {
            GenerateKeyResponse resp = GenerateKeyResponse.builder()
                    .apiKey("mp_sk_a3f2c8b1e9d4f7a6c5b2e8d1f4a7c3b9e6d2f8a1c4b7")
                    .message("Store this key safely")
                    .build();

            assertThat(resp.getApiKey()).startsWith("mp_sk_");
            assertThat(resp.getMessage()).isEqualTo("Store this key safely");
        }
    }

    @Nested
    @DisplayName("CostCalculationResponse")
    class CostCalculationResponseTests {

        @Test
        @DisplayName("Should build with all fields")
        void shouldBuild() {
            CostCalculationResponse resp = CostCalculationResponse.builder()
                    .costUsd(new BigDecimal("0.00113"))
                    .costInr(new BigDecimal("0.11"))
                    .model("deepseek-v4-pro")
                    .breakdown("model=deepseek-v4-pro | ...")
                    .walletDebited(true)
                    .build();

            assertThat(resp.getCostUsd()).isPositive();
            assertThat(resp.getCostInr()).isPositive();
            assertThat(resp.getModel()).isEqualTo("deepseek-v4-pro");
            assertThat(resp.getBreakdown()).isNotEmpty();
            assertThat(resp.isWalletDebited()).isTrue();
        }

        @Test
        @DisplayName("Should allow walletDebited=false")
        void shouldAllowNotDebited() {
            CostCalculationResponse resp = CostCalculationResponse.builder()
                    .walletDebited(false)
                    .build();

            assertThat(resp.isWalletDebited()).isFalse();
        }
    }

    @Nested
    @DisplayName("ApiErrorResponse")
    class ApiErrorResponseTests {

        @Test
        @DisplayName("Should build basic error")
        void shouldBuildBasicError() {
            ApiErrorResponse error = ApiErrorResponse.builder()
                    .timestamp(Instant.now())
                    .status(400)
                    .error("Bad Request")
                    .message("Validation failed")
                    .path("/api/v1/test")
                    .build();

            assertThat(error.getTimestamp()).isNotNull();
            assertThat(error.getStatus()).isEqualTo(400);
            assertThat(error.getError()).isEqualTo("Bad Request");
            assertThat(error.getMessage()).isEqualTo("Validation failed");
            assertThat(error.getPath()).isEqualTo("/api/v1/test");
        }

        @Test
        @DisplayName("Should allow null fields for optional details")
        void shouldAllowNullFields() {
            ApiErrorResponse error = ApiErrorResponse.builder()
                    .timestamp(Instant.now())
                    .status(500)
                    .error("Internal Server Error")
                    .message("Unexpected error")
                    .path("/api/v1/test")
                    .build();

            assertThat(error.getErrors()).isNull();
        }
    }

    @Nested
    @DisplayName("ApiKeyResponse")
    class ApiKeyResponseTests {

        @Test
        @DisplayName("Should build with key info")
        void shouldBuild() {
            ApiKeyResponse resp = ApiKeyResponse.builder()
                    .id(10L)
                    .keyPlaceholder("mp_sk_a3f2c8b1****c4b7")
                    .active(true)
                    .createdAt(LocalDate.of(2025, 1, 15))
                    .lastUsedAt(LocalDate.of(2025, 7, 1))
                    .build();

            assertThat(resp.getId()).isEqualTo(10L);
            assertThat(resp.getKeyPlaceholder()).contains("****");
            assertThat(resp.isActive()).isTrue();
            assertThat(resp.getCreatedAt()).isEqualTo("2025-01-15");
            assertThat(resp.getLastUsedAt()).isEqualTo("2025-07-01");
        }
    }

    @Nested
    @DisplayName("TransactionResponse")
    class TransactionResponseTests {

        @Test
        @DisplayName("Should build with balance info")
        void shouldBuild() {
            TransactionResponse resp = TransactionResponse.builder()
                    .transactionId(100L)
                    .amount(new BigDecimal("500.00"))
                    .status("SUCCESS")
                    .balanceAfter(new BigDecimal("1500.00"))
                    .build();

            assertThat(resp.getTransactionId()).isEqualTo(100L);
            assertThat(resp.getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
            assertThat(resp.getStatus()).isEqualTo("SUCCESS");
            assertThat(resp.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("1500.00"));
        }
    }

    @Nested
    @DisplayName("UserStatsResponse")
    class UserStatsResponseTests {

        @Test
        @DisplayName("Should build with stats data")
        void shouldBuild() {
            UserStatsResponse resp = UserStatsResponse.builder()
                    .walletBalanceInr(new BigDecimal("2500.00"))
                    .totalApiRequests(150)
                    .totalTokensConsumed(1_000_000L)
                    .totalCostUsd(new BigDecimal("3.50"))
                    .totalCostInr(new BigDecimal("350.50"))
                    .apiRequestsDateWise(List.of())
                    .costDateWise(List.of())
                    .last10Usages(List.of())
                    .build();

            assertThat(resp.getWalletBalanceInr()).isEqualByComparingTo(new BigDecimal("2500.00"));
            assertThat(resp.getTotalApiRequests()).isEqualTo(150);
            assertThat(resp.getTotalTokensConsumed()).isEqualTo(1_000_000L);
            assertThat(resp.getTotalCostInr()).isEqualByComparingTo(new BigDecimal("350.50"));
            assertThat(resp.getApiRequestsDateWise()).isEmpty();
            assertThat(resp.getLast10Usages()).isEmpty();
        }
    }

    @Nested
    @DisplayName("ProfileResponse")
    class ProfileResponseTests {

        @Test
        @DisplayName("Should build with profile fields")
        void shouldBuild() {
            ProfileResponse resp = ProfileResponse.builder()
                    .email("user@test.com")
                    .name("Jane Doe")
                    .joinedAt(LocalDate.of(2025, 1, 1))
                    .build();

            assertThat(resp.getEmail()).isEqualTo("user@test.com");
            assertThat(resp.getName()).isEqualTo("Jane Doe");
            assertThat(resp.getJoinedAt()).isEqualTo(LocalDate.of(2025, 1, 1));
        }
    }

    @Nested
    @DisplayName("WalletSummaryResponse")
    class WalletSummaryResponseTests {

        @Test
        @DisplayName("Should build with summary data")
        void shouldBuild() {
            WalletSummaryResponse resp = WalletSummaryResponse.builder()
                    .currentBalance(new BigDecimal("3000.00"))
                    .totalRecharged(new BigDecimal("5000.00"))
                    .totalConsumed(new BigDecimal("2000.00"))
                    .recentRecharges(List.of())
                    .build();

            assertThat(resp.getCurrentBalance()).isEqualByComparingTo(new BigDecimal("3000.00"));
            assertThat(resp.getTotalRecharged()).isEqualByComparingTo(new BigDecimal("5000.00"));
            assertThat(resp.getTotalConsumed()).isEqualByComparingTo(new BigDecimal("2000.00"));
            assertThat(resp.getRecentRecharges()).isEmpty();
        }
    }

    @Nested
    @DisplayName("CreateOrderResponse")
    class CreateOrderResponseTests {

        @Test
        @DisplayName("Should build with order details")
        void shouldBuild() {
            CreateOrderResponse resp = CreateOrderResponse.builder()
                    .transactionId(999L)
                    .orderId("order_ABC123")
                    .amount(new BigDecimal("500.00"))
                    .currency("INR")
                    .keyId("rzp_test_abc")
                    .receipt("rcpt_123")
                    .build();

            assertThat(resp.getTransactionId()).isEqualTo(999L);
            assertThat(resp.getOrderId()).isEqualTo("order_ABC123");
            assertThat(resp.getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
            assertThat(resp.getCurrency()).isEqualTo("INR");
            assertThat(resp.getKeyId()).isEqualTo("rzp_test_abc");
            assertThat(resp.getReceipt()).isEqualTo("rcpt_123");
        }
    }

    // ── JSON Serialization ──────────────────────────────────

    @Nested
    @DisplayName("JSON Serialization")
    class JsonSerializationTests {

        @Test
        @DisplayName("Should serialize and deserialize LoginResponse")
        void shouldSerializeLoginResponse() throws JsonProcessingException {
            LoginResponse original = LoginResponse.builder()
                    .token("jwt.token.value")
                    .userId(42L)
                    .name("Test User")
                    .email("test@test.com")
                    .build();

            String json = objectMapper.writeValueAsString(original);
            LoginResponse deserialized = objectMapper.readValue(json, LoginResponse.class);

            assertThat(deserialized.getToken()).isEqualTo(original.getToken());
            assertThat(deserialized.getUserId()).isEqualTo(original.getUserId());
            assertThat(deserialized.getName()).isEqualTo(original.getName());
            assertThat(deserialized.getEmail()).isEqualTo(original.getEmail());
        }
    }
}
