package online.misterpilot.platform.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostCalculationRequest {

    /** Total completion tokens the model generated across all LLM calls */
    private long outputTokens;

    /** Total prompt tokens served from DeepSeek's KV-cache */
    private long cacheHitTokens;

    /** Total fresh prompt tokens that needed computation */
    private long cacheMissTokens;

    /** Model name: deepseek-v4-pro or deepseek-v4-flash */
    private String model;

    /**
     * Who owns the underlying DeepSeek key:
     * "misterpilot" → 30% profit margin applied
     * "deepseek"   → raw provider cost only
     */
    private String keyType;
}
