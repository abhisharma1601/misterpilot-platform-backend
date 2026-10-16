package online.misterpilot.platform.dto.admin.response;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ProviderFundingSummaryDto {
    /** Sum of every USER wallet balance (what users can still spend, in marked-up INR) */
    private BigDecimal totalWalletBalanceInr;
    private BigDecimal markup;
    private BigDecimal fxBuffer;
    private BigDecimal safetyBuffer;
    private BigDecimal inFlightBufferInr;
    /** totalWalletBalanceInr / markup — total provider cost if all wallets are fully spent */
    private BigDecimal totalRequiredProviderBalanceInr;
    /** Sum of strictRequiredInr across listed providers */
    private BigDecimal totalStrictRequiredInr;
    /** Sum of recommendedInr across listed providers */
    private BigDecimal totalRecommendedInr;
    /** Usage window used to compute the split; null means all-time */
    private Integer windowDays;
    private LocalDateTime windowStart;
    /** Portion of the required balance that could not be split because there was no usage in the window */
    private BigDecimal unallocatedInr;
    private List<ProviderFundingDto> providers;
}
