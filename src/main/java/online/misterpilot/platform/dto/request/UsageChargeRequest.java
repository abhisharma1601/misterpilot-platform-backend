package online.misterpilot.platform.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageChargeRequest {

    private String apiKey;
    private long outputTokens;
    private long cacheHitTokens;
    private long cacheMissTokens;
    private String model;
}
