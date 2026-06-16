package online.misterpilot.platform.service;

import online.misterpilot.platform.dto.request.CostCalculationRequest;
import online.misterpilot.platform.dto.response.CostCalculationResponse;
import online.misterpilot.platform.entity.ApiKey;
import online.misterpilot.platform.entity.User;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("KeyUsageService")
class KeyUsageServiceTest {

    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private KeyUtil keyUtil;
    @Mock private CostCalculatorService costCalculatorService;
    @Mock private TokenUsageService tokenUsageService;

    private KeyUsageService keyUsageService;

    private User testUser;
    private ApiKey activeKey;
    private ApiKey disabledKey;

    // ── Shared test data ────────────────────────────────

    static User testUser() {
        return User.builder().id(1L).name("Test User")
                .email("test-example-com").build();
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

    private static CostCalculationResponse makeResponse(boolean walletDebited) {
        return CostCalculationResponse.builder()
                .costUsd(new BigDecimal("0.00113"))
                .costInr(new BigDecimal("0.11"))
                .model("deepseek-v4-pro")
                .breakdown("model=deepseek-v4-pro | ...")
                .walletDebited(walletDebited)
                .build();
    }

    private static CostCalculationResponse makeResponseWithCosts(
            String costUsd, String costInr, boolean walletDebited) {
        return CostCalculationResponse.builder()
                .costUsd(new BigDecimal(costUsd))
                .costInr(new BigDecimal(costInr))
                .model("deepseek-v4-pro")
                .breakdown("...")
                .walletDebited(walletDebited)
                .build();
    }

    @BeforeEach
    void setUp() {
        testUser = testUser();
        activeKey = activeKey(testUser);
        disabledKey = disabledKey(testUser);
        keyUsageService = new KeyUsageService(
                apiKeyRepository, keyUtil,
                costCalculatorService, tokenUsageService);
    }

    // ================================================================
    //  DeepSeek key path
    // ================================================================

    @Nested
    @DisplayName("chargeUsage() — DeepSeek key")
    class DeepSeekKey {

        @Test
        @DisplayName("Should return CostCalculationResponse without touching wallet, repo, or usage")
        void shouldCalculateCostOnly() {
            String plainKey = "sk-deepseek-key-12345";
            CostCalculationResponse expected = makeResponse(false);

            when(costCalculatorService.detectKeyType(plainKey)).thenReturn("deepseek");
            when(costCalculatorService.calcCost(any(CostCalculationRequest.class), isNull()))
                    .thenReturn(expected);

            CostCalculationResponse actual = keyUsageService.chargeUsage(
                    plainKey, 1000L, 500L, 200L, "deepseek-v4-pro");

            assertThat(actual).isSameAs(expected);
            assertThat(actual.isWalletDebited()).isFalse();

            verifyNoInteractions(apiKeyRepository);
            verifyNoInteractions(keyUtil);
            verifyNoInteractions(tokenUsageService);
        }

        @Test
        @DisplayName("Should pass correct token counts and model to calcCost")
        void shouldPassCorrectRequestToCalcCost() {
            String plainKey = "sk-another-key";
            ArgumentCaptor<CostCalculationRequest> captor =
                    ArgumentCaptor.forClass(CostCalculationRequest.class);

            when(costCalculatorService.detectKeyType(plainKey)).thenReturn("deepseek");
            when(costCalculatorService.calcCost(any(CostCalculationRequest.class), isNull()))
                    .thenReturn(makeResponse(false));

            keyUsageService.chargeUsage(plainKey, 3000L, 1000L, 500L,
                    "deepseek-v4-flash");

            verify(costCalculatorService).calcCost(captor.capture(), isNull());
            CostCalculationRequest req = captor.getValue();

            assertThat(req.getOutputTokens()).isEqualTo(3000L);
            assertThat(req.getCacheHitTokens()).isEqualTo(1000L);
            assertThat(req.getCacheMissTokens()).isEqualTo(500L);
            assertThat(req.getModel()).isEqualTo("deepseek-v4-flash");
            assertThat(req.getKeyType()).isEqualTo("deepseek");
        }
    }

    // ================================================================
    //  MisterPilot key path — key resolution
    // ================================================================

    @Nested
    @DisplayName("chargeUsage() — MisterPilot key resolution")
    class MisterPilotKeyResolution {

        @Test
        @DisplayName("Should throw 'Invalid API key' when key not found in DB")
        void shouldThrowForUnknownKey() {
            String plainKey = "mp_sk_unknown_key_hash";
            when(costCalculatorService.detectKeyType(plainKey))
                    .thenReturn("misterpilot");
            when(keyUtil.hashApiKey(plainKey)).thenReturn("hash_unknown");
            when(apiKeyRepository.findByHashValue("hash_unknown"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    keyUsageService.chargeUsage(
                            plainKey, 1000L, 0L, 0L, "deepseek-v4-pro"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid API key");
        }

        @Test
        @DisplayName("Should throw 'API key is disabled' when key is not active")
        void shouldThrowForDisabledKey() {
            String plainKey = "mp_sk_disabled_key";
            when(costCalculatorService.detectKeyType(plainKey))
                    .thenReturn("misterpilot");
            when(keyUtil.hashApiKey(plainKey)).thenReturn("hash_disabled");
            when(apiKeyRepository.findByHashValue("hash_disabled"))
                    .thenReturn(Optional.of(disabledKey));

            assertThatThrownBy(() ->
                    keyUsageService.chargeUsage(
                            plainKey, 1000L, 0L, 0L, "deepseek-v4-pro"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("API key is disabled");
        }

        @Test
        @DisplayName("Should throw 'No user associated' when key has null user")
        void shouldThrowForOrphanKey() {
            String plainKey = "mp_sk_orphan_key";
            ApiKey orphanKey = new ApiKey();
            orphanKey.setId(99L);
            orphanKey.setHashValue("hash_orphan");
            orphanKey.setActive(true);
            orphanKey.setUser(null);

            when(costCalculatorService.detectKeyType(plainKey))
                    .thenReturn("misterpilot");
            when(keyUtil.hashApiKey(plainKey)).thenReturn("hash_orphan");
            when(apiKeyRepository.findByHashValue("hash_orphan"))
                    .thenReturn(Optional.of(orphanKey));

            assertThatThrownBy(() ->
                    keyUsageService.chargeUsage(
                            plainKey, 1000L, 0L, 0L, "deepseek-v4-pro"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No user associated");
        }

        @Test
        @DisplayName("Should call keyUtil.hashApiKey exactly once with the plain key")
        void shouldHashThePlainKey() {
            String plainKey = "mp_sk_some_key_value";
            when(costCalculatorService.detectKeyType(plainKey))
                    .thenReturn("misterpilot");
            when(keyUtil.hashApiKey(plainKey)).thenReturn("hash_some");
            when(apiKeyRepository.findByHashValue("hash_some"))
                    .thenReturn(Optional.of(activeKey));
            when(costCalculatorService.calcCost(
                    any(CostCalculationRequest.class), any(User.class)))
                    .thenReturn(makeResponse(true));

            keyUsageService.chargeUsage(
                    plainKey, 0L, 1L, 2L, "deepseek-v4-pro");

            verify(keyUtil).hashApiKey(plainKey);
            verify(apiKeyRepository).findByHashValue("hash_some");
        }
    }

    // ================================================================
    //  MisterPilot key path — successful charge
    // ================================================================

    @Nested
    @DisplayName("chargeUsage() — MisterPilot successful charge")
    class MisterPilotSuccessfulCharge {

        @BeforeEach
        void stubHappyPath() {
            when(keyUtil.hashApiKey(anyString())).thenReturn("abc123hash");
            when(apiKeyRepository.findByHashValue("abc123hash"))
                    .thenReturn(Optional.of(activeKey));
        }

        @Test
        @DisplayName("Should call calcCost with the resolved user (not null)")
        void shouldCallCalcCostWithUser() {
            String plainKey = "mp_sk_active_key";
            when(costCalculatorService.detectKeyType(plainKey))
                    .thenReturn("misterpilot");
            CostCalculationResponse calcResponse = makeResponse(true);
            when(costCalculatorService.calcCost(
                    any(CostCalculationRequest.class), eq(testUser)))
                    .thenReturn(calcResponse);

            CostCalculationResponse actual = keyUsageService.chargeUsage(
                    plainKey, 1000L, 500L, 200L, "deepseek-v4-pro");

            assertThat(actual.isWalletDebited()).isTrue();
            verify(costCalculatorService).calcCost(
                    any(CostCalculationRequest.class), eq(testUser));
        }

        @Test
        @DisplayName("Should record usage with all token counts and costs from calcCost")
        void shouldRecordUsageWithCorrectFields() {
            String plainKey = "mp_sk_record_key";
            CostCalculationResponse calcResponse = makeResponseWithCosts(
                    "0.00250", "0.24", true);

            when(costCalculatorService.detectKeyType(plainKey))
                    .thenReturn("misterpilot");
            when(costCalculatorService.calcCost(
                    any(CostCalculationRequest.class), eq(testUser)))
                    .thenReturn(calcResponse);

            keyUsageService.chargeUsage(
                    plainKey, 5000L, 2000L, 1000L, "deepseek-v4-flash");

            verify(tokenUsageService).recordUsage(
                    eq(activeKey), eq(testUser), eq("deepseek-v4-flash"),
                    eq(5000L), eq(2000L), eq(1000L),
                    eq(new BigDecimal("0.00250")),
                    eq(new BigDecimal("0.24")));
        }

        @Test
        @DisplayName("Should return the same CostCalculationResponse from calcCost")
        void shouldReturnCalcCostResponse() {
            String plainKey = "mp_sk_return_key";
            CostCalculationResponse expected = makeResponse(true);

            when(costCalculatorService.detectKeyType(plainKey))
                    .thenReturn("misterpilot");
            when(costCalculatorService.calcCost(
                    any(CostCalculationRequest.class), eq(testUser)))
                    .thenReturn(expected);

            CostCalculationResponse actual = keyUsageService.chargeUsage(
                    plainKey, 1L, 0L, 0L, "deepseek-v4-pro");

            assertThat(actual).isSameAs(expected);
        }
    }
}
