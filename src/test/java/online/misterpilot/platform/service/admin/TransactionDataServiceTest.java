package online.misterpilot.platform.service.admin;

import online.misterpilot.platform.dto.admin.response.TransactionDto;
import online.misterpilot.platform.entity.Transaction;
import online.misterpilot.platform.entity.User;
import online.misterpilot.platform.entity.Wallet;
import online.misterpilot.platform.enums.TransactionStatus;
import online.misterpilot.platform.enums.TransactionType;
import online.misterpilot.platform.repository.TransactionRepository;
import online.misterpilot.platform.repository.WalletRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionDataService")
class TransactionDataServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private WalletRepository walletRepository;
    @InjectMocks private TransactionDataService service;

    private static Transaction tx(long id, Wallet wallet) {
        return Transaction.builder().id(id).wallet(wallet).type(TransactionType.RECHARGE)
                .status(TransactionStatus.SUCCESS).amount(new BigDecimal("99.00")).build();
    }

    @Test
    @DisplayName("transactions of deleted users (no wallet) are listed with a null userId")
    void detachedTransaction() {
        User user = User.builder().id(7L).build();
        Wallet wallet = Wallet.builder().id(3L).user(user).build();
        PageRequest pageable = PageRequest.of(0, 10);
        when(transactionRepository.findByTypeOrderByCreatedAtDesc(TransactionType.RECHARGE, pageable))
                .thenReturn(new PageImpl<>(List.of(tx(1, wallet), tx(2, null)), pageable, 2));
        when(walletRepository.findByIdIn(List.of(3L))).thenReturn(List.of(wallet));

        Page<TransactionDto> result = service.getTransactions(pageable);

        assertThat(result.getContent()).extracting(TransactionDto::getUserId).containsExactly(7L, null);
    }
}
