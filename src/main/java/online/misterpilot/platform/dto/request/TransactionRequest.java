package online.misterpilot.platform.dto.request;

import online.misterpilot.platform.enums.TransactionType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransactionRequest {
    private BigDecimal amount;
    private TransactionType type;
    private String orderId;
    private String paymentId;
    private String signature;

    @Override
    public String toString() {
        return "TransactionRequest{amount=" + amount + ", type=" + type + "}";
    }
}
