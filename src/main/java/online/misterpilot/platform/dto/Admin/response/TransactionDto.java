package online.misterpilot.platform.dto.Admin.response;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.misterpilot.platform.enums.TransactionStatus;


@Data
@NoArgsConstructor
public class TransactionDto {
    public long id;
    public BigDecimal amount;
    public TransactionStatus status;
    public Long userId;
    public String orderId;
    public LocalDateTime createdAt;
}
