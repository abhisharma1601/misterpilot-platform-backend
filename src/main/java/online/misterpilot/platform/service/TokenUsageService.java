package online.misterpilot.platform.service;

import online.misterpilot.platform.entity.ApiKey;
import online.misterpilot.platform.entity.TokenUsage;
import online.misterpilot.platform.entity.User;
import online.misterpilot.platform.repository.TokenUsageRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenUsageService {

    private final TokenUsageRepository tokenUsageRepository;

    /**
     * Persists a token usage record for a MisterPilot key usage.
     */
    @Transactional
    public TokenUsage recordUsage(ApiKey apiKey,
                                   User user,
                                   String model,
                                   long outputTokens,
                                   long cacheHitTokens,
                                   long cacheMissTokens,
                                   BigDecimal costUsd,
                                   BigDecimal costInr) {

        TokenUsage usage = TokenUsage.builder()
                .apiKey(apiKey)
                .user(user)
                .model(model)
                .outputTokens(outputTokens)
                .cacheHitTokens(cacheHitTokens)
                .cacheMissTokens(cacheMissTokens)
                .costUsd(costUsd)
                .costInr(costInr)
                .build();

        TokenUsage saved = tokenUsageRepository.save(usage);

        log.info("Token usage recorded: id={}, user={}, apiKey={}, model={}, output={}, cacheHit={}, cacheMiss={}, costUsd=${}, costInr=₹{}",
                saved.getId(), user.getId(), apiKey.getId(), model,
                outputTokens, cacheHitTokens, cacheMissTokens,
                costUsd.toPlainString(), costInr.toPlainString());

        return saved;
    }
}
