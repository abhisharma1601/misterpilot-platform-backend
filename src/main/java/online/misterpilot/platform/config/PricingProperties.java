package online.misterpilot.platform.config;

import java.math.BigDecimal;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "pricing")
public class PricingProperties {

    /** What users are charged relative to provider cost */
    private BigDecimal markup = new BigDecimal("1.2");

    /** Headroom for INR weakening against the USD that providers bill in (0.03 = 3%) */
    private BigDecimal fxBuffer = new BigDecimal("0.03");

    /** Headroom for usage shifting between providers, used by the recommended figure (0.4 = 40%) */
    private BigDecimal safetyBuffer = new BigDecimal("0.4");

    /**
     * Provider spend that can be outstanding before wallets are debited
     * (max concurrent requests × largest single-request cost), in INR, added per provider
     */
    private BigDecimal inFlightBufferInr = BigDecimal.ZERO;

    /**
     * Fails fast at startup on values that would cause a divide-by-zero or a
     * non-positive funding factor in {@code ProviderFundingService}.
     */
    @PostConstruct
    void validate() {
        if (markup == null || markup.signum() <= 0) {
            throw new IllegalStateException("pricing.markup must be > 0");
        }
        if (fxBuffer == null || BigDecimal.ONE.add(fxBuffer).signum() <= 0) {
            throw new IllegalStateException("pricing.fx-buffer must be > -1");
        }
        if (safetyBuffer == null || BigDecimal.ONE.add(safetyBuffer).signum() <= 0) {
            throw new IllegalStateException("pricing.safety-buffer must be > -1");
        }
        if (inFlightBufferInr == null || inFlightBufferInr.signum() < 0) {
            throw new IllegalStateException("pricing.in-flight-buffer-inr must be >= 0");
        }
    }
}
