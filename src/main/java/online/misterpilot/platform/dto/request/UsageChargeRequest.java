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
public class UsageChargeRequest {

    private String apiKey;
    private BigDecimal costInr;
    private String model;
    private long outputTokens;
    private long cacheHitTokens;
    private long cacheMissTokens;
}
