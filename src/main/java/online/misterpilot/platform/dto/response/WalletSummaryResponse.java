package online.misterpilot.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletSummaryResponse {

    private BigDecimal currentBalance;
    private BigDecimal totalRecharged;
    private BigDecimal totalConsumed;
    private List<RechargeItem> recentRecharges;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RechargeItem {
        private Long transactionId;
        private BigDecimal amount;
        private String status;
        private LocalDateTime createdAt;
    }
}
