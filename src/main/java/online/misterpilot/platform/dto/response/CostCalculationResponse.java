package online.misterpilot.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostCalculationResponse {

    private BigDecimal costUsd;
    private BigDecimal costInr;
    private String model;
    private String breakdown;
    private boolean walletDebited;
}
