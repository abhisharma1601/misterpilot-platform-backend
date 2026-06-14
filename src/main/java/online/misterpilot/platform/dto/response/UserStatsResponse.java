package online.misterpilot.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatsResponse {

    // #1
    private BigDecimal walletBalanceInr;

    // #2
    private long totalApiRequests;

    // #3
    private long totalTokensConsumed;

    // #4
    private BigDecimal totalCostUsd;
    private BigDecimal totalCostInr;

    // #5  — date → count
    private List<DateCount> apiRequestsDateWise;

    // #6  — date → cost
    private List<DateCost> costDateWise;

    // #7
    private List<TokenUsageItem> last10Usages;

    // --- Inner DTOs ---

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DateCount {
        private LocalDate date;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DateCost {
        private LocalDate date;
        private BigDecimal costInr;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenUsageItem {
        private Long id;
        private String model;
        private long outputTokens;
        private long cacheHitTokens;
        private long cacheMissTokens;
        private BigDecimal costUsd;
        private BigDecimal costInr;
        private LocalDate createdAt;
    }
}
