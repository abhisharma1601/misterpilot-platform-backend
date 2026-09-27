package online.misterpilot.platform.dto.admin.response;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.misterpilot.platform.enums.TransactionStatus;


@Data
@NoArgsConstructor
public class TransactionDto {
    private long id;
    private BigDecimal amount;
    private TransactionStatus status;
    private Long userId;
    private String orderId;
    private LocalDateTime createdAt;
}
