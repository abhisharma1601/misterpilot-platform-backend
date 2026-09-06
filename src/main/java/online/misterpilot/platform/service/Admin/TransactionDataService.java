package online.misterpilot.platform.service.Admin;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import online.misterpilot.platform.dto.Admin.response.TransactionDto;
import online.misterpilot.platform.entity.Transaction;
import online.misterpilot.platform.entity.Wallet;
import online.misterpilot.platform.enums.TransactionType;
import online.misterpilot.platform.repository.TransactionRepository;
import online.misterpilot.platform.repository.WalletRepository;

@Service
@RequiredArgsConstructor
public class TransactionDataService {

    final private TransactionRepository transactionRepository;
    final private WalletRepository walletRepository;

    public Page<TransactionDto> getTransactions(Pageable pageable) {
        Page<Transaction> transactions = transactionRepository
                .findByTypeOrderByCreatedAtDesc(TransactionType.RECHARGE, pageable);

        List<Long> walletIds = transactions.stream()
                .map(t -> t.getWallet().getId())
                .distinct()
                .collect(Collectors.toList());

        Map<Long, Long> walletIdToUserId = walletRepository.findByIdIn(walletIds)
                .stream()
                .collect(Collectors.toMap(
                        Wallet::getId,
                        w -> w.getUser().getId()));

        return transactions.map(tx -> toDto(tx, walletIdToUserId));
    }

    private TransactionDto toDto(Transaction transaction, Map<Long, Long> walletIdToUserId) {
        TransactionDto dto = new TransactionDto();
        dto.setId(transaction.getId());
        dto.setAmount(transaction.getAmount());
        dto.setStatus(transaction.getStatus());
        dto.setOrderId(transaction.getOrderId());
        dto.setCreatedAt(transaction.getCreatedAt());
        dto.setUserId(walletIdToUserId.get(transaction.getWallet().getId()));
        return dto;
    }
}
