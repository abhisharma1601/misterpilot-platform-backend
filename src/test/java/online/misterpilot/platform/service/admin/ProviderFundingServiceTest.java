package online.misterpilot.platform.service.admin;

import online.misterpilot.platform.config.PricingProperties;
import online.misterpilot.platform.dto.admin.response.ProviderFundingDto;
import online.misterpilot.platform.dto.admin.response.ProviderFundingSummaryDto;
import online.misterpilot.platform.enums.Provider;
import online.misterpilot.platform.enums.Role;
import online.misterpilot.platform.repository.TokenUsageRepository;
import online.misterpilot.platform.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProviderFundingService")
class ProviderFundingServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private TokenUsageRepository tokenUsageRepository;

    private PricingProperties pricing;
    private ProviderFundingService service;

    @BeforeEach
    void setUp() {
        pricing = new PricingProperties();
        service = new ProviderFundingService(walletRepository, tokenUsageRepository, pricing);
    }

    private void givenWalletTotal(String amount) {
        when(walletRepository.sumBalanceByUserRole(Role.USER)).thenReturn(new BigDecimal(amount));
    }

    private void givenUsage(Object[]... rows) {
        when(tokenUsageRepository.sumCostInrGroupByModelSince(any())).thenReturn(List.of(rows));
    }

    private static Object[] row(String model, String costInr) {
        return new Object[]{model, new BigDecimal(costInr)};
    }

    private static ProviderFundingDto find(ProviderFundingSummaryDto summary, Provider provider) {
        return summary.getProviders().stream()
                .filter(p -> p.getProvider() == provider)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No entry for " + provider));
    }

    @Nested
    @DisplayName("totals")
    class Totals {

        @Test
        @DisplayName("Should sum only USER wallets and divide by the markup")
        void totalRequiredIsWalletOverMarkup() {
            givenWalletTotal("12000");
            givenUsage(row("deepseek-v4-pro", "100"));

            ProviderFundingSummaryDto summary = service.getProviderFunding(30);

            verify(walletRepository).sumBalanceByUserRole(Role.USER);
            assertThat(summary.getTotalWalletBalanceInr()).isEqualByComparingTo("12000.00");
            assertThat(summary.getMarkup()).isEqualByComparingTo("1.2");
            assertThat(summary.getTotalRequiredProviderBalanceInr()).isEqualByComparingTo("10000.00");
        }

        @Test
        @DisplayName("Should round the required balance up so providers are never underfunded")
        void roundsRequiredBalanceUp() {
            givenWalletTotal("100");
            givenUsage(row("deepseek-v4-pro", "10"));

            ProviderFundingSummaryDto summary = service.getProviderFunding(30);

            // 100 / 1.2 = 83.333…
            assertThat(summary.getTotalRequiredProviderBalanceInr()).isEqualByComparingTo("83.34");
            assertThat(find(summary, Provider.DEEPSEEK).getRequiredBalanceInr()).isEqualByComparingTo("83.34");
        }

        @Test
        @DisplayName("Should honour a custom markup")
        void customMarkup() {
            pricing.setMarkup(new BigDecimal("1.5"));
            givenWalletTotal("1500");
            givenUsage(row("gpt-4o", "300"));

            ProviderFundingSummaryDto summary = service.getProviderFunding(30);

            assertThat(summary.getMarkup()).isEqualByComparingTo("1.5");
            assertThat(summary.getTotalRequiredProviderBalanceInr()).isEqualByComparingTo("1000.00");
            assertThat(find(summary, Provider.OPENAI).getProviderCostInr()).isEqualByComparingTo("200.00");
        }

        @Test
        @DisplayName("Should return zeros when no user holds any balance")
        void zeroWalletBalance() {
            givenWalletTotal("0");
            givenUsage(row("claude-sonnet-5", "50"));

            ProviderFundingSummaryDto summary = service.getProviderFunding(30);

            assertThat(summary.getTotalRequiredProviderBalanceInr()).isEqualByComparingTo("0");
            assertThat(find(summary, Provider.ANTHROPIC).getRequiredBalanceInr()).isEqualByComparingTo("0");
            assertThat(find(summary, Provider.ANTHROPIC).getUsageSharePercent()).isEqualByComparingTo("100");
        }
    }

    @Nested
    @DisplayName("per-provider split")
    class Split {

        @Test
        @DisplayName("Should split the required balance by each provider's usage share")
        void splitsByUsageShare() {
            givenWalletTotal("12000");
            givenUsage(
                    row("deepseek-v4-pro", "400"),
                    row("deepseek-v4-flash", "200"),
                    row("gpt-4o", "300"),
                    row("claude-opus-5-5", "100"));

            ProviderFundingSummaryDto summary = service.getProviderFunding(30);

            ProviderFundingDto deepseek = find(summary, Provider.DEEPSEEK);
            assertThat(deepseek.getChargedInr()).isEqualByComparingTo("600.00");
            assertThat(deepseek.getProviderCostInr()).isEqualByComparingTo("500.00");
            assertThat(deepseek.getUsageSharePercent()).isEqualByComparingTo("60.00");
            assertThat(deepseek.getRequiredBalanceInr()).isEqualByComparingTo("6000.00");

            ProviderFundingDto openai = find(summary, Provider.OPENAI);
            assertThat(openai.getChargedInr()).isEqualByComparingTo("300.00");
            assertThat(openai.getProviderCostInr()).isEqualByComparingTo("250.00");
            assertThat(openai.getUsageSharePercent()).isEqualByComparingTo("30.00");
            assertThat(openai.getRequiredBalanceInr()).isEqualByComparingTo("3000.00");

            ProviderFundingDto anthropic = find(summary, Provider.ANTHROPIC);
            assertThat(anthropic.getUsageSharePercent()).isEqualByComparingTo("10.00");
            assertThat(anthropic.getRequiredBalanceInr()).isEqualByComparingTo("1000.00");

            assertThat(summary.getUnallocatedInr()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Should always list OPENAI, DEEPSEEK and ANTHROPIC, even with no usage")
        void listsKnownProvidersWithZeroUsage() {
            givenWalletTotal("1200");
            givenUsage(row("deepseek-v4-pro", "100"));

            ProviderFundingSummaryDto summary = service.getProviderFunding(30);

            assertThat(summary.getProviders())
                    .extracting(ProviderFundingDto::getProvider)
                    .containsExactly(Provider.OPENAI, Provider.DEEPSEEK, Provider.ANTHROPIC);
            assertThat(find(summary, Provider.OPENAI).getRequiredBalanceInr()).isEqualByComparingTo("0");
            assertThat(find(summary, Provider.ANTHROPIC).getRequiredBalanceInr()).isEqualByComparingTo("0");
            assertThat(find(summary, Provider.DEEPSEEK).getRequiredBalanceInr()).isEqualByComparingTo("1000.00");
        }

        @Test
        @DisplayName("Should split unrecognised-model spend equally across the three providers")
        void unknownModelSplitEqually() {
            givenWalletTotal("1200");
            givenUsage(row("deepseek-v4-pro", "150"), row("mistral-large", "40"), row(null, "20"));

            ProviderFundingSummaryDto summary = service.getProviderFunding(30);

            assertThat(summary.getProviders())
                    .extracting(ProviderFundingDto::getProvider)
                    .containsExactly(Provider.OPENAI, Provider.DEEPSEEK, Provider.ANTHROPIC);

            // 60 of OTHER spend → 20 each; total 210
            ProviderFundingDto deepseek = find(summary, Provider.DEEPSEEK);
            assertThat(deepseek.getChargedInr()).isEqualByComparingTo("170.00");
            assertThat(deepseek.getUsageSharePercent()).isEqualByComparingTo("80.95");
            assertThat(deepseek.getRequiredBalanceInr()).isEqualByComparingTo("809.53");

            for (Provider p : List.of(Provider.OPENAI, Provider.ANTHROPIC)) {
                ProviderFundingDto dto = find(summary, p);
                assertThat(dto.getChargedInr()).isEqualByComparingTo("20.00");
                assertThat(dto.getUsageSharePercent()).isEqualByComparingTo("9.52");
                assertThat(dto.getRequiredBalanceInr()).isEqualByComparingTo("95.24");
            }
        }

        @Test
        @DisplayName("Should split usage evenly when only unrecognised models were used")
        void onlyUnknownModels() {
            givenWalletTotal("1200");
            givenUsage(row("mistral-large", "90"));

            ProviderFundingSummaryDto summary = service.getProviderFunding(30);

            assertThat(summary.getUnallocatedInr()).isEqualByComparingTo("0");
            assertThat(summary.getProviders()).allSatisfy(p -> {
                assertThat(p.getChargedInr()).isEqualByComparingTo("30.00");
                assertThat(p.getUsageSharePercent()).isEqualByComparingTo("33.33");
                assertThat(p.getRequiredBalanceInr()).isEqualByComparingTo("333.34");
            });
        }

        @Test
        @DisplayName("Should map model names to providers case-insensitively")
        void mapsModelsCaseInsensitively() {
            givenWalletTotal("1200");
            givenUsage(
                    row("DeepSeek-V4-Pro", "10"),
                    row("GPT-4o", "10"),
                    row("o3-mini", "10"),
                    row("Claude-Sonnet-5", "10"));

            ProviderFundingSummaryDto summary = service.getProviderFunding(30);

            assertThat(find(summary, Provider.DEEPSEEK).getChargedInr()).isEqualByComparingTo("10");
            assertThat(find(summary, Provider.OPENAI).getChargedInr()).isEqualByComparingTo("20");
            assertThat(find(summary, Provider.ANTHROPIC).getChargedInr()).isEqualByComparingTo("10");
            assertThat(summary.getProviders()).extracting(ProviderFundingDto::getProvider).doesNotContain(Provider.OTHER);
        }

        @Test
        @DisplayName("Should report the whole required balance as unallocated when there is no usage")
        void noUsageIsUnallocated() {
            givenWalletTotal("1200");
            givenUsage();

            ProviderFundingSummaryDto summary = service.getProviderFunding(30);

            assertThat(summary.getUnallocatedInr()).isEqualByComparingTo("1000.00");
            assertThat(summary.getProviders()).hasSize(3);
            assertThat(summary.getProviders()).allSatisfy(p -> {
                assertThat(p.getUsageSharePercent()).isEqualByComparingTo("0");
                assertThat(p.getRequiredBalanceInr()).isEqualByComparingTo("0");
            });
        }
    }

    @Nested
    @DisplayName("funding targets")
    class Targets {

        @BeforeEach
        void buffers() {
            pricing.setFxBuffer(new BigDecimal("0.03"));
            pricing.setSafetyBuffer(new BigDecimal("0.4"));
            pricing.setInFlightBufferInr(new BigDecimal("500"));
        }

        @Test
        @DisplayName("Should use the documented defaults")
        void defaults() {
            PricingProperties defaults = new PricingProperties();
            assertThat(defaults.getMarkup()).isEqualByComparingTo("1.2");
            assertThat(defaults.getFxBuffer()).isEqualByComparingTo("0.03");
            assertThat(defaults.getSafetyBuffer()).isEqualByComparingTo("0.4");
            assertThat(defaults.getInFlightBufferInr()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Strict target should give every provider the full liability plus FX and in-flight buffers")
        void strictCoversFullLiabilityPerProvider() {
            givenWalletTotal("12000");
            givenUsage(row("deepseek-v4-pro", "600"), row("gpt-4o", "300"), row("claude-sonnet-5", "100"));

            ProviderFundingSummaryDto summary = service.getProviderFunding(30);

            // 10000 × 1.03 + 500, regardless of usage share
            assertThat(summary.getProviders()).allSatisfy(p ->
                    assertThat(p.getStrictRequiredInr()).isEqualByComparingTo("10800.00"));
            assertThat(summary.getTotalStrictRequiredInr()).isEqualByComparingTo("32400.00");
        }

        @Test
        @DisplayName("Recommended target should scale the usage share by FX and safety buffers plus in-flight")
        void recommendedScalesUsageShare() {
            givenWalletTotal("12000");
            givenUsage(row("deepseek-v4-pro", "600"), row("gpt-4o", "300"), row("claude-sonnet-5", "100"));

            ProviderFundingSummaryDto summary = service.getProviderFunding(30);

            // share × 10000 × 1.03 × 1.4 + 500
            assertThat(find(summary, Provider.DEEPSEEK).getRecommendedInr()).isEqualByComparingTo("9152.00");
            assertThat(find(summary, Provider.OPENAI).getRecommendedInr()).isEqualByComparingTo("4826.00");
            assertThat(find(summary, Provider.ANTHROPIC).getRecommendedInr()).isEqualByComparingTo("1942.00");
            assertThat(summary.getTotalRecommendedInr()).isEqualByComparingTo("15920.00");
        }

        @Test
        @DisplayName("With no safety buffer and one provider, recommended should equal strict")
        void recommendedEqualsStrictForSingleProviderWithoutSafety() {
            pricing.setSafetyBuffer(BigDecimal.ZERO);
            givenWalletTotal("12000");
            givenUsage(row("deepseek-v4-pro", "100"));

            ProviderFundingDto deepseek = find(service.getProviderFunding(30), Provider.DEEPSEEK);

            assertThat(deepseek.getRecommendedInr()).isEqualByComparingTo(deepseek.getStrictRequiredInr());
        }

        @Test
        @DisplayName("Providers with no usage should still get the in-flight buffer and the full strict target")
        void zeroUsageProviderStillFunded() {
            givenWalletTotal("1200");
            givenUsage(row("deepseek-v4-pro", "100"));

            ProviderFundingDto anthropic = find(service.getProviderFunding(30), Provider.ANTHROPIC);

            assertThat(anthropic.getRecommendedInr()).isEqualByComparingTo("500.00");
            assertThat(anthropic.getStrictRequiredInr()).isEqualByComparingTo("1530.00");
        }

        @Test
        @DisplayName("Should echo the buffers used in the summary")
        void echoesBuffers() {
            givenWalletTotal("0");
            givenUsage();

            ProviderFundingSummaryDto summary = service.getProviderFunding(30);

            assertThat(summary.getFxBuffer()).isEqualByComparingTo("0.03");
            assertThat(summary.getSafetyBuffer()).isEqualByComparingTo("0.4");
            assertThat(summary.getInFlightBufferInr()).isEqualByComparingTo("500");
        }
    }

    @Nested
    @DisplayName("usage window")
    class Window {

        @Test
        @DisplayName("Should query usage from now minus the given days")
        void usesGivenDays() {
            givenWalletTotal("1200");
            givenUsage();

            ProviderFundingSummaryDto summary = service.getProviderFunding(7);

            ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(tokenUsageRepository).sumCostInrGroupByModelSince(since.capture());
            assertThat(since.getValue()).isCloseTo(LocalDateTime.now().minusDays(7), within(5, ChronoUnit.SECONDS));
            assertThat(summary.getWindowDays()).isEqualTo(7);
            assertThat(summary.getWindowStart()).isEqualTo(since.getValue());
        }

        @Test
        @DisplayName("Should use all-time usage when days is null")
        void nullDaysIsAllTime() {
            givenWalletTotal("1200");
            givenUsage();

            ProviderFundingSummaryDto summary = service.getProviderFunding(null);

            ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(tokenUsageRepository).sumCostInrGroupByModelSince(since.capture());
            assertThat(since.getValue()).isBefore(LocalDateTime.of(2000, 1, 1, 0, 0));
            assertThat(summary.getWindowDays()).isNull();
            assertThat(summary.getWindowStart()).isNull();
        }
    }
}
