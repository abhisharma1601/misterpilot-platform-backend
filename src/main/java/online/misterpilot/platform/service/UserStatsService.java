package online.misterpilot.platform.service;

import online.misterpilot.platform.dto.response.ApiKeyResponse;
import online.misterpilot.platform.dto.response.UserStatsForDateResponse;
import online.misterpilot.platform.dto.response.UserStatsResponse;
import online.misterpilot.platform.dto.response.WalletSummaryResponse;
import online.misterpilot.platform.entity.ApiKey;
import online.misterpilot.platform.entity.TokenUsage;
import online.misterpilot.platform.entity.User;
import online.misterpilot.platform.entity.Wallet;
import online.misterpilot.platform.enums.TransactionStatus;
import online.misterpilot.platform.enums.TransactionType;
import online.misterpilot.platform.repository.TokenUsageRepository;
import online.misterpilot.platform.repository.TransactionRepository;
import online.misterpilot.platform.repository.WalletRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserStatsService {

    private final WalletRepository walletRepository;
    private final TokenUsageRepository tokenUsageRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Full dashboard stats snapshot (no API keys — use {@link #getApiKeys(User)} for that).
     */
    @Transactional(readOnly = true)
    public UserStatsResponse getStats(User user) {

        log.debug("Building stats for user id={}", user.getId());

        // #1 — wallet balance (INR)
        BigDecimal walletBalanceInr = walletRepository.findByUser(user)
                .map(w -> w.getBalance())
                .orElse(BigDecimal.ZERO);

        // #2 — total API requests
        long totalApiRequests = tokenUsageRepository.countByUser(user);

        // #3 — total tokens consumed
        long totalTokensConsumed = tokenUsageRepository.sumTokensByUser(user);

        // #4 — total amount spent
        BigDecimal totalCostUsd = tokenUsageRepository.sumCostUsdByUser(user);
        BigDecimal totalCostInr = tokenUsageRepository.sumCostInrByUser(user);

        // #5 — API requests date-wise
        List<UserStatsResponse.DateCount> apiRequestsDateWise =
                tokenUsageRepository.countByUserGroupByDate(user).stream()
                        .map(row -> UserStatsResponse.DateCount.builder()
                                .date(toLocalDate(row[0]))
                                .count((Long) row[1])
                                .build())
                        .collect(Collectors.toList());

        // #6 — cost date-wise
        List<UserStatsResponse.DateCost> costDateWise =
                tokenUsageRepository.sumCostInrByUserGroupByDate(user).stream()
                        .map(row -> UserStatsResponse.DateCost.builder()
                                .date(toLocalDate(row[0]))
                                .costInr((BigDecimal) row[1])
                                .build())
                        .collect(Collectors.toList());

        // #7 — last 10 usages
        List<UserStatsResponse.TokenUsageItem> last10Usages =
                tokenUsageRepository.findTop10ByUserOrderByCreatedAtDesc(user).stream()
                        .map(this::toTokenUsageItem)
                        .collect(Collectors.toList());

        return UserStatsResponse.builder()
                .walletBalanceInr(walletBalanceInr)
                .totalApiRequests(totalApiRequests)
                .totalTokensConsumed(totalTokensConsumed)
                .totalCostUsd(totalCostUsd)
                .totalCostInr(totalCostInr)
                .apiRequestsDateWise(apiRequestsDateWise)
                .costDateWise(costDateWise)
                .last10Usages(last10Usages)
                .build();
    }

    /**
     * Tokens consumed & cost incurred for a specific date.
     */
    @Transactional(readOnly = true)
    public UserStatsForDateResponse getStatsForDate(User user, LocalDate date) {

        log.debug("Building stats for user id={} on date={}", user.getId(), date);

        Date sqlDate = Date.valueOf(date);

        Object[] agg = tokenUsageRepository.aggregateByUserAndDate(user, sqlDate);

        long totalTokens = (Long) agg[0];
        BigDecimal costUsd = (BigDecimal) agg[1];
        BigDecimal costInr = (BigDecimal) agg[2];
        long totalRequests = (Long) agg[3];

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(23, 59, 59);

        List<UserStatsResponse.TokenUsageItem> usages =
                tokenUsageRepository
                        .findByUserAndCreatedAtBetweenOrderByCreatedAtDesc(
                                user, startOfDay, endOfDay)
                        .stream()
                        .map(this::toTokenUsageItem)
                        .collect(Collectors.toList());

        return UserStatsForDateResponse.builder()
                .date(date)
                .totalApiRequests(totalRequests)
                .totalTokensConsumed(totalTokens)
                .totalCostUsd(costUsd)
                .totalCostInr(costInr)
                .usages(usages)
                .build();
    }

    /**
     * Wallet summary: current balance, total recharged, total consumed, last 5 recharges.
     */
    @Transactional(readOnly = true)
    public WalletSummaryResponse getWalletSummary(User user) {
        Wallet wallet = walletRepository.findByUser(user)
                .orElseThrow(() -> new IllegalStateException(
                        "Wallet not found for user id=" + user.getId()));

        BigDecimal totalRecharged = transactionRepository.sumAmountByWalletAndTypeAndStatus(
                wallet, TransactionType.RECHARGE, TransactionStatus.SUCCESS);

        BigDecimal totalConsumed = tokenUsageRepository.sumCostInrByUser(user);

        List<WalletSummaryResponse.RechargeItem> recentRecharges = transactionRepository
                .findTop5ByWalletAndTypeAndStatusOrderByCreatedAtDesc(
                        wallet, TransactionType.RECHARGE, TransactionStatus.SUCCESS)
                .stream()
                .map(t -> WalletSummaryResponse.RechargeItem.builder()
                        .transactionId(t.getId())
                        .amount(t.getAmount())
                        .status(t.getStatus().name())
                        .createdAt(t.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return WalletSummaryResponse.builder()
                .currentBalance(wallet.getBalance())
                .totalRecharged(totalRecharged)
                .totalConsumed(totalConsumed)
                .recentRecharges(recentRecharges)
                .build();
    }

    /**
     * All API keys belonging to the user.
     */
    @Transactional(readOnly = true)
    public List<ApiKeyResponse> getApiKeys(User user) {
        return user.getApiKeys().stream()
                .map(this::toApiKeyResponse)
                .collect(Collectors.toList());
    }

    // --- helpers ---

    private LocalDate toLocalDate(Object raw) {
        if (raw instanceof Date d) {
            return d.toLocalDate();
        }
        if (raw instanceof LocalDate ld) {
            return ld;
        }
        throw new IllegalArgumentException("Unexpected date type: " + raw.getClass());
    }

    private UserStatsResponse.TokenUsageItem toTokenUsageItem(TokenUsage t) {
        return UserStatsResponse.TokenUsageItem.builder()
                .id(t.getId())
                .model(t.getModel())
                .outputTokens(t.getOutputTokens())
                .cacheHitTokens(t.getCacheHitTokens())
                .cacheMissTokens(t.getCacheMissTokens())
                .costUsd(t.getCostUsd())
                .costInr(t.getCostInr())
                .createdAt(t.getCreatedAt().toLocalDate())
                .build();
    }

    private ApiKeyResponse toApiKeyResponse(ApiKey k) {
        return ApiKeyResponse.builder()
                .id(k.getId())
                .active(k.getActive())
                .createdAt(k.getCreatedAt().toLocalDate())
                .lastUsedAt(k.getLastUsedAt() != null ? k.getLastUsedAt().toLocalDate() : null)
                .build();
    }
}
