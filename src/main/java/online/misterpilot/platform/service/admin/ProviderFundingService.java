package online.misterpilot.platform.service.admin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import online.misterpilot.platform.config.PricingProperties;
import online.misterpilot.platform.dto.admin.response.ProviderFundingDto;
import online.misterpilot.platform.dto.admin.response.ProviderFundingSummaryDto;
import online.misterpilot.platform.enums.Provider;
import online.misterpilot.platform.enums.Role;
import online.misterpilot.platform.repository.TokenUsageRepository;
import online.misterpilot.platform.repository.WalletRepository;

/**
 * Works out how much balance to keep with each upstream provider so that every
 * rupee sitting in user wallets can be served.
 *
 * Wallets are provider-agnostic, so the total liability (sum of wallets / markup)
 * is split across providers by their share of recent usage.
 *
 * Two funding targets are reported per provider:
 *  - strict: any user may spend their whole wallet on any provider, so each provider
 *    must hold the full liability. Guarantees no user is ever blocked.
 *  - recommended: the usage-share split plus a safety buffer for shifts in usage.
 * Both include an FX buffer and an in-flight buffer. Spend on unrecognised models
 * is split equally across the known providers rather than reported on its own.
 */
@Service
@RequiredArgsConstructor
public class ProviderFundingService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final List<Provider> KNOWN_PROVIDERS = List.of(Provider.OPENAI, Provider.DEEPSEEK, Provider.ANTHROPIC);

    private final WalletRepository walletRepository;
    private final TokenUsageRepository tokenUsageRepository;
    private final PricingProperties pricingProperties;

    /**
     * @param days usage window used to compute each provider's share; null means all-time
     */
    @Transactional(readOnly = true)
    public ProviderFundingSummaryDto getProviderFunding(Integer days) {
        BigDecimal markup = pricingProperties.getMarkup();
        BigDecimal fxFactor = BigDecimal.ONE.add(pricingProperties.getFxBuffer());
        BigDecimal safetyFactor = BigDecimal.ONE.add(pricingProperties.getSafetyBuffer());
        BigDecimal inFlight = pricingProperties.getInFlightBufferInr();

        BigDecimal totalWallet = walletRepository.sumBalanceByUserRole(Role.USER);
        BigDecimal totalRequired = totalWallet.divide(markup, 2, RoundingMode.UP);

        LocalDateTime since = days == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : LocalDateTime.now().minusDays(days);

        Map<Provider, BigDecimal> chargedByProvider = new EnumMap<>(Provider.class);
        for (Provider p : KNOWN_PROVIDERS) {
            chargedByProvider.put(p, BigDecimal.ZERO);
        }
        BigDecimal otherCharged = BigDecimal.ZERO;
        for (Object[] row : tokenUsageRepository.sumCostInrGroupByModelSince(since)) {
            Provider provider = Provider.fromModel((String) row[0]);
            BigDecimal charged = (BigDecimal) row[1];
            if (provider == Provider.OTHER) {
                otherCharged = otherCharged.add(charged);
            } else {
                chargedByProvider.merge(provider, charged, BigDecimal::add);
            }
        }
        // Spend on unrecognised models is split equally across the known providers
        BigDecimal otherPerProvider = otherCharged.divide(BigDecimal.valueOf(KNOWN_PROVIDERS.size()), 8, RoundingMode.HALF_UP);
        for (Provider p : KNOWN_PROVIDERS) {
            chargedByProvider.merge(p, otherPerProvider, BigDecimal::add);
        }
        BigDecimal totalCharged = chargedByProvider.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal strictRequired = totalRequired.multiply(fxFactor).add(inFlight).setScale(2, RoundingMode.UP);

        List<ProviderFundingDto> providers = new ArrayList<>();
        for (Map.Entry<Provider, BigDecimal> entry : chargedByProvider.entrySet()) {
            Provider provider = entry.getKey();
            BigDecimal charged = entry.getValue();
            BigDecimal share = totalCharged.signum() == 0
                    ? BigDecimal.ZERO
                    : charged.divide(totalCharged, 6, RoundingMode.HALF_UP);
            BigDecimal shareOfRequired = totalRequired.multiply(share);
            providers.add(new ProviderFundingDto(
                    provider,
                    charged.setScale(2, RoundingMode.HALF_UP),
                    charged.divide(markup, 2, RoundingMode.HALF_UP),
                    share.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP),
                    shareOfRequired.setScale(2, RoundingMode.UP),
                    strictRequired,
                    shareOfRequired.multiply(fxFactor).multiply(safetyFactor).add(inFlight).setScale(2, RoundingMode.UP)));
        }

        ProviderFundingSummaryDto summary = new ProviderFundingSummaryDto();
        summary.setTotalWalletBalanceInr(totalWallet.setScale(2, RoundingMode.HALF_UP));
        summary.setMarkup(markup);
        summary.setFxBuffer(pricingProperties.getFxBuffer());
        summary.setSafetyBuffer(pricingProperties.getSafetyBuffer());
        summary.setInFlightBufferInr(inFlight);
        summary.setTotalRequiredProviderBalanceInr(totalRequired);
        summary.setTotalStrictRequiredInr(sum(providers, ProviderFundingDto::getStrictRequiredInr));
        summary.setTotalRecommendedInr(sum(providers, ProviderFundingDto::getRecommendedInr));
        summary.setWindowDays(days);
        summary.setWindowStart(days == null ? null : since);
        summary.setUnallocatedInr(totalCharged.signum() == 0 ? totalRequired : BigDecimal.ZERO);
        summary.setProviders(providers);
        return summary;
    }

    private static BigDecimal sum(List<ProviderFundingDto> providers, Function<ProviderFundingDto, BigDecimal> field) {
        return providers.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
