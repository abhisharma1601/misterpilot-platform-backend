package online.misterpilot.platform.service;

import online.misterpilot.platform.dto.request.TransactionRequest;
import online.misterpilot.platform.dto.response.CostCalculationResponse;
import online.misterpilot.platform.dto.response.TransactionResponse;
import online.misterpilot.platform.entity.ApiKey;
import online.misterpilot.platform.entity.User;
import online.misterpilot.platform.enums.TransactionType;
import online.misterpilot.platform.repository.ApiKeyRepository;
import online.misterpilot.platform.util.KeyUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("KeyUsageService")
class KeyUsageServiceTest {

    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private KeyUtil keyUtil;
    @Mock private WalletService walletService;
    @Mock private TokenUsageService tokenUsageService;

    private KeyUsageService keyUsageService;

    private User testUser;
    private ApiKey activeKey;
    private ApiKey disabledKey;

    static User testUser() {
        return User.builder().id(1L).name("Test User").email("test@example.com").build();
    }

    static ApiKey activeKey(User user) {
        ApiKey k = new ApiKey();
        k.setId(10L);
        k.setHashValue("abc123hash");
        k.setActive(true);
        k.setUser(user);
        return k;
    }

    static ApiKey disabledKey(User user) {
        ApiKey k = activeKey(user);
        k.setActive(false);
        return k;
    }

    private static TransactionResponse mockTxnResponse() {
        return TransactionResponse.builder()
                .transactionId(1L)
                .balanceAfter(new BigDecimal("98.70"))
                .build();
    }

    @BeforeEach
    void setUp() {
        testUser = testUser();
        activeKey = activeKey(testUser);
        disabledKey = disabledKey(testUser);
        keyUsageService = new KeyUsageService(
                apiKeyRepository, keyUtil, walletService, tokenUsageService);
    }

    // ================================================================
    //  Amount validation
    // ================================================================

    @Nested
    @DisplayName("chargeUsage() — amount validation")
    class AmountValidation {

        @Test
        @DisplayName("Should throw when costInr is null")
        void shouldThrowForNullAmount() {
            assertThatThrownBy(() ->
                    keyUsageService.chargeUsage("mp_sk_key", null, "deepseek-v4-pro", 0, 0, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("costInr");
        }

        @Test
        @DisplayName("Should throw when costInr is zero")
        void shouldThrowForZeroAmount() {
            assertThatThrownBy(() ->
                    keyUsageService.chargeUsage("mp_sk_key", BigDecimal.ZERO, "deepseek-v4-pro", 0, 0, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("costInr");
        }

        @Test
        @DisplayName("Should throw when costInr is negative")
        void shouldThrowForNegativeAmount() {
            assertThatThrownBy(() ->
                    keyUsageService.chargeUsage("mp_sk_key", new BigDecimal("-1.00"), "deepseek-v4-pro", 0, 0, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("costInr");
        }
    }

    // ================================================================
    //  Key resolution
    // ================================================================

    @Nested
    @DisplayName("chargeUsage() — key resolution")
    class KeyResolution {

        @Test
        @DisplayName("Should throw 'Invalid API key' when key not found in DB")
        void shouldThrowForUnknownKey() {
            when(keyUtil.hashApiKey("mp_sk_unknown")).thenReturn("hash_unknown");
            when(apiKeyRepository.findByHashValue("hash_unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    keyUsageService.chargeUsage("mp_sk_unknown", new BigDecimal("1.00"), "deepseek-v4-pro", 0, 0, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid API key");
        }

        @Test
        @DisplayName("Should throw 'API key is disabled' when key is not active")
        void shouldThrowForDisabledKey() {
            when(keyUtil.hashApiKey("mp_sk_disabled")).thenReturn("hash_disabled");
            when(apiKeyRepository.findByHashValue("hash_disabled")).thenReturn(Optional.of(disabledKey));

            assertThatThrownBy(() ->
                    keyUsageService.chargeUsage("mp_sk_disabled", new BigDecimal("1.00"), "deepseek-v4-pro", 0, 0, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("API key is disabled");
        }

        @Test
        @DisplayName("Should throw 'No user associated' when key has null user")
        void shouldThrowForOrphanKey() {
            ApiKey orphanKey = new ApiKey();
            orphanKey.setId(99L);
            orphanKey.setActive(true);
            orphanKey.setUser(null);

            when(keyUtil.hashApiKey("mp_sk_orphan")).thenReturn("hash_orphan");
            when(apiKeyRepository.findByHashValue("hash_orphan")).thenReturn(Optional.of(orphanKey));

            assertThatThrownBy(() ->
                    keyUsageService.chargeUsage("mp_sk_orphan", new BigDecimal("1.00"), "deepseek-v4-pro", 0, 0, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No user associated");
        }

        @Test
        @DisplayName("Should hash the plain key and use the hash for DB lookup")
        void shouldHashThePlainKey() {
            when(keyUtil.hashApiKey("mp_sk_some_key")).thenReturn("hash_some");
            when(apiKeyRepository.findByHashValue("hash_some")).thenReturn(Optional.of(activeKey));
            when(walletService.processTransaction(any(), any())).thenReturn(mockTxnResponse());

            keyUsageService.chargeUsage("mp_sk_some_key", new BigDecimal("1.00"), "deepseek-v4-pro", 0, 0, 0);

            verify(keyUtil).hashApiKey("mp_sk_some_key");
            verify(apiKeyRepository).findByHashValue("hash_some");
        }
    }

    // ================================================================
    //  Successful charge
    // ================================================================

    @Nested
    @DisplayName("chargeUsage() — successful charge")
    class SuccessfulCharge {

        @BeforeEach
        void stubHappyPath() {
            when(keyUtil.hashApiKey(anyString())).thenReturn("abc123hash");
            when(apiKeyRepository.findByHashValue("abc123hash")).thenReturn(Optional.of(activeKey));
            when(walletService.processTransaction(any(), any())).thenReturn(mockTxnResponse());
        }

        @Test
        @DisplayName("Should deduct exactly the costInr sent in request — no margin applied")
        void shouldDeductExactAmount() {
            CostCalculationResponse response = keyUsageService.chargeUsage(
                    "mp_sk_key", new BigDecimal("1.00"), "deepseek-v4-pro", 0, 0, 0);

            assertThat(response.getCostInr()).isEqualByComparingTo(new BigDecimal("1.00"));
        }

        @Test
        @DisplayName("Should pass costInr directly to wallet as USAGE_CHARGE")
        void shouldDebitWalletWithExactAmount() {
            keyUsageService.chargeUsage(
                    "mp_sk_key", new BigDecimal("10.00"), "deepseek-v4-pro", 0, 0, 0);

            ArgumentCaptor<TransactionRequest> captor = ArgumentCaptor.forClass(TransactionRequest.class);
            verify(walletService).processTransaction(captor.capture(), eq(testUser));

            TransactionRequest txn = captor.getValue();
            assertThat(txn.getType()).isEqualTo(TransactionType.USAGE_CHARGE);
            assertThat(txn.getAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
        }

        @Test
        @DisplayName("Should record token usage with the provided token counts and exact costInr")
        void shouldRecordTokenUsageWithCorrectCounts() {
            keyUsageService.chargeUsage(
                    "mp_sk_key", new BigDecimal("1.00"), "deepseek-v4-flash",
                    5000L, 2000L, 1000L);

            verify(tokenUsageService).recordUsage(
                    eq(activeKey), eq(testUser), eq("deepseek-v4-flash"),
                    eq(5000L), eq(2000L), eq(1000L),
                    eq(BigDecimal.ZERO),
                    eq(new BigDecimal("1.00")));
        }

        @Test
        @DisplayName("Should set walletDebited=true in response")
        void shouldReturnWalletDebitedTrue() {
            CostCalculationResponse response = keyUsageService.chargeUsage(
                    "mp_sk_key", new BigDecimal("1.00"), "deepseek-v4-pro", 0, 0, 0);

            assertThat(response.isWalletDebited()).isTrue();
        }

        @Test
        @DisplayName("Should set costUsd=0 in response (cost is INR-only)")
        void shouldReturnZeroCostUsd() {
            CostCalculationResponse response = keyUsageService.chargeUsage(
                    "mp_sk_key", new BigDecimal("1.00"), "deepseek-v4-pro", 0, 0, 0);

            assertThat(response.getCostUsd()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should include model in response")
        void shouldReturnCorrectModel() {
            CostCalculationResponse response = keyUsageService.chargeUsage(
                    "mp_sk_key", new BigDecimal("1.00"), "deepseek-v4-flash", 0, 0, 0);

            assertThat(response.getModel()).isEqualTo("deepseek-v4-flash");
        }

        @Test
        @DisplayName("Should include costInr in breakdown string")
        void shouldReturnBreakdownString() {
            CostCalculationResponse response = keyUsageService.chargeUsage(
                    "mp_sk_key", new BigDecimal("5.00"), "deepseek-v4-pro", 0, 0, 0);

            assertThat(response.getBreakdown()).contains("5.00");
        }
    }
}
