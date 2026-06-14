package online.misterpilot.platform.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    private BigDecimal amount;

    @Override
    public String toString() {
        return "CreateOrderRequest{amount=" + amount + "}";
    }
}
