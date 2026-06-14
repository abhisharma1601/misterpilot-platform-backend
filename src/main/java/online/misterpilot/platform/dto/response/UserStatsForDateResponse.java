package online.misterpilot.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatsForDateResponse {

    private LocalDate date;

    private long totalApiRequests;

    private long totalTokensConsumed;

    private BigDecimal totalCostUsd;
    private BigDecimal totalCostInr;

    private List<UserStatsResponse.TokenUsageItem> usages;
}
