package online.misterpilot.platform.service;

import online.misterpilot.platform.dto.request.CostCalculationRequest;
import online.misterpilot.platform.dto.response.CostCalculationResponse;
import online.misterpilot.platform.entity.ApiKey;
import online.misterpilot.platform.entity.User;
import online.misterpilot.platform.repository.ApiKeyRepository;
import online.misterpilot.platform.util.KeyUtil;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeyUsageService {

    private final ApiKeyRepository apiKeyRepository;
    private final KeyUtil keyUtil;
    private final CostCalculatorService costCalculatorService;
    private final TokenUsageService tokenUsageService;

    @Transactional
    public CostCalculationResponse chargeUsage(
            String plainApiKey,
            long outputTokens,
            long cacheHitTokens,
            long cacheMissTokens,
            String model) {

        String keyType = costCalculatorService.detectKeyType(plainApiKey);

        CostCalculationRequest request = CostCalculationRequest.builder()
                .outputTokens(outputTokens)
                .cacheHitTokens(cacheHitTokens)
                .cacheMissTokens(cacheMissTokens)
                .model(model)
                .keyType(keyType)
                .build();

        if ("deepseek".equals(keyType)) {
            log.info("DeepSeek key — calculating cost only, no wallet lookup");
            return costCalculatorService.calcCost(request, null);
        }

        ApiKey apiKey = resolveApiKey(plainApiKey);
        User user = apiKey.getUser();
        log.info("Charging usage: user={}, keyType={}, model={}", user.getId(), keyType, model);

        CostCalculationResponse response = costCalculatorService.calcCost(request, user);

        tokenUsageService.recordUsage(
                apiKey,
                user,
                model,
                outputTokens,
                cacheHitTokens,
                cacheMissTokens,
                response.getCostUsd(),
                response.getCostInr());

        return response;
    }

    private ApiKey resolveApiKey(String plainApiKey) {
        String hash = keyUtil.hashApiKey(plainApiKey);
        ApiKey apiKey = apiKeyRepository.findByHashValue(hash)
                .orElse(null);

        if (apiKey == null) {
            throw new IllegalArgumentException("Invalid API key");
        }

        if (!apiKey.getActive()) {
            throw new IllegalArgumentException("API key is disabled");
        }

        User user = apiKey.getUser();
        if (user == null) {
            throw new IllegalArgumentException("No user associated with this API key");
        }

        log.debug("Resolved API key id={} → user id={}", apiKey.getId(), user.getId());
        return apiKey;
    }
}
