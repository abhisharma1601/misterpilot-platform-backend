package online.misterpilot.platform.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditWalletRequest {

    private String orderId;
    private String paymentId;
    private String signature;

    @Override
    public String toString() {
        return "CreditWalletRequest{orderId=" + orderId + ", paymentId=" + paymentId + "}";
    }
}
