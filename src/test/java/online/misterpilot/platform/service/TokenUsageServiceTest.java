package online.misterpilot.platform.service;

import online.misterpilot.platform.entity.ApiKey;
import online.misterpilot.platform.entity.TokenUsage;
import online.misterpilot.platform.entity.User;
import online.misterpilot.platform.repository.TokenUsageRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenUsageService")
class TokenUsageServiceTest {

    @Mock
    private TokenUsageRepository tokenUsageRepository;

    private TokenUsageService tokenUsageService;

    private User testUser;
    private ApiKey testApiKey;

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

    @BeforeEach
    void setUp() {
        testUser = testUser();
        testApiKey = activeKey(testUser);
        tokenUsageService = new TokenUsageService(tokenUsageRepository);
    }

    // ================================================================
    //  recordUsage
    // ================================================================

    @Nested
    @DisplayName("recordUsage()")
    class RecordUsage {

        @Test
        @DisplayName("Should call repository.save with correctly built TokenUsage — all fields")
        void shouldSaveWithAllFields() {
            TokenUsage saved = TokenUsage.builder()
                    .id(42L).apiKey(testApiKey).user(testUser)
                    .model("deepseek-v4-pro")
                    .outputTokens(1000L).cacheHitTokens(500L).cacheMissTokens(200L)
                    .costUsd(new BigDecimal("0.00113"))
                    .costInr(new BigDecimal("0.11"))
                    .build();
            when(tokenUsageRepository.save(any(TokenUsage.class)))
                    .thenReturn(saved);

            tokenUsageService.recordUsage(
                    testApiKey, testUser, "deepseek-v4-pro",
                    1000L, 500L, 200L,
                    new BigDecimal("0.00113"), new BigDecimal("0.11"));

            ArgumentCaptor<TokenUsage> captor =
                    ArgumentCaptor.forClass(TokenUsage.class);
            verify(tokenUsageRepository).save(captor.capture());

            TokenUsage captured = captor.getValue();
            assertThat(captured.getApiKey()).isEqualTo(testApiKey);
            assertThat(captured.getUser()).isEqualTo(testUser);
            assertThat(captured.getModel()).isEqualTo("deepseek-v4-pro");
            assertThat(captured.getOutputTokens()).isEqualTo(1000L);
            assertThat(captured.getCacheHitTokens()).isEqualTo(500L);
            assertThat(captured.getCacheMissTokens()).isEqualTo(200L);
            assertThat(captured.getCostUsd()).isEqualByComparingTo(
                    new BigDecimal("0.00113"));
            assertThat(captured.getCostInr()).isEqualByComparingTo(
                    new BigDecimal("0.11"));
        }

        @Test
        @DisplayName("Should return what the repository returns")
        void shouldReturnSavedEntity() {
            TokenUsage expected = TokenUsage.builder().id(99L).build();
            when(tokenUsageRepository.save(any(TokenUsage.class)))
                    .thenReturn(expected);

            TokenUsage result = tokenUsageService.recordUsage(
                    testApiKey, testUser, "deepseek-v4-flash",
                    10L, 0L, 0L, BigDecimal.ZERO, BigDecimal.ZERO);

            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("Should handle zero tokens (all zeros)")
        void shouldHandleZeroTokens() {
            when(tokenUsageRepository.save(any(TokenUsage.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            TokenUsage result = tokenUsageService.recordUsage(
                    testApiKey, testUser, "deepseek-v4-pro",
                    0L, 0L, 0L, BigDecimal.ZERO, BigDecimal.ZERO);

            assertThat(result.getOutputTokens()).isEqualTo(0L);
            assertThat(result.getCacheHitTokens()).isEqualTo(0L);
            assertThat(result.getCacheMissTokens()).isEqualTo(0L);
            assertThat(result.getCostUsd()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getCostInr()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should handle large token counts")
        void shouldHandleLargeTokenCounts() {
            long large = Long.MAX_VALUE / 2;
            when(tokenUsageRepository.save(any(TokenUsage.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            TokenUsage result = tokenUsageService.recordUsage(
                    testApiKey, testUser, "deepseek-v4-pro",
                    large, large, large,
                    new BigDecimal("999999.99999999"),
                    new BigDecimal("95999999.99"));

            assertThat(result.getOutputTokens()).isEqualTo(large);
            assertThat(result.getCacheHitTokens()).isEqualTo(large);
            assertThat(result.getCacheMissTokens()).isEqualTo(large);
        }

        @Test
        @DisplayName("Should handle high-precision USD cost")
        void shouldHandleHighPrecisionCost() {
            when(tokenUsageRepository.save(any(TokenUsage.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            BigDecimal preciseCost = new BigDecimal("0.00001234");
            TokenUsage result = tokenUsageService.recordUsage(
                    testApiKey, testUser, "deepseek-v4-pro",
                    1L, 0L, 0L, preciseCost, BigDecimal.ZERO);

            assertThat(result.getCostUsd()).isEqualByComparingTo(preciseCost);
        }
    }
}
