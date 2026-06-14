package online.misterpilot.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    private Long transactionId;
    private BigDecimal amount;
    private String type;
    private String status;
    private BigDecimal balanceAfter;
    private LocalDateTime createdAt;
    private String message;
}
