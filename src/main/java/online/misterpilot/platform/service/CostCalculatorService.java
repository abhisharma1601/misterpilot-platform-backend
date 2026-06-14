package online.misterpilot.platform.service;

import online.misterpilot.platform.dto.request.CostCalculationRequest;
import online.misterpilot.platform.dto.request.TransactionRequest;
import online.misterpilot.platform.dto.response.CostCalculationResponse;
import online.misterpilot.platform.dto.response.TransactionResponse;
import online.misterpilot.platform.enums.TransactionType;
import online.misterpilot.platform.entity.User;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Slf4j
@Service
public class CostCalculatorService {

    private static final Map<String, Map<String, BigDecimal>> PRICING = Map.of(
            "deepseek-v4-pro", Map.of(
                    "output",     new BigDecimal("0.00000087"),
                    "cache_hit",  new BigDecimal("0.000000003625"),
                    "cache_miss", new BigDecimal("0.000000435")
            ),
            "deepseek-v4-flash", Map.of(
                    "output",     new BigDecimal("0.00000028"),
                    "cache_hit",  new BigDecimal("0.0000000028"),
                    "cache_miss", new BigDecimal("0.00000014")
            )
    );

    private static final BigDecimal PROFIT_MARGIN = new BigDecimal("0.30");
    private static final BigDecimal MARGIN_MULTIPLIER = BigDecimal.ONE.add(PROFIT_MARGIN);
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final WalletService walletService;
    private final BigDecimal inrRate;
    private final int scale;

    public CostCalculatorService(
            WalletService walletService,
            @Value("${app.billing.inr-rate:96.0}") BigDecimal inrRate,
            @Value("${app.billing.scale:8}") int scale) {
        this.walletService = walletService;
        this.inrRate = inrRate;
        this.scale = scale;
    }

    /**
     * Determines key type from the raw key prefix.
     * MisterPilot keys start with "mp", DeepSeek keys start with "sk".
     */
    public String detectKeyType(String rawKey) {
        if (rawKey != null && rawKey.startsWith("mp")) {
            return "misterpilot";
        }
        return "deepseek";
    }

    @Transactional
    public CostCalculationResponse calcCost(CostCalculationRequest request, User user) {
        boolean isMisterPilot = "misterpilot".equalsIgnoreCase(request.getKeyType());

        Map<String, BigDecimal> tier = PRICING.getOrDefault(
                request.getModel(), PRICING.get("deepseek-v4-pro"));

        BigDecimal outputCost    = tier.get("output").multiply(BigDecimal.valueOf(request.getOutputTokens()));
        BigDecimal cacheHitCost  = tier.get("cache_hit").multiply(BigDecimal.valueOf(request.getCacheHitTokens()));
        BigDecimal cacheMissCost = tier.get("cache_miss").multiply(BigDecimal.valueOf(request.getCacheMissTokens()));

        BigDecimal rawCost = outputCost.add(cacheHitCost).add(cacheMissCost)
                .setScale(scale, RoundingMode.HALF_UP);

        BigDecimal finalCostUsd = isMisterPilot
                ? rawCost.multiply(MARGIN_MULTIPLIER).setScale(scale, RoundingMode.HALF_UP)
                : rawCost;

        BigDecimal costInr = finalCostUsd.multiply(inrRate).setScale(2, RoundingMode.HALF_UP);

        String breakdown = String.format(
                "%s | output=%,d × $%s = $%s | cache_hit=%,d × $%s = $%s | cache_miss=%,d × $%s = $%s | raw=$%s | margin=%s%% | final=$%s",
                request.getModel(),
                request.getOutputTokens(), tier.get("output").toPlainString(), outputCost,
                request.getCacheHitTokens(), tier.get("cache_hit").toPlainString(), cacheHitCost,
                request.getCacheMissTokens(), tier.get("cache_miss").toPlainString(), cacheMissCost,
                rawCost.toPlainString(),
                isMisterPilot ? "30" : "0",
                finalCostUsd.toPlainString());

        log.info("Cost calculated: {}", breakdown);

        boolean walletDebited = false;

        if (isMisterPilot && costInr.compareTo(ZERO) > 0) {
            TransactionRequest txnRequest = new TransactionRequest();
            txnRequest.setAmount(costInr);
            txnRequest.setType(TransactionType.USAGE_CHARGE);
        //     txnRequest.setPaymentId("deduction_payment_id");
        //     txnRequest.setOrderId("deduction_order_id");

            TransactionResponse txnResponse = walletService.processTransaction(txnRequest, user);
            walletDebited = true;

            log.info("Wallet debited: user={}, amount=₹{}, newBalance=₹{}, txnId={}",
                    user.getId(), costInr, txnResponse.getBalanceAfter(), txnResponse.getTransactionId());
        }

        return CostCalculationResponse.builder()
                .costUsd(finalCostUsd)
                .costInr(costInr)
                .model(request.getModel())
                .breakdown(breakdown)
                .walletDebited(walletDebited)
                .build();
    }
}
