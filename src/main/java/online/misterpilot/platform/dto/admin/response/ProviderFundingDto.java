package online.misterpilot.platform.dto.admin.response;
import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.misterpilot.platform.enums.Provider;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProviderFundingDto {
    private Provider provider;
    /** What users were charged (marked-up INR) for this provider in the window, plus an equal third of spend on unrecognised models */
    private BigDecimal chargedInr;
    /** Actual provider cost in the window: chargedInr / markup */
    private BigDecimal providerCostInr;
    /** Share of total usage in the window, 0–100 */
    private BigDecimal usageSharePercent;
    /** This provider's usage share of the total provider liability, with no buffers */
    private BigDecimal requiredBalanceInr;
    /** Guarantees no user is ever blocked: full liability × (1 + fxBuffer) + inFlightBuffer */
    private BigDecimal strictRequiredInr;
    /** Usage-based target: requiredBalanceInr × (1 + fxBuffer) × (1 + safetyBuffer) + inFlightBuffer */
    private BigDecimal recommendedInr;
}
