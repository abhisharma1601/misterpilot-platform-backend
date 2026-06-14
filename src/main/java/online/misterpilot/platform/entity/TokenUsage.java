package online.misterpilot.platform.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "token_usage")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_key_id", nullable = false)
    private ApiKey apiKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "output_tokens", nullable = false)
    @Builder.Default
    private Long outputTokens = 0L;

    @Column(name = "cache_hit_tokens", nullable = false)
    @Builder.Default
    private Long cacheHitTokens = 0L;

    @Column(name = "cache_miss_tokens", nullable = false)
    @Builder.Default
    private Long cacheMissTokens = 0L;

    @Column(name = "cost_usd", nullable = false, precision = 12, scale = 8)
    private BigDecimal costUsd;

    @Column(name = "cost_inr", nullable = false, precision = 12, scale = 2)
    private BigDecimal costInr;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // --- Lifecycle ---

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
