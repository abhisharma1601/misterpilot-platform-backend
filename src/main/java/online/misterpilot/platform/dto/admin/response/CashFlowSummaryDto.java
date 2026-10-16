package online.misterpilot.platform.dto.admin.response;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CashFlowSummaryDto {
    /** All-time successful recharges */
    private BigDecimal totalRevenueInr;
    private long totalTransactionCount;
    private LocalDateTime generatedAt;
    private CashFlowPeriodDto week;
    private CashFlowPeriodDto month;
    private CashFlowPeriodDto quarter;
}
