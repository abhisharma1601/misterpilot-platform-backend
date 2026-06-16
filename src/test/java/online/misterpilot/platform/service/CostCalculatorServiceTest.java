package online.misterpilot.platform.service;

import online.misterpilot.platform.dto.request.CostCalculationRequest;
import online.misterpilot.platform.dto.response.CostCalculationResponse;
import online.misterpilot.platform.dto.request.TransactionRequest;
import online.misterpilot.platform.dto.response.TransactionResponse;
import online.misterpilot.platform.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CostCalculatorService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CostCalculatorService")
class CostCalculatorServiceTest {

    @Mock
    private WalletService walletService;

    private CostCalculatorService service;

    private static final BigDecimal INR_RATE = new BigDecimal("96.0");
    private static final int SCALE = 8;

    @BeforeEach
    void setUp() {
        service = new CostCalculatorService(walletService, INR_RATE, SCALE);
    }

    // ── Key Type Detection ──────────────────────────────────

    @Nested
    @DisplayName("detectKeyType()")
    class DetectKeyType {

        @Test
        @DisplayName("Should return 'misterpilot' for keys starting with 'mp'")
        void shouldDetectMisterPilot() {
            assertThat(service.detectKeyType("mp_sk_abc123")).isEqualTo("misterpilot");
        }

        @Test
        @DisplayName("Should return 'deepseek' for keys starting with 'sk'")
        void shouldDetectDeepSeek() {
            assertThat(service.detectKeyType("sk-abc123")).isEqualTo("deepseek");
        }

        @Test
        @DisplayName("Should return 'deepseek' for any non-mp key")
        void shouldDefaultToDeepSeek() {
            assertThat(service.detectKeyType("anything_else")).isEqualTo("deepseek");
        }

        @Test
        @DisplayName("Should return 'deepseek' for null input")
        void shouldHandleNull() {
            assertThat(service.detectKeyType(null)).isEqualTo("deepseek");
        }

        @Test
        @DisplayName("Should be case-sensitive — 'MP' != 'mp'")
        void shouldBeCaseSensitive() {
            assertThat(service.detectKeyType("MP_sk_test")).isEqualTo("deepseek");
        }
    }

    // ── Cost Calculation: DeepSeek Keys ─────────────────────

    @Nested
    @DisplayName("calcCost() — DeepSeek keys (no margin, no wallet)")
    class CalcCostDeepSeek {

        @Test
        @DisplayName("Should calculate raw cost without margin for deepseek-v4-pro")
        void shouldCalculateRawCostForPro() {
            CostCalculationRequest request = CostCalculationRequest.builder()
                    .model("deepseek-v4-pro")
                    .outputTokens(1000)
                    .cacheHitTokens(500)
                    .cacheMissTokens(200)
                    .keyType("deepseek")
                    .build();

            CostCalculationResponse response = service.calcCost(request, null);

            // Expected: 1000*0.00000087 + 500*0.000000003625 + 200*0.000000435
            // = 0.00087 + 0.0000018125 + 0.000087 = 0.0009588125
            BigDecimal expectedUsd = new BigDecimal("0.00095881");
            assertThat(response.getCostUsd()).isCloseTo(expectedUsd, within(new BigDecimal("0.00000001")));
            assertThat(response.getCostInr()).isCloseTo(new BigDecimal("0.09"), within(new BigDecimal("0.01")));
            assertThat(response.getModel()).isEqualTo("deepseek-v4-pro");
            assertThat(response.isWalletDebited()).isFalse();
            assertThat(response.getBreakdown()).contains("deepseek-v4-pro");
            assertThat(response.getBreakdown()).contains("margin=0%");
        }

        @Test
        @DisplayName("Should calculate raw cost without margin for deepseek-v4-flash")
        void shouldCalculateRawCostForFlash() {
            CostCalculationRequest request = CostCalculationRequest.builder()
                    .model("deepseek-v4-flash")
                    .outputTokens(1000)
                    .cacheHitTokens(500)
                    .cacheMissTokens(200)
                    .keyType("deepseek")
                    .build();

            CostCalculationResponse response = service.calcCost(request, null);

            // 1000*0.00000028 + 500*0.0000000028 + 200*0.00000014
            // = 0.00028 + 0.0000014 + 0.000028 = 0.0003094
            BigDecimal expectedUsd = new BigDecimal("0.00030940");
            assertThat(response.getCostUsd()).isCloseTo(expectedUsd, within(new BigDecimal("0.00000001")));
            assertThat(response.isWalletDebited()).isFalse();
        }

        @Test
        @DisplayName("Should return zero cost when all tokens are zero")
        void shouldReturnZeroForNoTokens() {
            CostCalculationRequest request = CostCalculationRequest.builder()
                    .model("deepseek-v4-pro")
                    .outputTokens(0)
                    .cacheHitTokens(0)
                    .cacheMissTokens(0)
                    .keyType("deepseek")
                    .build();

            CostCalculationResponse response = service.calcCost(request, null);

            assertThat(response.getCostUsd()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.getCostInr()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.isWalletDebited()).isFalse();
        }

        @Test
        @DisplayName("Should never debit wallet for deepseek keys even with cost > 0")
        void shouldNeverDebitWallet() {
            CostCalculationRequest request = CostCalculationRequest.builder()
                    .model("deepseek-v4-pro")
                    .outputTokens(1_000_000)
                    .cacheHitTokens(0)
                    .cacheMissTokens(0)
                    .keyType("deepseek")
                    .build();

            CostCalculationResponse response = service.calcCost(request, null);

            assertThat(response.isWalletDebited()).isFalse();
            verifyNoInteractions(walletService);
        }
    }

    // ── Cost Calculation: MisterPilot Keys ──────────────────

    @Nested
    @DisplayName("calcCost() — MisterPilot keys (30% margin, wallet debit)")
    class CalcCostMisterPilot {

        private User testUser;

        @BeforeEach
        void setUpUser() {
            testUser = mock(User.class);
            lenient().when(testUser.getId()).thenReturn(1L);
            lenient().when(testUser.getEmail()).thenReturn("test-example-com");
        }

        @Test
        @DisplayName("Should apply 30% profit margin on top of raw cost")
        void shouldApplyMargin() {
            CostCalculationRequest request = CostCalculationRequest.builder()
                    .model("deepseek-v4-pro")
                    .outputTokens(1000)
                    .cacheHitTokens(0)
                    .cacheMissTokens(0)
                    .keyType("misterpilot")
                    .build();

            when(walletService.processTransaction(any(TransactionRequest.class), eq(testUser)))
                    .thenReturn(mock(TransactionResponse.class));

            CostCalculationResponse response = service.calcCost(request, testUser);

            // Raw cost: 1000 * 0.00000087 = 0.00087
            // With 30% margin: 0.00087 * 1.30 = 0.001131
            BigDecimal expectedUsd = new BigDecimal("0.00113100");
            assertThat(response.getCostUsd()).isCloseTo(expectedUsd, within(new BigDecimal("0.00000001")));
            assertThat(response.getBreakdown()).contains("margin=30%");
        }

        @Test
        @DisplayName("Should debit wallet when amount > 0")
        void shouldDebitWallet() {
            CostCalculationRequest request = CostCalculationRequest.builder()
                    .model("deepseek-v4-pro")
                    .outputTokens(1_000_000)
                    .cacheHitTokens(0)
                    .cacheMissTokens(0)
                    .keyType("misterpilot")
                    .build();

            TransactionResponse mockResponse = TransactionResponse.builder()
                    .transactionId(100L)
                    .balanceAfter(new BigDecimal("999"))
                    .build();
            when(walletService.processTransaction(any(TransactionRequest.class), eq(testUser)))
                    .thenReturn(mockResponse);

            CostCalculationResponse response = service.calcCost(request, testUser);

            assertThat(response.isWalletDebited()).isTrue();
            verify(walletService, times(1))
                    .processTransaction(any(TransactionRequest.class), eq(testUser));
        }

        @Test
        @DisplayName("Should NOT debit wallet when cost is zero")
        void shouldNotDebitForZeroCost() {
            CostCalculationRequest request = CostCalculationRequest.builder()
                    .model("deepseek-v4-pro")
                    .outputTokens(0)
                    .cacheHitTokens(0)
                    .cacheMissTokens(0)
                    .keyType("misterpilot")
                    .build();

            CostCalculationResponse response = service.calcCost(request, testUser);

            assertThat(response.isWalletDebited()).isFalse();
            verifyNoInteractions(walletService);
        }

        @Test
        @DisplayName("Should pass correct amount and type to wallet service")
        void shouldPassCorrectTransactionData() {
            CostCalculationRequest request = CostCalculationRequest.builder()
                    .model("deepseek-v4-flash")
                    .outputTokens(100_000)
                    .cacheHitTokens(10_000)
                    .cacheMissTokens(5_000)
                    .keyType("misterpilot")
                    .build();

            TransactionResponse mockResponse = TransactionResponse.builder()
                    .transactionId(200L)
                    .balanceAfter(new BigDecimal("500"))
                    .build();
            when(walletService.processTransaction(any(TransactionRequest.class), eq(testUser)))
                    .thenReturn(mockResponse);

            service.calcCost(request, testUser);

            ArgumentCaptor<TransactionRequest> captor =
                    ArgumentCaptor.forClass(TransactionRequest.class);
            verify(walletService).processTransaction(captor.capture(), eq(testUser));

            TransactionRequest captured = captor.getValue();
            assertThat(captured.getAmount()).isPositive();
            assertThat(captured.getType())
                    .isEqualTo(online.misterpilot.platform.enums.TransactionType.USAGE_CHARGE);
        }
    }

    // ── Model Pricing Table ─────────────────────────────────

    @Nested
    @DisplayName("Pricing Table")
    class PricingTable {

        @Test
        @DisplayName("Should use deepseek-v4-pro pricing for recognized model")
        void shouldUseProPricing() {
            CostCalculationRequest request = CostCalculationRequest.builder()
                    .model("deepseek-v4-pro")
                    .outputTokens(0)
                    .cacheHitTokens(1_000_000_000) // 1B cache hits
                    .cacheMissTokens(0)
                    .keyType("deepseek")
                    .build();

            CostCalculationResponse response = service.calcCost(request, null);

            // 1B * $0.000000003625 = $3.625
            BigDecimal expectedUsd = new BigDecimal("3.62500000");
            assertThat(response.getCostUsd()).isCloseTo(expectedUsd, within(new BigDecimal("0.0001")));
        }

        @Test
        @DisplayName("Should default to v4-pro for unknown model names")
        void shouldDefaultToProForUnknownModel() {
            CostCalculationRequest request = CostCalculationRequest.builder()
                    .model("nonexistent-model-v99")
                    .outputTokens(1_000_000)
                    .cacheHitTokens(0)
                    .cacheMissTokens(0)
                    .keyType("deepseek")
                    .build();

            CostCalculationResponse response = service.calcCost(request, null);

            // Should behave exactly like deepseek-v4-pro pricing
            BigDecimal expectedUsd = new BigDecimal("0.87000000"); // 1M * 0.00000087
            assertThat(response.getCostUsd()).isCloseTo(expectedUsd, within(new BigDecimal("0.0001")));
        }

        @Test
        @DisplayName("Cache miss tokens should be more expensive than cache hit tokens (pro)")
        void cacheMissPricedHigherThanCacheHitPro() {
            CostCalculationRequest requestHitOnly = CostCalculationRequest.builder()
                    .model("deepseek-v4-pro")
                    .outputTokens(0)
                    .cacheHitTokens(1_000_000)
                    .cacheMissTokens(0)
                    .keyType("deepseek")
                    .build();

            CostCalculationRequest requestMissOnly = CostCalculationRequest.builder()
                    .model("deepseek-v4-pro")
                    .outputTokens(0)
                    .cacheHitTokens(0)
                    .cacheMissTokens(1_000_000)
                    .keyType("deepseek")
                    .build();

            CostCalculationResponse hitResponse = service.calcCost(requestHitOnly, null);
            CostCalculationResponse missResponse = service.calcCost(requestMissOnly, null);

            // cache_miss rate (0.000000435) is much higher than cache_hit (0.000000003625)
            assertThat(missResponse.getCostUsd())
                    .isGreaterThan(hitResponse.getCostUsd());
        }

        @Test
        @DisplayName("Flash model should be cheaper than Pro model for same usage")
        void flashShouldBeCheaperThanPro() {
            CostCalculationRequest proRequest = CostCalculationRequest.builder()
                    .model("deepseek-v4-pro")
                    .outputTokens(1_000_000)
                    .cacheHitTokens(0)
                    .cacheMissTokens(0)
                    .keyType("deepseek")
                    .build();

            CostCalculationRequest flashRequest = CostCalculationRequest.builder()
                    .model("deepseek-v4-flash")
                    .outputTokens(1_000_000)
                    .cacheHitTokens(0)
                    .cacheMissTokens(0)
                    .keyType("deepseek")
                    .build();

            CostCalculationResponse proResponse = service.calcCost(proRequest, null);
            CostCalculationResponse flashResponse = service.calcCost(flashRequest, null);

            assertThat(proResponse.getCostUsd()).isGreaterThan(flashResponse.getCostUsd());
        }
    }

    // ── INR Conversion ──────────────────────────────────────

    @Nested
    @DisplayName("INR Conversion")
    class InrConversion {

        @Test
        @DisplayName("Should convert USD to INR at configured rate")
        void shouldConvertToInr() {
            // $1.00 USD → ₹96.00 (at rate 96)
            CostCalculationRequest request = CostCalculationRequest.builder()
                    .model("deepseek-v4-pro")
                    .outputTokens(1_149_425) // roughly $1.00 raw
                    .cacheHitTokens(0)
                    .cacheMissTokens(0)
                    .keyType("deepseek")
                    .build();

            CostCalculationResponse response = service.calcCost(request, null);

            // The INR cost should be roughly USD * 96
            BigDecimal expectedInr = response.getCostUsd().multiply(new BigDecimal("96.0"));
            // INR is rounded to 2 decimal places
            assertThat(response.getCostInr()).isCloseTo(expectedInr, within(new BigDecimal("0.01")));
        }

        @Test
        @DisplayName("Should scale INR to 2 decimal places")
        void shouldScaleInrToTwoDecimals() {
            CostCalculationRequest request = CostCalculationRequest.builder()
                    .model("deepseek-v4-pro")
                    .outputTokens(1)
                    .cacheHitTokens(0)
                    .cacheMissTokens(0)
                    .keyType("deepseek")
                    .build();

            CostCalculationResponse response = service.calcCost(request, null);

            // INR should have at most 2 decimal places
            assertThat(response.getCostInr().scale()).isLessThanOrEqualTo(2);
        }
    }

    // ── Breakdown String ────────────────────────────────────

    @Nested
    @DisplayName("Breakdown")
    class Breakdown {

        @Test
        @DisplayName("Should contain all components in the breakdown")
        void shouldContainAllComponents() {
            CostCalculationRequest request = CostCalculationRequest.builder()
                    .model("deepseek-v4-pro")
                    .outputTokens(100)
                    .cacheHitTokens(200)
                    .cacheMissTokens(300)
                    .keyType("deepseek")
                    .build();

            CostCalculationResponse response = service.calcCost(request, null);

            String breakdown = response.getBreakdown();
            assertThat(breakdown).contains("output=100");
            assertThat(breakdown).contains("cache_hit=200");
            assertThat(breakdown).contains("cache_miss=300");
            assertThat(breakdown).contains("deepseek-v4-pro");
            assertThat(breakdown).contains("raw=$");
            assertThat(breakdown).contains("final=$");
        }
    }

    // ── Large Numbers ───────────────────────────────────────

    @Nested
    @DisplayName("Large token counts")
    class LargeNumbers {

        @Test
        @DisplayName("Should handle millions of tokens without overflow")
        void shouldHandleMillions() {
            CostCalculationRequest request = CostCalculationRequest.builder()
                    .model("deepseek-v4-pro")
                    .outputTokens(10_000_000)
                    .cacheHitTokens(100_000_000)
                    .cacheMissTokens(5_000_000)
                    .keyType("deepseek")
                    .build();

            CostCalculationResponse response = service.calcCost(request, null);

            // Cost must be > 0 and not overflow
            assertThat(response.getCostUsd()).isPositive();
            assertThat(response.getCostInr()).isPositive();
            assertThat(response.getBreakdown()).isNotEmpty();
        }
    }
}
