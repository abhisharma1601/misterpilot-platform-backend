package online.misterpilot.platform.service;

import lombok.RequiredArgsConstructor;
import online.misterpilot.platform.dto.response.ProfileResponse;
import online.misterpilot.platform.entity.User;
import online.misterpilot.platform.entity.Wallet;
import online.misterpilot.platform.repository.ApiKeyRepository;
import online.misterpilot.platform.repository.TokenUsageRepository;
import online.misterpilot.platform.repository.TransactionRepository;
import online.misterpilot.platform.repository.UserRepository;
import online.misterpilot.platform.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final TokenUsageRepository tokenUsageRepository;

    public ProfileResponse getProfile(User user) {
        return ProfileResponse.builder()
                .email(user.getEmail())
                .name(user.getName())
                .joinedAt(user.getCreatedAt().toLocalDate())
                .build();
    }

    @Transactional
    public void deleteAccount(User user) {
        Long userId = user.getId();
        Optional<Wallet> wallet = walletRepository.findByUser(user);
        if (wallet.isPresent()) {
            transactionRepository.detachFromWallet(wallet.get().getId());
        }
        tokenUsageRepository.deleteAllByUserId(userId);
        apiKeyRepository.deleteAllByUserId(userId);
        walletRepository.deleteByUserId(userId);
        userRepository.deleteByIdBulk(userId);
    }
}
