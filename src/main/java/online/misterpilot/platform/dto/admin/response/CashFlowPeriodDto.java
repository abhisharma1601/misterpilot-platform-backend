package online.misterpilot.platform.dto.admin.response;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CashFlowPeriodDto {
    /** Start of the current calendar period (Monday / 1st of month / 1st of quarter, 00:00) */
    private LocalDateTime periodStart;
    /** When the figures were computed; the period runs from periodStart up to this point */
    private LocalDateTime periodEnd;
    /** Successful recharges in the period */
    private BigDecimal revenueInr;
    private long transactionCount;
    /** revenueInr / transactionCount; 0 when there are no transactions */
    private BigDecimal averageTransactionInr;
    /** Same span of the previous period, e.g. the first 10 days of last month when today is the 11th */
    private LocalDateTime previousPeriodStart;
    private LocalDateTime previousPeriodEnd;
    private BigDecimal previousRevenueInr;
    /** Change vs the previous period, one decimal; null when the previous period had no revenue */
    private BigDecimal growthPercent;
}
