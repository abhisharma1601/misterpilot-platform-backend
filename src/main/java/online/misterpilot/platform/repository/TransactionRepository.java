package online.misterpilot.platform.repository;

import online.misterpilot.platform.entity.Transaction;
import online.misterpilot.platform.entity.Wallet;
import online.misterpilot.platform.enums.TransactionStatus;
import online.misterpilot.platform.enums.TransactionType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByWalletOrderByCreatedAtDesc(Wallet wallet);

    List<Transaction> findByWalletAndStatus(Wallet wallet, TransactionStatus status);

    Page<Transaction> findByTypeOrderByCreatedAtDesc(TransactionType type, Pageable pageable);

    Optional<Transaction> findByOrderId(String orderId);

    Optional<Transaction> findByPaymentId(String paymentId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.wallet = :wallet AND t.type = :type AND t.status = :status")
    BigDecimal sumAmountByWalletAndTypeAndStatus(@Param("wallet") Wallet wallet,
                                                 @Param("type") TransactionType type,
                                                 @Param("status") TransactionStatus status);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.type = :type AND t.status = :status AND t.createdAt >= :from AND t.createdAt < :to")
    BigDecimal sumAmountByTypeAndStatusBetween(@Param("type") TransactionType type,
                                               @Param("status") TransactionStatus status,
                                               @Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(t) FROM Transaction t " +
           "WHERE t.type = :type AND t.status = :status AND t.createdAt >= :from AND t.createdAt < :to")
    long countByTypeAndStatusBetween(@Param("type") TransactionType type,
                                     @Param("status") TransactionStatus status,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);

    List<Transaction> findTop5ByWalletAndTypeAndStatusOrderByCreatedAtDesc(Wallet wallet, TransactionType type, TransactionStatus status);

    @Modifying
    @Query("UPDATE Transaction t SET t.wallet = NULL WHERE t.wallet.id = :walletId")
    void detachFromWallet(@Param("walletId") Long walletId);
}
