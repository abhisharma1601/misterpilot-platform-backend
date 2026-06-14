package online.misterpilot.platform.service;

import online.misterpilot.platform.entity.ApiKey;
import online.misterpilot.platform.entity.User;
import online.misterpilot.platform.entity.Wallet;
import online.misterpilot.platform.repository.ApiKeyRepository;
import online.misterpilot.platform.repository.WalletRepository;
import online.misterpilot.platform.util.KeyUtil;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final WalletRepository walletRepository;
    private final KeyUtil keyUtil;

    private static final BigDecimal MINIMUM_BALANCE = new BigDecimal("5.00");

    // ==================== 1. Generate Key ====================

    /**
     * Generates a new API key for the given user.
     * The plain key is returned ONCE — only its SHA-256 hash is persisted.
     */
    @Transactional
    public String generateKey(User user) {
        String plainKey = keyUtil.generateApiKey();
        String hash = keyUtil.hashApiKey(plainKey);
        String placeholder = keyUtil.buildPlaceholder(plainKey);

        ApiKey apiKey = ApiKey.builder()
                .user(user)
                .hashValue(hash)
                .keyPlaceholder(placeholder)
                .active(true)
                .build();
        apiKeyRepository.save(apiKey);

        log.info("API key generated for user id={}, email={}", user.getId(), user.getEmail());

        return plainKey;
    }

    // ==================== 2. Verify Key ====================

    /**
     * Verifies an API key is valid:
     * 1. Key exists and is active
     * 2. The owning user has a wallet with minimum ₹5 balance
     *
     * Used by the Python Gateway before proxying requests to DeepSeek.
     */
    @Transactional(readOnly = true)
    public boolean verifyKey(String plainKey) {
        String hash = keyUtil.hashApiKey(plainKey);

        ApiKey apiKey = apiKeyRepository.findByHashValue(hash).orElse(null);

        if (apiKey == null) {
            log.warn("API key verification failed: key not found");
            return false;
        }

        if (!apiKey.getActive()) {
            log.warn("API key verification failed: key is disabled (id={})", apiKey.getId());
            return false;
        }

        // Check the user has minimum balance of ₹5
        User user = apiKey.getUser();
        Wallet wallet = walletRepository.findByUser(user).orElse(null);

        if (wallet == null) {
            log.warn("API key verification failed: wallet not found for user id={}", user.getId());
            return false;
        }

        if (wallet.getBalance().compareTo(MINIMUM_BALANCE) < 0) {
            log.warn("API key verification failed: insufficient balance (id={}, user={}, balance={})",
                    apiKey.getId(), user.getId(), wallet.getBalance());
            return false;
        }

        log.debug("API key verified: id={}, user={}, balance={}", apiKey.getId(), user.getId(), wallet.getBalance());

        return true;
    }

    // ==================== 3. Get Active Keys ====================

    @Transactional(readOnly = true)
    public List<ApiKey> getActiveKeys(User user) {
        return apiKeyRepository.findByUserAndActive(user, true);
    }

    // ==================== 4. Disable Key ====================

    /**
     * Disables (deactivates) an API key belonging to the given user.
     * The key is not deleted — just marked inactive.
     */
    @Transactional
    public boolean disableKey(int apiKeyId, User user) {
        ApiKey apiKey = apiKeyRepository.findById(apiKeyId).orElse(null);

        if (apiKey == null) {
            log.warn("Disable key failed: key not found");
            return false;
        }

        if (!apiKey.getUser().getId().equals(user.getId())) {
            log.warn("Disable key failed: key id={} does not belong to user id={}",
                    apiKey.getId(), user.getId());
            return false;
        }

        apiKey.setActive(false);
        apiKeyRepository.save(apiKey);

        log.info("API key disabled: id={}, user id={}", apiKey.getId(), user.getId());

        return true;
    }

}
