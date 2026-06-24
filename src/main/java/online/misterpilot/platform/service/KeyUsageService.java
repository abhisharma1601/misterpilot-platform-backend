package online.misterpilot.platform.service;

import online.misterpilot.platform.dto.request.TransactionRequest;
import online.misterpilot.platform.dto.response.CostCalculationResponse;
import online.misterpilot.platform.dto.response.TransactionResponse;
import online.misterpilot.platform.entity.ApiKey;
import online.misterpilot.platform.entity.User;
import online.misterpilot.platform.enums.TransactionType;
import online.misterpilot.platform.repository.ApiKeyRepository;
import online.misterpilot.platform.util.KeyUtil;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeyUsageService {

    private final ApiKeyRepository apiKeyRepository;
    private final KeyUtil keyUtil;
    private final WalletService walletService;
    private final TokenUsageService tokenUsageService;

    @Transactional
    public CostCalculationResponse chargeUsage(
            String plainApiKey,
            BigDecimal costInr,
            String model,
            long outputTokens,
            long cacheHitTokens,
            long cacheMissTokens) {

        if (costInr == null || costInr.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("costInr must be greater than zero");
        }

        ApiKey apiKey = resolveApiKey(plainApiKey);
        User user = apiKey.getUser();

        TransactionRequest txnRequest = new TransactionRequest();
        txnRequest.setAmount(costInr);
        txnRequest.setType(TransactionType.USAGE_CHARGE);

        TransactionResponse txnResponse = walletService.processTransaction(txnRequest, user);

        tokenUsageService.recordUsage(apiKey, user, model, outputTokens, cacheHitTokens, cacheMissTokens, BigDecimal.ZERO, costInr);

        log.info("Usage charged: user={}, model={}, costInr=₹{}, balanceAfter=₹{}",
                user.getId(), model, costInr, txnResponse.getBalanceAfter());

        return CostCalculationResponse.builder()
                .costUsd(BigDecimal.ZERO)
                .costInr(costInr)
                .model(model)
                .breakdown(String.format("deducted=₹%s", costInr.toPlainString()))
                .walletDebited(true)
                .build();
    }

    private ApiKey resolveApiKey(String plainApiKey) {
        String hash = keyUtil.hashApiKey(plainApiKey);
        ApiKey apiKey = apiKeyRepository.findByHashValue(hash).orElse(null);

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
